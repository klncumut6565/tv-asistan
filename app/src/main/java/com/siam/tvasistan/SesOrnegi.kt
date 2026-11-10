package com.siam.tvasistan

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Ayarlardaki "sesi dinle" örnekleri: Gemini'nin konuşma (TTS) modeliyle, Jülide'nin kullandığı AYNI ses adıyla
 * kısa bir Türkçe cümle üretir. Her ses bir kez üretilip TV'de saklanır; sonraki dinlemeler internetsiz ve anında.
 * Çıktı 24 kHz, 16 bit, mono PCM — Jülide'nin oynatıcısıyla aynı biçim.
 */
object SesOrnegi {

    const val CUMLE = "Merhaba, ben Jülide. Bu benim sesim. Bugün size nasıl yardımcı olabilirim?"

    /** Sesin kısa tarifi (Google'ın Gemini ses tablosundaki niteliklerin Türkçesi). */
    val TARIF = mapOf(
        "Kore" to "kararlı", "Sulafat" to "sıcak", "Vindemiatrix" to "nazik", "Achernar" to "yumuşak",
        "Aoede" to "ferah", "Callirrhoe" to "rahat", "Despina" to "akıcı", "Autonoe" to "parlak",
        "Erinome" to "net", "Gacrux" to "olgun", "Laomedeia" to "neşeli", "Leda" to "genç",
        "Pulcherrima" to "atak", "Zephyr" to "canlı",
    )

    private const val YEDEK_MODEL = "models/gemini-2.5-flash-preview-tts"
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()
    @Volatile private var model: String? = null

    /** Model listesinden konuşma (TTS) modelini seçer: "tts" içeren, generateContent destekleyen; flash önce. */
    fun ttsModeliSec(listeJson: String): String? {
        val l = JSONObject(listeJson).optJSONArray("models") ?: return null
        val adaylar = (0 until l.length()).mapNotNull { l.optJSONObject(it) }.filter { m ->
            val ad = m.optString("name")
            val yontemler = m.optJSONArray("supportedGenerationMethods") ?: JSONArray()
            "tts" in ad.lowercase() && (0 until yontemler.length()).any { yontemler.optString(it) == "generateContent" }
        }.map { it.optString("name") }
        return adaylar.sortedWith(compareBy({ "flash" !in it }, { "preview" in it }, { it })).firstOrNull()
    }

    /** generateContent cevabından ses verisini (base64 PCM) çıkarır. */
    fun sesVerisi(cevapJson: String): ByteArray? {
        val parcalar = JSONObject(cevapJson).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        for (i in 0 until parcalar.length()) {
            val v = parcalar.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data")
            if (!v.isNullOrBlank()) return Base64.getDecoder().decode(v)
        }
        return null
    }

    private fun modelBul(anahtar: String): String {
        model?.let { return it }
        val secilen = try {
            val r = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
                .header("x-goog-api-key", anahtar).build()
            http.newCall(r).execute().use { c -> if (c.isSuccessful) ttsModeliSec(c.body?.string().orEmpty()) else null }
        } catch (e: Exception) {
            Log.w("TvAsistan", "TTS modeli listelenemedi: ${e.message}")
            null
        }
        return (secilen ?: YEDEK_MODEL).also { model = it }
    }

    /** [ses] için örnek PCM: önce TV'deki kayıt, yoksa Gemini'den üretip kaydeder. */
    fun getir(ctx: Context, anahtar: String, ses: String): ByteArray {
        val ad = Uzak.sesDogrula(ses)
        val dosya = File(File(ctx.cacheDir, "ses_ornek").apply { mkdirs() }, "$ad.pcm")
        if (dosya.length() > 4800) return dosya.readBytes()
        if (anahtar.isBlank()) throw IOException("Önce Gemini API anahtarını girin.")
        val govde = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject()
                .put("text", "Sıcak, samimi ve doğal bir İstanbul Türkçesiyle söyle: $CUMLE")))))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                    .put("prebuiltVoiceConfig", JSONObject().put("voiceName", ad)))))
        val r = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/${modelBul(anahtar)}:generateContent")
            .header("x-goog-api-key", anahtar)
            .post(govde.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val pcm = http.newCall(r).execute().use { c ->
            val metin = c.body?.string().orEmpty()
            if (!c.isSuccessful) {
                model = null // bir sonraki denemede modeli yeniden seç
                throw IOException(if (c.code == 429) "Örnek ses kotası doldu; biraz sonra tekrar deneyin." else "Örnek ses alınamadı (HTTP ${c.code}).")
            }
            sesVerisi(metin) ?: throw IOException("Örnek ses cevabında ses yok.")
        }
        dosya.writeBytes(pcm)
        return pcm
    }
}
