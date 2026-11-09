package com.siam.tvasistan

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.view.WindowManager
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** "Kanal listesini göster": öğrenilen kanalları ekranın sağ üstünde yarı saydam bir kartta gösterir (40 sn). */
class KanalListesiPenceresi(private val s: AccessibilityService) {

    companion object {
        private const val SURE_MS = 40_000L
        private val TR = Locale("tr", "TR")
        private val BUYUK = setOf("atv", "trt", "tv", "ntv", "cnn", "tgrt", "dmax", "tlc", "now", "fox", "hd")

        /** "show tv" → "Show TV", "trt 1" → "TRT 1". */
        fun guzelAd(ad: String): String = ad.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { k ->
            val y = k.lowercase(TR)
            when {
                y in BUYUK || Regex("^(tv|trt)\\d+$").matches(y) -> y.uppercase(TR)
                else -> y.replaceFirstChar { it.titlecase(TR) }
            }
        }
    }

    private val ana = Handler(Looper.getMainLooper())
    private val wm = s.getSystemService(WindowManager::class.java)
    private var kart: LinearLayout? = null
    private val gizleyici = Runnable { gizle() }
    @Volatile private var acik = false
    @Volatile private var sonBaslik = ""

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, s.resources.displayMetrics).toInt()

    private fun yazi(metin: String, boy: Float, kalin: Boolean = false, renk: Int = Color.WHITE) = TextView(s).apply {
        text = metin
        setTextColor(renk)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, boy)
        if (kalin) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(2f), 0, dp(2f))
    }

    /** Kartı gösterir; çizildiyse true. */
    fun goster(baslik: String, satirlar: List<String>): Boolean {
        sonBaslik = baslik
        val bitti = CountDownLatch(1)
        var tamam = false
        ana.post {
            try {
                kart?.let { try { wm.removeView(it) } catch (_: Exception) {} }
                val k = LinearLayout(s).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(22f), dp(16f), dp(22f), dp(16f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(18f).toFloat()
                        setColor(Color.argb(225, 24, 12, 44))
                        setStroke(dp(2f), Color.argb(200, 168, 85, 247))
                    }
                }
                k.addView(yazi("📺  $baslik", 20f, kalin = true, renk = Color.rgb(216, 180, 254)))
                // Uzun listeler iki sütuna bölünür
                val sutunSayisi = if (satirlar.size > 14) 2 else 1
                val satir = LinearLayout(s).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8f), 0, 0) }
                val parca = (satirlar.size + sutunSayisi - 1) / sutunSayisi
                for (i in 0 until sutunSayisi) {
                    val sutun = LinearLayout(s).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(28f), 0) }
                    satirlar.drop(i * parca).take(parca).take(20).forEach { sutun.addView(yazi(it, 17f)) }
                    satir.addView(sutun)
                }
                k.addView(satir)
                k.addView(yazi("“Listeyi kapat” diyerek kapatabilirsiniz", 13f, renk = Color.argb(170, 255, 255, 255)))
                val p = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = dp(36f)
                    y = dp(36f)
                }
                wm.addView(k, p)
                kart = k
                acik = true
                tamam = true
                ana.removeCallbacks(gizleyici)
                ana.postDelayed(gizleyici, SURE_MS)
            } catch (_: Exception) {
                tamam = false
            }
            bitti.countDown()
        }
        bitti.await(1500, TimeUnit.MILLISECONDS)
        return tamam
    }

    /** Kart açıksa yeni kayıtlarla günceller (kanal kaydedilince liste anında değişsin). */
    fun yenile(harita: Map<String, String>) {
        if (!acik || harita.isEmpty()) return
        val satirlar = harita.entries.sortedWith(compareBy({ it.value.toIntOrNull() ?: 99999 }, { it.key }))
            .map { "${it.value}. ${guzelAd(it.key)}" }
        goster("Kayıtlı kanallar (${harita.size})", satirlar)
    }

    fun gizle() {
        acik = false
        ana.post {
            ana.removeCallbacks(gizleyici)
            kart?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            kart = null
        }
    }
}
