package com.siam.tvasistan

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.text.Normalizer
import java.util.Locale

/** TV'de kurulu uygulamaları listeleme, isimle bulma ve açma. */
object Uygulamalar {
    data class Uyg(val ad: String, val paket: String)

    private val TR = Locale("tr", "TR")

    /** Türkçe karakterleri sadeleştirip küçük harfe çevirir: "Tarayıcı" → "tarayici". */
    fun normal(s: String): String {
        val k = s.lowercase(TR)
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
            .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
        return Normalizer.normalize(k, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun liste(ctx: Context): List<Uyg> {
        val pm = ctx.packageManager
        val sonuc = LinkedHashMap<String, Uyg>()
        for (kat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(kat)
            for (ri in pm.queryIntentActivities(i, 0)) {
                val paket = ri.activityInfo.packageName
                if (paket == ctx.packageName || sonuc.containsKey(paket)) continue
                sonuc[paket] = Uyg(ri.loadLabel(pm).toString(), paket)
            }
        }
        return sonuc.values.sortedBy { it.ad.lowercase(TR) }
    }

    /** "https" bağlantılarını açabilen uygulamalar (tarayıcılar). */
    fun tarayicilar(ctx: Context): List<Uyg> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        return pm.queryIntentActivities(i, 0)
            .map { Uyg(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.paket }
    }

    private val TARAYICI_KELIMELERI = setOf("tarayici", "internet", "browser", "web", "chrome")

    /** İsme göre en iyi eşleşmeler (en iyisi başta). */
    fun bul(ctx: Context, ad: String): List<Uyg> {
        val h = normal(ad)
        if (h.isEmpty()) return emptyList()
        val hepsi = liste(ctx)
        if (h in TARAYICI_KELIMELERI) {
            val t = tarayicilar(ctx).filter { tr -> hepsi.any { it.paket == tr.paket } }
            if (t.isNotEmpty()) return t
        }
        return hepsi
            .map { u -> u to puan(h, normal(u.ad), u.paket.lowercase(TR)) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun puan(h: String, ad: String, paket: String): Int = when {
        ad == h -> 100
        ad.replace(" ", "") == h.replace(" ", "") -> 95
        ad.startsWith(h) -> 80
        ad.split(" ").any { it == h } -> 70
        ad.contains(h) -> 60
        h.length >= 3 && h.contains(ad) && ad.length >= 3 -> 50
        h.length >= 4 && paket.contains(h.replace(" ", "")) -> 40
        else -> 0
    }

    fun ac(ctx: Context, paket: String): Boolean {
        val pm = ctx.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(paket) ?: pm.getLaunchIntentForPackage(paket) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }
}
