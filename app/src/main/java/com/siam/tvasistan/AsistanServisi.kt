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
    /** Bas-konuş sırasında videoyu duraklatmak/kısmak için alınan ses odağı. */
    private var konusOdagi: AudioFocusRequest? = null
    @Volatile private var konusurkenDuraklat = true

    @Volatile private var sonEtkinlik = 0L
    @Volatile private var bostaZamanAsimiMs = 20_000L
    /** true: oturum yalnız "sohbeti kapat" (veya tetik tuşuna uzun basış) ile kapanır. */
    @Volatile private var surekliSohbet = true
    /** Video/müzik çalarken mikrofon yalnız bu ana kadar açık (tetik tuşuyla açılır). */
    @Volatile private var konusmaPenceresiBitis = 0L
    @Volatile private var medyaSessizUyarisi = false
    /** Son denetimde video/müzik çalıyordu (zamanlayıcı 500 ms'de bir günceller). */
    @Volatile private var medyaAktif = false
    /** Hoparlör çıkışını uyanık tutar; yoksa bu TV'nin mikrofonu sessizlikte sıfır verir. */
    private var sessizCikis: SessizCikis? = null
    /** "Jülide" uyandırma dinleyicisi (yalnız sohbet açıkken; TV sesi varken beslenir). */
    @Volatile private var uyandirma: Uyandirma? = null
    /** Uyandırmadan önceki son ~3 sn mikrofon sesi ("Jülide, TRT Haber'i aç" kaybolmasın). */
    private val sonSesler = ArrayDeque<ByteArray>()
    @Volatile private var tamponuGonder = false
    /** Art arda gelen tamamen sessiz (dijital sıfır) mikrofon parçası sayısı. */
    @Volatile private var sifirParca = 0
    private var mikrofonUyarisi = false
    private var sonMikrofonYenileme = 0L
    // Tanılama: 2 sn'lik pencerede mikrofon istatistikleri (mikrofon iş parçacığı yazar)
    @Volatile private var tPar = 0
    @Volatile private var tSifir = 0
    @Volatile private var tRmsToplam = 0.0
    @Volatile private var tRmsMaks = 0.0
    @Volatile private var tKapali = 0
    private var sonTani = 0L
    private var oncekiCaliyor = false
    @Volatile private var sonCalma = 0L
    @Volatile private var yenidenBaglaniyor = false
    private var yenidenDenemeler = 0
    private var tusInisZamani = 0L
    private var uyari: Uzak.Yapilandirma? = null
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
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) tusInisZamani = SystemClock.elapsedRealtime()
        if (event.action == KeyEvent.ACTION_UP) {
            val uzun = tusInisZamani > 0 && SystemClock.elapsedRealtime() - tusInisZamani > 1000
            tusInisZamani = 0
            ana.post { if (uzun && durum != Durum.BOSTA) bitir() else tetiklendi() }
        }
        return true
    }

    fun tetiklendi() {
        when (durum) {
            Durum.BOSTA -> oturumBaslat()
            Durum.BAGLANIYOR -> bitir()
            Durum.DINLIYOR -> {
                // Oturum açıkken kısa basış: Jülide'yi sustur ve dinlemeye geç (video açıkken
                // mikrofonu 12 sn açar). Kapatmak için "sohbeti kapat" de ya da tuşa uzun bas.
                oynatici?.let { if (it.caliyor()) it.temizle() }
                konusmaPenceresiBitis = SystemClock.elapsedRealtime() + 12_000
                // Video/müzik açıksa konuşurken TV sesini sustur: odak alınca uygulamalar duraklar
                // (ya da sesini kısar), pencere bitince odak bırakılır ve kaldığı yerden devam eder.
                if (medyaAktif) konusOdagiAl()
                medyaSessizUyarisi = false
                panel.durum("Dinliyorum…")
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
        konusmaPenceresiBitis = SystemClock.elapsedRealtime() + 12_000
        medyaSessizUyarisi = false
        yenidenDenemeler = 0
        sonMikrofonYenileme = 0L
        panel.goster("Bağlanıyor…")
        try {
            startForegroundService(Intent(this, MikrofonServisi::class.java))
        } catch (e: Exception) {
            Log.w(ETIKET, "Ön plan servisi başlatılamadı: ${e.message}")
        }

        kapsam.launch {
            try {
                val y = withContext(Dispatchers.IO) { Uzak.getir(ayarlar) }
                uyari = y
                bostaZamanAsimiMs = y.bostaSn * 1000L
                surekliSohbet = y.surekliSohbet
                konusurkenDuraklat = y.konusurkenDuraklat
                araclar.abonelikler = y.abonelikler
                val talimat = withContext(Dispatchers.IO) { araclar.sistemTalimati() }
                val l = GeminiLive(this@AsistanServisi)
                live = l
                withContext(Dispatchers.IO) { l.baglan(ayarlar.geminiAnahtari, y, talimat, araclar.bildirimler()) }
                if (durum != Durum.BAGLANIYOR) {
                    l.kapat()
                    return@launch
                }
                val oy = Oynatici { panel.asistanSeviyesi(it) }
                oynatici = oy
                val kapi = SesKapisi(y.vadEsik, gonder = { live?.sesGonder(it) }, kapandi = { live?.sesAkisiBitti() })
                val m = Mikrofon(y.yankiEngelle, hata = { mesaj -> ana.post { hataGoster(mesaj) } }) { pcm ->
                    // Yarı çift yönlü: asistan konuşurken TV hoparlöründen gelen sesi modele geri gönderme.
                    run {
                        val r = pcmRms(pcm)
                        tPar++
                        if (r < 1.0) tSifir++
                        tRmsToplam += r
                        if (r > tRmsMaks) tRmsMaks = r
                    }
                    if (oy.caliyor()) { tKapali++; return@Mikrofon }
                    // Video/müzik çalarken TV sesi mikrofona girer: yalnız tetik tuşuyla açılan pencerede dinle.
                    val simdi = SystemClock.elapsedRealtime()
                    if (surekliSohbet && simdi > konusmaPenceresiBitis && medyaAktif && konusOdagi == null) {
                        // TV sesi var: ses Gemini'ye GİTMEZ, yalnız yerel "Jülide" dinleyicisine gider
                        kapi.kes()
                        panel.mikrofonSeviyesi(0f)
                        uyandirma?.besle(pcm)
                        synchronized(sonSesler) {
                            sonSesler.addLast(pcm)
                            while (sonSesler.size > 75) sonSesler.removeFirst() // ~3 sn
                        }
                        return@Mikrofon
                    }
                    if (tamponuGonder) {
                        // Uyandırma: "Jülide, …" ile başlayan son saniyeleri de Gemini'ye ilet
                        tamponuGonder = false
                        val eski = synchronized(sonSesler) { sonSesler.toList().also { sonSesler.clear() } }
                        kapi.esikCarpani = 1.0
                        for (p in eski) kapi.isle(p)
                    }
                    val rms = pcmRms(pcm)
                    // Başka bir uygulama mikrofonu alırsa Android bize dijital sıfır verir
                    sifirParca = if (rms < 1.0) sifirParca + 1 else 0
                    panel.mikrofonSeviyesi(sesSeviyesi(rms, 7.0))
                    // TV sesi açıkken (pencere içinde) yalnız belirgin şekilde yüksek sesi konuşma say
                    kapi.esikCarpani = if (medyaAktif && konusOdagi == null) 2.2 else 1.0
                    kapi.isle(pcm)
                    // Konuşma sürerken bas-konuş penceresi kapanmasın
                    if (kapi.acik && konusmaPenceresiBitis > 0) konusmaPenceresiBitis = maxOf(konusmaPenceresiBitis, simdi + 3000)
                }
                sessizCikis = SessizCikis().also { it.baslat() }
                withContext(Dispatchers.IO) { m.baslat() }
                mikrofon = m
                if (y.uyandirma && surekliSohbet) {
                    uyandirma = Uyandirma(this@AsistanServisi, y.uyandirmaKelimeleri) { ana.post { uyandirmaDuyuldu() } }
                        .also { it.baslat() }
                }
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
                medyaAktif = medyaCaliyor()
                // Tanılama: Jülide'nin konuşmaya başlaması/bitmesi ve 2 sn'de bir mikrofon özeti
                if (caliyor != oncekiCaliyor) {
                    oncekiCaliyor = caliyor
                    Log.i(ETIKET, if (caliyor) "TANI olay: Jülide konuşmaya başladı" else "TANI olay: Jülide sustu")
                }
                if (simdi - sonTani >= 2000) {
                    sonTani = simdi
                    val n = tPar
                    if (n > 0) {
                        Log.i(ETIKET, "TANI mik: parça=$n sıfır=%${tSifir * 100 / n} ort=${(tRmsToplam / n).toInt()} maks=${tRmsMaks.toInt()} " +
                            "jülideKonuşuyor=$caliyor kapalı=$tKapali medya=$medyaAktif kaynak=${mikrofon?.kaynakAdi} odak=${odakIstegi != null || konusOdagi != null}")
                    } else {
                        Log.i(ETIKET, "TANI mik: HİÇ PARÇA GELMEDİ kaynak=${mikrofon?.kaynakAdi}")
                    }
                    tPar = 0; tSifir = 0; tRmsToplam = 0.0; tRmsMaks = 0.0; tKapali = 0
                }
                if (caliyor) {
                    sonEtkinlik = simdi
                    sonCalma = simdi
                    // Ses odağı yalnız Jülide konuşurken: video duraklamaz, sesi kısılır (ducking).
                    if (odakIstegi == null && konusOdagi == null) sesOdagiAl()
                } else if (odakIstegi != null && simdi - sonCalma > 800) {
                    sesOdagiBirak()
                }
                // Bas-konuş bitti ve Jülide sustu → videoyu geri bırak
                if (konusOdagi != null && !caliyor && simdi > konusmaPenceresiBitis && simdi - sonCalma > 1500) {
                    konusOdagiBirak()
                }
                // Kapanış: Jülide'nin veda cümlesi bitince (hiç konuşmazsa en geç 3 sn sonra) küre söner.
                if (bekleyenKapat && !caliyor && (sonCalma > kapatmaIstegi || simdi - kapatmaIstegi > 3000)) {
                    bitir()
                    break
                }
                if (!surekliSohbet && simdi - sonEtkinlik > bostaZamanAsimiMs) {
                    bitir()
                    break
                }
                if (!caliyor && !yenidenBaglaniyor) {
                    val sessizMik = sifirParca > 75 // ~3 sn
                    // Android mikrofonu susturduysa: görünmez ekranla servisi öndeyken yeniden başlat
                    // (en çok 20 sn'de bir; video duraklamasın diye Jülide konuşmuyorken)
                    // Çıkış uykuya geçtiyse (sessiz akış durduysa) yeniden başlat
                    if (sessizMik && simdi - sonMikrofonYenileme > 20_000) {
                        sonMikrofonYenileme = simdi
                        Log.w(ETIKET, "Mikrofon sıfır — sessiz çıkış yeniden başlatılıyor")
                        sessizCikis?.durdur()
                        sessizCikis = SessizCikis().also { it.baslat() }
                    }
                    if (sessizMik && !mikrofonUyarisi) {
                        mikrofonUyarisi = true
                        Log.w(ETIKET, "Mikrofon sıfır veriyor — Android arka planda susturmuş olabilir")
                        panel.durum("Mikrofondan ses gelmiyor")
                    } else if (!sessizMik && mikrofonUyarisi) {
                        mikrofonUyarisi = false
                        panel.durum("Jülide dinliyor…")
                    }
                }
                if (surekliSohbet && !caliyor && !yenidenBaglaniyor && !mikrofonUyarisi) {
                    val sessiz = simdi > konusmaPenceresiBitis && medyaAktif
                    if (sessiz && !medyaSessizUyarisi) {
                        medyaSessizUyarisi = true
                        panel.durum(if (uyandirma != null && Uyandirma.modelHazirMi(this@AsistanServisi))
                            "Video açık — \"Jülide\" de ya da tuşa bas" else "Video açık — konuşmak için tuşa bas")
                    } else if (!sessiz && medyaSessizUyarisi) {
                        medyaSessizUyarisi = false
                        panel.durum("Jülide dinliyor…")
                    }
                }
            }
        }
    }

    private val KAPATMA = listOf("sohbeti kapat", "sohbet kapat", "sohbeti bitir", "julide kapan", "julide kapat", "seni kapat", "konusmayi kapat", "konusmayi bitir")

    private fun kapatmaKomutuMu(metin: String): Boolean {
        val n = Uygulamalar.normal(metin)
        return KAPATMA.any { n.contains(it) }
    }

    /** Yerel dinleyici "Jülide"yi duydu: TV'yi kıs/duraklat, dinlemeye geç, son saniyeleri ilet. */
    private fun uyandirmaDuyuldu() {
        if (durum != Durum.DINLIYOR || bekleyenKapat) return
        konusmaPenceresiBitis = SystemClock.elapsedRealtime() + 10_000
        if (medyaAktif) konusOdagiAl()
        medyaSessizUyarisi = false
        tamponuGonder = true
        panel.durum("Dinliyorum…")
    }

    /** Araç: numara rozetlerini çiz / kaldır. */
    fun numaralariCiz(l: List<Pair<Int, android.graphics.Rect>>) = panel.numaralar(l)
    fun numaralariGizle() = panel.numaralariGizle()

    /** Araç: kullanıcı "tamam/kapat" dedi — asistanın veda cümlesi bitince kapat. */
    fun oturumuKapatIste() {
        if (bekleyenKapat) return
        kapatmaIstegi = SystemClock.elapsedRealtime()
        bekleyenKapat = true
        panel.durum("Sohbet kapatılıyor")
    }

    @Volatile private var kapatmaIstegi = 0L

    /** Video/müzik çalıyor mu? (Jülide'nin kendi sesi USAGE_ASSISTANT olduğu için sayılmaz.) */
    /**
     * Gerçekten video/müzik ÇALIYOR mu? activePlaybackConfigurations duraklatılmış/boşta bekleyen
     * oynatıcıları da listelediği için kullanılmaz (ilk komutta açılan uygulama mikrofonu kalıcı
     * susturuyordu). isMusicActive yalnız o an ses üreten akışlar için true döner; Jülide'nin kendi
     * sesi bittikten sonraki 1,5 sn sayılmaz.
     */
    private fun medyaCaliyor(): Boolean {
        // Jülide kendi sessiz akışını medya kanalından çaldığı için isMusicActive hep true olur;
        // bu yüzden 'TV'den video/müzik sesi geliyor' durumu öndeki uygulamaya göre tahmin edilir.
        val p = aktifPaket ?: return false
        if (p == packageName) return false
        val n = p.lowercase()
        if (OYNATICI_PAKET_IPUCLARI.any { n.contains(it) }) return true
        val ad = try {
            Uygulamalar.liste(this, hepsi = true).firstOrNull { it.paket == p }?.ad?.let { Uygulamalar.normal(it) }
        } catch (_: Exception) { null } ?: return false
        return OYNATICI_AD_IPUCLARI.any { (" $ad ").contains(" $it ") }
    }

    private val OYNATICI_PAKET_IPUCLARI = listOf(
        "youtube", "netflix", "disney", "amazon.avod", "amazonvideo", "primevideo", "spotify", "music",
        "tvcenter", "livetv", "tvplayer", "exoplayer", "mxtech", "vlc", "kodi", "plex", "twitch",
        "blutv", "exxen", "gain", "tabii", "trt", "puhutv", "todtv", "digiturk", "dsmart", "tivibu", "beinconnect", "ssport",
    )
    private val OYNATICI_AD_IPUCLARI = listOf(
        "tod", "canli tv", "prime video", "youtube", "spotify", "netflix", "disney", "exxen", "blutv", "tabii", "gain",
    )

    /**
     * Gemini bağlantısı kapandı (oturum süresi doldu / ağ koptu): sohbet bitmediyse aynı bağlamla
     * sessizce yeniden bağlan. Mikrofon ve küre çalışmaya devam eder.
     */
    private fun yenidenBaglan() {
        if (durum != Durum.DINLIYOR || bekleyenKapat || yenidenBaglaniyor) return
        val y = uyari ?: return bitir()
        if (++yenidenDenemeler > 5) {
            hataGoster("Gemini bağlantısı tekrar tekrar koptu.")
            return
        }
        yenidenBaglaniyor = true
        val eski = live
        val tutamac = eski?.tutamac
        live = null
        eski?.kapat()
        oynatici?.temizle()
        panel.durum("Yeniden bağlanıyor…")
        kapsam.launch {
            try {
                val talimat = withContext(Dispatchers.IO) { araclar.sistemTalimati() }
                val l = GeminiLive(this@AsistanServisi)
                l.tutamac = tutamac
                try {
                    withContext(Dispatchers.IO) { l.baglan(ayarlar.geminiAnahtari, y, talimat, araclar.bildirimler()) }
                } catch (e: Exception) {
                    if (tutamac == null) throw e
                    l.kapat()
                    // Tutamaç geçersizse temiz bir oturumla devam et.
                    val t = GeminiLive(this@AsistanServisi)
                    withContext(Dispatchers.IO) { t.baglan(ayarlar.geminiAnahtari, y, talimat, araclar.bildirimler()) }
                    live = t
                    return@launch
                }
                if (durum != Durum.DINLIYOR) {
                    l.kapat()
                    return@launch
                }
                live = l
            } catch (e: Exception) {
                Log.w(ETIKET, "Yeniden bağlanılamadı: ${e.message}")
                yenidenBaglaniyor = false
                delay(2000L * yenidenDenemeler)
                yenidenBaglan()
                return@launch
            } finally {
                if (live != null) {
                    yenidenBaglaniyor = false
                    if (durum == Durum.DINLIYOR) panel.durum("Jülide dinliyor…")
                    kapsam.launch {
                        delay(60_000)
                        if (live != null) yenidenDenemeler = 0
                    }
                }
            }
        }
    }

    @Volatile private var sonraKurulacak: Guncelleme.Surum? = null

    /** Araç: sesli "güncelle" — oturum kapanınca kurulum başlar. */
    fun guncellemeSonraKur(s: Guncelleme.Surum) {
        sonraKurulacak = s
    }

    fun bitir() {
        val oncekiDurum = durum
        durum = Durum.BOSTA
        yenidenBaglaniyor = false
        mikrofon?.durdur()
        mikrofon = null
        uyandirma?.durdur()
        uyandirma = null
        sessizCikis?.durdur()
        sessizCikis = null
        synchronized(sonSesler) { sonSesler.clear() }
        tamponuGonder = false
        live?.kapat()
        live = null
        oynatici?.kapat()
        oynatici = null
        sesOdagiBirak()
        konusOdagiBirak()
        try {
            stopService(Intent(this, MikrofonServisi::class.java))
        } catch (_: Exception) {
        }
        if (::panel.isInitialized) {
            panel.numaralariGizle()
            panel.gizle()
        }
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
        val istek = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
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

    private fun konusOdagiAl() {
        if (konusOdagi != null) return
        sesOdagiBirak()
        val tur = if (konusurkenDuraklat) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        val istek = AudioFocusRequest.Builder(tur)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { }
            .build()
        getSystemService(AudioManager::class.java).requestAudioFocus(istek)
        konusOdagi = istek
    }

    private fun konusOdagiBirak() {
        val istek = konusOdagi ?: return
        getSystemService(AudioManager::class.java).abandonAudioFocusRequest(istek)
        konusOdagi = null
    }

    /** Araçlardan çağrılır. "Oynat" oturum bitince tekrar gönderilir (ses odağı geri dönünce). */
    fun medyaTusu(kod: Int) {
        medyaTusuGonder(kod)
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
            medyaSessizUyarisi = false
            // Cevaptan sonra 8 sn takip penceresi: video/canlı yayın açılmış olsa bile kullanıcı
            // hemen ikinci komutu verebilsin (TV sesi varken konuşma eşiği yükseltilir).
            konusmaPenceresiBitis = maxOf(konusmaPenceresiBitis, SystemClock.elapsedRealtime() + 8000)
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
            // "Sohbeti kapat" modelin aracı çağırmasına bırakılmaz: duyulduğu an kapanış başlar
            // (veda cümlesi bitince küre söner).
            if (kapatmaKomutuMu(kullaniciMetin.toString())) oturumuKapatIste()
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
        Log.i(ETIKET, "TANI olay: araç $ad")
        sonEtkinlik = SystemClock.elapsedRealtime()
        panel.durum("İşlem: ${ad.replace('_', ' ')}")
        val sonuc = araclar.calistir(ad, args)
        sonEtkinlik = SystemClock.elapsedRealtime()
        return sonuc
    }

    override fun hata(mesaj: String) {
        ana.post {
            when {
                durum == Durum.BOSTA -> Unit
                durum == Durum.DINLIYOR && !bekleyenKapat -> yenidenBaglan()
                else -> hataGoster(mesaj)
            }
        }
    }

    override fun kapandi() {
        ana.post {
            when {
                durum == Durum.BOSTA -> Unit
                durum == Durum.DINLIYOR && !bekleyenKapat -> yenidenBaglan()
                else -> bitir()
            }
        }
    }
}
