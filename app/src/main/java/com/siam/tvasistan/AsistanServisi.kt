package com.siam.tvasistan

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Asistanın merkezi: kumanda tuşunu dinler, sesli oturumu yönetir,
 * araçlar üzerinden diğer uygulamaları açar/kontrol eder.
 */
class AsistanServisi : AccessibilityService(), GeminiLive.Dinleyici {

    companion object {
        private const val ETIKET = "TvAsistan"
        /** Yeni sürüm denetim aralığı. */
        private const val GUNCELLEME_ARALIGI_MS = 6 * 60 * 60 * 1000L
        @Volatile var ornek: AsistanServisi? = null
            private set

        private val VARSAYILAN_TETIKLER = setOf(
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_VOICE_ASSIST,
        )
        /** Öğrenme modunda tetik olarak atanamayacak tuşlar (kilitlenmeyi önler). */
        private val YASAKLI = setOf(
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_POWER,
        )
    }

    enum class Durum { BOSTA, BAGLANIYOR, DINLIYOR }

    private val ana = Handler(Looper.getMainLooper())
    private val kapsam = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var ayarlar: Ayarlar
    private lateinit var panel: Panel
    private lateinit var araclar: Araclar

    @Volatile var durum = Durum.BOSTA
        private set
    private var live: GeminiLive? = null
    private var mikrofon: Mikrofon? = null
    @Volatile private var oynatici: Oynatici? = null
    private var odakIstegi: AudioFocusRequest? = null

    @Volatile private var sonEtkinlik = 0L
    @Volatile private var bostaZamanAsimiMs = 20_000L
    /** İndirilmeyi bekleyen yeni sürüm (TV ana ekrandayken kurulum onayı açılır). */
    @Volatile private var bekleyenSurum: Guncelleme.Surum? = null
    @Volatile private var bekleyenKapat = false
    @Volatile private var oturumSonuOynat = false

    @Volatile var aktifPaket: String? = null
        private set

    private val kullaniciMetin = StringBuilder()
    private val asistanMetin = StringBuilder()
    private var yeniTur = true

    // ------------------------------------------------------------ yaşam döngüsü

    override fun onServiceConnected() {
        super.onServiceConnected()
        ayarlar = Ayarlar(this)
        panel = Panel(this)
        araclar = Araclar(this)
        ornek = this
        Log.i(ETIKET, "Erişilebilirlik servisi bağlandı")
        guncellemeDongusu()
    }

    // ------------------------------------------------------------ otomatik güncelleme

    private fun guncellemeDongusu() {
        kapsam.launch {
            delay(30_000)
            var sonDenetim = 0L
            while (true) {
                val simdi = SystemClock.elapsedRealtime()
                if (ayarlar.otomatikGuncelleme && (sonDenetim == 0L || simdi - sonDenetim > GUNCELLEME_ARALIGI_MS)) {
                    sonDenetim = simdi
                    bekleyenSurum = try {
                        withContext(Dispatchers.IO) { Guncelleme.denetle() }
                    } catch (e: Exception) {
                        Log.w(ETIKET, "Güncelleme denetlenemedi: ${e.message}")
                        bekleyenSurum
                    }
                }
                val s = bekleyenSurum
                // Film izlerken araya girmemek için onay yalnızca TV ana ekrandayken açılır.
                if (s != null && durum == Durum.BOSTA && anaEkrandaMi() && !Guncelleme.kuruluyor) {
                    guncellemeyiKur(s)
                }
                // Bekleyen sürüm yoksa 15 dk uyu; varsa ana ekrana dönülmesini dakikada bir kontrol et
                delay(if (bekleyenSurum != null) 60_000 else 15 * 60_000)
            }
        }
    }

    private fun anaEkrandaMi(): Boolean {
        val ana = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName ?: return false
        return aktifPaket == ana
    }

