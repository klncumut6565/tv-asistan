package com.siam.tvasistan

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Jülide'nin ekran katmanı: sağ altta yarı saydam, sese tepki veren MOR küre + kısa metin balonu.
 * Erişilebilirlik katmanıdır: tüm uygulamaların üstünde kalır, uygulama değişince kaybolmaz,
 * odak ÇALMAZ ve dokunmaları engellemez. Oturum kapanana kadar ekranda kalır.
 */
class Panel(private val servis: AccessibilityService) {

    enum class Mod { BAGLANIYOR, DINLIYOR, KONUSUYOR, BEKLIYOR, HATA }

    private val ana = Handler(Looper.getMainLooper())
    private val wm = servis.getSystemService(WindowManager::class.java)
    private var kok: LinearLayout? = null
    private var kart: LinearLayout? = null
    private var durumTv: TextView? = null
    private var metinTv: TextView? = null
    @Volatile private var kure: Kure? = null
    private var sonDurum = ""
    private val kartGizle = Runnable { kart?.animate()?.alpha(0f)?.setDuration(400)?.start() }

    /** Katmanı açar (yoksa oluşturur) ve durumu yazar. */
    fun goster(durum: String) = ana.post {
        if (kok == null) olustur()
        metinTv?.text = ""
        durumYaz(durum)
        modAyarla(durum)
    }

    fun durum(d: String) = ana.post {
        if (kok == null) return@post
        if (d != sonDurum) {
            durumYaz(d)
            modAyarla(d)
        }
    }

    fun metin(kullanici: String, asistan: String) = ana.post {
        val sb = StringBuilder()
        if (kullanici.isNotBlank()) sb.append("Sen: ").append(kirp(kullanici))
        if (asistan.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Jülide: ").append(kirp(asistan))
        }
        metinTv?.text = sb
        kartGoster(8000)
    }

    /** Mikrofon seviyesi (0..1) — kullanıcı konuşurken küre büyür. Herhangi bir iş parçacığından çağrılabilir. */
    fun mikrofonSeviyesi(s: Float) {
        kure?.seviye(s, asistan = false)
    }

    /** Jülide'nin ses seviyesi (0..1). Herhangi bir iş parçacığından çağrılabilir. */
    fun asistanSeviyesi(s: Float) {
        kure?.seviye(s, asistan = true)
    }

    fun gizle() = ana.post {
        ana.removeCallbacks(kartGizle)
        kure?.durdur()
        kok?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        kok = null
        kart = null
        durumTv = null
        metinTv = null
        kure = null
        sonDurum = ""
    }

    // ------------------------------------------------------------------ iç

    private fun durumYaz(d: String) {
        sonDurum = d
        durumTv?.text = d
        // Sürekli sohbette ekranı kapatmamak için durum balonu birkaç saniye sonra kaybolur;
        // hata ve "tuşa bas" uyarıları daha uzun kalır.
        kartGoster(if (d.startsWith("Hata") || d.contains("tuş")) 9000 else 3500)
    }

    private fun modAyarla(d: String) {
        val m = when {
            d.startsWith("Hata") || d.startsWith("Mikrofon izni") -> Mod.HATA
            d.startsWith("Bağlan") || d.startsWith("Yeniden bağlan") -> Mod.BAGLANIYOR
            d.startsWith("Konuşuyor") -> Mod.KONUSUYOR
            d.contains("tuşa bas") -> Mod.BEKLIYOR
            else -> Mod.DINLIYOR
        }
        kure?.mod = m
    }

    private fun kartGoster(sureMs: Long) {
        val k = kart ?: return
        ana.removeCallbacks(kartGizle)
        k.animate().cancel()
        k.alpha = 1f
        ana.postDelayed(kartGizle, sureMs)
    }

    private fun kirp(s: String): String {
        val t = s.trim()
        return if (t.length > 140) "…" + t.takeLast(140) else t
    }

    private fun olustur() {
        val dp = servis.resources.displayMetrics.density
        val ekranG = servis.resources.displayMetrics.widthPixels

        val k = LinearLayout(servis).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * dp).toInt()
            setPadding(p, (10 * dp).toInt(), p, (12 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 20 * dp
                setColor(Color.argb(170, 30, 12, 58))          // koyu mor, yarı saydam
                setStroke((1.5f * dp).toInt(), Color.argb(150, 192, 132, 252))
            }
            alpha = 0f
        }
        val d = TextView(servis).apply {
            setTextColor(Color.rgb(216, 180, 254))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        val m = TextView(servis).apply {
            setTextColor(Color.argb(240, 255, 255, 255))
            textSize = 16f
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }
        k.addView(d)
        k.addView(m)

        val kureBoyut = (150 * dp).toInt()
        val c = Kure(servis)

        val l = LinearLayout(servis).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        l.addView(k, LinearLayout.LayoutParams((ekranG * 0.38f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = (4 * dp).toInt()
        })
        l.addView(c, LinearLayout.LayoutParams(kureBoyut, kureBoyut))

        val prm = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = (24 * dp).toInt()
            y = (24 * dp).toInt()
        }
        try {
            wm.addView(l, prm)
            kok = l
            kart = k
            durumTv = d
            metinTv = m
            kure = c
            c.baslat()
        } catch (_: Exception) {
        }
    }

