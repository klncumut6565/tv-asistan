package com.siam.tvasistan.arastirma

import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Konuya özel, ücretsiz ve anahtarsız BİRİNCİL veri kaynakları (yol haritası 3.2-D):
 *  - Döviz: TCMB gösterge kurları (today.xml)
 *  - Hava: Open-Meteo (meteoroloji modeli verisi, saat dilimi Europe/Istanbul)
 * Genel web aramasından çok daha net ve güvenilir cevap verir.
 */
object ResmiVeri {
    private fun simdi() = SimpleDateFormat("d MMMM yyyy HH:mm", Locale("tr", "TR")).format(Date())

    private val BIRIM_ADLARI = mapOf(
        "USD" to listOf("dolar", "usd", "amerikan"), "EUR" to listOf("euro", "avro", "eur"), "GBP" to listOf("sterlin", "pound", "gbp"),
        "CHF" to listOf("frank", "chf"), "JPY" to listOf("yen", "jpy"), "SAR" to listOf("riyal", "sar"), "RUB" to listOf("ruble", "rub"),
        "CNY" to listOf("yuan", "cny"), "AZN" to listOf("manat", "azn"), "AED" to listOf("dirhem", "aed"), "KWD" to listOf("dinar", "kwd"),
    )

    fun birimBul(s: String): List<String> {
        val k = s.lowercase(Locale("tr", "TR"))
        val l = BIRIM_ADLARI.filter { (kod, adlar) -> kod.lowercase() in k || adlar.any { it in k } }.keys.toList()
        return l.ifEmpty { listOf("USD", "EUR") }
    }

    fun doviz(istek: String): JSONObject {
        val xml = WebAlici.getir("https://www.tcmb.gov.tr/kurlar/today.xml", 200_000).metin
        return tcmbAyristir(xml, birimBul(istek))
    }

    internal fun tcmbAyristir(xml: String, birimler: List<String>): JSONObject {
        val tarih = Regex("Tarih=\"([^\"]+)\"").find(xml)?.groupValues?.get(1)
        val kurlar = org.json.JSONArray()
        for (kod in birimler) {
            val blok = Regex("(?is)<Currency[^>]*CurrencyCode=\"$kod\"[^>]*>(.*?)</Currency>").find(xml)?.groupValues?.get(1) ?: continue
            fun alan(a: String) = Regex("(?is)<$a>([^<]*)</$a>").find(blok)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            kurlar.put(JSONObject().put("birim", kod).put("ad", alan("Isim") ?: kod)
                .put("doviz_alis", alan("ForexBuying")).put("doviz_satis", alan("ForexSelling"))
                .put("efektif_alis", alan("BanknoteBuying")).put("efektif_satis", alan("BanknoteSelling")))
        }
        if (kurlar.length() == 0) return JSONObject().put("hata", "TCMB kur verisi okunamadı.")
        return JSONObject()
            .put("kaynak", "Türkiye Cumhuriyet Merkez Bankası gösterge kurları (tcmb.gov.tr)")
            .put("kaynak_turu", KaynakTuru.BIRINCIL.kod)
            .put("kanit_duzeyi", KanitDuzeyi.GUCLU.kod)
            .put("veri_tarihi", tarih ?: JSONObject.NULL)
            .put("sorgu_zamani", simdi())
            .put("not", "TCMB kurları her iş günü 15:30'da açıklanan GÖSTERGE kurlardır; piyasadaki anlık kur farklı olabilir. Veri tarihini söyle.")
            .put("kurlar", kurlar)
    }

    private val HAVA_KODLARI = mapOf(
        0 to "açık", 1 to "az bulutlu", 2 to "parçalı bulutlu", 3 to "kapalı", 45 to "sisli", 48 to "kırağılı sis",
        51 to "hafif çisenti", 53 to "çisenti", 55 to "yoğun çisenti", 61 to "hafif yağmurlu", 63 to "yağmurlu", 65 to "kuvvetli yağmurlu",
        66 to "dondurucu yağmur", 67 to "kuvvetli dondurucu yağmur", 71 to "hafif kar yağışlı", 73 to "kar yağışlı", 75 to "yoğun kar yağışlı",
        77 to "kar taneli", 80 to "hafif sağanak", 81 to "sağanak", 82 to "şiddetli sağanak", 85 to "hafif kar sağanağı", 86 to "kar sağanağı",
        95 to "gök gürültülü fırtına", 96 to "dolulu fırtına", 99 to "şiddetli dolulu fırtına",
    )

    fun hava(sehir: String): JSONObject {
        val ad = sehir.trim().ifBlank { return JSONObject().put("hata", "Şehir belirtilmedi; kullanıcıya hangi şehir olduğunu sor.") }
        val g = JSONObject(WebAlici.getir("https://geocoding-api.open-meteo.com/v1/search?count=1&language=tr&format=json&name=" +
            URLEncoder.encode(ad, "UTF-8"), 60_000).metin)
        val yer = g.optJSONArray("results")?.optJSONObject(0) ?: return JSONObject().put("hata", "'$ad' adlı yer bulunamadı.")
        val lat = yer.optDouble("latitude"); val lon = yer.optDouble("longitude")
        val t = JSONObject(WebAlici.getir("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&timezone=Europe%2FIstanbul&forecast_days=3" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,precipitation" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max", 120_000).metin)
        val c = t.optJSONObject("current") ?: return JSONObject().put("hata", "Hava verisi alınamadı.")
        val d = t.optJSONObject("daily")
        val gunler = org.json.JSONArray()
        if (d != null) for (i in 0 until (d.optJSONArray("time")?.length() ?: 0)) {
            gunler.put(JSONObject().put("tarih", d.getJSONArray("time").optString(i))
                .put("durum", HAVA_KODLARI[d.getJSONArray("weather_code").optInt(i)] ?: "bilinmiyor")
                .put("en_yuksek", d.getJSONArray("temperature_2m_max").optDouble(i))
                .put("en_dusuk", d.getJSONArray("temperature_2m_min").optDouble(i))
                .put("yagis_olasiligi_yuzde", d.optJSONArray("precipitation_probability_max")?.optInt(i)))
        }
        return JSONObject()
            .put("yer", listOfNotNull(yer.optString("name"), yer.optString("admin1").ifBlank { null }).joinToString(", "))
            .put("kaynak", "Open-Meteo hava modeli verisi (open-meteo.com)")
            .put("kaynak_turu", KaynakTuru.BIRINCIL.kod)
            .put("kanit_duzeyi", KanitDuzeyi.GUCLU.kod)
            .put("olcum_zamani", c.optString("time"))
            .put("simdi", JSONObject().put("sicaklik", c.optDouble("temperature_2m")).put("hissedilen", c.optDouble("apparent_temperature"))
                .put("nem_yuzde", c.optInt("relative_humidity_2m")).put("ruzgar_kmh", c.optDouble("wind_speed_10m"))
                .put("durum", HAVA_KODLARI[c.optInt("weather_code")] ?: "bilinmiyor"))
            .put("uc_gunluk", gunler)
            .put("not", "Bu bir tahmin modelidir; resmî uyarılar için MGM'ye bakılmalı. Ölçüm zamanını belirt.")
    }
}
