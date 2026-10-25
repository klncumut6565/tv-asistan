package com.siam.tvasistan

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * "Jülide" uyandırma kelimesi — TAMAMEN TV'NİN İÇİNDE, İNTERNETSİZ çalışır (Vosk, küçük Türkçe model).
 *
 * Video/canlı yayın açıkken mikrofon sesi Gemini'ye GİTMEZ; yalnızca bu yerel dinleyiciye verilir.
 * Dinleyici "Jülide" kelimesini duyunca [uyandi] çağrılır, servis TV'yi kısar/duraklatır ve
 * son birkaç saniyenin sesini ("Jülide, TRT Haber'i aç") Gemini'ye iletir.
 *
 * Model (~35 MB) ilk kullanımda GitHub Releases'tan indirilir, SHA-256 ile doğrulanır ve TV'de
 * saklanır. Bellek için yalnızca sohbet açıkken yüklenir.
 */
class Uyandirma(
    private val ctx: Context,
    private val kelimeler: List<String>,
    private val uyandi: () -> Unit,
) {
    companion object {
        private const val ETIKET = "TvAsistan"
        private const val MODEL_ADI = "vosk-model-small-tr-0.3"
        private const val MODEL_URL =
            "https://github.com/klncumut6565/tv-asistan/releases/download/vosk-tr-0.3/vosk-model-small-tr-0.3.zip"
        /** CI'nın (model.yml) yansıttığı dosyanın SHA-256'sı. Uyuşmazsa model kullanılmaz. */
        const val MODEL_SHA256 = "8c8d07cec1bce31add14967c16891c84152cdf76d391e33c08278119b9ea96e5"

        /** Ayarlar ekranı için durum metni. */
        @Volatile var durum: String = "Hazırlanmadı"
            private set

        @Volatile private var indiriliyor = false

        private fun klasor(ctx: Context) = File(ctx.filesDir, "vosk-tr")
        fun modelHazirMi(ctx: Context) = File(klasor(ctx), ".tamam").exists()

        /** Modeli (yoksa) indirir, doğrular ve açar. Arka plan iş parçacığında çağrılmalı. */
        @Synchronized
        fun modeliHazirla(ctx: Context) {
            if (modelHazirMi(ctx)) { durum = "Hazır"; return }
            if (indiriliyor) return
            indiriliyor = true
            val gecici = File(ctx.cacheDir, "$MODEL_ADI.zip")
            val acilan = File(ctx.filesDir, "vosk-tr.yeni")
            try {
                durum = "İndiriliyor…"
                val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
                http.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("Model indirilemedi (HTTP ${r.code}).")
                    val govde = r.body ?: throw IOException("Model indirilemedi (boş yanıt).")
                    val toplam = govde.contentLength()
                    val ozet = MessageDigest.getInstance("SHA-256")
                    govde.byteStream().use { giris ->
                        gecici.outputStream().use { cikis ->
                            val tampon = ByteArray(64 * 1024)
                            var okunan = 0L
                            var sonYuzde = -1
                            while (true) {
                                val n = giris.read(tampon)
                                if (n < 0) break
                                cikis.write(tampon, 0, n)
                                ozet.update(tampon, 0, n)
                                okunan += n
                                if (toplam > 0) {
                                    val y = (okunan * 100 / toplam).toInt()
                                    if (y != sonYuzde) { sonYuzde = y; durum = "İndiriliyor… %$y" }
                                }
                            }
                        }
                    }
                    val sha = ozet.digest().joinToString("") { "%02x".format(it) }
                    if (!sha.equals(MODEL_SHA256, ignoreCase = true)) {
                        throw IOException("Model doğrulanamadı (SHA-256 uyuşmuyor).")
                    }
                }
                durum = "Açılıyor…"
                acilan.deleteRecursively()
                acilan.mkdirs()
                val kok = acilan.canonicalPath + File.separator
                ZipInputStream(gecici.inputStream().buffered()).use { zip ->
                    while (true) {
                        val g = zip.nextEntry ?: break
                        // Üst klasörü (vosk-model-small-tr-0.3/) at
                        val ad = g.name.substringAfter('/', "")
                        if (ad.isEmpty()) continue
                        val hedef = File(acilan, ad)
                        if (!hedef.canonicalPath.startsWith(kok)) throw IOException("Geçersiz model arşivi.")
                        if (g.isDirectory) hedef.mkdirs() else {
                            hedef.parentFile?.mkdirs()
                            hedef.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                val k = klasor(ctx)
                k.deleteRecursively()
                if (!acilan.renameTo(k)) throw IOException("Model klasörü taşınamadı.")
                File(k, ".tamam").writeText(MODEL_SHA256)
                durum = "Hazır"
            } catch (e: Exception) {
                Log.w(ETIKET, "Uyandırma modeli hazırlanamadı: ${e.message}")
                durum = "Hata: ${e.message}"
                acilan.deleteRecursively()
            } finally {
                gecici.delete()
                indiriliyor = false
            }
        }
    }

    private val kuyruk = LinkedBlockingQueue<ByteArray>(60)
    @Volatile private var calisiyor = false
    private var is_: Thread? = null
    @Volatile private var sonUyanma = 0L

    /** Modeli yükler ve dinleyiciyi başlatır (arka planda). Model yoksa önce indirir. */
    fun baslat() {
        if (calisiyor) return
        calisiyor = true
        is_ = Thread({
            var model: Model? = null
            var rec: Recognizer? = null
            try {
                modeliHazirla(ctx)
                if (!modelHazirMi(ctx) || !calisiyor) return@Thread
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                model = Model(klasor(ctx).absolutePath)
                rec = Recognizer(model, 16000f)
                Log.i(ETIKET, "Uyandırma dinleyicisi hazır")
                while (calisiyor) {
                    val p = kuyruk.poll(200, TimeUnit.MILLISECONDS) ?: continue
                    val bitti = rec.acceptWaveForm(p, p.size)
                    val json = if (bitti) rec.result else rec.partialResult
                    val metin = JSONObject(json).let { it.optString("text").ifEmpty { it.optString("partial") } }
                    if (metin.isNotEmpty() && eslesir(metin)) {
                        val simdi = android.os.SystemClock.elapsedRealtime()
                        if (simdi - sonUyanma > 2500) {
                            sonUyanma = simdi
                            Log.i(ETIKET, "Uyandırma kelimesi duyuldu: \"$metin\"")
                            uyandi()
                        }
                        rec.reset()
                    } else if (bitti) {
                        // Ayar için: tanıyıcının duyduğu tam cümleler (yalnız TV'nin yerel kaydında kalır)
                        if (metin.isNotEmpty()) Log.i(ETIKET, "Uyandırma duydu: \"${metin.take(60)}\"")
                        rec.reset()
                    }
                }
            } catch (e: Throwable) {
                Log.w(ETIKET, "Uyandırma dinleyicisi durdu: ${e.message}")
                durum = "Hata: ${e.message}"
            } finally {
                try { rec?.close() } catch (_: Throwable) {}
                try { model?.close() } catch (_: Throwable) {}
            }
        }, "uyandirma").apply { start() }
    }

    /** Mikrofon parçasını dinleyiciye verir; dinleyici yetişemezse parça atılır (mikrofonu bekletmez). */
    fun besle(pcm: ByteArray) {
        if (calisiyor) kuyruk.offer(pcm)
    }

    fun durdur() {
        calisiyor = false
        kuyruk.clear()
        try { is_?.join(500) } catch (_: InterruptedException) {}
        is_ = null
    }

    private fun eslesir(metin: String): Boolean {
        val n = Uygulamalar.normal(metin)
        val bitisik = n.replace(" ", "")
        return kelimeler.any { k ->
            val kn = Uygulamalar.normal(k)
            kn.isNotEmpty() && (bitisik.contains(kn.replace(" ", "")) || (" $n ").contains(" $kn "))
        }
    }
}
