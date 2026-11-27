package com.siam.tvasistan

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.abs

/** Yön seçimi (Android'den bağımsız, birim testli): ekrandaki kutular arasında uzamsal gezinme. */
object BaglantiYonu {
    data class Kutu(val sol: Int, val ust: Int, val sag: Int, val alt: Int) {
        val cx get() = (sol + sag) / 2
        val cy get() = (ust + alt) / 2
    }

    /**
     * [mevcut] kutusundan [yon] yönündeki en uygun kutunun indeksi; yoksa null.
     * Yukarı/aşağı: bir sonraki SATIR öncelikli (dikey mesafe), aynı sütuna yakın olan kazanır.
     * Sol/sağ: aynı satırdaki (dikey örtüşen) kutular öncelikli.
     */
    fun sonraki(l: List<Kutu>, mevcut: Kutu, yon: String): Int? {
        var en: Int? = null
        var enPuan = Long.MAX_VALUE
        l.forEachIndexed { i, k ->
            if (k == mevcut) return@forEachIndexed
            val dx = k.cx - mevcut.cx
            val dy = k.cy - mevcut.cy
            val puan: Long = when (yon) {
                "asagi" -> if (k.ust >= mevcut.cy && dy > 4) dy * 4L + abs(dx) else return@forEachIndexed
                "yukari" -> if (k.alt <= mevcut.cy && dy < -4) -dy * 4L + abs(dx) else return@forEachIndexed
                "sag" -> if (k.sol >= mevcut.cx && dx > 4) dx + abs(dy) * (if (ortusurDikey(k, mevcut)) 1L else 6L) + (if (ortusurDikey(k, mevcut)) 0L else 400L) else return@forEachIndexed
                "sol" -> if (k.sag <= mevcut.cx && dx < -4) -dx + abs(dy) * (if (ortusurDikey(k, mevcut)) 1L else 6L) + (if (ortusurDikey(k, mevcut)) 0L else 400L) else return@forEachIndexed
                else -> return null
            }
            if (puan < enPuan) { enPuan = puan; en = i }
        }
        return en
    }

    private fun ortusurDikey(a: Kutu, b: Kutu) = a.ust < b.alt && b.ust < a.alt

    /** İlk seçim: aşağı/sağ için ekranın üstündeki ilk, yukarı/sol için en alttaki bağlantı (okuma sırası). */
    fun ilk(l: List<Kutu>, yon: String, ustSinir: Int, altSinir: Int): Int? {
        val gorunen = l.withIndex().filter { it.value.ust >= ustSinir && it.value.alt <= altSinir }.ifEmpty { l.withIndex().toList() }
        val sirali = gorunen.sortedWith(compareBy({ it.value.ust / 12 }, { it.value.sol }))
        return (if (yon == "yukari" || yon == "sol") sirali.lastOrNull() else sirali.firstOrNull())?.index
    }
}

/**
 * TARAYICIDA BAĞLANTI MODU: telefondaki yön tuşları tarayıcının kendi imlecini oynatmak yerine sayfadaki
 * bağlantıları (erişilebilirlik ağacındaki tıklanabilir öğeler) sırayla seçer; seçili olan sarı çerçeveyle
 * gösterilir; OK, seçilinin ortasına GERÇEK dokunma yapar. Sayfa sonuna gelince kaydırır.
 * Ağaçta bağlantı yoksa (tarayıcı sayfayı resim gibi çiziyor) null döner ve çağıran normal tuşa düşer.
 */