    /** Sesli komut ("kendini güncelle") veya otomatik döngü tarafından çağrılır. */
    fun guncellemeyiKur(s: Guncelleme.Surum) {
        bekleyenSurum = null
        panel.goster("Yeni sürüm ${s.ad} indiriliyor…")
        kapsam.launch {
            try {
                withContext(Dispatchers.IO) { Guncelleme.kur(this@AsistanServisi, s) }
                panel.durum("Kurulum için ekrandaki Yükle'ye basın.")
            } catch (e: Exception) {
                panel.durum("Güncelleme yapılamadı: ${e.message}")
            }
            delay(6000)
            if (durum == Durum.BOSTA) panel.gizle()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = event.packageName?.toString() ?: return
            if (p != packageName && p != "com.android.systemui") aktifPaket = p
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        bitir()
        ornek = null
        kapsam.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ kumanda tuşu

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::ayarlar.isInitialized) return false
        val kod = event.keyCode

        if (ayarlar.tusOgrenme) {
            if (kod in YASAKLI) return false
            if (event.action == KeyEvent.ACTION_UP) {
                ayarlar.tetikTusu = kod
                ayarlar.tusOgrenme = false
                ana.post {
                    Toast.makeText(this, "Tetik tuşu kaydedildi: ${KeyEvent.keyCodeToString(kod)}", Toast.LENGTH_LONG).show()
                }
            }
            return true
        }

        val tetik = ayarlar.tetikTusu
        val eslesti = if (tetik > 0) kod == tetik else kod in VARSAYILAN_TETIKLER
        if (!eslesti) return false
        if (event.action == KeyEvent.ACTION_UP) ana.post { tetiklendi() }
        return true
    }

    fun tetiklendi() {
        when (durum) {
            Durum.BOSTA -> oturumBaslat()
            Durum.BAGLANIYOR -> bitir()
            Durum.DINLIYOR -> {
                val o = oynatici
                if (o != null && o.caliyor()) {
                    o.temizle()
                    panel.durum("Jülide dinliyor…")
                } else {
                    bitir()
                }
            }
        }
    }

    // ------------------------------------------------------------ oturum

    fun oturumBaslat() {
        if (durum != Durum.BOSTA) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            panel.goster("Mikrofon izni yok — Jülide uygulamasını açıp izin verin.")
            ana.postDelayed({ if (durum == Durum.BOSTA) panel.gizle() }, 6000)
            return
        }
        durum = Durum.BAGLANIYOR
        bekleyenKapat = false
        oturumSonuOynat = false
        kullaniciMetin.clear()
        asistanMetin.clear()
        yeniTur = true
        panel.goster("Bağlanıyor…")
        try {
            startForegroundService(Intent(this, MikrofonServisi::class.java))
        } catch (e: Exception) {
            Log.w(ETIKET, "Ön plan servisi başlatılamadı: ${e.message}")
        }
        sesOdagiAl()

