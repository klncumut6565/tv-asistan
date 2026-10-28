package com.siam.tvasistan

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.net.Uri
import android.util.Log

/**
 * Canlı yayın kanalları. İki yol:
 *  1) TV sağlayıcısından (TvContract) kanal listesi okunabiliyorsa kanal doğrudan açılır.
 *     Android bunu çoğu cihazda izinle kısıtlar; izin verilmişse en hızlı ve kesin yol budur.
 *  2) Okunamıyorsa Canlı TV uygulaması açılır, kanal listesi ekranda aranır (Araclar tarafında).
 */
object Kanallar {
    private const val ETIKET = "TvAsistan"

    data class Kanal(val id: Long, val ad: String, val numara: String)

    /** TV sağlayıcısındaki kanallar; izin yoksa ya da liste boşsa boş liste. */
    fun liste(ctx: Context): List<Kanal> = try {
        val sonuc = mutableListOf<Kanal>()
        ctx.contentResolver.query(
            TvContract.Channels.CONTENT_URI,
            arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NAME, TvContract.Channels.COLUMN_DISPLAY_NUMBER),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val ad = c.getString(1).orEmpty().trim()
                if (ad.isNotEmpty()) sonuc += Kanal(c.getLong(0), ad, c.getString(2).orEmpty().trim())
            }
        }
        sonuc
    } catch (e: Exception) {
        Log.i(ETIKET, "Kanal listesi okunamadı: ${e.javaClass.simpleName} ${e.message}")
        emptyList()
    }

    /** Ada ("atv", "show tv") ya da numaraya ("7") göre en iyi eşleşme. */
    fun bul(liste: List<Kanal>, sorgu: String): Kanal? {
        val h = Uygulamalar.normal(sorgu).removeSuffix(" kanali").removeSuffix(" kanal").trim()
        if (h.isEmpty()) return null
        val sayi = Regex("^\\d+").find(h)?.value
        if (sayi != null) liste.firstOrNull { it.numara == sayi }?.let { return it }
        val bosluksuz = h.replace(" ", "")
        return liste
            .map { k ->
                val n = Uygulamalar.normal(k.ad)
                val nb = n.replace(" ", "")
                val p = when {
                    n == h || nb == bosluksuz -> 100
                    nb.startsWith(bosluksuz) -> 80
                    n.split(" ").any { it == h } -> 70
                    nb.contains(bosluksuz) -> 60
                    else -> 0
                }
                k to p - (if (n.contains("hd")) 0 else 1) // aynı puanda HD olanı tercih et
            }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first
    }

    fun ac(ctx: Context, k: Kanal): Boolean = try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, TvContract.buildChannelUri(k.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        Log.w(ETIKET, "Kanal açılamadı: ${e.message}")
        false
    }

    private val CANLI_TV_ADLARI = listOf("canli tv", "live tv", "televizyon", "tv izle", "kanallar", "tv")

    /** Canlı yayın uygulamasının paketi (kanal URI'sini açabilen ya da adı uyan uygulama). */
    fun canliTvPaketi(ctx: Context): String? {
        val pm = ctx.packageManager
        // Vestel/MediaTek: canlı TV her zaman bu paket (kuruluysa önce o)
        try { pm.getPackageInfo(SistemPaketleri.CANLI_TV, 0); return SistemPaketleri.CANLI_TV } catch (_: Exception) {}
        val niyet = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUri(1))
        pm.queryIntentActivities(niyet, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }.firstOrNull { it !in SistemPaketleri.CANLI_TV_DEGIL }?.let { return it }
        val hepsi = Uygulamalar.liste(ctx, hepsi = true)
        for (ad in CANLI_TV_ADLARI) {
            hepsi.firstOrNull { Uygulamalar.normal(it.ad) == ad }?.let { return it.paket }
        }
        return hepsi.firstOrNull { it.paket.contains("livetv", true) || it.paket.contains("tvplayer", true) }?.paket
    }

    /** Canlı TV'yi açar: önce son izlenen kanala (TvContract), olmazsa uygulamayı başlatır. */
    fun canliTvAc(ctx: Context): Boolean {
        val paket = canliTvPaketi(ctx)
        if (paket != null) {
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI).setPackage(paket)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            } catch (_: Exception) {
            }
            if (Uygulamalar.ac(ctx, paket)) return true
        }
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://android.media.tv/channel")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
