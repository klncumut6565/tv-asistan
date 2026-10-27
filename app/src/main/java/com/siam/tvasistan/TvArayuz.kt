package com.siam.tvasistan

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Ana uygulama ekranının (MainActivity) TV bileşenleri: odakta parlayan kartlar, büyük düğmeler, durum
 * hapları ve açık/kapalı satırları. Renkler Panel.kt'deki Jülide küresinin mor/lila tonlarından alınmıştır;
 * Panel.kt'ye hiçbir bağımlılık yoktur (sesli asistan katmanı bu dosyadan etkilenmez).
 *
 * Kumanda için: her etkileşimli öğe odaklanabilir; odakta büyür (1,05x), mor çerçeve alır ve zemini
 * aydınlanır. Tıklama, kumandanın OK (DPAD_CENTER) tuşuyla çalışır.
 */
class TvArayuz(private val ctx: Activity) {

    object Renk {
        val ZEMIN = Color.rgb(11, 15, 23)
        val KART = Color.rgb(21, 27, 41)
        val KART_ODAK = Color.rgb(46, 26, 84)
        val CIZGI = Color.rgb(38, 47, 66)
        val MOR = Color.rgb(126, 34, 206)
        val LILA = Color.rgb(192, 132, 252)
        val ACIK_LILA = Color.rgb(233, 213, 255)
        val YAZI = Color.rgb(241, 245, 249)
        val SOLUK = Color.rgb(148, 163, 184)
        val YESIL = Color.rgb(74, 222, 128)
        val SARI = Color.rgb(251, 191, 36)
        val KIRMIZI = Color.rgb(248, 113, 113)
    }

    enum class Durum { IYI, UYARI, HATA }

    private val yogunluk = ctx.resources.displayMetrics.density
    fun dp(v: Int) = (v * yogunluk).toInt()

    private fun zemin(renk: Int, cizgi: Int, yaricap: Int, kalinlik: Int = 2) = GradientDrawable().apply {
        setColor(renk)
        cornerRadius = dp(yaricap).toFloat()
        setStroke(dp(kalinlik), cizgi)
    }

    /** Odaklanınca büyüyen, mor çerçeve alan öğe davranışı. */
    private fun odakEfekti(v: View, yaricap: Int, normal: Int = Renk.KART, odak: Int = Renk.KART_ODAK) {
        v.isFocusable = true
        v.isClickable = true
        v.background = zemin(normal, Renk.CIZGI, yaricap)
        v.setOnFocusChangeListener { g, odakta ->
            g.background = if (odakta) zemin(odak, Renk.LILA, yaricap, 3) else zemin(normal, Renk.CIZGI, yaricap)
            g.animate().scaleX(if (odakta) 1.05f else 1f).scaleY(if (odakta) 1.05f else 1f).setDuration(120).start()
            g.elevation = if (odakta) dp(8).toFloat() else 0f
        }
    }

    fun yazi(t: String, boyut: Float = 20f, renk: Int = Renk.YAZI, kalin: Boolean = false) = TextView(ctx).apply {
        text = t
        textSize = boyut
        setTextColor(renk)
        if (kalin) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setLineSpacing(0f, 1.15f)
    }

