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

    /** calisti=false: istek kota/sunucu hatasıyla cevapsız kaldı → "ÇALIŞMADI" (kaldı SAYILMAZ, geçti de sayılmaz). */
    private data class Sonuc(val id: String, val tur: String, val soz: String, val gecti: Boolean, val beklenen: String, val gelen: String, val neden: String, val calisti: Boolean = true)

    private fun normal(s: String) = Uygulamalar.normal(s)

    /** Günlük kota bitti: kalan vakalara hiç istek gönderilmez (beklemek işe yaramaz). */
    private class GunlukKotaDoldu(m: String) : Exception(m)

    private fun istek(anahtar: String, model: String, govde: JSONObject): JSONObject {
        repeat(2) { deneme ->
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
            if (kod == 429 && Regex("PerDay|per day|daily", RegexOption.IGNORE_CASE).containsMatchIn(metin)) {
                throw GunlukKotaDoldu("Gemini günlük kotası doldu")
            }
            if (kod == 429 || kod >= 500) {
                // Dakikalık sınır: Gemini'nin önerdiği kadar (en çok 15 sn) bekle, bir kez daha dene
                val oneri = Regex("\"retryDelay\"\\s*:\\s*\"(\\d+)").find(metin)?.groupValues?.get(1)?.toLongOrNull() ?: 10
                if (deneme == 0) { Thread.sleep(minOf(oneri, 15) * 1000); return@repeat }
                error("Gemini HTTP $kod (dakikalık sınır ya da sunucu hatası)")
            }
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
        // İsteğe bağlı: yalnız belirli vakalar (ör. "hava,sohbet") — kota azken kalanları yeniden denemek için
        val secili = System.getenv("GEMINI_TEST_VAKALAR").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        val sonuclar = mutableListOf<Sonuc>()
        var kotaBitti = false
        for (i in 0 until set.length()) {
            val v = set.getJSONObject(i)
            val id = v.getString("id"); val soz = v.getString("soz"); val tur = v.optString("tur", "normal")
            if (secili.isNotEmpty() && id !in secili) continue
            val kabul = (0 until (v.optJSONArray("arac")?.length() ?: 0)).map { v.getJSONArray("arac").getString(it) }.toSet()
            val yasak = (0 until (v.optJSONArray("yasak")?.length() ?: 0)).map { v.getJSONArray("yasak").getString(it) }.toSet()
            val arguman = v.optJSONObject("arguman") ?: JSONObject()

            val govde = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", talimat))))
                .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", araclar)))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", soz)))))
                .put("generationConfig", JSONObject().put("temperature", 0))
            if (kotaBitti) {
                println("ÇALIŞMADI $id: günlük kota dolduğu için gönderilmedi")
                sonuclar += Sonuc(id, tur, soz, false, kabul.joinToString("/"), "—", "günlük kota doldu (gönderilmedi)", calisti = false); continue
            }
            val cevap = try { istek(anahtar, model, govde) } catch (e: Exception) {
                if (e is GunlukKotaDoldu) kotaBitti = true
                val m = e.message.orEmpty().take(160)
                println("ÇALIŞMADI $id: '$soz' → $m")
                sonuclar += Sonuc(id, tur, soz, false, kabul.joinToString("/"), "—", m, calisti = false); continue
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
            Thread.sleep(6_500) // ücretsiz kota: dakikada ~9 istek
        }

        val gecen = sonuclar.count { it.gecti }
        val calismayan = sonuclar.count { !it.calisti }
        val kalan = sonuclar.count { it.calisti && !it.gecti }
        val rapor = buildString {
            appendLine("## Jülide davranış testi — $gecen geçti · $kalan kaldı · $calismayan çalışmadı (toplam ${sonuclar.size})")
            appendLine()
            appendLine("Model: `$model` (metin). TV'deki Gemini Live sesli modeliyle birebir aynı değildir; talimat + araç seçimini yaklaşık ölçer.")
            appendLine()
            appendLine("| Sonuç | Tür | Cümle | Beklenen | Gelen | Neden |")
            appendLine("|---|---|---|---|---|---|")
            if (calismayan > 0) appendLine("\n⚠️ 'Çalışmadı' = Gemini cevap vermedi (çoğunlukla günlük/dakikalık kota). Bunlar sonuç DEĞİLDİR; vakalar alanına id yazıp yeniden çalıştırın.\n")
            for (s in sonuclar) appendLine("| ${if (!s.calisti) "⏸️" else if (s.gecti) "✅" else "❌"} | ${s.tur} | ${s.soz} | ${s.beklenen} | `${s.gelen.replace("|", "/")}` | ${s.neden} |")
        }
        File("build/reports").mkdirs()
        File("build/reports/davranis-testi.md").writeText(rapor)
        System.getenv("GITHUB_STEP_SUMMARY")?.let { File(it).appendText(rapor) }
        println(rapor)
        assertTrue("Davranış testi: $kalan vaka KALDI (rapor: build/reports/davranis-testi.md)", kalan == 0)
        assertTrue("Davranış testi: $calismayan vaka ÇALIŞMADI (kota?) — sonuç eksik", calismayan == 0)
    }
}
