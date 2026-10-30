package com.siam.tvasistan

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager

/**
 * Sesle yönetilen ekran imleci (tarayıcılar için). İki görünüm:
 *  - FARE: ok imleci (sayfa içeriği erişilebilirlik ağacında görünmüyorsa: ör. Puffin, uzaktan çizilen sayfalar)
 *  - EL:   el/parmak işareti (sayfadaki linkler algılanabiliyorsa: dokunma hedefi gösterilir)
 * Katman dokunmayı ENGELLEMEZ (NOT_TOUCHABLE); tıklamayı Araclar dispatchGesture ile yapar.
 */
class Imlec(private val s: AccessibilityService) {
    enum class Mod { FARE, EL }

    private val ana = Handler(Looper.getMainLooper())
    private val wm = s.getSystemService(WindowManager::class.java)
    private var gorunum: ImlecView? = null
    private val gizleyici = Runnable { gizle() }

    @Volatile var x = 0f; private set
    @Volatile var y = 0f; private set
    @Volatile var mod = Mod.FARE
    @Volatile var acik = false; private set

    private val gen get() = s.resources.displayMetrics.widthPixels.toFloat()
    private val yuk get() = s.resources.displayMetrics.heightPixels.toFloat()

    fun goster(mod: Mod? = null) {
        mod?.let { this.mod = it }
        if (!acik) { x = gen / 2f; y = yuk / 2f }
        acik = true
        ana.post {
            val v = gorunum ?: ImlecView(s).also { v ->
                try { wm.addView(v, prm()); gorunum = v } catch (_: Exception) { return@post }
            }
            v.mod = this.mod; v.px = x; v.py = y; v.basildi = false
            v.invalidate()
            ana.removeCallbacks(gizleyici)
            ana.postDelayed(gizleyici, 120_000)
        }
    }

    /** Yüzde (0..100) ile konuma git. */
    fun git(xYuzde: Float, yYuzde: Float) {
        x = (xYuzde.coerceIn(0f, 100f) / 100f) * gen
        y = (yYuzde.coerceIn(0f, 100f) / 100f) * yuk
        goster()
    }

    /** Piksel konumuna git. */
    fun gitPx(px: Float, py: Float) { x = px.coerceIn(0f, gen - 1); y = py.coerceIn(0f, yuk - 1); goster() }

    /** Yön + adım: 1 adım = ekranın %4'ü. */
    fun tasi(yon: String, adim: Int) {
        val d = adim.coerceIn(1, 12) * 0.04f
        when (yon) {
            "sag" -> x += gen * d
            "sol" -> x -= gen * d
            "yukari" -> y -= yuk * d
            "asagi" -> y += yuk * d
        }
        x = x.coerceIn(0f, gen - 1); y = y.coerceIn(0f, yuk - 1)
        goster()
    }

    /** Tıklama anında imleç kısa süre "basılı" çizilir. */
    fun basildiGoster() = ana.post {
        gorunum?.let { it.basildi = true; it.invalidate() }
        ana.postDelayed({ gorunum?.let { it.basildi = false; it.invalidate() } }, 350)
    }

    fun gizle() {
        acik = false
        ana.post {
            ana.removeCallbacks(gizleyici)
            gorunum?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            gorunum = null
        }
    }

    private fun prm() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    private class ImlecView(ctx: Context) : View(ctx) {
        var mod = Mod.FARE
        var px = 0f
        var py = 0f
        var basildi = false
        private val d = resources.displayMetrics.density
        private val konum = IntArray(2)
        private val dolgu = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val kenar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2.5f * d; strokeJoin = Paint.Join.ROUND }
        private val halka = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 250, 204, 21); style = Paint.Style.STROKE; strokeWidth = 4f * d }
        init { setWillNotDraw(false) }

        override fun onDraw(c: Canvas) {
            getLocationOnScreen(konum)
            val x = px - konum[0]
            val y = py - konum[1]
            val k = 1.6f * d
            if (basildi) c.drawCircle(x, y, 26f * d, halka)
            val yol = Path()
            if (mod == Mod.FARE) {
                // Ok imleci, ucu (x,y)
                yol.moveTo(x, y)
                yol.lineTo(x, y + 22 * k); yol.lineTo(x + 6 * k, y + 16.5f * k)
                yol.lineTo(x + 10 * k, y + 25 * k); yol.lineTo(x + 13.5f * k, y + 23.5f * k)
                yol.lineTo(x + 9.5f * k, y + 15 * k); yol.lineTo(x + 17 * k, y + 15 * k)
                yol.close()
            } else {
                // El / işaret parmağı, parmak ucu (x,y)
                yol.moveTo(x - 3 * k, y + 2 * k)
                yol.lineTo(x - 3 * k, y + 13 * k)
                yol.lineTo(x - 9 * k, y + 10 * k); yol.lineTo(x - 11 * k, y + 13 * k)
                yol.lineTo(x - 3 * k, y + 26 * k); yol.lineTo(x + 12 * k, y + 26 * k)
                yol.lineTo(x + 15 * k, y + 15 * k); yol.lineTo(x + 15 * k, y + 11 * k)
                yol.lineTo(x + 3 * k, y + 9 * k); yol.lineTo(x + 3 * k, y + 2 * k)
                yol.quadTo(x, y - 2 * k, x - 3 * k, y + 2 * k)
                yol.close()
            }
            c.drawPath(yol, dolgu)
            c.drawPath(yol, kenar)
        }
    }
}
