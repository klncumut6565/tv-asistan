package com.siam.tvasistan

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Jülide'nin ekran katmanı — TMGD web asistanındaki VoiceOrbOverlay'in Android karşılığı.
 *
 * Sohbet açıkken TÜM EKRANI kaplayan yarı saydam "enerji küresi": ortada parlak çekirdek ve
 * parçacık bulutu, sese göre şekil değiştiren 7 yörünge halkası, yanlardan akan ışık iplikleri,
 * uçuşan kıvılcımlar ve altta durum etiketi. Yazılı sohbet balonu YOKTUR.
 *
 * Erişilebilirlik katmanıdır: tüm uygulamaların üstünde kalır, uygulama değişince kaybolmaz,
 * odak çalmaz, dokunma/kumanda olaylarını engellemez. Oturum kapanınca yumuşakça söner ve kalkar.
 */
class Panel(private val servis: AccessibilityService) {

    enum class Mod { BAGLANIYOR, YENIDEN, DINLIYOR, ISLEM, KONUSUYOR, BEKLIYOR, HATA, BILGI }

    private val ana = Handler(Looper.getMainLooper())
    private val wm = servis.getSystemService(WindowManager::class.java)
    @Volatile private var kure: KureKatmani? = null
    private var numara: NumaraKatmani? = null
    private val numaraGizleyici = Runnable { numaralariGizle() }
    private var sonDurum = ""

    /** Katmanı açar (yoksa oluşturur) ve durumu ayarlar. */
    fun goster(durum: String) = ana.post {
        if (kure == null) olustur()
        kure?.ac()
        durumUygula(durum)
    }

    fun durum(d: String) = ana.post {
        if (kure == null || d == sonDurum) return@post
        durumUygula(d)
    }

    /** TMGD'deki gibi yazılı sohbet gösterilmez; konuşma metni yalnızca modele gider. */
    @Suppress("UNUSED_PARAMETER")
    fun metin(kullanici: String, asistan: String) = Unit

    /** Mikrofon seviyesi (0..1). Herhangi bir iş parçacığından çağrılabilir. */
    fun mikrofonSeviyesi(s: Float) {
        kure?.mikrofon(s)
    }

    /** Jülide'nin ses seviyesi (0..1). Herhangi bir iş parçacığından çağrılabilir. */
    fun asistanSeviyesi(s: Float) {
        kure?.asistan(s)
    }

    /** Küreyi ~0,5 sn'de söndürüp katmanı kaldırır. */
    fun gizle() = ana.post {
        val k = kure ?: return@post
        sonDurum = ""
        k.kapat {
            if (kure === k) {
                try { wm.removeView(k) } catch (_: Exception) {}
                kure = null
            }
        }
    }

    /** Tıklanabilir öğelerin üstüne numara rozetleri çizer; 30 sn sonra kendiliğinden kalkar. */
    fun numaralar(l: List<Pair<Int, android.graphics.Rect>>) {
        ana.post {
        val n = numara ?: NumaraKatmani(servis).also { v ->
            try { wm.addView(v, tamEkran()); numara = v } catch (_: Exception) { return@post }
        }
        n.ogeler = l
        n.invalidate()
        ana.removeCallbacks(numaraGizleyici)
        ana.postDelayed(numaraGizleyici, 30_000)
        }
    }

    fun numaralariGizle() {
        ana.post {
            ana.removeCallbacks(numaraGizleyici)
            numara?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            numara = null
        }
    }

