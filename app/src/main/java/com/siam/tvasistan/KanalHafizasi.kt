package com.siam.tvasistan

/**
 * Kullanıcının öğrettiği kanal numaraları ("4. kanalda Show TV var") için ortak kurallar.
 * Kaydederken ve ararken AYNI anahtar üretilir: "Show TV", "show", "SHOW TV kanalı" → "show";
 * "TRT 1", "trt bir", "TRT1" → "trt1". Böylece söyleyiş farkı kaydı kaçırmaz.
 */
object KanalHafizasi {

    private val RAKAM = mapOf(
        "bir" to "1", "iki" to "2", "uc" to "3", "dort" to "4", "bes" to "5",
        "alti" to "6", "yedi" to "7", "sekiz" to "8", "dokuz" to "9", "on" to "10",
    )

    /** Sıra sayıları (normalleştirilmiş): "birinci" → 1 … "yirminci" → 20. */
    private val SIRA = mapOf(
        "birinci" to 1, "ikinci" to 2, "ucuncu" to 3, "dorduncu" to 4, "besinci" to 5, "altinci" to 6,
        "yedinci" to 7, "sekizinci" to 8, "dokuzuncu" to 9, "onuncu" to 10, "on birinci" to 11,
        "on ikinci" to 12, "on ucuncu" to 13, "on dorduncu" to 14, "on besinci" to 15, "on altinci" to 16,
        "on yedinci" to 17, "on sekizinci" to 18, "on dokuzuncu" to 19, "yirminci" to 20,
    )

    /** Kanal adının karşılaştırma anahtarı. */
    fun anahtar(ad: String): String {
        var n = Uygulamalar.normal(ad)
        for (ek in listOf(" kanalini", " kanalina", " kanalinda", " kanali", " kanal")) n = n.removeSuffix(ek)
        val kelimeler = n.split(" ").filter { it.isNotEmpty() }.map { RAKAM[it] ?: it }.toMutableList()
        if (kelimeler.size > 1 && kelimeler.last() == "tv") kelimeler.removeAt(kelimeler.lastIndex)
        return kelimeler.joinToString("")
    }

    /** "4", "4.", "dördüncü", "on birinci" → numara; değilse null. */
    fun numara(ham: String): Int? {
        val n = Uygulamalar.normal(ham)
        SIRA[n]?.let { return it }
        val h = n.replace(Regex(" ?(inci|nci|unci|uncu|ncu)$"), "").trim()
        return h.toIntOrNull()?.takeIf { it in 1..9999 }
    }

    private const val SIRA_KALIBI = "(\\d{1,4}|(?:on )?(?:birinci|ikinci|ucuncu|dorduncu|besinci|altinci|yedinci|sekizinci|dokuzuncu)|onuncu|yirminci)"
    private const val KANALDA = "kanal(?:da|de|ın|in)?"
    private const val VAR = "(?: (?:var|yayinda|kayitli|bulunuyor|duruyor|oluyor|cikiyor))?"
    private val KALIP_NUMARA_ONCE = Regex("^$SIRA_KALIBI (?:numarali )?$KANALDA ([a-z0-9 ]{2,30}?)$VAR$")
    private val KALIP_AD_ONCE = Regex("^([a-z0-9 ]{2,30}?) (?:kanali )?$SIRA_KALIBI (?:numarali )?$KANALDA(?:dir|dir| olarak kaydet)?$")
    private val KALIP_KAYDET = Regex("^([a-z0-9 ]{2,30}?) (?:kanalini )?$SIRA_KALIBI (?:kanal )?olarak kaydet$")

    /** "4. kanalda Show TV var", "TRT 1 birinci kanalda" → (ad, numara). Değilse null. */
    fun kayitCumlesi(metin: String): Pair<String, Int>? {
        val n = HizliKomut.sadelestir(metin)
        if (n.length > 60) return null
        KALIP_NUMARA_ONCE.find(n)?.let { m ->
            val no = numara(m.groupValues[1]) ?: return null
            val ad = m.groupValues[2].trim()
            return if (gecerliAd(ad)) ad to no else null
        }
        for (k in listOf(KALIP_AD_ONCE, KALIP_KAYDET)) {
            k.find(n)?.let { m ->
                val ad = m.groupValues[1].trim()
                val noMetni = m.groupValues[2]
                // "TRT 1 kanalda" belirsiz: numara kanalın kendi adının parçası olabilir → kaydetme
                if (noMetni.toIntOrNull() != null && HizliKomut.bilinenKanalMi("$ad $noMetni")) return null
                val no = numara(noMetni) ?: return null
                return if (gecerliAd(ad)) ad to no else null
            }
        }
        return null
    }

    private val YASAK_ADLAR = setOf("ne", "hangi", "kac", "su", "bu", "o", "simdi", "acik", "kanal", "ilk", "son", "sonraki", "onceki")

    private fun gecerliAd(ad: String): Boolean {
        val k = ad.split(" ")
        return ad.length >= 2 && k.none { it in YASAK_ADLAR } && ad.toIntOrNull() == null
    }

    /** Kayıtlı haritada adın numarasını bulur (eski kayıtlar farklı biçimde saklanmış olabilir). */
    fun bul(harita: Map<String, String>, ad: String): String? {
        val a = anahtar(ad)
        if (a.isEmpty()) return null
        return harita[a] ?: harita.entries.firstOrNull { anahtar(it.key) == a }?.value
    }
}
