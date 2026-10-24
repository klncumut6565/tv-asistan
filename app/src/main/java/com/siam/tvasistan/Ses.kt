package com.siam.tvasistan

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Mikrofon: 16 kHz mono 16-bit PCM, 40 ms'lik parçalar. */
class Mikrofon(
    /**
     * true: VOICE_COMMUNICATION kaynağı — TV'nin ses donanımı destekliyorsa kendi hoparlör
     * sesini (yankı/TV sesi) mikrofondan çıkarır. Desteklemiyorsa ya da kaynak sessiz kalırsa
     * otomatik olarak VOICE_RECOGNITION'a döner.
     */
    private val yankiEngelle: Boolean = true,
    private val hata: ((String) -> Unit)? = null,
    private val parca: (ByteArray) -> Unit,
) {
    /** Şu an kullanılan kaynak (tanı için). */
    @Volatile var kaynakAdi = ""
        private set

    @Volatile private var calisiyor = false
    private var kayit: AudioRecord? = null
    private var is_: Thread? = null

    @SuppressLint("MissingPermission")
    private fun ac(kaynak: Int): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = try {
            AudioRecord(kaynak, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 12800))
        } catch (_: Exception) {
            return null
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return null
        }
        try {
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(r.audioSessionId)?.enabled = true
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(r.audioSessionId)?.enabled = true
        } catch (_: Exception) {
        }
        return try {
            r.startRecording()
            if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) { r.release(); null } else r
        } catch (_: Exception) {
            r.release()
            null
        }
    }

    fun baslat() {
        var r: AudioRecord? = null
        if (yankiEngelle) {
            r = ac(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            if (r != null) kaynakAdi = "VOICE_COMMUNICATION"
        }
        if (r == null) {
            r = ac(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            if (r != null) kaynakAdi = "VOICE_RECOGNITION"
        }
        val ilkKayit: AudioRecord = r ?: throw IllegalStateException("Mikrofon açılamadı (TV'de kullanılabilir mikrofon yok olabilir).")
        kayit = ilkKayit
        calisiyor = true
        is_ = Thread({
            val tampon = ByteArray(1280) // 40 ms
            var ardisikHata = 0
            var rec: AudioRecord = ilkKayit
            var sessizParca = 0
            var denetimBitti = !yankiEngelle || kaynakAdi != "VOICE_COMMUNICATION"
            while (calisiyor) {
                val n = rec.read(tampon, 0, tampon.size)
                if (n > 0) {
                    ardisikHata = 0
                    // Bazı TV'lerde iletişim kaynağı açılır ama hep sıfır verir: ilk 4 sn tamamen
                    // sessizse tanıma kaynağına geç.
                    if (!denetimBitti) {
                        if (pcmRms(tampon, 0, n) < 3.0) sessizParca++ else denetimBitti = true
                        if (sessizParca >= 100) {
                            denetimBitti = true
                            val yedek = ac(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                            if (yedek != null) {
                                try { rec.stop() } catch (_: Exception) {}
                                rec.release()
                                rec = yedek
                                kayit = yedek
                                kaynakAdi = "VOICE_RECOGNITION"
                                continue
                            }
                        }
                    }
                    parca(tampon.copyOf(n))
                } else {
                    // read() hata/0 dönerse sıkı döngüde işlemciyi yakmasın
                    if (++ardisikHata > 50) {
                        hata?.invoke("Mikrofon okunamıyor (kod $n).")
                        break
                    }
                    try { Thread.sleep(20) } catch (_: InterruptedException) { break }
                }
            }
        }, "mikrofon").apply { start() }
    }

    fun durdur() {
        calisiyor = false
        try { is_?.join(300) } catch (_: InterruptedException) {}
        try { kayit?.stop() } catch (_: Exception) {}
        kayit?.release()
        kayit = null
    }
}

/** Asistan sesi: 24 kHz mono 16-bit PCM akışı. */
class Oynatici(private val seviye: ((Float) -> Unit)? = null) {
    private val kuyruk = LinkedBlockingQueue<ByteArray>()
    @Volatile private var acik = true
    /** Yazılan sesin tahmini bitiş anı (elapsedRealtime ms). */
    @Volatile private var bitis = 0L

    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(24000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
        )
        .setBufferSizeInBytes(
            maxOf(
                AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                48000
            )
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private val is_ = Thread({
        var oynuyor = false
        while (acik) {
            val p = try { kuyruk.poll(100, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { null }
            if (p == null) {
                // Boştayken çalar duraklatılır: sistem "müzik çalıyor" sanmasın (video algılaması
                // yanlış tetiklenmesin) ve ses çıkışı gereksiz açık kalmasın.
                if (oynuyor && SystemClock.elapsedRealtime() > bitis + 400) {
                    try { track.pause() } catch (_: Exception) {}
                    oynuyor = false
                }
                continue
            }
            if (!oynuyor) {
                try { track.play() } catch (_: Exception) {}
                oynuyor = true
            }
            val simdi = SystemClock.elapsedRealtime()
            val sureMs = p.size / 48L // 24000 örnek/sn × 2 bayt = 48 bayt/ms
            bitis = maxOf(bitis, simdi) + sureMs
            // Küreyi konuşmayla senkron tutmak için parçayı ~40 ms'lik dilimlerle yaz ve seviyeyi bildir.
            var o = 0
            while (o < p.size && acik) {
                val n = minOf(1920, p.size - o)
                seviye?.invoke(sesSeviyesi(pcmRms(p, o, n)))
                track.write(p, o, n)
                o += n
            }
        }
    }, "oynatici").apply { start() }

    fun ekle(p: ByteArray) {
        if (acik) kuyruk.offer(p)
    }

    /** Asistan konuşuyor mu? (yankıyı modele geri göndermemek için) */
    fun caliyor(): Boolean = kuyruk.isNotEmpty() || SystemClock.elapsedRealtime() < bitis + 300

    /** Kesinti: kuyruktaki ve tampondaki sesi anında at. */
    fun temizle() {
        kuyruk.clear()
        try {
            track.pause()
            track.flush()
            track.play()
        } catch (_: Exception) {
        }
        bitis = 0L
    }

    fun kapat() {
        acik = false
        kuyruk.clear()
        try { is_.join(300) } catch (_: InterruptedException) {}
        try { track.stop() } catch (_: Exception) {}
        track.release()
    }
}

/**
 * Yerel konuşma algılama: yalnızca konuşma varken (öncesindeki ~320 ms ile birlikte) ve
 * bitişinden sonra 1,5 sn boyunca Gemini'ye ses gönderir; sessizlikte hiç veri gitmez.
 * 1,5 sn "kuyruk", sunucunun kendi bitiş algılaması (800 ms sessizlik) için gereklidir.
 */
class SesKapisi(
    private val esik: Int,
    private val gonder: (ByteArray) -> Unit,
    private val kapandi: () -> Unit,
) {
    private val onTampon = ArrayDeque<ByteArray>()
    /** Şu an konuşma algılanıp sunucuya ses gidiyor mu? */
    @Volatile var acik = false
        private set
    private var sonKonusma = 0L
    private var gurultu = 150.0
    /** Video/TV sesi varken eşiği yükseltmek için çarpan (1 = normal). */
    @Volatile var esikCarpani = 1.0

    fun isle(pcm: ByteArray) {
        val simdi = SystemClock.elapsedRealtime()
        val rms = rms(pcm)
        val konusma = rms > maxOf(esik.toDouble(), gurultu * 2.5) * esikCarpani
        if (!konusma && !acik) gurultu = gurultu * 0.95 + rms * 0.05 // ortam gürültüsünü yalnız sessizken öğren
        if (konusma) {
            sonKonusma = simdi
            if (!acik) {
                acik = true
                while (onTampon.isNotEmpty()) gonder(onTampon.removeFirst())
            }
        }
        if (acik) {
            gonder(pcm)
            if (simdi - sonKonusma > 1500) {
                acik = false
                kapandi()
            }
        } else {
            onTampon.addLast(pcm)
            if (onTampon.size > 8) onTampon.removeFirst()
        }
    }

    /** Gönderim sürerken mikrofon susturulursa (ör. video başladı) sunucuya konuşmanın bittiğini bildir. */
    fun kes() {
        onTampon.clear()
        if (acik) {
            acik = false
            kapandi()
        }
    }

    private fun rms(b: ByteArray): Double = pcmRms(b)
}

/** RMS → 0..1 görsel seviye (TMGD web asistanındaki ölçekle aynı: (rms − 0.008) × 4.5). */
fun sesSeviyesi(rms: Double, kazanc: Double = 4.5): Float =
    ((rms / 32768.0 - 0.008) * kazanc).coerceIn(0.0, 1.0).toFloat()

/** 16-bit little-endian PCM'in RMS değeri. */
fun pcmRms(b: ByteArray, bas: Int = 0, uzunluk: Int = b.size): Double {
    var toplam = 0.0
    var n = 0
    var i = bas
    val son = bas + uzunluk
    while (i + 1 < son) {
        val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xff)).toShort().toInt()
        toplam += s.toDouble() * s
        n++
        i += 2
    }
    return if (n == 0) 0.0 else Math.sqrt(toplam / n)
}
