package com.siam.tvasistan

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * "Ekrana bak": ekran görüntüsü alır (Android 11+, erişilebilirlik servisi), Gemini'ye gönderilecek şekilde
 * KÜÇÜLTÜR ve SIKIŞTIRIR. Token tasarrufu:
 *  - 768 piksel genişliğe küçültülür (1080p → 768×432): Gemini tek görsel parçası (~258 token) sayar.
 *  - JPEG kalite 50 (≈30-50 KB).
 *  - Ekran bir önceki bakıştan beri değişmediyse görsel YENİDEN GÖNDERİLMEZ.
 *  - Simsiyah ekran (kopya korumalı video) gönderilmez.
 */
class EkranGoruntusu(private val s: AccessibilityService) {
    sealed class Sonuc {
        class Yeni(val jpeg: ByteArray, val gen: Int, val yuk: Int) : Sonuc()
        object Ayni : Sonuc()
        object Siyah : Sonuc()
        class Hata(val mesaj: String) : Sonuc()
    }

    private val yurutucu = Executors.newSingleThreadExecutor()
    @Volatile private var sonParmakIzi = 0L
    @Volatile private var sonZaman = 0L

    /**
     * Yerel işlem (yazı tanıma, odak bulma) için ekran görüntüsü: en çok [enCokGenislik] px. Gemini'ye GİTMEZ.
     * Çağıran recycle() etmelidir. Alınamazsa null.
     */
    fun bitmap(enCokGenislik: Int = 1920): Bitmap? {
        val (tam, _) = yakala()
        tam ?: return null
        if (tam.width <= enCokGenislik) return tam
        val oran = enCokGenislik.toFloat() / tam.width
        val k = Bitmap.createScaledBitmap(tam, enCokGenislik, (tam.height * oran).toInt(), true)
        tam.recycle()
        return k
    }

    /** Ham görüntü (tam çözünürlük) ya da hata mesajı. Android en çok saniyede bir görüntüye izin verir. */
    @Synchronized
    private fun yakala(): Pair<Bitmap?, String?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null to "Bu Android sürümü ekran görüntüsünü desteklemiyor."
        val bekle = 1100 - (SystemClock.elapsedRealtime() - sonZaman)
        if (bekle > 0) SystemClock.sleep(bekle)
        sonZaman = SystemClock.elapsedRealtime()

        val bitti = CountDownLatch(1)
        var bmp: Bitmap? = null
        var hata: String? = null
        s.takeScreenshot(Display.DEFAULT_DISPLAY, yurutucu, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                try {
                    val hw = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                    bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hw?.recycle()
                    r.hardwareBuffer.close()
                } catch (e: Exception) { hata = e.message }
                bitti.countDown()
            }
            override fun onFailure(kod: Int) {
                hata = when (kod) {
                    AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
                        "Ekran görüntüsü izni yok: Ayarlar → Erişilebilirlik → Jülide'yi bir kez KAPATIP yeniden AÇIN."
                    AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "Bu ekran korumalı (görüntü alınamaz)."
                    else -> "Ekran görüntüsü alınamadı (kod $kod)."
                }
                bitti.countDown()
            }
        })
        if (!bitti.await(4, TimeUnit.SECONDS)) return null to "Ekran görüntüsü zaman aşımı."
        return bmp to (if (bmp == null) (hata ?: "Ekran görüntüsü boş.") else null)
    }

    fun al(hedefGenislik: Int = 768, kalite: Int = 50): Sonuc {
        val (ham, hataMesaji) = yakala()
        val tam = ham ?: return Sonuc.Hata(hataMesaji ?: "Ekran görüntüsü boş.")

        val oran = hedefGenislik.toFloat() / tam.width
        val kucuk = if (oran < 1f) Bitmap.createScaledBitmap(tam, hedefGenislik, (tam.height * oran).toInt(), true) else tam
        if (kucuk !== tam) tam.recycle()

        val (iz, siyah) = parmakIzi(kucuk)
        if (siyah) { kucuk.recycle(); return Sonuc.Siyah }
        if (iz == sonParmakIzi) { kucuk.recycle(); return Sonuc.Ayni }
        sonParmakIzi = iz

        val cikis = ByteArrayOutputStream()
        kucuk.compress(Bitmap.CompressFormat.JPEG, kalite, cikis)
        val g = kucuk.width; val y = kucuk.height
        kucuk.recycle()
        Log.i("TvAsistan", "Ekran görüntüsü: ${g}x$y, ${cikis.size() / 1024} KB")
        return Sonuc.Yeni(cikis.toByteArray(), g, y)
    }

    companion object {
        /** Odak bulucu için küçük parlaklık ızgarası (0..255), satır satır. */
        fun parlaklikIzgarasi(b: Bitmap, g: Int, y: Int): IntArray {
            val k = Bitmap.createScaledBitmap(b, g, y, true)
            val p = IntArray(g * y)
            k.getPixels(p, 0, g, 0, 0, g, y)
            if (k !== b) k.recycle()
            return IntArray(g * y) { i -> val c = p[i]; (Color.red(c) * 3 + Color.green(c) * 6 + Color.blue(c)) / 10 }
        }

        /** Simsiyah (korumalı video / ekran kapalı) mı? */
        fun siyahMi(b: Bitmap): Boolean {
            var parlak = 0
            for (yy in 0 until 18) for (xx in 0 until 32) {
                val p = b.getPixel(xx * (b.width - 1) / 31, yy * (b.height - 1) / 17)
                if ((Color.red(p) * 3 + Color.green(p) * 6 + Color.blue(p)) / 10 > 24) parlak++
            }
            return parlak < 6
        }
    }

    /** 32×18 örnek noktadan kaba parmak izi (küçük değişimler önemsenmez) + simsiyah kontrolü. */
    private fun parmakIzi(b: Bitmap): Pair<Long, Boolean> {
        var iz = 1125899906842597L
        var parlak = 0
        for (yy in 0 until 18) for (xx in 0 until 32) {
            val p = b.getPixel(xx * (b.width - 1) / 31, yy * (b.height - 1) / 17)
            val l = (Color.red(p) * 3 + Color.green(p) * 6 + Color.blue(p)) / 10
            if (l > 24) parlak++
            iz = 31 * iz + (l / 16) // 16 seviyeli parlaklık: sıkıştırma gürültüsünden etkilenmez
        }
        return iz to (parlak < 6)
    }
}