    /**
     * Sese tepki veren, nefes alan mor küre.
     * Performans: donanım hızlandırmalı çizim (yazılım katmanı/blur YOK), gradyanlar boyut başına
     * bir kez üretilir ve her karede yalnız matrisle ölçeklenir (kare başına sıfır nesne), kare hızı
     * 30 fps; Jülide beklerken/sessizken 10 fps.
     */
    private class Kure(ctx: Context) : View(ctx) {
        @Volatile var mod = Mod.BAGLANIYOR
        @Volatile private var hedefMik = 0f
        @Volatile private var hedefAsistan = 0f
        @Volatile private var sonMik = 0L
        @Volatile private var sonAsistan = 0L
        private var seviye = 0f
        private var renkKarisim = 0f // 0 = dinleme moru, 1 = konuşma pembesi
        private var t = 0f
        private var sonKare = 0L
        @Volatile private var calisiyor = false
        private val dongu = object : Runnable {
            override fun run() {
                if (!calisiyor) return
                kare()
                val sakin = mod == Mod.BEKLIYOR || mod == Mod.HATA
                postDelayed(this, if (sakin) 100L else 33L)
            }
        }

        private val halePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val cekirdekPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val halkaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val parlakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 230, 255) }
        private val matris = Matrix()

        // Palet: 0 = mor (dinliyor), 1 = pembe (konuşuyor), 2 = kırmızı (hata)
        private val anaRenkler = intArrayOf(Color.rgb(168, 85, 247), Color.rgb(232, 121, 249), Color.rgb(244, 63, 94))
        private val merkezRenkler = intArrayOf(Color.rgb(233, 213, 255), Color.rgb(253, 224, 255), Color.rgb(255, 228, 230))
        private val koyuMor = Color.rgb(109, 40, 217)
        private var haleGradyan = arrayOfNulls<RadialGradient>(3)
        private var cekirdekGradyan = arrayOfNulls<RadialGradient>(3)
        private var olcuR = 1f

        fun seviye(s: Float, asistan: Boolean) {
            val v = s.coerceIn(0f, 1f)
            val simdi = SystemClock.elapsedRealtime()
            if (asistan) { hedefAsistan = v; sonAsistan = simdi } else { hedefMik = v; sonMik = simdi }
        }

        fun baslat() {
            calisiyor = true
            sonKare = SystemClock.elapsedRealtime()
            removeCallbacks(dongu)
            post(dongu)
        }

        fun durdur() {
            calisiyor = false
            removeCallbacks(dongu)
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            olcuR = maxOf(1f, min(w, h) / 2f)
            for (i in 0 until 3) {
                val a = anaRenkler[i]
                haleGradyan[i] = RadialGradient(
                    0f, 0f, olcuR,
                    intArrayOf(renk(150, a), renk(60, koyuMor), renk(0, koyuMor)),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
                cekirdekGradyan[i] = RadialGradient(
                    0f, 0f, olcuR,
                    intArrayOf(renk(235, merkezRenkler[i]), renk(215, a), renk(190, koyuMor)),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
            }
        }

        private fun renk(alfa: Int, c: Int) = Color.argb(alfa, Color.red(c), Color.green(c), Color.blue(c))

        private fun kare() {
            val simdi = SystemClock.elapsedRealtime()
            val dt = ((simdi - sonKare).coerceIn(0L, 200L)) / 1000f
            sonKare = simdi
            // Seviyeler 250 ms güncellenmezse sönümlenir
            val mik = if (simdi - sonMik < 250) hedefMik else 0f
            val ast = if (simdi - sonAsistan < 250) hedefAsistan else 0f
            val hedef = when (mod) {
                Mod.KONUSUYOR -> max(ast, mik * 0.5f)
                Mod.DINLIYOR -> mik
                else -> 0f
            }
            // hızlı yüksel, yavaş in (kare hızından bağımsız)
            val k = if (hedef > seviye) 18f else 4.5f
            seviye += (hedef - seviye) * min(1f, k * dt)
            val hedefRenk = if (mod == Mod.KONUSUYOR) 1f else 0f
            renkKarisim += (hedefRenk - renkKarisim) * min(1f, 3f * dt)
            t += dt
            invalidate()
        }

        /** Önceden üretilmiş gradyanı (cx, cy) merkezine r yarıçapla yerleştirip daire çizer. */
        private fun gradyanDaire(c: Canvas, p: Paint, g: RadialGradient?, cx: Float, cy: Float, merkezX: Float, merkezY: Float,
                                 gradyanR: Float, r: Float, alfa: Float) {
            if (g == null || alfa <= 0.01f) return
            matris.setScale(gradyanR / olcuR, gradyanR / olcuR)
            matris.postTranslate(merkezX, merkezY)
            g.setLocalMatrix(matris)
            p.shader = g
            p.alpha = (255 * alfa).toInt().coerceIn(0, 255)
            c.drawCircle(cx, cy, r, p)
        }

        override fun onDraw(c: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val maxR = min(width, height) / 2f

            val nefes = (sin(t * 2.2f) * 0.5f + 0.5f)             // 0..1 yavaş nefes
            val sonuk = mod == Mod.BEKLIYOR || mod == Mod.HATA
            val alfa = when {
                sonuk -> 0.45f
                mod == Mod.BAGLANIYOR -> 0.55f + 0.35f * nefes
                else -> 1f
            }
            val hata = mod == Mod.HATA
            val pembe = if (hata) 0f else renkKarisim

            val cekirdekR = maxR * (0.40f + 0.04f * nefes + 0.16f * seviye)
            val haleR = maxR * (0.62f + 0.06f * nefes + 0.36f * seviye).coerceAtMost(1f)

            // dış hale: mor ve pembe katmanlar karışım oranında üst üste
            if (hata) {
                gradyanDaire(c, halePaint, haleGradyan[2], cx, cy, cx, cy, haleR, haleR, alfa)
            } else {
                gradyanDaire(c, halePaint, haleGradyan[0], cx, cy, cx, cy, haleR, haleR, alfa * (1f - pembe))
                gradyanDaire(c, halePaint, haleGradyan[1], cx, cy, cx, cy, haleR, haleR, alfa * pembe)
            }

            // ses dalgası halkaları: blur yerine geniş+ince iki çizgiyle yumuşak parıltı
            if (!sonuk) {
                val a0 = anaRenkler[0]
                val a1 = anaRenkler[1]
                val rr = (Color.red(a0) + (Color.red(a1) - Color.red(a0)) * pembe).toInt()
                val gg = (Color.green(a0) + (Color.green(a1) - Color.green(a0)) * pembe).toInt()
                val bb = (Color.blue(a0) + (Color.blue(a1) - Color.blue(a0)) * pembe).toInt()
                for (i in 0 until 3) {
                    val faz = (t * 0.8f + i / 3f) % 1f
                    val r = cekirdekR + (haleR - cekirdekR) * faz
                    val a = (1f - faz) * (0.25f + 0.75f * seviye) * alfa
                    halkaPaint.color = Color.argb((70 * a).toInt().coerceIn(0, 255), rr, gg, bb)
                    halkaPaint.strokeWidth = maxR * 0.08f
                    c.drawCircle(cx, cy, r, halkaPaint)
                    halkaPaint.color = Color.argb((200 * a).toInt().coerceIn(0, 255), rr, gg, bb)
                    halkaPaint.strokeWidth = maxR * 0.025f
                    c.drawCircle(cx, cy, r, halkaPaint)
                }
            }

            // çekirdek: parlak merkezden koyu kenara (ışık sol üstten)
            val gx = cx - cekirdekR * 0.25f
            val gy = cy - cekirdekR * 0.3f
            if (hata) {
                gradyanDaire(c, cekirdekPaint, cekirdekGradyan[2], cx, cy, gx, gy, cekirdekR * 1.3f, cekirdekR, alfa)
            } else {
                gradyanDaire(c, cekirdekPaint, cekirdekGradyan[0], cx, cy, gx, gy, cekirdekR * 1.3f, cekirdekR, alfa * (1f - pembe))
                gradyanDaire(c, cekirdekPaint, cekirdekGradyan[1], cx, cy, gx, gy, cekirdekR * 1.3f, cekirdekR, alfa * pembe)
            }

            // dönen küçük parıltılar (canlılık hissi)
            if (!sonuk) {
                parlakPaint.alpha = (140 * alfa).toInt()
                val r = cekirdekR * (1.12f + 0.25f * seviye)
                val nr = maxR * (0.025f + 0.02f * seviye)
                for (i in 0 until 4) {
                    val aci = t * (1.1f + i * 0.25f) + i * (PI.toFloat() / 2f)
                    c.drawCircle(cx + cos(aci) * r, cy + sin(aci) * r, nr, parlakPaint)
                }
            }
        }
    }
}
