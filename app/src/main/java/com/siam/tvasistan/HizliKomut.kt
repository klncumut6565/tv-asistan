package com.siam.tvasistan

import org.json.JSONObject

/**
 * HIZLI YOL — "anla" adımı, LLM'siz.
 *
 * Basit ve kalıbı belli komutları ("ATV'yi aç", "HDMI 1'e geç", "sonraki kanal", "sesi kıs",
 * "duraklat") yazıya dökülmüş kullanıcı sözünden doğrudan araç çağrısına çevirir.
 *
 * Kurallar:
 *  - Kalıplar cümlenin TAMAMIYLA eşleşmeli (^…$). "ATV'yi açma", "ATV açık mı" eşleşmez.
 *  - Belirsiz sözler bilerek dışarıda: "sesi aç" (sesi mi yükselt, sessizi mi kaldır?),
 *    "devam et" (konuşmaya mı devam?), "dur", "kapat", TV'yi kapatma (onay ister).
 *  - Eşleşmeyen her şey eskisi gibi Gemini'ye kalır.
 */
object HizliKomut {

    data class Komut(val arac: String, val args: JSONObject) {
        override fun toString() = "$arac $args"
    }

    private val HITAP = Regex("^((hey|hay|ey) )?(julide|culide|julida)( |$)")
    private val NEZAKET = Regex("( (lutfen|hadi|bakalim|misin|musun|abla|canim))+$")

    private const val AC = "(ac|acar|acsana|acin|acabilir)"
    private const val GEC = "(gec|gecer|gecsene|gecin|gecebilir)"

    /** Söylenen biçim → kanal_ac'a verilecek ad. Yalnızca bilinen kanallar; uygulama adlarıyla karışmasın. */
    private val KANALLAR: Map<String, String> = linkedMapOf(
        "atv" to "ATV", "a tv" to "ATV",
        "show tv" to "Show TV", "show" to "Show TV",
        "kanal d" to "Kanal D",
        "star tv" to "Star", "star" to "Star",
        "now tv" to "NOW", "now" to "NOW", "fox tv" to "NOW", "fox" to "NOW",
        "tv8" to "TV8", "tv 8" to "TV8", "tv sekiz" to "TV8",
        "tv8 bucuk" to "TV8,5", "tv 8 bucuk" to "TV8,5",
        "trt 1" to "TRT 1", "trt1" to "TRT 1", "trt bir" to "TRT 1",
        "trt 2" to "TRT 2", "trt iki" to "TRT 2",
        "trt haber" to "TRT Haber", "trt spor" to "TRT Spor", "trt cocuk" to "TRT Çocuk",
        "trt belgesel" to "TRT Belgesel", "trt muzik" to "TRT Müzik",
        "haberturk" to "Habertürk", "cnn turk" to "CNN Türk", "ntv" to "NTV",
        "tv100" to "TV100", "tv 100" to "TV100",
        "beyaz tv" to "Beyaz TV", "teve2" to "teve2", "teve 2" to "teve2",
        "kanal 7" to "Kanal 7", "a haber" to "A Haber", "a spor" to "A Spor",
        "halk tv" to "Halk TV", "sozcu tv" to "Sözcü TV", "tgrt haber" to "TGRT Haber",
        "ekol tv" to "Ekol TV", "dmax" to "DMAX", "tlc" to "TLC", "360" to "360",
    )

    private val KANAL_ADI: Regex by lazy {
        val secenekler = KANALLAR.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        Regex("^($secenekler)( kanal(i|ini|ina|a)?)?( ?(y|n)?[iuea])? ($AC|$GEC)$")
    }
    private val KANAL_NO = Regex("^(\\d{1,4}) ((inci|nci|unci|uncu|ncu) )?kanal(i|a)? ($AC|$GEC)$")

