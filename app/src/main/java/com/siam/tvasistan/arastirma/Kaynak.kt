package com.siam.tvasistan.arastirma

import java.net.URI

/**
 * Kaynak değerlendirme çekirdeği (yol haritası Bölüm 4). Android'e bağımlı DEĞİL: birim testleri JVM'de çalışır.
 */
enum class KaynakTuru(val kod: String, val oncelik: Int) {
    BIRINCIL("birincil", 0),        // resmî veri/metin: TCMB, TÜİK, Resmî Gazete, federasyon, üretici
    YETKILI("yetkili", 1),          // kurum: bakanlık, üniversite, uluslararası kuruluş
    GUVENILIR_IKINCIL("ikincil", 2), // editoryal denetimi olan haber/spor/teknik yayın
    BELIRSIZ("belirsiz", 3),        // yazarı/kaynağı belirsiz site
    SOSYAL("sosyal", 4),            // forum, sosyal medya, video yorumları
}

enum class IcerikDurumu(val kod: String) { ALINDI("alindi"), KISMI("kismi"), ENGELLI("engelli"), BASARISIZ("basarisiz"), ALINMADI("alinmadi") }

enum class KanitDuzeyi(val kod: String, val aciklama: String) {
    GUCLU("guclu", "Güçlü kanıt: birincil/yetkili kaynak ya da en az iki bağımsız güvenilir kaynak sayfası okundu."),
    ORTA("orta", "Orta kanıt: tek güvenilir kaynak okundu ya da yalnızca özetler var."),
    YETERSIZ("yetersiz", "Yetersiz kanıt: kaynak okunamadı, zayıf ya da yok. Kesin bilgi verme."),
}

data class Aday(
    val baslik: String,
    val adres: String,
    val ozet: String,
    val yayinTarihi: String?,
    val saglayici: String,
)

data class Degerlendirilmis(
    val aday: Aday,
    val tur: KaynakTuru,
    val alanAdi: String,
    var durum: IcerikDurumu = IcerikDurumu.ALINMADI,
    var metin: String? = null,
    var sayfaTarihi: String? = null,
    var kanonik: String? = null,
    var kopyasi: String? = null, // aynı metni yayınlayan başka kaynağın alan adı
)

object Kaynak {
    /** Alan adı → tür. Liste kasıtlı olarak küçük; bilinmeyen site BELIRSIZ sayılır (yol haritası 4.4). */
    private val BIRINCIL_ALANLAR = listOf(
        "tcmb.gov.tr", "tuik.gov.tr", "resmigazete.gov.tr", "mevzuat.gov.tr", "mgm.gov.tr", "tff.org", "tbf.org.tr",
        "tvf.org.tr", "uefa.com", "fifa.com", "euroleaguebasketball.net", "tsyd.org.tr", "sgk.gov.tr", "gib.gov.tr",
        "who.int", "nasa.gov", "esa.int", "afad.gov.tr", "koeri.boun.edu.tr", "osym.gov.tr", "meb.gov.tr",
        "superlig.com.tr", "trendyolsuperlig.com.tr"
    )
    private val YETKILI_SONEKLER = listOf(".gov.tr", ".edu.tr", ".gov", ".edu", ".int", ".bel.tr", ".tsk.tr", ".pol.tr", ".k12.tr")
    private val YETKILI_ALANLAR = listOf("europa.eu", "un.org", "oecd.org", "worldbank.org", "imf.org", "saglik.gov.tr", "wikipedia.org")
    private val IKINCIL_ALANLAR = listOf(
        "aa.com.tr", "trthaber.com", "trtspor.com.tr", "ntv.com.tr", "ntvspor.net", "hurriyet.com.tr", "milliyet.com.tr",
        "sabah.com.tr", "sozcu.com.tr", "haberturk.com", "cnnturk.com", "bbc.com", "bbc.co.uk", "reuters.com", "apnews.com",
        "dw.com", "euronews.com", "mackolik.com", "sporx.com", "fanatik.com.tr", "fotomac.com.tr", "ajansspor.com",
        "beinsports.com.tr", "aspor.com.tr", "transfermarkt.com.tr", "transfermarkt.com", "flashscore.com.tr", "sofascore.com",
        "bloomberght.com", "dunya.com", "ekonomim.com", "investing.com", "donanimhaber.com", "webtekno.com", "shiftdelete.net",
        "chip.com.tr", "theverge.com", "arstechnica.com", "nature.com", "sciencedirect.com", "nytimes.com", "theguardian.com",
        "t24.com.tr", "cumhuriyet.com.tr", "karar.com", "yenisafak.com", "star.com.tr", "posta.com.tr", "haberler.com",
        "iha.com.tr", "dha.com.tr", "ensonhaber.com", "mynet.com"
    )
    private val SOSYAL_ALANLAR = listOf(
        "twitter.com", "x.com", "facebook.com", "instagram.com", "tiktok.com", "youtube.com", "reddit.com", "eksisozluk.com",
        "quora.com", "medium.com", "blogspot.com", "wordpress.com", "pinterest.com", "linkedin.com", "t.me", "kizlarsoruyor.com",
        "donanimarsivi.com", "forum."
    )

