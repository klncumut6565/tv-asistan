package com.siam.tvasistan

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log

/**
 * TV girişleri (HDMI) arasında geçiş: "HDMI 1'e geç", "PlayStation'a geç", "hangi girişler var".
 *
 * Girişler standart TvInputManager'dan okunur, geçiş TvContract.buildChannelUriForPassthroughInput ile
 * yapılır ve önce canlı TV uygulamasına yönlendirilir (birden çok uygulama yanıtlarsa seçici çıkmasın).
 * Yaklaşım CleanYourTV launcher'ından uyarlanmıştır (MIT, bkz. THIRD_PARTY_NOTICES.md).
 *
 * Bu TV'de (dumpsys tv_input, 27.10.2026): com.mediatek.tvinput/.hdmi.HDMIInputService/HW5 = HDMI 1,
 * HW6 = HDMI 2, HW7 = HDMI 3. AV girişi yok. Etiket okunamazsa sıra, HWn numarasına göre verilir.
 */
object Girisler {
    private const val ETIKET = "TvAsistan"

    data class Giris(val id: String, val ad: String, val hdmiNo: Int?)

    private val SAYILAR = mapOf(
        "bir" to "1", "birinci" to "1", "iki" to "2", "ikinci" to "2", "uc" to "3", "ucuncu" to "3",
        "dort" to "4", "dorduncu" to "4",
    )

    /** Görünür (gizlenmemiş) HDMI/harici girişler; CEC alt cihazları ayrı listelenmez. */
    fun liste(ctx: Context): List<Giris> = try {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        val girisler = tim.tvInputList
            .filter { it.isPassthroughInput && it.parentId == null && !gizli(ctx, it) }
            .sortedBy { hwNo(it.id) ?: Int.MAX_VALUE }
        var hdmiSira = 0
        girisler.map { info ->
            val hdmi = if (info.type == TvInputInfo.TYPE_HDMI) ++hdmiSira else null
            val ozel = try { info.loadCustomLabel(ctx)?.toString() } catch (_: Exception) { null }
            val etiket = try { info.loadLabel(ctx)?.toString() } catch (_: Exception) { null }
            val ad = ozel?.takeIf { it.isNotBlank() } ?: etiket?.takeIf { it.isNotBlank() }
                ?: if (hdmi != null) "HDMI $hdmi" else info.id
            Giris(info.id, ad, hdmi)
        }
    } catch (e: Exception) {
        Log.w(ETIKET, "Girişler okunamadı: ${e.message}")
        emptyList()
    }

    private fun gizli(ctx: Context, info: TvInputInfo): Boolean = try { info.isHidden(ctx) } catch (_: Exception) { false }

    /** ".../HW5" → 5 */
    private fun hwNo(id: String): Int? = Regex("HW(\\d+)$").find(id)?.groupValues?.get(1)?.toIntOrNull()

    /** Söylenen ada en uyan giriş: "HDMI 2", "ikinci HDMI", "hdmi iki", "PlayStation" (özel etiket). */
    fun bul(liste: List<Giris>, sorgu: String): Giris? {
        var h = Uygulamalar.normal(sorgu)
        for ((k, v) in SAYILAR) h = Regex("\\b$k\\b").replace(h, v)
        if (h.isEmpty()) return null
        // 1) Etiketle tam/kapsayan eşleşme (kullanıcının verdiği özel adlar dahil)
        liste.firstOrNull { Uygulamalar.normal(it.ad) == h }?.let { return it }
        // 2) "hdmi" + numara
        val no = Regex("\\d+").find(h)?.value?.toIntOrNull()
        if (no != null && (h.contains("hdmi") || h.replace(" ", "") == no.toString())) {
            liste.firstOrNull { it.hdmiNo == no }?.let { return it }
            liste.firstOrNull { Uygulamalar.normal(it.ad).replace(" ", "") == "hdmi$no" }?.let { return it }
        }
        // 3) Kısmi ad eşleşmesi
        return liste.firstOrNull { val n = Uygulamalar.normal(it.ad); h.length >= 3 && (n.contains(h) || h.contains(n)) }
    }

    /** Girişe geçer. Önce canlı TV uygulamasına yönlendirir, olmazsa genel intent. */
    fun ac(ctx: Context, g: Giris): Boolean {
        val uri = TvContract.buildChannelUriForPassthroughInput(g.id)
        val niyet = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Kanallar.canliTvPaketi(ctx)?.let { paket ->
            try {
                ctx.startActivity(Intent(niyet).setPackage(paket))
                return true
            } catch (_: Exception) {
            }
        }
        return try {
            ctx.startActivity(niyet)
            true
        } catch (e: Exception) {
            Log.w(ETIKET, "Girişe geçilemedi (${g.id}): ${e.message}")
            false
        }
    }
}
