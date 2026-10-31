package com.siam.tvasistan

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Telefon mikrofonu köprüsü.
 *
 * Telefon, Vercel'deki sayfayı (web/index.html) açar; sayfa telefonun mikrofonunu dinler ve YALNIZ konuşma
 * algıladığında 16 kHz mono PCM parçalarını Supabase Realtime "broadcast" kanalına gönderir. Bu sınıf aynı
 * kanala bağlanır ve sesi Jülide'ye verir. Tarayıcılar HTTPS sayfadan yerel ağdaki ws:// adresine
 * bağlanmaya izin vermediği için doğrudan bağlantı yerine bu aktarıcı kullanılır.
 *
 * Kanal adı ayar ekranında üretilen eşleştirme koduyla oluşur ("julide-<kod>"); kodu bilmeyen biri kanala
 * ses gönderemez. Veritabanına dokunulmaz, yalnız Realtime yayın kanalı kullanılır.
 *
 * Phoenix protokolü (vsn 1.0.0): katılma "phx_join", 25 sn'de bir "heartbeat", gelen ses
 * {"event":"broadcast","payload":{"event":"ses","payload":{"d":"<base64>"}}}.
 */
class TelefonKoprusu(
    private val kod: String,
    /** Gelen PCM parçası (okhttp iş parçacığında çağrılır). */
    private val sesGeldi: (ByteArray, JSONObject) -> Unit,
    /** Telefon bağlandı/ayrıldı bilgisi (okhttp iş parçacığında). */
    private val durumDegisti: (String) -> Unit = {},
    /** Kanal adı öneki ("julide-" telefon mikrofonu, "julide-anahtar-" API anahtarı kurulumu). */
    kanalOn: String = "julide-",
    /** Diğer olaylar (ör. "anahtar"); okhttp iş parçacığında çağrılır. */
    private val olayGeldi: (String, JSONObject) -> Unit = { _, _ -> },
) {
    companion object {
        private const val ETIKET = "TvAsistan"
        const val PROJE = "eiacwimxjpggomngsnyo"
        /** Supabase anon anahtarı: tarayıcıda da kullanılan, herkese açık olması tasarım gereği olan anahtar. */
        const val ANON =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVpYWN3aW14anBnZ29tbmdzbnlvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODIxNDE5ODAsImV4cCI6MjA5NzcxNzk4MH0.OTUmblVqYTatRt8IUqjuDCAC0ABdYTg6Gy6mBS-wI8w"
        private const val ADRES = "wss://$PROJE.supabase.co/realtime/v1/websocket?apikey=$ANON&vsn=1.0.0"

        /** 8 karakterlik, karıştırılması zor harf/rakamlardan eşleştirme kodu. */
        fun yeniKod(): String {
            val harfler = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val r = java.security.SecureRandom()
            return (1..8).map { harfler[r.nextInt(harfler.length)] }.joinToString("")
        }
    }

    private val istemci = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val ana = Handler(Looper.getMainLooper())
    private val konu = "realtime:$kanalOn$kod"

    @Volatile private var ws: WebSocket? = null
    @Volatile private var acik = false
    @Volatile private var ref = 0
    private var bekleme = 2_000L

    private val nabiz = object : Runnable {
        override fun run() {
            gonder(JSONObject().put("topic", "phoenix").put("event", "heartbeat").put("payload", JSONObject()))
            ana.postDelayed(this, 25_000)
        }
    }

    fun baslat() {
        if (acik) return
        acik = true
        baglan()
    }

    fun durdur() {
        acik = false
        ana.removeCallbacksAndMessages(null)
        try { ws?.close(1000, "kapat") } catch (_: Exception) {}
        ws = null
    }

    /** Telefona kısa durum bilgisi gönderir (ör. "Jülide dinliyor", Jülide'nin cevabı). */
    fun durumGonder(mesaj: String) {
        val yuk = JSONObject().put("type", "broadcast").put("event", "durum")
            .put("payload", JSONObject().put("m", mesaj.take(500)))
        gonder(JSONObject().put("topic", konu).put("event", "broadcast").put("payload", yuk))
    }

    /** Telefona sohbetin açık olup olmadığını ve Jülide'nin konuşup konuşmadığını bildirir. */
    fun halGonder(oturumAcik: Boolean, konusuyor: Boolean) {
        val yuk = JSONObject().put("type", "broadcast").put("event", "hal")
            .put("payload", JSONObject().put("oturum", oturumAcik).put("konusuyor", konusuyor))
        gonder(JSONObject().put("topic", konu).put("event", "broadcast").put("payload", yuk))
    }

    /** Kanala serbest bir olay yayınlar. */
    fun olayGonder(olay: String, yuk: JSONObject) {
        val b = JSONObject().put("type", "broadcast").put("event", olay).put("payload", yuk)
        gonder(JSONObject().put("topic", konu).put("event", "broadcast").put("payload", b))
    }

    private fun gonder(j: JSONObject) {
        j.put("ref", (++ref).toString())
        try { ws?.send(j.toString()) } catch (_: Exception) {}
    }

    private fun baglan() {
        if (!acik) return
        val istek = Request.Builder().url(ADRES).build()
        ws = istemci.newWebSocket(istek, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                bekleme = 2_000L
                val ayar = JSONObject().put("config", JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("key", ""))
                    .put("private", false))
                    .put("access_token", ANON)
                gonder(JSONObject().put("topic", konu).put("event", "phx_join").put("payload", ayar))
                ana.removeCallbacks(nabiz)
                ana.postDelayed(nabiz, 25_000)
                Log.i(ETIKET, "Telefon köprüsü bağlandı ($konu)")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val j = JSONObject(text)
                    when (j.optString("event")) {
                        "broadcast" -> {
                            val p = j.optJSONObject("payload") ?: return
                            val ic = p.optJSONObject("payload") ?: return
                            when (p.optString("event")) {
                                "ses" -> {
                                    val d = ic.optString("d")
                                    if (d.isNotEmpty()) sesGeldi(Base64.decode(d, Base64.DEFAULT), ic)
                                }
                                "merhaba" -> durumDegisti("telefon bağlandı")
                                "hoscakal" -> durumDegisti("telefon ayrıldı")
                                "ac" -> durumDegisti("ac")
                                "tv_kapat" -> durumDegisti("tv_kapat")
                                "kapat" -> durumDegisti("kapat")
                                else -> olayGeldi(p.optString("event"), ic)
                            }
                        }
                        "phx_reply" -> {
                            val st = j.optJSONObject("payload")?.optString("status")
                            if (st != null && st != "ok") Log.w(ETIKET, "Telefon köprüsü yanıtı: $text".take(300))
                        }
                        "phx_error", "system" -> Log.w(ETIKET, "Telefon köprüsü: $text".take(300))
                    }
                } catch (e: Exception) {
                    Log.w(ETIKET, "Telefon köprüsü mesajı çözülemedi: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(ETIKET, "Telefon köprüsü koptu: ${t.message}; ${bekleme / 1000} sn sonra tekrar")
                yenidenDene()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (acik) yenidenDene()
            }
        })
    }

    private fun yenidenDene() {
        ana.removeCallbacks(nabiz)
        if (!acik) return
        val b = bekleme
        bekleme = minOf(bekleme * 2, 10_000L)
        ana.postDelayed({ baglan() }, b)
    }
}