    fun alanAdi(adres: String): String =
        runCatching { URI(adres).host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") }.getOrNull().orEmpty()

    private fun eslesir(alan: String, liste: List<String>) = liste.any { alan == it || alan.endsWith(".$it") || (it.endsWith(".") && alan.startsWith(it)) }

    fun siniflandir(adres: String): KaynakTuru {
        val a = alanAdi(adres)
        return when {
            a.isEmpty() -> KaynakTuru.BELIRSIZ
            eslesir(a, BIRINCIL_ALANLAR) -> KaynakTuru.BIRINCIL
            eslesir(a, SOSYAL_ALANLAR) -> KaynakTuru.SOSYAL
            YETKILI_SONEKLER.any { a.endsWith(it) } || eslesir(a, YETKILI_ALANLAR) -> KaynakTuru.YETKILI
            eslesir(a, IKINCIL_ALANLAR) -> KaynakTuru.GUVENILIR_IKINCIL
            else -> KaynakTuru.BELIRSIZ
        }
    }

    /** Yalnızca https ve herkese açık adresler (yerel ağ, IP, localhost, dahili ad YOK) — yol haritası 7. */
    fun guvenliAdres(adres: String): Boolean {
        val u = runCatching { URI(adres) }.getOrNull() ?: return false
        if (!u.scheme.equals("https", true)) return false
        val h = u.host?.lowercase() ?: return false
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".internal") || h.endsWith(".home")) return false
        if (!h.contains('.')) return false
        if (Regex("^[0-9.]+$").matches(h) || h.contains(':') || h.startsWith("[")) return false
        return true
    }

    /** Çözümlenen IP özel/yerel mi (yönlendirme ve DNS sonrası SSRF kontrolü). */
    fun ozelIp(ip: ByteArray): Boolean {
        if (ip.size == 16) {
            val ilk = ip[0].toInt() and 0xff
            val ikinci = ip[1].toInt() and 0xff
            val geriDongu = ip.copyOfRange(0, 15).all { it.toInt() == 0 } && ip[15].toInt() == 1
            val hepsiSifir = ip.all { it.toInt() == 0 }
            return geriDongu || hepsiSifir || (ilk and 0xfe) == 0xfc || (ilk == 0xfe && (ikinci and 0xc0) == 0x80)
        }
        if (ip.size != 4) return true
        val a = ip[0].toInt() and 0xff
        val b = ip[1].toInt() and 0xff
        return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 100 && b in 64..127)
    }

    fun normalSorgu(s: String): String = s.replace(Regex("\\s+"), " ").trim().take(200)

    /** Metin parmak izi için 5'li kelime parçaları. */
    private fun parcalar(m: String): Set<String> {
        val k = m.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(' ').filter { it.length > 1 }
        if (k.size < 5) return k.toSet()
        return (0..k.size - 5).map { k.subList(it, it + 5).joinToString(" ") }.toSet()
    }

    /** İki metnin benzerliği (Jaccard, 0..1). 0.5 üstü: aynı metnin kopyası (ajans haberi vb.). */
    fun benzerlik(a: String, b: String): Double {
        val x = parcalar(a.take(3000)); val y = parcalar(b.take(3000))
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.intersect(y).size.toDouble() / x.union(y).size
    }

    /** Aynı adres ya da aynı kanonik adres ya da aynı başlıklı aynı site → tekrar. */
    fun tekillestir(l: List<Aday>): List<Aday> {
        val gorulen = mutableSetOf<String>()
        return l.filter { a ->
            val anahtar = runCatching { URI(a.adres).let { (it.host.orEmpty().removePrefix("www.") + it.path.orEmpty()).trimEnd('/') } }.getOrDefault(a.adres)
            gorulen.add(anahtar.lowercase())
        }
    }

    /** Kopya metinleri işaretler: bağımsız doğrulama sayılmazlar (yol haritası 4.5). */
    fun kopyalariIsaretle(l: List<Degerlendirilmis>) {
        val okunan = l.filter { it.durum == IcerikDurumu.ALINDI && !it.metin.isNullOrBlank() }
        for (i in okunan.indices) for (j in 0 until i) {
            if (okunan[i].kopyasi == null && okunan[i].alanAdi != okunan[j].alanAdi &&
                benzerlik(okunan[i].metin!!, okunan[j].metin!!) >= 0.5
            ) okunan[i].kopyasi = okunan[j].alanAdi
        }
    }

    /**
     * Kanıt düzeyi: YALNIZCA gerçekten okunmuş sayfalara göre (özet tek başına kanıt değildir — yol haritası 4.8).
     * Kopya metinler bağımsız sayılmaz; sosyal/belirsiz kaynaklar güçlü kanıt oluşturmaz.
     */
    fun kanitDuzeyi(l: List<Degerlendirilmis>, resmiVeri: Boolean = false): KanitDuzeyi {
        if (resmiVeri) return KanitDuzeyi.GUCLU
        val okunan = l.filter { it.durum == IcerikDurumu.ALINDI && it.kopyasi == null }
        val ust = okunan.count { it.tur == KaynakTuru.BIRINCIL || it.tur == KaynakTuru.YETKILI }
        val ikincil = okunan.filter { it.tur == KaynakTuru.GUVENILIR_IKINCIL }.map { it.alanAdi }.distinct().size
        return when {
            ust >= 1 || ikincil >= 2 -> KanitDuzeyi.GUCLU
            ikincil == 1 || okunan.any { it.tur == KaynakTuru.BELIRSIZ } -> KanitDuzeyi.ORTA
            l.any { it.aday.ozet.length > 40 && it.tur.oncelik <= KaynakTuru.GUVENILIR_IKINCIL.oncelik } -> KanitDuzeyi.ORTA
            else -> KanitDuzeyi.YETERSIZ
        }
    }

    /** Güvenilir kaynaklar önce, sonra arama sırası. */
    fun sirala(l: List<Degerlendirilmis>): List<Degerlendirilmis> = l.withIndex()
        .sortedWith(compareBy<IndexedValue<Degerlendirilmis>> { it.value.tur.oncelik }.thenBy { it.index })
        .map { it.value }

    /** HTML'den yayın tarihi (bulunamazsa null — tarih UYDURULMAZ). */
    fun tarihBul(html: String): String? {
        val desenler = listOf(
            Regex("(?i)<meta[^>]+(?:property|name|itemprop)=[\"'](?:article:published_time|og:published_time|datePublished|pubdate|publish-date|date)[\"'][^>]*content=[\"']([^\"']{8,40})[\"']"),
            Regex("(?i)<meta[^>]+content=[\"']([^\"']{8,40})[\"'][^>]*(?:property|name|itemprop)=[\"'](?:article:published_time|datePublished)[\"']"),
            Regex("\"datePublished\"\\s*:\\s*\"([^\"]{8,40})\""),
            Regex("(?i)<time[^>]+datetime=[\"']([^\"']{8,40})[\"']"),
        )
        for (d in desenler) d.find(html)?.groupValues?.get(1)?.trim()?.let { return it }
        return null
    }

    fun kanonikBul(html: String): String? =
        Regex("(?i)<link[^>]+rel=[\"']canonical[\"'][^>]*href=[\"']([^\"']+)[\"']").find(html)?.groupValues?.get(1)

    fun htmlTemizle(s: String): String = s
        .replace(Regex("(?s)<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("&#(\\d{2,5});")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: " " }
        .replace(Regex("\\s+"), " ").trim()

    /** Sayfanın ana metni: script/stil/menü atılır, paragraf/tablo/liste satırları alınır. */
    fun anaMetin(html: String, sinir: Int = 1500): String? {
        val govde = html
            .replace(Regex("(?is)<(script|style|noscript|svg|nav|footer|header|aside|form|iframe)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?is)<!--.*?-->"), " ")
        val satirlar = Regex("(?is)<(p|li|td|th|h1|h2|h3|dd|dt)[^>]*>(.*?)</\\1>").findAll(govde)
            .map { htmlTemizle(it.groupValues[2]) }
            .filter { it.length > 2 && !Regex("(?i)(çerez|cookie|abone ol|giriş yap|üye ol|reklam|copyright|tüm hakları)").containsMatchIn(it) }
            .toList()
        val metin = if (satirlar.joinToString(" ").length > 200) satirlar.joinToString(" | ") else htmlTemizle(govde)
        return metin.replace(Regex("\\s+"), " ").trim().take(sinir).ifBlank { null }
    }

    /** Sorudaki anahtar kelimelere en yakın metin bölümü (kanıt alıntısı). */
    fun ilgiliBolum(metin: String, sorgu: String, uzunluk: Int = 900): String {
        if (metin.length <= uzunluk) return metin
        val kelimeler = sorgu.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
        if (kelimeler.isEmpty()) return metin.take(uzunluk)
        val k = metin.lowercase()
        var enIyi = 0; var enPuan = -1
        var i = 0
        while (i < k.length) {
            val pencere = k.substring(i, minOf(k.length, i + uzunluk))
            val puan = kelimeler.sumOf { w -> Regex(Regex.escape(w)).findAll(pencere).count() }
            if (puan > enPuan) { enPuan = puan; enIyi = i }
            i += uzunluk / 3
        }
        return metin.substring(enIyi, minOf(metin.length, enIyi + uzunluk))
    }
}