class BaglantiSecici(
    private val s: AccessibilityService,
    private val dokun: (Float, Float) -> Boolean,
    private val kaydir: (Boolean) -> Unit,
) {
    class Baglanti(val kutu: BaglantiYonu.Kutu, val metin: String)

    private val ana = Handler(Looper.getMainLooper())
    private val wm = s.getSystemService(WindowManager::class.java)
    private var gorunum: CerceveView? = null
    @Volatile private var secili: Baglanti? = null
    private val gizleyici = Runnable { temizle() }

    val aktif get() = secili != null

    private val gen get() = s.resources.displayMetrics.widthPixels
    private val yuk get() = s.resources.displayMetrics.heightPixels

    /** Yön: seçili bağlantının metni; bağlantı bulunamazsa null (normal tuşa düş). */
    fun yon(t: String): String? {
        var l = topla()
        if (l.size < 2) return null
        val onceki = secili
        var hedef: Baglanti? = null
        if (onceki == null) {
            BaglantiYonu.ilk(l.map { it.kutu }, t, (yuk * 0.08).toInt(), (yuk * 0.95).toInt())?.let { hedef = l[it] }
        } else {
            // Sayfa değişmiş olabilir: önceki seçime en yakın kutudan devam et
            val ref = l.minByOrNull { abs(it.kutu.cx - onceki.kutu.cx) + abs(it.kutu.cy - onceki.kutu.cy) }!!.kutu
            val yakin = abs(ref.cx - onceki.kutu.cx) + abs(ref.cy - onceki.kutu.cy) < 60
            val mevcut = if (yakin) ref else onceki.kutu
            BaglantiYonu.sonraki(l.map { it.kutu }, mevcut, t)?.let { hedef = l[it] }
            // Bu yönde görünür bağlantı kalmadıysa ya da hedef ekran kenarındaysa sayfayı kaydır
            val kenarda = hedef?.let { (t == "asagi" && it.kutu.alt > yuk * 0.93) || (t == "yukari" && it.kutu.ust < yuk * 0.07) } ?: false
            if ((hedef == null || kenarda) && (t == "asagi" || t == "yukari")) {
                kaydir(t == "asagi")
                Thread.sleep(450)
                l = topla()
                if (l.size < 2) return null
                BaglantiYonu.ilk(l.map { it.kutu }, if (t == "asagi") "asagi" else "yukari",
                    if (t == "asagi") (yuk * 0.25).toInt() else 0, if (t == "asagi") yuk else (yuk * 0.75).toInt())?.let { hedef = l[it] }
            }
        }
        val h = hedef ?: return onceki?.metin ?: return null
        secili = h
        ciz(h.kutu)
        return h.metin
    }

    /** OK: seçili bağlantıya gerçek dokunma; seçim yoksa null. */
    fun tamam(): String? {
        val h = secili ?: return null
        temizle()
        dokun(h.kutu.cx.toFloat(), h.kutu.cy.toFloat())
        return h.metin
    }

    fun temizle() {
        secili = null
        ana.post {
            ana.removeCallbacks(gizleyici)
            gorunum?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            gorunum = null
        }
    }

    private fun topla(): List<Baglanti> {
        val kokler = try {
            s.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.mapNotNull { it.root }
        } catch (_: Exception) { emptyList() }.ifEmpty { listOfNotNull(s.rootInActiveWindow) }
        val l = mutableListOf<Baglanti>()
        val r = Rect()
        val ekranAlan = gen.toLong() * yuk
        fun gez(n: AccessibilityNodeInfo, d: Int) {
            if (d > 60 || l.size >= 300) return
            if (n.isVisibleToUser && (n.isClickable || n.isEditable)) {
                n.getBoundsInScreen(r)
                val ic = Rect(r); val gorunur = ic.intersect(0, 0, gen, yuk)
                if (gorunur && ic.width() >= 10 && ic.height() >= 10 && ic.width().toLong() * ic.height() < ekranAlan * 0.45) {
                    val metin = (n.text ?: n.contentDescription ?: n.hintText)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                        ?: ilkMetin(n, 0) ?: ""
                    l += Baglanti(BaglantiYonu.Kutu(ic.left, ic.top, ic.right, ic.bottom), metin.replace(Regex("\\s+"), " ").take(80))
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { gez(it, d + 1) }
        }
        kokler.filter { it.packageName?.toString() != s.packageName }.forEach { gez(it, 0) }
        // Aynı kutu iki kez (iç içe tıklanabilir) gelmesin; yazısız ve çok küçük simgeler en sona
        return l.distinctBy { it.kutu }
    }

    private fun ilkMetin(n: AccessibilityNodeInfo, d: Int): String? {
        if (d > 6) return null
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            (c.text ?: c.contentDescription)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            ilkMetin(c, d + 1)?.let { return it }
        }
        return null
    }

    private fun ciz(k: BaglantiYonu.Kutu) = ana.post {
        val v = gorunum ?: CerceveView(s).also { v ->
            try {
                wm.addView(v, WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT))
                gorunum = v
            } catch (_: Exception) { return@post }
        }
        v.kutu = k
        v.invalidate()
        ana.removeCallbacks(gizleyici)
        ana.postDelayed(gizleyici, 30_000)
    }

    private class CerceveView(ctx: Context) : View(ctx) {
        var kutu: BaglantiYonu.Kutu? = null
        private val d = resources.displayMetrics.density
        private val konum = IntArray(2)
        private val kenar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 204, 21); style = Paint.Style.STROKE; strokeWidth = 4f * d }
        private val dis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = 7f * d }
        private val dolgu = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(45, 250, 204, 21); style = Paint.Style.FILL }
        init { setWillNotDraw(false) }
        override fun onDraw(c: Canvas) {
            val k = kutu ?: return
            getLocationOnScreen(konum)
            val p = 5f * d
            val r = RectF(k.sol - konum[0] - p, k.ust - konum[1] - p, k.sag - konum[0] + p, k.alt - konum[1] + p)
            c.drawRoundRect(r, 8f * d, 8f * d, dolgu)
            c.drawRoundRect(r, 8f * d, 8f * d, dis)
            c.drawRoundRect(r, 8f * d, 8f * d, kenar)
        }
    }
}