    private fun tamEkran() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    /** Numara rozetleri: öğenin çevresinde ince mor çerçeve, sol üstte mor rozet içinde numara. */
    private class NumaraKatmani(ctx: Context) : View(ctx) {
        @Volatile var ogeler: List<Pair<Int, android.graphics.Rect>> = emptyList()
        private val d = resources.displayMetrics.density
        private val konum = IntArray(2)
        private val cerceve = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f * d; color = Color.argb(200, 192, 132, 252)
        }
        private val rozet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(126, 34, 206) }
        private val kenar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f * d; color = Color.WHITE
        }
        private val yazi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 15f * d
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        override fun onDraw(c: Canvas) {
            getLocationOnScreen(konum) // ekran koordinatlarını pencere koordinatına çevir
            val ox = -konum[0].toFloat()
            val oy = -konum[1].toFloat()
            for ((no, r) in ogeler) {
                val l = r.left + ox
                val t = r.top + oy
                c.drawRoundRect(l, t, r.right + ox, r.bottom + oy, 6f * d, 6f * d, cerceve)
                val metin = no.toString()
                val gen = max(24f * d, yazi.measureText(metin) + 12f * d)
                val yuk = 22f * d
                val bx = l.coerceAtLeast(0f)
                val by = (t - yuk / 2f).coerceAtLeast(0f)
                c.drawRoundRect(bx, by, bx + gen, by + yuk, yuk / 2f, yuk / 2f, rozet)
                c.drawRoundRect(bx, by, bx + gen, by + yuk, yuk / 2f, yuk / 2f, kenar)
                c.drawText(metin, bx + gen / 2f, by + yuk / 2f - (yazi.descent() + yazi.ascent()) / 2f, yazi)
            }
        }
    }

    // ------------------------------------------------------------------ iç

    private fun durumUygula(d: String) {
        sonDurum = d
        val k = kure ?: return
        val (m, etiket) = when {
            d.startsWith("Mikrofon izni") -> Mod.HATA to "MİKROFON İZNİ YOK"
            d.startsWith("Hata") -> Mod.HATA to ("HATA · " + d.removePrefix("Hata:").trim()).uppercase(TR).take(70)
            d.startsWith("Yeniden bağlan") -> Mod.YENIDEN to "YENİDEN BAĞLANIYOR"
            d.startsWith("Bağlan") -> Mod.BAGLANIYOR to "BAĞLANIYOR"
            d.startsWith("Konuşuyor") -> Mod.KONUSUYOR to "KONUŞUYOR"
            d.startsWith("İşlem") -> Mod.ISLEM to "İŞLEM YAPIYOR"
            d.contains("tuşa bas") -> Mod.BEKLIYOR to "VİDEO AÇIK · KONUŞMAK İÇİN TUŞA BAS"
            d.contains("dinliyor", true) || d.startsWith("Dinliyorum") -> Mod.DINLIYOR to "DİNLİYOR"
            else -> Mod.BILGI to d.uppercase(TR).take(70)
        }
        k.mod = m
        k.etiket = etiket
    }

    private fun olustur() {
        val k = KureKatmani(servis)
        val prm = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(k, prm)
            kure = k
        } catch (_: Exception) {
        }
    }

    companion object {
        private val TR = Locale("tr", "TR")
    }

    /**
     * Tam ekran küre çizimi. Ölçüler dp cinsindendir (web sürümündeki CSS px ile aynı oranlar).
     * Performans: 30 fps; blur/gölge kullanılmaz, parıltı katmanlı çizgi + ADD karıştırma ile
     * yapılır (web'deki "lighter"). Boyalar ve yol nesnesi yeniden kullanılır.
     */
    private class KureKatmani(ctx: Context) : View(ctx) {
        @Volatile var mod = Mod.BAGLANIYOR
        @Volatile var etiket = "BAĞLANIYOR"
        @Volatile private var hedefMik = 0f
        @Volatile private var hedefAsistan = 0f
        @Volatile private var sonMik = 0L
        @Volatile private var sonAsistan = 0L

        private val yogunluk = resources.displayMetrics.density

        /** Web'deki KURE_OPAKLIK: küre katmanının genel saydamlığı (alt etiket etkilenmez). */
        private val kureOpaklik = 0.6f

        private class Halka(val rx: Float, val oran: Float, val egim: Float, val hiz: Float,
                            val f1: Float, val f2: Float, val k1: Int, val k2: Int, val bant: Int)
        private class Parcacik(val r: Float, val a: Float, val w: Float, val boy: Float, val ph: Float)
        private class Kivilcim(var x: Float, var y: Float, val vx: Float, val vy: Float, var omur: Float, val maks: Float)

        private val halkalar: List<Halka>
        private val parcaciklar: List<Parcacik>
        private val kivilcimlar = ArrayList<Kivilcim>(80)

        init {
            // Web'dekiyle aynı tohum → aynı desen
            var tohum = 20261022L
            fun rnd(): Float {
                tohum = (tohum * 1664525L + 1013904223L) and 0xFFFFFFFFL
                return (tohum.toDouble() / 4294967296.0).toFloat()
            }
            halkalar = List(7) { i ->
                val rx = 1.4f + rnd() * 0.65f
                val oran = 0.55f + rnd() * 0.4f
                val egim = rnd() * PI.toFloat()
                val hiz = (if (rnd() < 0.5f) -1f else 1f) * (0.05f + rnd() * 0.13f)
                val f1 = rnd() * PI.toFloat() * 2f
                val f2 = rnd() * PI.toFloat() * 2f
                val k1 = 2 + (rnd() * 2).toInt()
                val k2 = 4 + (rnd() * 2).toInt()
                Halka(rx, oran, egim, hiz, f1, f2, k1, k2, i % 3)
            }
            parcaciklar = List(150) {
                val r = rnd().toDouble().pow(1.8).toFloat() // merkeze yoğunlaşan dağılım
                val a = rnd() * PI.toFloat() * 2f
                val w = (rnd() - 0.5f) * 0.6f
                val boy = 0.6f + rnd() * 1.2f
                val ph = rnd() * PI.toFloat() * 2f
                Parcacik(r, a, w, boy, ph)
            }
        }

        // Animasyon durumu
        private var enerji = 0f
        private var ton = 272f
        private var doyg = 82f
        private var yogun = 0f
        private var t = 0f
        private val bantlar = floatArrayOf(0f, 0f, 0f)
        private var giris = 0f          // 0..1 açılış/kapanış solması
        private var kapaniyor = false
        private var kapaninca: (() -> Unit)? = null
        private var onceki = 0L
        @Volatile private var calisiyor = false
        private var kullaniciKonusuyor = false

        private val dongu = object : Runnable {
            override fun run() {
                if (!calisiyor) return
                adim()
                invalidate()
                if (calisiyor) postDelayed(this, 33L) // ~30 fps
            }
        }

        // Boyalar (yeniden kullanılır)
        private val dolgu = Paint(Paint.ANTI_ALIAS_FLAG)
        private val cizgi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val nokta = Paint(Paint.ANTI_ALIAS_FLAG)
        private val yazi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.3f
            color = Color.rgb(243, 232, 255)
        }
        private val hap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 59, 7, 100) }
        private val ekle = PorterDuffXfermode(PorterDuff.Mode.ADD)
        private val yol = Path()

        fun mikrofon(s: Float) { hedefMik = s.coerceIn(0f, 1f); sonMik = SystemClock.elapsedRealtime() }
        fun asistan(s: Float) { hedefAsistan = s.coerceIn(0f, 1f); sonAsistan = SystemClock.elapsedRealtime() }

        fun ac() {
            kapaniyor = false
            kapaninca = null
            if (!calisiyor) {
                calisiyor = true
                onceki = SystemClock.elapsedRealtime()
                post(dongu)
            }
        }

        fun kapat(bitince: () -> Unit) {
            kapaniyor = true
            kapaninca = bitince
            if (!calisiyor) bitince()
        }

        override fun onDetachedFromWindow() {
            calisiyor = false
            removeCallbacks(dongu)
            super.onDetachedFromWindow()
        }

        private fun yumusat(m: Float, h: Float, dt: Float, yuk: Float, son: Float): Float {
            val tau = if (h > m) yuk else son
            return m + (h - m) * (1f - exp(-dt / tau))
        }

        /** Ton farkını en kısa yoldan yumuşat (ör. 350° → 10°). */
        private fun tonYumusat(m: Float, h: Float, dt: Float): Float {
            var fark = ((h - m + 540f) % 360f) - 180f
            fark *= 1f - exp(-dt / 0.35f)
            return (m + fark + 360f) % 360f
        }

        private fun adim() {
            val simdi = SystemClock.elapsedRealtime()
            val dt = ((simdi - onceki) / 1000f).coerceIn(0.001f, 0.1f)
            onceki = simdi
            t += dt
            val sn = simdi / 1000f

            val mik = if (simdi - sonMik < 250) hedefMik else 0f
            val ast = if (simdi - sonAsistan < 250) hedefAsistan else 0f
            kullaniciKonusuyor = mod == Mod.DINLIYOR && mik > 0.12f

            // 1) Hedef enerji
            var hedef = 0.04f
            var hb0 = 0.3f
            var hb1 = 0.3f
            var hb2 = 0.3f
            when (mod) {
                Mod.KONUSUYOR -> {
                    hedef = ast
                    // Frekans analizörü yok: seviyeye bağlı, birbirinden kaymış dalgalanma
                    hb0 = ast * (0.85f + 0.15f * sin(sn * 7f))
                    hb1 = ast * (0.75f + 0.25f * sin(sn * 11f + 1f))
                    hb2 = ast * (0.6f + 0.4f * sin(sn * 17f + 2f))
                }
                Mod.DINLIYOR -> hedef = max(0.03f, mik)
                Mod.ISLEM -> hedef = 0.14f + 0.08f * sin(sn * 3.2f)
                Mod.BAGLANIYOR, Mod.YENIDEN -> hedef = 0.05f + 0.04f * sin(sn * 2.5f)
                else -> {}
            }
            enerji = yumusat(enerji, hedef, dt, 0.05f, 0.18f)
            bantlar[0] = yumusat(bantlar[0], hb0, dt, 0.06f, 0.2f)
            bantlar[1] = yumusat(bantlar[1], hb1, dt, 0.06f, 0.2f)
            bantlar[2] = yumusat(bantlar[2], hb2, dt, 0.06f, 0.2f)

            // Durum → [ton, doygunluk, yoğunluk]  (web PALET ile aynı)
            val hTon: Float
            val hDoyg: Float
            val hYogun: Float
            when {
                mod == Mod.KONUSUYOR -> { hTon = 282f; hDoyg = 88f; hYogun = 1f }
                kullaniciKonusuyor -> { hTon = 188f; hDoyg = 80f; hYogun = 0.95f } // turkuaz: senin sesin
                mod == Mod.DINLIYOR -> { hTon = 272f; hDoyg = 82f; hYogun = 0.72f }
                mod == Mod.ISLEM -> { hTon = 215f; hDoyg = 85f; hYogun = 0.85f }
                mod == Mod.HATA -> { hTon = 0f; hDoyg = 80f; hYogun = 0.6f }
                mod == Mod.BEKLIYOR -> { hTon = 268f; hDoyg = 45f; hYogun = 0.42f }
                mod == Mod.BILGI -> { hTon = 272f; hDoyg = 60f; hYogun = 0.6f }
                else -> { hTon = 265f; hDoyg = 25f; hYogun = 0.5f } // bağlanıyor
            }
            ton = tonYumusat(ton, hTon, dt)
            doyg = yumusat(doyg, hDoyg, dt, 0.3f, 0.3f)
            yogun = yumusat(yogun, hYogun, dt, 0.4f, 0.4f)

            // Açılış / kapanış solması (0,5 sn)
            giris = if (kapaniyor) max(0f, giris - dt / 0.5f) else min(1f, giris + dt / 0.5f)
            if (kapaniyor && giris <= 0f) {
                calisiyor = false
                val f = kapaninca
                kapaninca = null
                post { f?.invoke() }
            }

            // Kıvılcımlar (ses arttıkça çoğalır)
            val wd = width / yogunluk
            val hd = height / yogunluk
            val r = max(56f, min(210f, min(wd, hd) * 0.17f))
            val cx = wd / 2f
            val cy = hd * 0.46f
            val uret = 0.6f + enerji * 6f
            if (!kapaniyor && Math.random() < uret * dt * 10 && kivilcimlar.size < 70) {
                val a = (Math.random() * PI * 2).toFloat()
                val r0 = r * (1f + Math.random().toFloat() * 0.8f)
                val v = r * (0.08f + Math.random().toFloat() * 0.25f)
                kivilcimlar.add(Kivilcim(cx + cos(a) * r0, cy + sin(a) * r0, cos(a) * v, sin(a) * v, 0f, 1.5f + Math.random().toFloat() * 2f))
            }
            val it = kivilcimlar.iterator()
            while (it.hasNext()) {
                val s = it.next()
                s.omur += dt
                if (s.omur > s.maks) { it.remove(); continue }
                s.x += s.vx * dt
                s.y += s.vy * dt
            }
        }

        /** hsla → ARGB. h: derece, s/l: yüzde, a: 0..1 */
        private fun hsla(h: Float, s: Float, l: Float, a: Float): Int {
            val ss = (s / 100f).coerceIn(0f, 1f)
            val ll = (l / 100f).coerceIn(0f, 1f)
            val c = (1f - abs(2f * ll - 1f)) * ss
            val hh = (((h % 360f) + 360f) % 360f) / 60f
            val x = c * (1f - abs(hh % 2f - 1f))
            var r1 = 0f
            var g1 = 0f
            var b1 = 0f
            when (hh.toInt()) {
                0 -> { r1 = c; g1 = x }
                1 -> { r1 = x; g1 = c }
                2 -> { g1 = c; b1 = x }
                3 -> { g1 = x; b1 = c }
                4 -> { r1 = x; b1 = c }
                else -> { r1 = c; b1 = x }
            }
            val m = ll - c / 2f
            return Color.argb(
                (a.coerceIn(0f, 1f) * 255).toInt(),
                ((r1 + m) * 255).toInt().coerceIn(0, 255),
                ((g1 + m) * 255).toInt().coerceIn(0, 255),
                ((b1 + m) * 255).toInt().coerceIn(0, 255),
            )
        }

        override fun onDraw(c: Canvas) {
            if (giris <= 0.001f) return
            val yo = yogun * giris * kureOpaklik
            val wd = width / yogunluk
            val hd = height / yogunluk
            c.save()
            c.scale(yogunluk, yogunluk) // bundan sonra her ölçü dp

            val e = enerji
            val cx = wd / 2f
            val cy = hd * 0.46f
            val r = max(56f, min(210f, min(wd, hd) * 0.17f))

            if (yo > 0.003f) {
                // 3) Pus / bulutsu (kenarlara doğru tamamen şeffaf)
                dolgu.xfermode = null
                dolgu.shader = RadialGradient(
                    cx, cy, r * 3.6f,
                    intArrayOf(hsla(ton, doyg, 32f, 0.2f * yo), hsla(ton, doyg, 32f, 0.2f * yo), hsla(ton, doyg, 26f, 0.08f * yo), hsla(ton, doyg, 20f, 0f)),
                    floatArrayOf(0f, 0.11f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, wd, hd, dolgu)
                for (b in 0 until 3) {
                    val aci = t * 0.07f * (b + 1) + b * 2.1f
                    val bx = cx + cos(aci) * r * 1.7f
                    val by = cy + sin(aci * 1.3f) * r * 1.2f
                    dolgu.shader = RadialGradient(
                        bx, by, r * 1.9f,
                        intArrayOf(hsla(ton + 12f, doyg, 48f, 0.08f * yo), hsla(ton + 12f, doyg, 40f, 0f)),
                        null, Shader.TileMode.CLAMP
                    )
                    c.drawCircle(bx, by, r * 1.9f, dolgu)
                }

                // 4) Yanlardan küreye akan ışık iplikleri
                cizgi.xfermode = ekle
                cizgi.pathEffect = null
                for (s in 0 until 4) {
                    val taraf = if (s < 2) -1f else 1f
                    val kenarX = if (taraf < 0) 0f else wd
                    val hedefX = cx + taraf * r * 1.25f
                    val yOff = (if (s % 2 == 0) -1f else 1f) * r * (0.25f + 0.1f * s)
                    val dalga = sin(t * 0.6f + s) * r * 0.5f * (1f + e)
                    yol.rewind()
                    yol.moveTo(kenarX, cy + yOff * 2.2f + dalga)
                    yol.cubicTo(
                        (kenarX + hedefX) / 2f, cy + yOff * 3f - dalga,
                        (kenarX + hedefX * 2f) / 3f, cy + yOff - dalga * 0.5f,
                        hedefX, cy + yOff * 0.6f
                    )
                    cizgi.color = hsla(ton + 15f, doyg, 72f, 0.05f * yo); cizgi.strokeWidth = 7f
                    c.drawPath(yol, cizgi)
                    cizgi.color = hsla(ton + 15f, doyg, 82f, 0.22f * yo); cizgi.strokeWidth = 1.2f
                    c.drawPath(yol, cizgi)
                }

                // 5) Küre gövdesi (yarı saydam koyu disk)
                dolgu.xfermode = null
                dolgu.shader = RadialGradient(
                    cx - r * 0.2f, cy - r * 0.25f, r * 1.1f,
                    intArrayOf(hsla(ton - 12f, 70f, 34f, 0.3f * yo), hsla(ton - 12f, 70f, 34f, 0.3f * yo), hsla(ton - 18f, 75f, 16f, 0.38f * yo)),
                    floatArrayOf(0f, 0.09f, 1f), Shader.TileMode.CLAMP
                )
                c.drawCircle(cx, cy, r, dolgu)

                // Kenar parıltısı
                cizgi.color = hsla(ton + 10f, 90f, 78f, 0.07f * yo); cizgi.strokeWidth = 10f
                c.drawCircle(cx, cy, r, cizgi)
                cizgi.color = hsla(ton + 10f, 90f, 85f, (0.45f + e * 0.3f) * yo); cizgi.strokeWidth = 1.5f
                c.drawCircle(cx, cy, r, cizgi)

                // 6) İç kesikli halka (döner)
                cizgi.pathEffect = DashPathEffect(floatArrayOf(r * 0.22f, r * 0.16f), t * r * 0.25f)
                cizgi.color = hsla(ton + 40f, 90f, 72f, 0.5f * yo); cizgi.strokeWidth = 1.5f
                c.drawCircle(cx, cy, r * (0.62f + e * 0.05f), cizgi)
                cizgi.pathEffect = null

                // 7) Parlak çekirdek + parçacık bulutu
                val rc = r * (0.26f + 0.24f * e)
                dolgu.xfermode = ekle
                dolgu.shader = RadialGradient(
                    cx, cy, rc,
                    intArrayOf(Color.argb((0.95f * yo * 255).toInt().coerceIn(0, 255), 255, 255, 255), hsla(ton + 40f, 100f, 82f, 0.45f * yo), hsla(ton + 40f, 100f, 70f, 0f)),
                    floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
                )
                c.drawCircle(cx, cy, rc, dolgu)
                dolgu.shader = null

                val bulutR = r * 0.48f * (1f + e * 0.45f)
                nokta.xfermode = ekle
                nokta.color = hsla(ton + 50f, 100f, 90f, 0.7f * yo)
                for (p in parcaciklar) {
                    val a = p.a + t * p.w
                    val pr = p.r * bulutR
                    val parla = 0.5f + 0.5f * sin(t * 3f + p.ph)
                    if (parla < 0.25f) continue
                    val boy = p.boy * (0.6f + parla * 0.6f)
                    val x = cx + cos(a) * pr
                    val y = cy + sin(a) * pr
                    c.drawRect(x - boy / 2f, y - boy / 2f, x + boy / 2f, y + boy / 2f, nokta)
                }

                // 8) Yörünge halkaları — sese göre şekil değiştirir
                val n = 96
                for (i in halkalar.indices) {
                    val h = halkalar[i]
                    val rot = h.egim + t * h.hiz
                    val cosR = cos(rot)
                    val sinR = sin(rot)
                    val amp = 0.035f + e * (0.12f + 0.14f * bantlar[h.bant])
                    val rx = h.rx * r * (1f + e * 0.08f)
                    val ry = rx * h.oran
                    yol.rewind()
                    for (j in 0..n) {
                        val th = (j.toFloat() / n) * PI.toFloat() * 2f
                        val dz = 1f + amp * (0.6f * sin(h.k1 * th + t * 0.9f + h.f1) + 0.4f * sin(h.k2 * th - t * 1.3f + h.f2))
                        val ex = cos(th) * rx * dz
                        val ey = sin(th) * ry * dz
                        val x = cx + ex * cosR - ey * sinR
                        val y = cy + ex * sinR + ey * cosR
                        if (j == 0) yol.moveTo(x, y) else yol.lineTo(x, y)
                    }
                    val hh = ton + i * 5f
                    cizgi.color = hsla(hh, 95f, 70f, 0.045f * yo); cizgi.strokeWidth = 9f
                    c.drawPath(yol, cizgi)
                    cizgi.color = hsla(hh, 95f, 78f, 0.12f * yo); cizgi.strokeWidth = 3.5f
                    c.drawPath(yol, cizgi)
                    cizgi.color = hsla(hh + 10f, 100f, 90f, (0.55f + e * 0.35f) * yo); cizgi.strokeWidth = 1.2f
                    c.drawPath(yol, cizgi)
                }

                // 9) Uçuşan kıvılcımlar
                for (s in kivilcimlar) {
                    val a = sin((s.omur / s.maks) * PI.toFloat()) * 0.8f * yo
                    nokta.color = hsla(ton + 30f, 100f, 88f, a)
                    c.drawRect(s.x - 1f, s.y - 1f, s.x + 1f, s.y + 1f, nokta)
                }
                cizgi.xfermode = null
                nokta.xfermode = null
                dolgu.xfermode = null
            }

            // Alt durum göstergesi (küre opaklığından etkilenmez, okunaklı kalır)
            val et = etiket
            if (et.isNotEmpty()) {
                val ga = giris
                val altY = hd - 24f
                val noktaY = altY - 26f
                nokta.color = Color.argb((204 * ga).toInt(), 233, 213, 255)
                var x = cx - 14f - 96f
                while (x < cx - 14f) { c.drawCircle(x, noktaY, 0.8f, nokta); x += 6f }
                x = cx + 14f
                while (x < cx + 14f + 96f) { c.drawCircle(x, noktaY, 0.8f, nokta); x += 6f }
                val nr = 5f * (1f + e * 1.4f)
                nokta.color = Color.argb((90 * ga).toInt(), 216, 180, 254)
                c.drawCircle(cx, noktaY, nr + 3f, nokta)
                nokta.color = Color.argb((255 * ga).toInt(), 255, 255, 255)
                c.drawCircle(cx, noktaY, nr * 0.55f, nokta)

                yazi.textSize = 11f
                yazi.alpha = (255 * ga).toInt()
                val gen = yazi.measureText(et) + 22f
                hap.alpha = (140 * ga).toInt()
                c.drawRoundRect(cx - gen / 2f, altY - 13f, cx + gen / 2f, altY + 6f, 10f, 10f, hap)
                c.drawText(et, cx + 1.6f, altY - 0.5f, yazi)
            }
            c.restore()
        }
    }
}
