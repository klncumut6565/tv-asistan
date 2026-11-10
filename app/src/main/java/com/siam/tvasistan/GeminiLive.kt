package com.siam.tvasistan

import android.os.SystemClock
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
        /** TANI: her cevabın sonunda ses akışı ölçümü (Gemini'den ses ne zaman, ne hızda geldi). */
        fun tani(ozet: String) {}
    }

    // ---- TANI: uzun cevaplarda kopma ölçümü. Davranışı DEĞİŞTİRMEZ, yalnız ölçer.
    private var turBas = -1L        // ilk ses parçasının geliş anı
    private var turSesMs = 0L       // gelen toplam ses süresi
    private var turParca = 0
    private var gerekTampon = 0L    // hiç takılmadan çalmak için gereken en az ön tampon (ms)
    private var bosluk = 0          // parçalar arası 300 ms'den uzun boşluk sayısı
    private var enUzunBosluk = 0L
    private var sonGelis = 0L
    private var turKesinti = 0      // sunucudan gelen "interrupted" sayısı

    private fun sesOlc(bayt: Int) {
        val t = SystemClock.elapsedRealtime()
        if (turBas < 0) { turBas = t; sonGelis = t }
        // Sıfır tamponla çalınsaydı bu parçanın çalınması gereken an: turBas + şimdiye kadarki ses
        gerekTampon = maxOf(gerekTampon, t - (turBas + turSesMs))
        val ara = t - sonGelis
        if (ara > 300) bosluk++
        enUzunBosluk = maxOf(enUzunBosluk, ara)
        turSesMs += bayt / 48L // 24 kHz × 2 bayt
        sonGelis = t
        turParca++
    }

    private fun turOzeti() {
        if (turParca > 0) {
            val sure = sonGelis - turBas
            fun sn(ms: Long) = String.format(java.util.Locale("tr"), "%.1f", ms / 1000.0)
            d.tani("Tanı · ${modelEtiketi ?: "?"} · ses ${sn(turSesMs)} sn, ${sn(sure)} sn'de geldi · " +
                "gerekli tampon ${sn(gerekTampon)} sn · boşluk $bosluk (en uzun ${sn(enUzunBosluk)} sn) · kesinti $turKesinti · parça $turParca")
        }
        turBas = -1L; turSesMs = 0L; turParca = 0; gerekTampon = 0L; bosluk = 0; enUzunBosluk = 0L; turKesinti = 0
    }


    companion object {
        private const val ETIKET = "GeminiLive"
        @Volatile private var sonCalisan: String? = null
        /** Google Arama aracını kabul etmeyen model/sürüm çiftleri (bir kez denenir, sonra aramasız bağlanılır). */
        private val aramasizlar = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        /** Kotası dolan modeller → ne zamana kadar sona atılacağı (ms). */
        private val kotaDolu = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val ORTAK_HTTP: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }
    }

    private var sesAdi = "Kore"
    private var duyguUyumu = false
    private var sessizlikMs = Uzak.SESSIZLIK_VARSAYILAN

    private val http get() = ORTAK_HTTP
    private val aracYurutucu = Executors.newSingleThreadExecutor()
    /** Uzun süren bilgi araçları ayrı kuyrukta: araştırma sürerken TV komutları (ses, kanal...) beklemez. */
    private val bilgiYurutucu = Executors.newFixedThreadPool(2)
    private val BILGI_ARACLARI = setOf("internette_arastir", "internette_ara", "doviz_kuru", "hava_durumu")

    @Volatile private var ws: WebSocket? = null
    @Volatile private var kapatildi = false

    var modelEtiketi: String? = null
        private set

    /** Bu oturumda Gemini'nin yerleşik Google Araması açık mı. */
    @Volatile var aramaAcik = false
        private set

    /** Oturum devam tutamacı: bağlantı yenilenince sohbet bağlamı kaybolmaz. */
    @Volatile var tutamac: String? = null

    /** Son bağlantı, telefondan seçilen ses/duygu ayarı reddedildiği için varsayılan (Kore, duygu kapalı) ile mi kuruldu. */
    @Volatile var varsayilanSesleBaglandi = false
        private set

    suspend fun baglan(anahtar: String, y: Uzak.Yapilandirma, talimat: String, araclar: JSONArray, guvenliDeneme: Boolean = false) {
        if (!guvenliDeneme) varsayilanSesleBaglandi = false
        if (anahtar.isBlank()) throw IOException("Gemini API anahtarı girilmemiş — Jülide uygulamasını açıp anahtarı kaydedin.")
        sesAdi = Uzak.sesDogrula(y.ses)
        duyguUyumu = y.duyguUyumu
        sessizlikMs = Uzak.sessizlikDogrula(y.sessizlikMs)
        val surumler = listOf("v1beta", "v1alpha")
        val simdi = System.currentTimeMillis()
        val sira = y.modeller.sortedWith(
            compareBy<Uzak.Model> { (kotaDolu[it.ad] ?: 0L) > simdi }.thenByDescending { it.ad == sonCalisan }
        )
        val hatalar = mutableListOf<String>()
        val kotasiDolan = mutableSetOf<String>()
        for (m in sira) {
            for (s in surumler) {
                val anahtarCift = "${m.ad}/$s"
                val denemeler = if (anahtarCift in aramasizlar) listOf(false) else listOf(true, false)
                for (arama in denemeler) {
                    try {
                        tekBaglanti(anahtar, s, m, talimat, araclar, arama)
                        modelEtiketi = m.etiket + if (arama) " + Google Arama" else ""
                        aramaAcik = arama
                        sonCalisan = m.ad
                        Log.i(ETIKET, "Bağlandı: ${m.etiket} / $s / arama=$arama")
                        return
                    } catch (e: KotaHatasi) {
                        // Ücretsiz planda Google Arama kotası 0'dır: aramayla kota hatası → aramasız dene.
                        // Aramasız da kota doluysa bu modelin kotası bitmiştir → SIRADAKİ MODELE geç.
                        ws?.cancel()
                        ws = null
                        hatalar += "${m.etiket}/$s${if (arama) "+arama" else ""}: kota"
                        if (arama) { aramasizlar += anahtarCift; continue }
                        kotasiDolan += m.ad
                        kotaDolu[m.ad] = System.currentTimeMillis() + 3 * 3600_000L
                        break
                    } catch (e: KaliciHata) {
                        ws?.cancel()
                        ws = null
                        throw e // anahtar geçersiz: diğer modelleri denemek boşuna
                    } catch (e: IOException) {
                        hatalar += "${m.etiket}/$s${if (arama) "+arama" else ""}: ${e.message}"
                        ws?.cancel()
                        ws = null
                        if (arama) aramasizlar += anahtarCift
                    }
                }
            }
        }
        if (kotasiDolan.size >= sira.size) {
            throw KaliciHata("Gemini kotası tüm modellerde doldu (${kotasiDolan.size} model denendi). Kota genelde gece yarısı (Pasifik saati, TR 10:00) sıfırlanır.")
        }
        Log.w(ETIKET, "Gemini Live bağlanamadı (ses=$sesAdi, duygu=$duyguUyumu). Tüm denemeler: " + hatalar.joinToString(" | "))
        // Telefondan seçilen ses ya da duygu uyumu bir modelde reddedilebilir: bir kez güvenli varsayılanla dene
        if (!guvenliDeneme && (sesAdi != "Kore" || duyguUyumu)) {
            Log.w(ETIKET, "Seçilen ses/duygu ayarıyla bağlanılamadı; Kore + duygu kapalı ile yeniden deneniyor")
            baglan(anahtar, y.copy(ses = "Kore", duyguUyumu = false), talimat, araclar, guvenliDeneme = true)
            varsayilanSesleBaglandi = true
            return
        }
        throw IOException("Gemini Live bağlanamadı. " + hatalar.lastOrNull().orEmpty())
    }

    private suspend fun tekBaglanti(anahtar: String, surum: String, m: Uzak.Model, talimat: String, araclar: JSONArray, arama: Boolean) {
        val hazir = CompletableDeferred<Unit>()
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.$surum." +
            "GenerativeService.BidiGenerateContent?key=" + URLEncoder.encode(anahtar, "UTF-8")
        kapatildi = false

        val dinleyici = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(kurulumMesaji(m, talimat, araclar, arama).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket === ws) isle(text, hazir)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (webSocket === ws) isle(bytes.utf8(), hazir)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val kod = response?.code
                val kalici = when {
                    kod == 401 || kod == 403 -> KaliciHata("Gemini API anahtarı geçersiz veya yetkisiz (HTTP $kod). Anahtarı uygulamadan yeniden kaydedin.")
                    kod == 429 || kotaMi(t.message) -> KotaHatasi("Gemini kotası doldu (${m.etiket}).")
                    else -> null
                }
                if (kalici != null && !hazir.isCompleted) {
                    hazir.completeExceptionally(kalici)
                    return
                }
                val mesaj = (t.message ?: "bağlantı hatası") + (response?.let { " (HTTP ${it.code})" } ?: "")
                if (!hazir.isCompleted) hazir.completeExceptionally(IOException(mesaj))
                else if (webSocket === ws && !kapatildi) d.hata(mesaj)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (!hazir.isCompleted) {
                    hazir.completeExceptionally(
                        when {
                            reason.contains("API key", true) -> KaliciHata("Gemini API anahtarı geçersiz. Anahtarı uygulamadan yeniden kaydedin.")
                            kotaMi(reason) -> KotaHatasi("Gemini kotası doldu (${m.etiket}): $reason")
                            else -> IOException("Bağlantı kapandı ($code) $reason")
                        }
                    )
                }
                else if (webSocket === ws && !kapatildi) {
                    // Oturum ortasında kota: büyük olasılıkla Google Arama (ücretsiz planda kotası 0).
                    if (kotaMi(reason)) {
                        if (arama) aramasizlar += "${m.ad}/$surum"
                        else kotaDolu[m.ad] = System.currentTimeMillis() + 3 * 3600_000L
                        Log.w(ETIKET, "Oturum kota nedeniyle kapandı (arama=$arama): $reason")
                    }
                    d.kapandi()
                }
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

    private fun kurulumMesaji(m: Uzak.Model, talimat: String, araclar: JSONArray, arama: Boolean): JSONObject {
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
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", bildirimler)).apply {
                // Gemini'nin yerleşik Google Araması: güncel bilgiyi (maçlar, haberler, döviz...) kendisi arar.
                if (arama) put(JSONObject().put("googleSearch", JSONObject()))
            })
            .put(
                "realtimeInputConfig", JSONObject().put(
                    "automaticActivityDetection", JSONObject()
                        .put("startOfSpeechSensitivity", "START_SENSITIVITY_LOW")
                        .put("prefixPaddingMs", 200)
                        .put("silenceDurationMs", sessizlikMs)
                )
            )
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            .apply {
                // Duygu uyumu: yalnız açıkça açıldıysa ve model destekliyorsa (Gemini 3.1 Flash Live desteklemez)
                if (duyguUyumu && !m.ad.contains("3.1-flash-live")) put("enableAffectiveDialog", true)
            }
            .put("sessionResumption", JSONObject().apply { tutamac?.let { put("handle", it) } })
        return JSONObject().put("setup", setup)
    }

    private fun isle(metin: String, hazir: CompletableDeferred<Unit>) {
        val j = try { JSONObject(metin) } catch (e: Exception) { return }

        if (j.has("setupComplete")) {
            hazir.complete(Unit)
            return
        }

        j.optJSONObject("sessionResumptionUpdate")?.let { u ->
            val h = u.optString("newHandle")
            if (u.optBoolean("resumable") && h.isNotEmpty()) tutamac = h
        }

        // Sunucu bağlantıyı yakında kapatacak (oturum süresi doldu): servis hemen yeniden bağlanır.
        if (j.has("goAway")) {
            if (!kapatildi) d.kapandi()
            return
        }

        j.optJSONObject("toolCall")?.let {
            aracCagrisi(it)
            return
        }

        val sc = j.optJSONObject("serverContent") ?: return
        if (sc.optBoolean("interrupted")) { turKesinti++; turOzeti(); d.kesildi() }

        val parcalar = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
        if (parcalar != null) {
            for (i in 0 until parcalar.length()) {
                val veri = parcalar.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data").orEmpty()
                if (veri.isNotEmpty()) {
                    val pcm = Base64.decode(veri, Base64.DEFAULT)
                    sesOlc(pcm.size)
                    d.sesGeldi(pcm)
                }
            }
        }
        sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { d.kullaniciMetni(it) }
        sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { d.asistanMetni(it) }
        if (sc.optBoolean("turnComplete")) { turOzeti(); d.turBitti() }
    }

    private fun aracCagrisi(tc: JSONObject) {
        val cagrilar = tc.optJSONArray("functionCalls") ?: return
        val hepsiBilgi = (0 until cagrilar.length()).all { cagrilar.optJSONObject(it)?.optString("name") in BILGI_ARACLARI }
        (if (hepsiBilgi) bilgiYurutucu else aracYurutucu).execute {
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

    /** Mikrofon akışı durdu (sessizlik): sunucudaki önbellekli sesi işlemesi için bildirir. */
    fun sesAkisiBitti() {
        ws?.send(JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true)).toString())
    }

    /** Görsel (JPEG) gönderir: Gemini bir sonraki cevabında bu görüntüyü "görür". */
    fun gorselGonder(jpeg: ByteArray) {
        ws?.send(JSONObject().put("realtimeInput", JSONObject().put("video", JSONObject()
            .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
            .put("mimeType", "image/jpeg"))).toString())
    }

    fun metinGonder(t: String) {
        ws?.send(JSONObject().put("realtimeInput", JSONObject().put("text", t)).toString())
    }

    fun kapat() {
        kapatildi = true
        ws?.close(1000, "bitti")
        ws = null
        aracYurutucu.shutdown()
        bilgiYurutucu.shutdown()
    }
}

/** Yeniden denemenin anlamsız olduğu hata (geçersiz anahtar, tüm modellerde dolu kota). */
open class KaliciHata(mesaj: String) : IOException(mesaj)

/** Tek bir modelin (ya da Google Arama'nın) kotası doldu: sıradaki seçenek denenir. */
class KotaHatasi(mesaj: String) : IOException(mesaj)

internal fun kotaMi(m: String?): Boolean {
    val k = m?.lowercase() ?: return false
    return "quota" in k || "resource_exhausted" in k || "resource exhausted" in k || "rate limit" in k || "exceeded" in k
}
