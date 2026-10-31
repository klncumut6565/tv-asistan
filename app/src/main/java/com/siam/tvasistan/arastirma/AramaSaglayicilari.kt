package com.siam.tvasistan.arastirma

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Ücretsiz, anahtarsız arama sağlayıcıları. Biri çalışmazsa diğeri denenir (yol haritası 3.2-C).
 * Not: Bu kaynaklar resmî arama API'si değildir; değişirse sağlayıcı burada değiştirilir, gerisi etkilenmez.
 */
interface AramaSaglayici {
    val ad: String
    fun ara(sorgu: String, haber: Boolean): List<Aday>
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** RSS'i bağımlılıksız ayrıştırır (item → title/link/description/pubDate). */
internal fun rssAyristir(xml: String, saglayici: String): List<Aday> {
    fun etiket(item: String, ad: String): String? =
        Regex("(?is)<$ad[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</$ad>").find(item)?.groupValues?.get(1)?.trim()
    return Regex("(?is)<item[^>]*>(.*?)</item>").findAll(xml).mapNotNull { m ->
        val it = m.groupValues[1]
        val baslik = etiket(it, "title")?.let { Kaynak.htmlTemizle(it) }.orEmpty()
        var link = etiket(it, "link")?.let { Kaynak.htmlTemizle(it) }.orEmpty()
        // Bing haber bağlantısı yönlendirmelidir: asıl adres url= parametresinde
        Regex("[?&]url=([^&]+)").find(link)?.takeIf { link.contains("bing.com") }?.let { u ->
            link = runCatching { URLDecoder.decode(u.groupValues[1], "UTF-8") }.getOrDefault(link)
        }
        if (baslik.isBlank() || !link.startsWith("http")) null
        else Aday(baslik.take(200), link, Kaynak.htmlTemizle(etiket(it, "description").orEmpty()).take(400), etiket(it, "pubDate"), saglayici)
    }.take(10).toList()
}

class BingRss : AramaSaglayici {
    override val ad = "bing"
    override fun ara(sorgu: String, haber: Boolean): List<Aday> {
        val yol = if (haber) "news/search" else "search"
        val y = WebAlici.getir("https://www.bing.com/$yol?format=rss&setlang=tr&cc=TR&mkt=tr-TR&q=" + enc(sorgu), 400_000)
        return rssAyristir(y.metin, if (haber) "bing-haber" else "bing")
    }
}

/** DuckDuckGo HTML sürümü (JavaScript'siz): sonuç başlığı, adresi, özeti. */
class DuckDuckGo : AramaSaglayici {
    override val ad = "duckduckgo"
    override fun ara(sorgu: String, haber: Boolean): List<Aday> {
        val html = WebAlici.getir("https://html.duckduckgo.com/html/?kl=tr-tr&q=" + enc(sorgu), 500_000).metin
        return ddgAyristir(html)
    }
}

internal fun ddgAyristir(html: String): List<Aday> {
    val bloklar = html.split(Regex("(?i)class=\"result(?:__body| results_links)")).drop(1)
    return bloklar.mapNotNull { b ->
        val a = Regex("(?is)class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>").find(b) ?: return@mapNotNull null
        var link = a.groupValues[1].replace("&amp;", "&")
        Regex("[?&]uddg=([^&]+)").find(link)?.let { link = URLDecoder.decode(it.groupValues[1], "UTF-8") }
        if (link.startsWith("//")) link = "https:$link"
        if (link.contains("duckduckgo.com/y.js")) return@mapNotNull null // reklam
        val ozet = Regex("(?is)class=\"result__snippet\"[^>]*>(.*?)</a>").find(b)?.groupValues?.get(1)?.let { Kaynak.htmlTemizle(it) }.orEmpty()
        Aday(Kaynak.htmlTemizle(a.groupValues[2]).take(200), link, ozet.take(400), null, "duckduckgo")
    }.take(10)
}

/** Türkçe Vikipedi: değişmeyen genel bilgi için yetkili ikincil kaynak. */
object Vikipedi {
    fun ozet(sorgu: String): Pair<Aday, String>? {
        val ara = WebAlici.getir("https://tr.wikipedia.org/w/api.php?action=opensearch&limit=1&namespace=0&format=json&search=" + enc(sorgu), 60_000).metin
        val baslik = JSONArray(ara).optJSONArray(1)?.optString(0)?.takeIf { it.isNotBlank() } ?: return null
        val j = JSONObject(WebAlici.getir("https://tr.wikipedia.org/api/rest_v1/page/summary/" + enc(baslik.replace(' ', '_')), 120_000).metin)
        val metin = j.optString("extract").takeIf { it.length > 40 } ?: return null
        val adres = j.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page").orEmpty()
            .ifBlank { "https://tr.wikipedia.org/wiki/" + enc(baslik.replace(' ', '_')) }
        return Aday("Vikipedi: $baslik", adres, metin.take(300), j.optString("timestamp").ifBlank { null }, "vikipedi") to metin.take(1200)
    }
}
