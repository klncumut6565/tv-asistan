package com.siam.tvasistan

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * DAVRANIŞ TESTİ (Skill Doctor'daki "gerçek görevle dene" fikri).
 *
 * Gerçek sistem talimatı + gerçek araç tanımlarıyla Gemini'ye cümleler METİN olarak verilir; modelin seçtiği ilk araç
 * beklenenle karşılaştırılır. Normal istekler, sınır durumları ve tetiklememesi gereken benzer istekler birlikte denenir.
 *
 * Yalnız GEMINI_TEST_ANAHTARI ortam değişkeni varken çalışır (normal CI'da ATLANIR, kota harcamaz).
 * Elle: GitHub → Actions → "Davranış testi" → Run workflow.
 *
 * SINIR: TV'deki Jülide Gemini Live (sesli model) kullanır; bu test metin modeliyle yaklaşık ölçer. Sonuçlar
 * "bu talimat ve araç tanımlarıyla model doğru aracı seçiyor mu" sorusunu cevaplar, ses tanımayı ölçmez.
 */
class DavranisTesti {

    private data class Sonuc(val id: String, val tur: String, val soz: String, val gecti: Boolean, val beklenen: String, val gelen: String, val neden: String)

    private fun normal(s: String) = Uygulamalar.normal(s)

    private fun istek(anahtar: String, model: String, govde: JSONObject): JSONObject {
        repeat(3) { deneme ->
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", anahtar)
            c.outputStream.use { it.write(govde.toString().toByteArray()) }
            val kod = c.responseCode
            val metin = (if (kod in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (kod == 429 || kod >= 500) { Thread.sleep(20_000L * (deneme + 1)); return@repeat }
            if (kod !in 200..299) error("Gemini HTTP $kod: ${metin.take(300)}")
            return JSONObject(metin)
        }
        error("Gemini art arda kota/sunucu hatası verdi")
    }

    @Test fun cumlelerDogruAracaGider() {
        val anahtar = System.getenv("GEMINI_TEST_ANAHTARI").orEmpty()
        assumeTrue("GEMINI_TEST_ANAHTARI yok: davranış testi atlandı (normal CI'da beklenen durum)", anahtar.isNotBlank())
        val model = System.getenv("GEMINI_TEST_MODELI").orEmpty().ifBlank { "gemini-2.5-flash" }

        val talimat = Araclar.talimatMetni(
            sakaModu = false, abonelikler = listOf("Prime Video", "TOD"),
            uygulamalar = "YouTube, Prime Video, TOD, Spotify, TARAYICI, Yandex Browser, Ayarlar",
            bellek = "", tarih = "11 Kasım 2026 Çarşamba, 20:00",
        )
        val araclar = AracTanimi.bildirimler()
        val set = JSONObject(javaClass.classLoader!!.getResource("davranis_seti.json")!!.readText()).getJSONArray("vakalar")

        val sonuclar = mutableListOf<Sonuc>()
        for (i in 0 until set.length()) {
            val v = set.getJSONObject(i)
            val id = v.getString("id"); val soz = v.getString("soz"); val tur = v.optString("tur", "normal")
            val kabul = (0 until (v.optJSONArray("arac")?.length() ?: 0)).map { v.getJSONArray("arac").getString(it) }.toSet()
            val yasak = (0 until (v.optJSONArray("yasak")?.length() ?: 0)).map { v.getJSONArray("yasak").getString(it) }.toSet()
            val arguman = v.optJSONObject("arguman") ?: JSONObject()

            val govde = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", talimat))))
                .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", araclar)))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", soz)))))
                .put("generationConfig", JSONObject().put("temperature", 0))
            val cevap = try { istek(anahtar, model, govde) } catch (e: Exception) {
                sonuclar += Sonuc(id, tur, soz, false, kabul.joinToString("/"), "HATA", e.message.orEmpty().take(120)); continue
            }
            val parcalar = cevap.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val cagri = (0 until parcalar.length()).mapNotNull { parcalar.optJSONObject(it)?.optJSONObject("functionCall") }.firstOrNull()
            val gelenAd = cagri?.optString("name") ?: "YOK"
            val args = cagri?.optJSONObject("args") ?: JSONObject()
            val gelen = if (cagri == null) "YOK (yalnız konuştu)" else "$gelenAd $args"

            var neden = ""
            when {
                gelenAd in yasak -> neden = "YASAK araç çağrıldı"
                kabul.isEmpty() && cagri != null && yasak.isEmpty() -> neden = "araç beklenmiyordu"
                kabul.isNotEmpty() && gelenAd !in kabul -> neden = "yanlış araç"
                cagri != null && gelenAd in kabul -> {
                    for (k in arguman.keys()) {
                        val gelenDeger = normal(args.opt(k)?.toString() ?: "")
                        val secenek = arguman.getString(k).split('|').map { normal(it) }
                        if (secenek.none { gelenDeger.contains(it) }) { neden = "argüman '$k' beklenmedik: '${args.opt(k)}'"; break }
                    }
                }
            }
            sonuclar += Sonuc(id, tur, soz, neden.isEmpty(), if (kabul.isEmpty()) "araç yok" else kabul.joinToString("/"), gelen.take(140), neden)
            println((if (neden.isEmpty()) "GEÇTİ " else "KALDI ") + "$id: '$soz' → $gelen ${if (neden.isEmpty()) "" else "← $neden"}")
            Thread.sleep(4_500) // ücretsiz kota: dakikada ~13 istek
        }

        val gecen = sonuclar.count { it.gecti }
        val rapor = buildString {
            appendLine("## Jülide davranış testi — $gecen / ${sonuclar.size} geçti")
            appendLine()
            appendLine("Model: `$model` (metin). TV'deki Gemini Live sesli modeliyle birebir aynı değildir; talimat + araç seçimini yaklaşık ölçer.")
            appendLine()
            appendLine("| Sonuç | Tür | Cümle | Beklenen | Gelen | Neden |")
            appendLine("|---|---|---|---|---|---|")
            for (s in sonuclar) appendLine("| ${if (s.gecti) "✅" else "❌"} | ${s.tur} | ${s.soz} | ${s.beklenen} | `${s.gelen.replace("|", "/")}` | ${s.neden} |")
        }
        File("build/reports").mkdirs()
        File("build/reports/davranis-testi.md").writeText(rapor)
        System.getenv("GITHUB_STEP_SUMMARY")?.let { File(it).appendText(rapor) }
        println(rapor)
        assertTrue("Davranış testi: ${sonuclar.size - gecen} vaka kaldı (rapor: build/reports/davranis-testi.md)", gecen == sonuclar.size)
    }
}
