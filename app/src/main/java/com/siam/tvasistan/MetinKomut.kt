package com.siam.tvasistan

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Telefon sayfasındaki YAZILI KOMUT: sesli oturum (Gemini Live) açılmadan görev yapar.
 * Akış: önce yerel hızlı yol (HizliKomut — kanal, ses, HDMI, medya; LLM yok), olmazsa metin tabanlı
 * Gemini (generateContent) aynı araç tanımlarıyla (AracTanimi) çağrılır ve araçlar TV'de çalıştırılır.
 * Ses hattına (Mikrofon, Oynatici, GeminiLive) DOKUNMAZ. Engelleyicidir: arka plan iş parçacığında çağrılmalı.
 */
class MetinKomut(
    private val anahtar: () -> String,
    private val talimat: () -> String,
    private val calistir: (String, JSONObject) -> JSONObject,
) {
    /**
     * basari: true = son eylem aracı başarıyla çalıştı, false = başarısız ya da komut işlenemedi,
     * null = yalnız bilgi/cevap (eylem aracı çalışmadı).
     */
    class Sonuc(val mesaj: String, val araclar: List<SozDenetcisi.AracKaydi>, private val hata: Boolean = false) {
        val basari: Boolean? get() {
            if (hata) return false
            val eylemler = araclar.filter { it.ad !in SozDenetcisi.OKUMA_ARACLARI }
            return if (eylemler.isEmpty()) null else eylemler.last().basarili
        }
    }

    companion object {
        private const val ETIKET = "TvAsistan"
        private val MODELLER = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")
        private const val EN_COK_TUR = 8
        private const val KOTA_BEKLEME_MS = 3 * 3600_000L
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val EK_TALIMAT = "\n\nYAZILI KOMUT MODU: Kullanıcı telefondan YAZIYLA komut veriyor; sesli konuşma yok. " +
            "Görevi araçlarla yap; sonra ne yaptığını 1-2 kısa Türkçe cümleyle yaz. Araç başarısız olduysa ya da doğrulanmadıysa " +
            "'yaptım/açtım' DEME, ne olduğunu dürüstçe yaz. Belirsizse kısa bir soru sor. Ekran görüntüsü bu modda gönderilemez."
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    private val kotaDolu = HashMap<String, Long>()
    /** Aynı aracı aynı argümanla döngüye sokmayı engeller (sesli yoldaki korumanın aynısı, ayrı sayaç). */
    private val koruma = AracKorumasi()
    /** Son birkaç yazılı komut/cevap (takip komutları için kısa bağlam). */
    private val gecmis = ArrayDeque<JSONObject>()

    private class KotaHatasi(m: String) : IOException(m)
    private class ModelYok(m: String) : IOException(m)

    @Synchronized
    fun yurut(metin: String, ara: (String) -> Unit): Sonuc {
        val kayitlar = mutableListOf<SozDenetcisi.AracKaydi>()
        koruma.sifirla()
        // 1) Yerel hızlı yol: modeli beklemeden
        HizliKomut.coz(metin)?.let { k ->
            val r = calistir(k.arac, k.args)
            val hata = r.optString("hata").takeIf { it.isNotEmpty() }
            kayitlar += SozDenetcisi.AracKaydi(k.arac, hata == null, hata)
            val m = if (hata == null) "✓ " + (r.optString("sonuc").ifBlank { r.optString("mesaj") }.ifBlank { "Yapıldı (${k.arac})." })
            else "Yapılamadı: $hata"
            return Sonuc(m, kayitlar)
        }
        // 2) Metin Gemini + araçlar
        val k = anahtar().trim()
        if (k.isEmpty()) return Sonuc("Gemini anahtarı TV'de kayıtlı değil.", kayitlar, hata = true)
        val simdi = System.currentTimeMillis()
        val sira = MODELLER.sortedBy { (kotaDolu[it] ?: 0) > simdi }
        var sonHata = ""
        for (model in sira) {
            try {
                val cevap = modelleYurut(model, k, metin, kayitlar, ara)
                gecmis += JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", metin)))
                gecmis += JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", cevap)))
                while (gecmis.size > 6) gecmis.removeFirst()
                return Sonuc(cevap, kayitlar)
            } catch (e: KotaHatasi) {
                kotaDolu[model] = System.currentTimeMillis() + KOTA_BEKLEME_MS
                sonHata = "kota doldu"
                Log.w(ETIKET, "Yazılı komut: $model kota doldu, sıradaki model")
            } catch (e: ModelYok) {
                sonHata = e.message.orEmpty()
            } catch (e: Exception) {
                // Araç çalıştıktan sonra model değiştirmek işi ikinci kez yaptırabilir: dur
                if (kayitlar.isNotEmpty()) return Sonuc("Komut kısmen işlendi; Gemini cevabı alınamadı (${e.message}).", kayitlar, hata = true)
                sonHata = e.message.orEmpty()
                Log.w(ETIKET, "Yazılı komut: $model hata: $sonHata")
            }
        }
        return Sonuc(if (sonHata == "kota doldu") "Gemini kotası tüm metin modellerinde doldu; sonra tekrar deneyin."
            else "Gemini'ye ulaşılamadı: ${sonHata.take(140)}", kayitlar, hata = true)
    }

    private fun modelleYurut(model: String, k: String, metin: String, kayitlar: MutableList<SozDenetcisi.AracKaydi>, ara: (String) -> Unit): String {
        val icerik = JSONArray()
        gecmis.forEach { icerik.put(it) }
        icerik.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", metin))))
        val govdeSabit = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", talimat() + EK_TALIMAT))))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", AracTanimi.bildirimler())))
        repeat(EN_COK_TUR) {
            val govde = JSONObject(govdeSabit.toString()).put("contents", icerik)
            val j = istek(model, k, govde)
            val aday = j.optJSONArray("candidates")?.optJSONObject(0)
            val modelIcerik = aday?.optJSONObject("content") ?: throw IOException("boş cevap (${aday?.optString("finishReason") ?: "aday yok"})")
            val parcalar = modelIcerik.optJSONArray("parts") ?: JSONArray()
            val cagrilar = (0 until parcalar.length()).mapNotNull { parcalar.optJSONObject(it)?.optJSONObject("functionCall") }
            if (cagrilar.isEmpty()) {
                val yazi = (0 until parcalar.length()).joinToString("") { i ->
                    parcalar.optJSONObject(i)?.takeIf { !it.optBoolean("thought") }?.optString("text").orEmpty()
                }.trim()
                return yazi.ifBlank { if (kayitlar.isNotEmpty()) "İşlem tamamlandı." else "Cevap alınamadı." }
            }
            icerik.put(JSONObject().put("role", "model").put("parts", parcalar))
            val yanitlar = JSONArray()
            for (c in cagrilar) {
                val ad = c.optString("name")
                val args = c.optJSONObject("args") ?: JSONObject()
                ara("Çalışıyor: $ad…")
                val r = try { koruma.denetle(ad, args) ?: calistir(ad, args) } catch (e: Exception) { JSONObject().put("hata", e.message ?: "hata") }
                val hata = r.optString("hata").takeIf { it.isNotEmpty() }
                kayitlar += SozDenetcisi.AracKaydi(ad, hata == null, hata)
                Log.i(ETIKET, "Yazılı komut aracı: $ad ${if (hata == null) "ok" else "hata: $hata"}")
                yanitlar.put(JSONObject().put("functionResponse", JSONObject().put("name", ad).put("response", r)))
            }
            icerik.put(JSONObject().put("role", "user").put("parts", yanitlar))
        }
        return "İşlem çok uzun sürdü; son durumu TV ekranından kontrol edin."
    }

    private fun istek(model: String, k: String, govde: JSONObject): JSONObject {
        val r = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", k)
            .post(govde.toString().toRequestBody(JSON))
            .build()
        http.newCall(r).execute().use { y ->
            val metin = y.body?.string().orEmpty()
            if (y.isSuccessful) return JSONObject(metin)
            val mesaj = try { JSONObject(metin).optJSONObject("error")?.optString("message").orEmpty() } catch (_: Exception) { "" }
            if (y.code == 429 || mesaj.contains("quota", true) || mesaj.contains("exhausted", true)) throw KotaHatasi(mesaj)
            if (y.code == 404) throw ModelYok("$model bulunamadı")
            if (mesaj.contains("API key", true)) throw IOException("Gemini anahtarı geçersiz.")
            throw IOException("HTTP ${y.code} ${mesaj.take(120)}")
        }
    }
}
