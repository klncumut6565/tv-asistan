package com.siam.tvasistan

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
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

    /** Sese tepki veren, nefes alan mor küre. */
    private class Kure(ctx: Context) : View(ctx) {
        @Volatile var mod = Mod.BAGLANIYOR
        @Volatile private var hedefMik = 0f
        @Volatile private var hedefAsistan = 0f
        @Volatile private var sonMik = 0L
        @Volatile private var sonAsistan = 0L
        private var seviye = 0f
        private var renkKarisim = 0f // 0 = dinleme moru, 1 = konuşma pembesi
        private var t = 0f
        @Volatile private var calisiyor = false
        private val dongu = object : Runnable {
            override fun run() {
                if (!calisiyor) return
                kare()
                postOnAnimation(this)
            }
        }

        private val halePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val cekirdekPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val halkaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val parlakPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null) // BlurMaskFilter için
            halkaPaint.maskFilter = BlurMaskFilter(6f * resources.displayMetrics.density, BlurMaskFilter.Blur.NORMAL)
        }

        fun seviye(s: Float, asistan: Boolean) {
            val v = s.coerceIn(0f, 1f)
            val simdi = SystemClock.elapsedRealtime()
            if (asistan) { hedefAsistan = v; sonAsistan = simdi } else { hedefMik = v; sonMik = simdi }
        }

        // ValueAnimator yerine kare döngüsü: geliştirici ayarlarındaki "animasyon ölçeği 0"dan etkilenmez.
        fun baslat() {
            calisiyor = true
            removeCallbacks(dongu)
            postOnAnimation(dongu)
        }

        fun durdur() {
            calisiyor = false
            removeCallbacks(dongu)
        }

        private fun kare() {
            val simdi = SystemClock.elapsedRealtime()
            // Seviyeler 250 ms güncellenmezse sönümlenir
            val mik = if (simdi - sonMik < 250) hedefMik else 0f
            val ast = if (simdi - sonAsistan < 250) hedefAsistan else 0f
            val hedef = when (mod) {
                Mod.KONUSUYOR -> max(ast, mik * 0.5f)
                Mod.DINLIYOR -> mik
                Mod.BEKLIYOR -> 0f
                else -> 0f
            }
            // hızlı yüksel, yavaş in
            seviye += (hedef - seviye) * (if (hedef > seviye) 0.45f else 0.12f)
            val hedefRenk = if (mod == Mod.KONUSUYOR) 1f else 0f
            renkKarisim += (hedefRenk - renkKarisim) * 0.08f
            t += 0.016f
            invalidate()
        }

        private fun karistir(a: Int, b: Int, f: Float): Int = Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
        )

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val maxR = min(w, h) / 2f

            val nefes = (sin(t * 2.2f) * 0.5f + 0.5f)             // 0..1 yavaş nefes
            val sonuk = mod == Mod.BEKLIYOR || mod == Mod.HATA
            val bag = mod == Mod.BAGLANIYOR
            val alfa = when {
                sonuk -> 0.45f
                bag -> 0.55f + 0.35f * nefes
                else -> 1f
            }

            val mor = Color.rgb(168, 85, 247)       // #A855F7
            val koyuMor = Color.rgb(109, 40, 217)   // #6D28D9
            val pembe = Color.rgb(232, 121, 249)    // #E879F9
            val hataRenk = Color.rgb(244, 63, 94)
            val ana = if (mod == Mod.HATA) hataRenk else karistir(mor, pembe, renkKarisim)

            val cekirdekR = maxR * (0.40f + 0.04f * nefes + 0.16f * seviye)
            val haleR = maxR * (0.62f + 0.06f * nefes + 0.36f * seviye).coerceAtMost(1f)

            // dış hale (yarı saydam)
            halePaint.shader = RadialGradient(
                cx, cy, haleR,
                intArrayOf(
                    Color.argb((150 * alfa).toInt(), Color.red(ana), Color.green(ana), Color.blue(ana)),
                    Color.argb((60 * alfa).toInt(), Color.red(koyuMor), Color.green(koyuMor), Color.blue(koyuMor)),
                    Color.argb(0, Color.red(koyuMor), Color.green(koyuMor), Color.blue(koyuMor)),
                ),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, haleR, halePaint)

            // ses dalgası halkaları
            if (!sonuk) {
                for (i in 0 until 3) {
                    val faz = (t * 0.8f + i / 3f) % 1f
                    val r = cekirdekR + (haleR - cekirdekR) * faz
                    val a = ((1f - faz) * (0.25f + 0.75f * seviye) * 200 * alfa).toInt().coerceIn(0, 255)
                    halkaPaint.color = Color.argb(a, Color.red(ana), Color.green(ana), Color.blue(ana))
                    halkaPaint.strokeWidth = maxR * 0.03f
                    c.drawCircle(cx, cy, r, halkaPaint)
                }
            }

            // çekirdek: parlak merkezden koyu kenara
            val merkez = karistir(Color.rgb(233, 213, 255), Color.rgb(253, 224, 255), renkKarisim)
            cekirdekPaint.shader = RadialGradient(
                cx - cekirdekR * 0.25f, cy - cekirdekR * 0.3f, cekirdekR * 1.3f,
                intArrayOf(
                    Color.argb((235 * alfa).toInt(), Color.red(merkez), Color.green(merkez), Color.blue(merkez)),
                    Color.argb((215 * alfa).toInt(), Color.red(ana), Color.green(ana), Color.blue(ana)),
                    Color.argb((190 * alfa).toInt(), Color.red(koyuMor), Color.green(koyuMor), Color.blue(koyuMor)),
                ),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, cekirdekR, cekirdekPaint)

            // dönen küçük parıltılar (canlılık hissi)
            if (!sonuk) {
                for (i in 0 until 4) {
                    val aci = t * (1.1f + i * 0.25f) + i * (PI.toFloat() / 2f)
                    val r = cekirdekR * (1.12f + 0.25f * seviye)
                    val px = cx + cos(aci) * r
                    val py = cy + sin(aci) * r
                    parlakPaint.color = Color.argb((140 * alfa).toInt(), 245, 230, 255)
                    c.drawCircle(px, py, maxR * (0.025f + 0.02f * seviye), parlakPaint)
                }
            }
        }
    }
}