        kapsam.launch {
            try {
                val y = withContext(Dispatchers.IO) { Uzak.getir(ayarlar) }
                bostaZamanAsimiMs = y.bostaSn * 1000L
                val talimat = withContext(Dispatchers.IO) { araclar.sistemTalimati() }
                val l = GeminiLive(this@AsistanServisi)
                live = l
                withContext(Dispatchers.IO) { l.baglan(ayarlar.geminiAnahtari, y, talimat, araclar.bildirimler()) }
                if (durum != Durum.BAGLANIYOR) {
                    l.kapat()
                    return@launch
                }
                val oy = Oynatici()
                oynatici = oy
                val kapi = SesKapisi(y.vadEsik, gonder = { l.sesGonder(it) }, kapandi = { l.sesAkisiBitti() })
                val m = Mikrofon(hata = { mesaj -> ana.post { hataGoster(mesaj) } }) { pcm ->
                    // Yarı çift yönlü: asistan konuşurken TV hoparlöründen gelen sesi modele geri gönderme.
                    if (!oy.caliyor()) kapi.isle(pcm)
                }
                withContext(Dispatchers.IO) { m.baslat() }
                mikrofon = m
                durum = Durum.DINLIYOR
                sonEtkinlik = SystemClock.elapsedRealtime()
                panel.durum("Jülide dinliyor…")
                zamanlayici()
            } catch (e: Exception) {
                Log.e(ETIKET, "Oturum başlatılamadı", e)
                hataGoster(e.message ?: "Bilinmeyen hata")
            }
        }
    }

    private fun zamanlayici() {
        kapsam.launch {
            while (durum == Durum.DINLIYOR) {
                delay(500)
                val caliyor = oynatici?.caliyor() == true
                val simdi = SystemClock.elapsedRealtime()
                if (caliyor) sonEtkinlik = simdi
                if (bekleyenKapat && !caliyor) {
                    bitir()
                    break
                }
                if (simdi - sonEtkinlik > bostaZamanAsimiMs) {
                    bitir()
                    break
                }
            }
        }
    }

    /** Araç: kullanıcı "tamam/kapat" dedi — asistanın veda cümlesi bitince kapat. */
    fun oturumuKapatIste() {
        bekleyenKapat = true
    }

    @Volatile private var sonraKurulacak: Guncelleme.Surum? = null

    /** Araç: sesli "güncelle" — oturum kapanınca kurulum başlar. */
    fun guncellemeSonraKur(s: Guncelleme.Surum) {
        sonraKurulacak = s
    }

    fun bitir() {
        val oncekiDurum = durum
        durum = Durum.BOSTA
        mikrofon?.durdur()
        mikrofon = null
        live?.kapat()
        live = null
        oynatici?.kapat()
        oynatici = null
        sesOdagiBirak()
        try {
            stopService(Intent(this, MikrofonServisi::class.java))
        } catch (_: Exception) {
        }
        if (::panel.isInitialized) panel.gizle()
        sonraKurulacak?.let { s ->
            sonraKurulacak = null
            ana.postDelayed({ guncellemeyiKur(s) }, 1500)
        }
        if (oturumSonuOynat && oncekiDurum != Durum.BOSTA) {
            oturumSonuOynat = false
            ana.postDelayed({ medyaTusuGonder(KeyEvent.KEYCODE_MEDIA_PLAY) }, 600)
        }
    }

    private fun hataGoster(m: String) {
        bitir()
        panel.goster("Hata: $m")
        ana.postDelayed({ if (durum == Durum.BOSTA) panel.gizle() }, 7000)
    }

    // ------------------------------------------------------------ ses odağı ve medya

    private fun sesOdagiAl() {
        val am = getSystemService(AudioManager::class.java)
        val istek = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        am.requestAudioFocus(istek)
        odakIstegi = istek
    }

    private fun sesOdagiBirak() {
        val istek = odakIstegi ?: return
        getSystemService(AudioManager::class.java).abandonAudioFocusRequest(istek)
        odakIstegi = null
    }

    /** Araçlardan çağrılır. "Oynat" oturum bitince tekrar gönderilir (ses odağı geri dönünce). */
    fun medyaTusu(kod: Int) {
        medyaTusuGonder(kod)
        oturumSonuOynat = kod == KeyEvent.KEYCODE_MEDIA_PLAY || kod == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    }

    private fun medyaTusuGonder(kod: Int) {
        val am = getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, kod))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, kod))
    }

    // ------------------------------------------------------------ GeminiLive.Dinleyici (arka plan iş parçacığı)

    override fun sesGeldi(pcm: ByteArray) {
        sonEtkinlik = SystemClock.elapsedRealtime()
        oynatici?.ekle(pcm)
        panel.durum("Konuşuyor…")
    }

    override fun kesildi() {
        oynatici?.temizle()
    }

    override fun turBitti() {
        ana.post {
            yeniTur = true
            if (durum == Durum.DINLIYOR && !bekleyenKapat) panel.durum("Jülide dinliyor…")
        }
    }

    override fun kullaniciMetni(t: String) {
        sonEtkinlik = SystemClock.elapsedRealtime()
        ana.post {
            if (yeniTur) {
                kullaniciMetin.clear()
                asistanMetin.clear()
                yeniTur = false
            }
            kullaniciMetin.append(t)
            panel.metin(kullaniciMetin.toString(), asistanMetin.toString())
        }
    }

    override fun asistanMetni(t: String) {
        sonEtkinlik = SystemClock.elapsedRealtime()
        ana.post {
            yeniTur = false
            asistanMetin.append(t)
            panel.metin(kullaniciMetin.toString(), asistanMetin.toString())
        }
    }

    override fun aracCagrisi(ad: String, args: JSONObject): JSONObject {
        sonEtkinlik = SystemClock.elapsedRealtime()
        panel.durum("İşlem: ${ad.replace('_', ' ')}")
        val sonuc = araclar.calistir(ad, args)
        sonEtkinlik = SystemClock.elapsedRealtime()
        return sonuc
    }

    override fun hata(mesaj: String) {
        ana.post { if (durum != Durum.BOSTA) hataGoster(mesaj) }
    }

    override fun kapandi() {
        ana.post { if (durum != Durum.BOSTA) bitir() }
    }
}
