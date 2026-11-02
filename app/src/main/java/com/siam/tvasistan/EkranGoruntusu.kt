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

    fun al(hedefGenislik: Int = 768, kalite: Int = 50): Sonuc {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Sonuc.Hata("Bu Android sürümü ekran görüntüsünü desteklemiyor.")
        // Android en çok saniyede bir görüntüye izin verir
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
        if (!bitti.await(4, TimeUnit.SECONDS)) return Sonuc.Hata("Ekran görüntüsü zaman aşımı.")
        val tam = bmp ?: return Sonuc.Hata(hata ?: "Ekran görüntüsü boş.")

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
