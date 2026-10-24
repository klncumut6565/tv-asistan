package com.siam.tvasistan

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Gemini Live API (ham WebSocket) istemcisi. Kullanıcının kendi API anahtarıyla
 * doğrudan bağlanır; model listesi ve ses uzaktan ayardan (config/ayarlar.json) gelir.
 *   Giriş: 16-bit PCM 16 kHz mono   Çıkış: 16-bit PCM 24 kHz mono
 */
class GeminiLive(private val d: Dinleyici) {

    interface Dinleyici {
        fun sesGeldi(pcm: ByteArray)
        fun kesildi()
        fun turBitti()
        fun kullaniciMetni(t: String)
        fun asistanMetni(t: String)
        /** Arka plan iş parçacığında çağrılır; sonucu JSON olarak döndürür. */
        fun aracCagrisi(ad: String, args: JSONObject): JSONObject
        fun hata(mesaj: String)
        fun kapandi()
    }


    companion object {
        private const val ETIKET = "GeminiLive"
        @Volatile private var sonCalisan: String? = null
        private val ORTAK_HTTP: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }
    }

    private var sesAdi = "Kore"

    private val http get() = ORTAK_HTTP
    private val aracYurutucu = Executors.newSingleThreadExecutor()

    @Volatile private var ws: WebSocket? = null
    @Volatile private var kapatildi = false

    var modelEtiketi: String? = null
        private set

    suspend fun baglan(anahtar: String, y: Uzak.Yapilandirma, talimat: String, araclar: JSONArray) {
        if (anahtar.isBlank()) throw IOException("Gemini API anahtarı girilmemiş — Jülide uygulamasını açıp anahtarı kaydedin.")
        sesAdi = y.ses
        val surumler = listOf("v1beta", "v1alpha")
        val sira = y.modeller.sortedByDescending { it.ad == sonCalisan }
        val hatalar = mutableListOf<String>()
        for (m in sira) {
            for (s in surumler) {
                try {
                    tekBaglanti(anahtar, s, m, talimat, araclar)
                    modelEtiketi = m.etiket
                    sonCalisan = m.ad
                    Log.i(ETIKET, "Bağlandı: ${m.etiket} / $s")
                    return
                } catch (e: IOException) {
                    hatalar += "${m.etiket}/$s: ${e.message}"
                    ws?.cancel()
                    ws = null
                }
            }
        }
        throw IOException("Gemini Live bağlanamadı. " + hatalar.lastOrNull().orEmpty())
    }

    private suspend fun tekBaglanti(anahtar: String, surum: String, m: Uzak.Model, talimat: String, araclar: JSONArray) {
        val hazir = CompletableDeferred<Unit>()
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.$surum." +
            "GenerativeService.BidiGenerateContent?key=" + URLEncoder.encode(anahtar, "UTF-8")
        kapatildi = false

        val dinleyici = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(kurulumMesaji(m, talimat, araclar).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket === ws) isle(text, hazir)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (webSocket === ws) isle(bytes.utf8(), hazir)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val mesaj = (t.message ?: "bağlantı hatası") + (response?.let { " (HTTP ${it.code})" } ?: "")
                if (!hazir.isCompleted) hazir.completeExceptionally(IOException(mesaj))
                else if (webSocket === ws && !kapatildi) d.hata(mesaj)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (!hazir.isCompleted) hazir.completeExceptionally(IOException("Bağlantı kapandı ($code) $reason"))
                else if (webSocket === ws && !kapatildi) d.kapandi()
            }
        }

        val w = http.newWebSocket(Request.Builder().url(url).build(), dinleyici)
        ws = w
        val sonuc = withTimeoutOrNull(10_000) { hazir.await() }
        if (sonuc == null) {
            w.cancel()
            throw IOException("Kurulum zaman aşımı")
        }
    }

    private fun kurulumMesaji(m: Uzak.Model, talimat: String, araclar: JSONArray): JSONObject {
        val bildirimler = JSONArray()
        for (i in 0 until araclar.length()) {
            val a = JSONObject(araclar.getJSONObject(i).toString())
            if (m.blocking) a.put("behavior", "BLOCKING")
            bildirimler.put(a)
        }
        val setup = JSONObject()
            .put("model", m.ad)
            .put(
                "generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig", JSONObject()
                            .put("languageCode", "tr-TR")
                            .put(
                                "voiceConfig", JSONObject().put(
                                    "prebuiltVoiceConfig", JSONObject().put("voiceName", sesAdi)
                                )
                            )
                    )
            )
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", talimat))))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", bildirimler)))
            .put(
                "realtimeInputConfig", JSONObject().put(
                    "automaticActivityDetection", JSONObject()
                        .put("startOfSpeechSensitivity", "START_SENSITIVITY_LOW")
                        .put("prefixPaddingMs", 200)
                        .put("silenceDurationMs", 800)
                )
            )
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
        return JSONObject().put("setup", setup)
    }

    private fun isle(metin: String, hazir: CompletableDeferred<Unit>) {
        val j = try { JSONObject(metin) } catch (e: Exception) { return }

        if (j.has("setupComplete")) {
            hazir.complete(Unit)
            return
        }

        j.optJSONObject("toolCall")?.let {
            aracCagrisi(it)
            return
        }

        val sc = j.optJSONObject("serverContent") ?: return
        if (sc.optBoolean("interrupted")) d.kesildi()

        val parcalar = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
        if (parcalar != null) {
            for (i in 0 until parcalar.length()) {
                val veri = parcalar.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data").orEmpty()
                if (veri.isNotEmpty()) d.sesGeldi(Base64.decode(veri, Base64.DEFAULT))
            }
        }
        sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { d.kullaniciMetni(it) }
        sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { d.asistanMetni(it) }
        if (sc.optBoolean("turnComplete")) d.turBitti()
    }

    private fun aracCagrisi(tc: JSONObject) {
        val cagrilar = tc.optJSONArray("functionCalls") ?: return
        aracYurutucu.execute {
            val yanitlar = JSONArray()
            for (i in 0 until cagrilar.length()) {
                val c = cagrilar.optJSONObject(i) ?: continue
                val ad = c.optString("name")
                val sonuc = try {
                    d.aracCagrisi(ad, c.optJSONObject("args") ?: JSONObject())
                } catch (e: Exception) {
                    JSONObject().put("hata", e.message ?: e.toString())
                }
                Log.i(ETIKET, "Araç: $ad (${if (sonuc.has("hata")) "hata" else "tamam"})")
                yanitlar.put(JSONObject().put("id", c.optString("id")).put("name", ad).put("response", sonuc))
            }
            ws?.send(JSONObject().put("toolResponse", JSONObject().put("functionResponses", yanitlar)).toString())
        }
    }

    fun sesGonder(pcm: ByteArray) {
        val w = ws ?: return
        val mesaj = JSONObject().put(
            "realtimeInput", JSONObject().put(
                "audio", JSONObject()
                    .put("data", Base64.encodeToString(pcm, Base64.NO_WRAP))
                    .put("mimeType", "audio/pcm;rate=16000")
            )
        )
        w.send(mesaj.toString())
    }

    fun metinGonder(t: String) {
        ws?.send(JSONObject().put("realtimeInput", JSONObject().put("text", t)).toString())
    }

    fun kapat() {
        kapatildi = true
        ws?.close(1000, "bitti")
        ws = null
        aracYurutucu.shutdown()
    }
}