    private val SONRAKI_KANAL = Regex("^(((bir )?sonraki|ileriki) kanal(a)?( ($GEC|$AC))?|kanal(i)? (yukari|ileri))$")
    private val ONCEKI_KANAL = Regex("^((bir )?onceki kanal(a)?( ($GEC|$AC|don))?|kanal(i)? (asagi|geri))$")

    private val SES_ARTIR = Regex("^ses(i)?( (biraz|bir tik|az|biraz daha))? (artir|yukselt|yukari al|yukari)$")
    private val SES_AZALT = Regex("^ses(i)?( (biraz|bir tik|az|biraz daha))? (azalt|kis|dusur|alcalt|asagi al|asagi)$")
    private val SESSIZ = Regex("^(sessize al|sesi kapat|sesi kes)$")

    private const val MEDYA_NESNE = "((videoyu|filmi|diziyi|sarkiyi|muzigi|yayini) )?"
    private val DURAKLAT = Regex("^$MEDYA_NESNE(duraklat|durdur)$")
    private val OYNAT = Regex("^(${MEDYA_NESNE}oynat|(videoyu|filmi|diziyi|sarkiyi|muzigi) (devam ettir|baslat)|videoya devam et)$")
    private val ILERI_SAR = Regex("^(biraz )?ileri sar$")
    private val GERI_SAR = Regex("^(biraz )?geri sar$")

    private val HDMI = Regex("^hdmi ?(1|2|3|4|bir|iki|uc|dort)( ?(e|ye|a|ya|ine|ina))? ((girisine|girise|kaynagina) )?($GEC|$AC)$")
    private val SAYI = mapOf("bir" to "1", "iki" to "2", "uc" to "3", "dort" to "4")

    /** Sözün normalleştirilmiş, hitap ve nezaket ekleri atılmış hali. Testlerde de kullanılır. */
    fun sadelestir(metin: String): String {
        var n = Uygulamalar.normal(metin)
        n = n.replace(HITAP, "").trim()
        n = n.replace(NEZAKET, "").trim()
        return n
    }

    /** Söz basit bir komutsa araç çağrısı, değilse null (→ karar Gemini'ye kalır). */
    fun coz(metin: String): Komut? {
        val n = sadelestir(metin)
        if (n.isEmpty() || n.length > 40) return null

        HDMI.find(n)?.let { m ->
            val no = m.groupValues[1].let { SAYI[it] ?: it }
            return Komut("kaynak_degistir", JSONObject().put("kaynak", "HDMI $no"))
        }
        KANAL_NO.find(n)?.let { m ->
            return Komut("kanal_ac", JSONObject().put("kanal", m.groupValues[1]))
        }
        KANAL_ADI.find(n)?.let { m ->
            val ad = KANALLAR[m.groupValues[1]] ?: return null
            return Komut("kanal_ac", JSONObject().put("kanal", ad))
        }
        if (SONRAKI_KANAL.matches(n)) return Komut("kanal_degistir", JSONObject().put("yon", "sonraki").put("adet", 1))
        if (ONCEKI_KANAL.matches(n)) return Komut("kanal_degistir", JSONObject().put("yon", "onceki").put("adet", 1))
        if (SESSIZ.matches(n)) return Komut("ses", JSONObject().put("islem", "sessiz"))
        if (SES_ARTIR.matches(n)) return Komut("ses", JSONObject().put("islem", "artir").put("seviye", 2))
        if (SES_AZALT.matches(n)) return Komut("ses", JSONObject().put("islem", "azalt").put("seviye", 2))
        if (DURAKLAT.matches(n)) return Komut("medya", JSONObject().put("islem", "duraklat"))
        if (OYNAT.matches(n)) return Komut("medya", JSONObject().put("islem", "oynat"))
        if (ILERI_SAR.matches(n)) return Komut("medya", JSONObject().put("islem", "ileri_sar"))
        if (GERI_SAR.matches(n)) return Komut("medya", JSONObject().put("islem", "geri_sar"))
        return null
    }
}