    fun dikey(bosluk: Int = 0) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        if (bosluk > 0) dividerPadding = bosluk
    }

    fun yatay() = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

    fun bosluk(yukseklik: Int) = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, dp(yukseklik)) }

    /** Büyük kart: simge + başlık + alt yazı. */
    fun kart(simge: String, baslik: String, alt: String = "", buyuk: Boolean = false, tikla: () -> Unit) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (buyuk) Gravity.CENTER else Gravity.CENTER_VERTICAL or Gravity.START
            val ic = if (buyuk) dp(28) else dp(20)
            setPadding(ic, ic, ic, ic)
            minimumHeight = dp(if (buyuk) 220 else 92)
            odakEfekti(this, 22, normal = if (buyuk) Color.rgb(31, 22, 54) else Renk.KART)
            addView(yazi(simge, if (buyuk) 54f else 30f))
            addView(yazi(baslik, if (buyuk) 30f else 21f, kalin = true).apply {
                setPadding(0, dp(if (buyuk) 10 else 6), 0, 0)
            })
            if (alt.isNotEmpty()) addView(yazi(alt, if (buyuk) 18f else 15f, Renk.SOLUK))
            setOnClickListener { tikla() }
        }

    /** Tam genişlik büyük düğme. */
    fun dugme(metin: String, vurgulu: Boolean = false, tikla: () -> Unit) = TextView(ctx).apply {
        text = metin
        textSize = 20f
        setTextColor(Renk.YAZI)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(dp(24), dp(18), dp(24), dp(18))
        minimumHeight = dp(64)
        odakEfekti(this, 16, normal = if (vurgulu) Color.rgb(59, 7, 100) else Renk.KART)
        setOnClickListener { tikla() }
    }

    /** Açık/kapalı satırı (onay kutusu yerine, uzaktan okunur). */
    fun onay(metin: String, ilk: Boolean, degisti: (Boolean) -> Unit): TextView {
        var acik = ilk
        val v = TextView(ctx)
        fun yaz() {
            v.text = (if (acik) "●  AÇIK      " else "○  KAPALI   ") + metin
            v.setTextColor(if (acik) Renk.ACIK_LILA else Renk.SOLUK)
        }
        v.textSize = 20f
        v.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        v.setPadding(dp(24), dp(18), dp(24), dp(18))
        v.minimumHeight = dp(64)
        odakEfekti(v, 16)
        yaz()
        v.setOnClickListener {
            acik = !acik
            yaz()
            degisti(acik)
        }
        return v
    }

    /** Durum hapı: yeşil/sarı/kırmızı nokta + metin; tıklanınca ilgili ekrana gider. */
    fun hap(metin: String, durum: Durum, tikla: (() -> Unit)? = null) = TextView(ctx).apply { hapGuncelle(this, metin, durum, tikla) }

    fun hapGuncelle(v: TextView, metin: String, durum: Durum, tikla: (() -> Unit)? = v.tag as? (() -> Unit)) {
        val renk = when (durum) { Durum.IYI -> Renk.YESIL; Durum.UYARI -> Renk.SARI; Durum.HATA -> Renk.KIRMIZI }
        v.text = "●  $metin"
        v.textSize = 17f
        v.setTextColor(renk)
        v.setPadding(dp(18), dp(10), dp(18), dp(10))
        if (tikla != null && v.tag == null) {
            v.tag = tikla
            odakEfekti(v, 30)
            v.setOnClickListener { tikla() }
        } else if (tikla == null && v.tag == null) {
            v.background = zemin(Renk.KART, Renk.CIZGI, 30)
        }
    }

    /** Kategori ekranı başlığı + bölüm başlığı. */
    fun bolum(baslik: String) = yazi(baslik, 22f, Renk.LILA, kalin = true).apply { setPadding(0, dp(18), 0, dp(6)) }

    fun aciklama(t: String) = yazi(t, 16f, Renk.SOLUK).apply { setPadding(0, dp(2), 0, dp(8)) }

    fun alan(ipucu: String, deger: String, sifre: Boolean = false) = EditText(ctx).apply {
        hint = ipucu
        setText(deger)
        setTextColor(Renk.YAZI)
        setHintTextColor(Renk.SOLUK)
        textSize = 20f
        setSingleLine(true)
        setPadding(dp(20), dp(16), dp(20), dp(16))
        inputType = if (sifre) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        background = zemin(Renk.KART, Renk.CIZGI, 16)
        setOnFocusChangeListener { g, odakta -> g.background = zemin(Renk.KART, if (odakta) Renk.LILA else Renk.CIZGI, 16, if (odakta) 3 else 2) }
    }

    /** Alt alta öğeler arasında boşluklu yerleşim parametresi. */
    fun satir(ust: Int = 6, alt: Int = 6, agirlik: Float = 0f) =
        LinearLayout.LayoutParams(
            if (agirlik > 0f) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            agirlik
        ).apply { topMargin = dp(ust); bottomMargin = dp(alt) }
}
