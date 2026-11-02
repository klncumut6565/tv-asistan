package com.siam.tvasistan.arastirma

import java.net.URLEncoder

/**
 * YouTube'da aranan şeyin VİDEO KİMLİĞİNİ bulur (API anahtarı gerekmez), böylece video doğrudan başlatılır.
 * 1) youtube.com/results sayfasındaki videoRenderer kayıtları  2) yedek: Bing'de youtube.com/watch bağlantıları.
 * Reklam, kısa video (shorts) ve canlı yayın kayıtları elenir.
 */
object YouTubeBulucu {
    data class Video(val id: String, val baslik: String, val kanal: String?)

    fun bul(sorgu: String): List<Video> {
        val q = URLEncoder.encode(sorgu.trim().take(150), "UTF-8")
        val l = runCatching {
            // AB onay sayfasına düşmemek için onay çerezi
            sonucAyristir(WebAlici.getir("https://www.youtube.com/results?search_query=$q&hl=tr&gl=TR", 2_500_000,
                ekBasliklar = mapOf("Cookie" to "CONSENT=YES+cb; SOCS=CAI")).metin)
        }.getOrDefault(emptyList())
        if (l.isNotEmpty()) return l
        return runCatching {
            val rss = WebAlici.getir("https://www.bing.com/search?format=rss&setlang=tr&q=" +
                URLEncoder.encode("site:youtube.com $sorgu", "UTF-8"), 400_000).metin
            rssAyristir(rss, "bing").mapNotNull { a -> kimlik(a.adres)?.let { Video(it, a.baslik.removeSuffix(" - YouTube"), null) } }
        }.getOrDefault(emptyList())
    }

    private val KIMLIK = Regex("^[A-Za-z0-9_-]{11}$")

    /** watch?v=, youtu.be/, /embed/ adreslerinden 11 karakterlik kimlik. */
    fun kimlik(adres: String): String? {
        val m = Regex("(?:[?&]v=|youtu\\.be/|/embed/|/shorts/)([A-Za-z0-9_-]{11})").find(adres) ?: return null
        return m.groupValues[1].takeIf { KIMLIK.matches(it) }
    }

    internal fun sonucAyristir(html: String): List<Video> {
        val l = mutableListOf<Video>()
        val gorulen = mutableSetOf<String>()
        for (m in Regex("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"").findAll(html)) {
            val id = m.groupValues[1]
            if (!gorulen.add(id)) continue
            val parca = html.substring(m.range.first, minOf(html.length, m.range.first + 6000))
            val baslik = Regex("\"title\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(parca)?.groupValues?.get(1)
                ?: Regex("\"title\":\\{\"accessibility\".*?\"label\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(parca)?.groupValues?.get(1)
                ?: continue
            if (parca.contains("\"BADGE_STYLE_TYPE_LIVE_NOW\"")) continue // canlı yayın
            val kanal = Regex("\"ownerText\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(parca)?.groupValues?.get(1)
            l += Video(id, coz(baslik), kanal?.let { coz(it) })
            if (l.size >= 8) break
        }
        return l
    }

    /** JSON kaçışlarını çözer (\\u00e7, \\", \\\\). */
    private fun coz(s: String): String = Regex("\\\\u([0-9a-fA-F]{4})").replace(s) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/")
}
