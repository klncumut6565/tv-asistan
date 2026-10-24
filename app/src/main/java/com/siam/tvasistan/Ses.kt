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
    private val hata: ((String) -> Unit)? = null,
    private val parca: (ByteArray) -> Unit,
) {
    @Volatile private var calisiyor = false
    private var kayit: AudioRecord? = null
    private var is_: Thread? = null

    @SuppressLint("MissingPermission")
    fun baslat() {
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(min, 12800)
        )
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            throw IllegalStateException("Mikrofon açılamadı (TV'de kullanılabilir mikrofon yok olabilir).")
        }
        try {
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(r.audioSessionId)?.enabled = true
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(r.audioSessionId)?.enabled = true
        } catch (_: Exception) {
        }
        r.startRecording()
        kayit = r
        calisiyor = true
        is_ = Thread({
            val tampon = ByteArray(1280) // 40 ms
            var ardisikHata = 0
            while (calisiyor) {
                val n = r.read(tampon, 0, tampon.size)
                if (n > 0) {
                    ardisikHata = 0
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
class Oynatici {
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
        track.play()
        while (acik) {
            val p = try { kuyruk.poll(100, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { null } ?: continue
            val simdi = SystemClock.elapsedRealtime()
            val sureMs = p.size / 48L // 24000 örnek/sn × 2 bayt = 48 bayt/ms
            bitis = maxOf(bitis, simdi) + sureMs
            track.write(p, 0, p.size)
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
