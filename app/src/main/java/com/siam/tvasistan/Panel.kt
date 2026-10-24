package com.siam.tvasistan

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Ekranın altında beliren yarı saydam asistan paneli (erişilebilirlik katmanı —
 * ekstra "diğer uygulamaların üzerinde göster" izni gerektirmez, odak ÇALMAZ).
 */
class Panel(private val servis: AccessibilityService) {
    private val ana = Handler(Looper.getMainLooper())
    private val wm = servis.getSystemService(WindowManager::class.java)
    private var kok: LinearLayout? = null
    private var durumTv: TextView? = null
    private var metinTv: TextView? = null
    private var sonDurum = ""

    fun goster(durum: String) = ana.post {
        if (kok == null) olustur()
        durumYaz(durum)
        metinTv?.text = ""
    }

    fun durum(d: String) = ana.post {
        if (d != sonDurum) durumYaz(d)
    }

    fun metin(kullanici: String, asistan: String) = ana.post {
        val sb = StringBuilder()
        if (kullanici.isNotBlank()) sb.append("Sen: ").append(kirp(kullanici))
        if (asistan.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Asistan: ").append(kirp(asistan))
        }
        metinTv?.text = sb
    }

    fun gizle() = ana.post {
        kok?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        kok = null
        durumTv = null
        metinTv = null
        sonDurum = ""
    }

    private fun durumYaz(d: String) {
        sonDurum = d
        durumTv?.text = "● $d"
    }

    private fun kirp(s: String): String {
        val t = s.trim()
        return if (t.length > 160) "…" + t.takeLast(160) else t
    }

    private fun olustur() {
        val dp = servis.resources.displayMetrics.density
        val genislik = (servis.resources.displayMetrics.widthPixels * 0.62f).toInt()
        val l = LinearLayout(servis).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * dp).toInt()
            setPadding(p, (14 * dp).toInt(), p, (16 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 22 * dp
                setColor(Color.argb(232, 15, 23, 32))
                setStroke((2 * dp).toInt(), Color.rgb(34, 197, 94))
            }
        }
        val d = TextView(servis).apply {
            setTextColor(Color.rgb(34, 197, 94))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        val m = TextView(servis).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, (6 * dp).toInt(), 0, 0)
        }
        l.addView(d)
        l.addView(m)
        val prm = WindowManager.LayoutParams(
            genislik,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (48 * dp).toInt()
        }
        try {
            wm.addView(l, prm)
            kok = l
            durumTv = d
            metinTv = m
        } catch (_: Exception) {
        }
    }
}
