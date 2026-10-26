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

    /** Son tetik tuşu basışı (elapsedRealtime). Kumanda mikrofonu yalnız bu tuşla açılır. */
    @Volatile private var tusAni = 0L

    /**
     * Kumandadaki Asistan/tetik tuşuna basıldı: kumanda ~15 sn ses gönderecek. Ses bu kayda birkaç yüz
     * ms içinde gelmezse kayıt bir kez temiz yenilenir (kumanda sesi yeni kayda yönlensin).
     */
    fun tusaBasildi() {
        tusAni = SystemClock.elapsedRealtime()
    }

    companion object {
        /** Tuşa basıldıktan sonra kumanda sesini bekleme penceresi. */
        private const val TUS_PENCERESI_MS = 3_000L
        /** Pencere içinde bu kadar ardışık sıfır parça (40 ms) → ses yönlenmemiş, kaydı yenile: ~0,4 sn. */
        private const val SIFIR_PARCA_ESIK = 10
        /** Pencere içinde iki yenileme arasında en az bu kadar süre. */
        private const val YENILEME_ARALIGI_MS = 800L
        /** Kapatma ile yeniden açma arasındaki kısa ara. */
        private const val TEMIZ_ARA_MS = 150L

        /** Tanı için sistem ses yöneticisi (AsistanServisi atar). */
        @Volatile var sesYoneticisi: android.media.AudioManager? = null
        /** Oturumlar arası: son kez sıfır olmayan ses veren kaynağın indeksi (-1 = henüz yok). */
        @Volatile var sonCalisanKalici = -1
    }

    @Volatile private var calisiyor = false
    private var kayit: AudioRecord? = null
    private var is_: Thread? = null
    /** Açılan her kayıt için kaydedilen AudioRecordingCallback'ler; durdur()'da sökülür. */
    private val geriCagrilar = java.util.concurrent.CopyOnWriteArrayList<android.media.AudioManager.AudioRecordingCallback>()

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
        // TANI: sistem kaydı susturuyor mu? (silenced=true ise sorun Android/TV tarafında)
        val yonetici = sesYoneticisi
        // Tek geri çağrı yeter: kayıt artık ~13 sn'de bir yenilendiği için her açılışta yenisini eklemek
        // geri çağrıları biriktiriyordu. Geri çağrı o anki kaydın oturumuna bakar.
        if (android.os.Build.VERSION.SDK_INT >= 29 && yonetici != null && geriCagrilar.isEmpty()) {
            try {
                val cb = object : android.media.AudioManager.AudioRecordingCallback() {
                    override fun onRecordingConfigChanged(configs: MutableList<android.media.AudioRecordingConfiguration>) {
                        val su = kayit ?: return
                        for (c in configs) {
                            if (c.clientAudioSessionId != su.audioSessionId) continue
                            android.util.Log.i("TvAsistan", "TANI kayit: kaynak=$kaynakAdi silenced=${c.isClientSilenced}")
                        }
                    }
                }
                geriCagrilar.add(cb)
                yonetici.registerAudioRecordingCallback(cb, android.os.Handler(android.os.Looper.getMainLooper()))
            } catch (_: Exception) {
            }
        }
        // USB mikrofon takılıysa dahili mikrofon yerine onu kullan (TV'nin sıfır veren dahili mikrofonunu atla)
        try {
            if (yonetici != null && android.os.Build.VERSION.SDK_INT >= 28) {
                val girisler = yonetici.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
                // Öncelik: USB mikrofon, sonra Bluetooth (SCO) mikrofon
                val secilen = girisler.firstOrNull {
                    it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE || it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                } ?: girisler.firstOrNull {
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (secilen != null) {
                    r.setPreferredDevice(secilen)
                    android.util.Log.i("TvAsistan", "TANI giriş cihazı: ${secilen.productName} tip=${secilen.type} tercih edildi")
                } else {
                    android.util.Log.i("TvAsistan", "TANI giriş cihazı: harici mikrofon yok, dahili kullanılıyor")
                }
            }
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

    /** Denenecek kaynak sırası: yankı engelli → tanıma → ham mikrofon. */
    private fun kaynaklar(): List<Pair<Int, String>> = buildList {
        if (yankiEngelle) add(MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION")
        add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION")
        add(MediaRecorder.AudioSource.MIC to "MIC")
    }

    /** [bas] indeksinden başlayarak açılabilen ilk kaynağı açar. [tercih] geçerliyse önce o denenir. */
    private fun sirayla(bas: Int, tercih: Int = -1): Triple<AudioRecord, String, Int>? {
        val l = kaynaklar()
        // Son kez sıfır olmayan ses veren kaynak önce denenir (loglarda VOICE_RECOGNITION hep canlandırdı)
        if (tercih in l.indices) {
            ac(l[tercih].first)?.let { return Triple(it, l[tercih].second, tercih) }
        }
        for (k in l.indices) {
            val i = (bas + k) % l.size
            ac(l[i].first)?.let { return Triple(it, l[i].second, i) }
        }
        return null
    }

    fun baslat() {
        // Hatırlanan kaynak yoksa (uygulama yeni başladıysa) ilk deneme VOICE_RECOGNITION: loglarda VOICE_COMMUNICATION
        // ilk açılışta hep sıfır verdi, VOICE_RECOGNITION bazen veri getirdi.
        val varsayilan = kaynaklar().indexOfFirst { it.first == MediaRecorder.AudioSource.VOICE_RECOGNITION }.coerceAtLeast(0)
        val ilk = sirayla(0, if (sonCalisanKalici >= 0) sonCalisanKalici else varsayilan) ?: throw IllegalStateException("Mikrofon açılamadı (TV'de kullanılabilir mikrofon yok olabilir).")
        val ilkKayit: AudioRecord = ilk.first
        kaynakAdi = ilk.second
        android.util.Log.i("TvAsistan", "Mikrofon kaynağı: $kaynakAdi")
        kayit = ilkKayit
        calisiyor = true
        is_ = Thread({
            val tampon = ByteArray(1280) // 40 ms
            var ardisikHata = 0
            var rec: AudioRecord = ilkKayit
            var kaynakSira = ilk.third
            var sifirParca = 0
            var yenilemeSayisi = 0
            var sonYenileme = 0L
            var sonCalisan = -1

            // ---- Bas-konuş (TANI 27.10.2026) ----
            // Bu TV'nin (Toshiba 50UA3D63DT, Vestel, Android 14) dahili mikrofon yolu hep SIFIR verir.
            // Ses yalnız KUMANDANIN mikrofonundan gelir: Asistan tuşuna basınca kumanda mikrofonu açılır
            // (remote.service: "Mic Open", "Frames received"), ses ~15 sn boyunca kayıt yapanlara enjekte edilir.
            // Bu yüzden tuşa basılmamışken gelen sıfırlar NORMALDİR (kayıt açılıp kapatılmaz); yalnız tuşa
            // basıldıktan sonraki 3 sn içinde ses gelmezse kayıt temiz yenilenir.
            var acilisAni = SystemClock.elapsedRealtime()
            var sonTemiz = 0L
            tusAni = acilisAni // oturum genelde tuşla başlar

            /** Kaydı kapatıp aynı kaynakla yeniden açar. Açılamazsa birkaç kez dener; olmazsa false. */
            fun temizYenile(neden: String): Boolean {
                try { rec.stop() } catch (_: Exception) {}
                try { rec.release() } catch (_: Exception) {}
                kayit = null
                for (deneme in 1..5) {
                    try { Thread.sleep(if (deneme == 1) TEMIZ_ARA_MS else 400L) } catch (_: InterruptedException) { return false }
                    if (!calisiyor) return false
                    val yeni = sirayla(kaynakSira, kaynakSira) ?: continue
                    rec = yeni.first
                    kayit = yeni.first
                    kaynakSira = yeni.third
                    kaynakAdi = yeni.second
                    val simdi = SystemClock.elapsedRealtime()
                    android.util.Log.i("TvAsistan", "Mikrofon yenilendi ($neden, ${simdi - acilisAni} ms sonra): $kaynakAdi")
                    acilisAni = simdi
                    sonTemiz = simdi
                    sifirParca = 0
                    return true
                }
                return false
            }

            while (calisiyor) {
                val n = rec.read(tampon, 0, tampon.size)
                if (n > 0) {
                    ardisikHata = 0
                    // Gerçek bir odada ortam gürültüsü sıfır olmaz; tam sıfır = ses gelmiyor.
                    if (pcmRms(tampon, 0, n) < 1.0) sifirParca++ else {
                        sifirParca = 0; yenilemeSayisi = 0; sonCalisan = kaynakSira; sonCalisanKalici = kaynakSira
                    }
                    val simdi = SystemClock.elapsedRealtime()
                    val tusPenceresi = simdi - tusAni in 0..TUS_PENCERESI_MS
                    if (tusPenceresi && sifirParca >= SIFIR_PARCA_ESIK && simdi - sonTemiz >= YENILEME_ARALIGI_MS) {
                        if (temizYenile("tuş")) continue
                        hata?.invoke("Mikrofon yeniden açılamadı.")
                        break
                    }
                    parca(tampon.copyOf(n))
                } else {
                    // read() hata/0 dönerse sıkı döngüde işlemciyi yakmasın
                    if (++ardisikHata > 50) {
                        // Okuma bozulduysa bir kez yeni kayıt aç; o da olmazsa ancak o zaman bildir
                        val yeni = sirayla(kaynakSira)
                        if (yeni != null) {
                            try { rec.release() } catch (_: Exception) {}
                            rec = yeni.first
                            kayit = yeni.first
                            kaynakSira = yeni.third
                            kaynakAdi = yeni.second
                            ardisikHata = 0
                            android.util.Log.w("TvAsistan", "Mikrofon okuma hatası ($n), kayıt yeniden açıldı: $kaynakAdi")
                            continue
                        }
                        hata?.invoke("Mikrofon okunamıyor (kod $n).")
                        break
                    }
                    try { Thread.sleep(20) } catch (_: InterruptedException) { break }
                }
            }
            // Durdurulurken temiz yenileme yeni bir kayıt açtıysa o da serbest kalsın (release iki kez güvenli).
            if (!calisiyor) {
                try { rec.stop() } catch (_: Exception) {}
                try { rec.release() } catch (_: Exception) {}
            }
        }, "mikrofon").apply { start() }
    }

    fun durdur() {
        calisiyor = false
        try { is_?.join(300) } catch (_: InterruptedException) {}
        try { kayit?.stop() } catch (_: Exception) {}
        kayit?.release()
        kayit = null
        // Geri çağrıları sök; aksi halde her açma denemesinde bir geri çağrı birikip AudioManager'da kalıyordu
        val yonetici = sesYoneticisi
        if (android.os.Build.VERSION.SDK_INT >= 29 && yonetici != null) {
            for (cb in geriCagrilar) {
                try { yonetici.unregisterAudioRecordingCallback(cb) } catch (_: Exception) {}
            }
        }
        geriCagrilar.clear()
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

/**
 * Ses çıkışını UYANIK tutan, duyulmayan sessizlik akışı.
 *
 * Bu TV'nin (Vestel/MediaTek) yerleşik mikrofonu yankı engelleme için hoparlör çıkışını referans
 * alır: hoparlörden ses çıkmazken çıkış uykuya geçer, referans kesilir ve mikrofon uygulamalara
 * yalnızca SIFIR verir (TANI kaydı: medya/Jülide sesi yokken %100 sıfır, ses başlayınca anında canlı).
 * Sohbet boyunca ±1 bitlik (duyulmaz) gürültü çalarak çıkışı açık tutar.
 * MEDYA kanalından çalar (yalnız bu kanal mikrofonu açık tutuyor). Bu yüzden 'video çalıyor mu'
 * artık isMusicActive ile değil, öndeki uygulamaya bakılarak belirlenir (AsistanServisi.medyaCaliyor).
 */
class SessizCikis {
    @Volatile private var calisiyor = false
    private var is_: Thread? = null

    fun baslat() {
        if (calisiyor) return
        calisiyor = true
        is_ = Thread({
            val oran = 16000
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            // MEDYA kanalı: TV mikrofonu yalnız medya akışı çalarken açık (TANI 06:00:53–06:01:07);
                            // bildirim kanalı (SONIFICATION) mikrofonu uyandırmıyordu.
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(oran)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(oran, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), 6400))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Exception) {
                android.util.Log.w("TvAsistan", "Sessiz çıkış açılamadı: ${e.message}")
                calisiyor = false
                return@Thread
            }
            // 40 ms'lik ±30 LSB gürültü (≈ -60 dBFS). ±1 LSB, TV ses donanımında "sessizlik" sayılıp
            // çıkış uyku moduna geçiyordu (TANI: BUFFER TIMEOUT); bu seviye çıkışı uyanık tutmalı.
            val tampon = ByteArray(1280)
            val rnd = java.util.Random()
            for (i in tampon.indices step 2) {
                val v = rnd.nextInt(61) - 30
                tampon[i] = (v and 0xFF).toByte()
                tampon[i + 1] = ((v shr 8) and 0xFF).toByte()
            }
            try {
                track.play()
                android.util.Log.i("TvAsistan", "TANI olay: sessiz çıkış başladı (mikrofon referansı açık)")
                var yazilan = 0
                var sonLog = SystemClock.elapsedRealtime()
                while (calisiyor) {
                    val n = track.write(tampon, 0, tampon.size) // yazma, çalma hızında bloklar
                    yazilan++
                    // TANI: çıkış akışı durumu (her 3 sn ya da yazma hatasında)
                    val simdi = SystemClock.elapsedRealtime()
                    if (n < 0 || simdi - sonLog > 3000) {
                        android.util.Log.i("TvAsistan", "TANI sessiz: yazma=$n durum=${track.playState} parca=$yazilan")
                        sonLog = simdi
                    }
                }
                android.util.Log.i("TvAsistan", "TANI sessiz: döngü bitti (calisiyor=$calisiyor) parca=$yazilan")
            } catch (e: Exception) {
                android.util.Log.w("TvAsistan", "TANI sessiz: hata ${e.javaClass.simpleName} ${e.message}")
            } finally {
                try { track.stop() } catch (_: Exception) {}
                track.release()
            }
        }, "sessiz-cikis").apply { start() }
    }

    fun durdur() {
        calisiyor = false
        try { is_?.join(300) } catch (_: InterruptedException) {}
        is_ = null
    }
}
