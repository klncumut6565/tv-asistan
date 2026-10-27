package com.siam.tvasistan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import com.siam.tvasistan.TvArayuz.Durum
import com.siam.tvasistan.TvArayuz.Renk

/** TV sağlayıcısındaki kanal listesini okuma izni (Kanallar.kt). */
private const val KANAL_IZNI = "com.android.providers.tv.permission.READ_EPG_DATA"

/**
 * Uygulama ikonuyla açılan ana ekran ve ayarlar.
 *
 * Yerleşim: ana ekran (büyük "Jülide'yi kullan" kartı, hızlı işlemler, durum hapları, kategori kartları)
 * ve kategori ekranları (Yapay zekâ, Ses ve mikrofon, Kumanda, TV ve izinler, Uygulamalar, Sistem).
 * Tek Activity içinde içerik değişir; kumandanın Geri tuşu kategoriden ana ekrana döner.
 *
 * Ayarların okunup yazıldığı yer Ayarlar.kt'dir; bu ekran yalnız arayüzdür. Sesli asistanın ekrandaki
 * küresi (Panel.kt) AsistanServisi'ne aittir ve bu ekrandan bağımsızdır.
 */
class MainActivity : Activity() {

    private enum class Kategori(val simge: String, val baslik: String, val alt: String) {
        YAPAY_ZEKA("🧠", "Yapay zekâ", "Gemini bağlantısı"),
        SES("🎙", "Ses ve mikrofon", "Telefon, test, uyandırma"),
        KUMANDA("🎮", "Kumanda", "Tetik tuşu"),
        TV("📺", "TV ve izinler", "Erişilebilirlik, kanallar"),
        UYGULAMALAR("📱", "Uygulamalar", "Aktif / pasif"),
        SISTEM("🔧", "Sistem", "Güncelleme, hakkında"),
    }

    private lateinit var a: Ayarlar
    private lateinit var ui: TvArayuz
    private lateinit var kok: FrameLayout
    private lateinit var guncellemeTv: TextView
    private val ana = Handler(Looper.getMainLooper())

    /** Şu an açık kategori; null = ana ekran. */
    private var acik: Kategori? = null
    /** Ana ekrana dönünce odaklanacak kart. */
    private val kategoriKartlari = mutableMapOf<Kategori, View>()
    /** Ekrandaki canlı durum alanları (saniyede bir yenilenir). */
    private val yenilenecekler = mutableListOf<() -> Unit>()

    private val yenileyici = object : Runnable {
        override fun run() {
            yenilenecekler.forEach { it() }
            ana.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        a = Ayarlar(this)
        ui = TvArayuz(this)
        guncellemeTv = ui.yazi("", 18f, Renk.SOLUK)
        kok = FrameLayout(this).apply { setBackgroundColor(Renk.ZEMIN) }
        setContentView(kok)
        anaEkran()
    }

    override fun onResume() {
        super.onResume()
        ana.post(yenileyici)
        guncellemeDenetle(kur = false)
        // Servis listeden düştüyse hemen; listede görünüp bağlanmadıysa ("çökmüş") 3 sn tanıyıp onar.
        ServisOnarim.onar(this)
        ana.postDelayed({ if (AsistanServisi.ornek == null) ServisOnarim.onar(this, listedeyseDe = true) }, 3000)
    }

    override fun onPause() {
        ana.removeCallbacks(yenileyici)
        super.onPause()
    }

    @Deprecated("Android TV kumandasının Geri tuşu")
    override fun onBackPressed() {
        val k = acik
        if (k != null) {
            anaEkran()
            kategoriKartlari[k]?.requestFocus()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ================================================================ ortak

    private fun ekranKur(icerik: View) {
        yenilenecekler.clear()
        kok.removeAllViews()
        (guncellemeTv.parent as? ViewGroup)?.removeView(guncellemeTv)
        val kaydirici = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(icerik, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        kok.addView(kaydirici, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun sutun() = ui.dikey().apply { setPadding(ui.dp(56), ui.dp(36), ui.dp(56), ui.dp(36)) }

    private fun LinearLayout.ekle(v: View, ust: Int = 6, alt: Int = 6) = addView(v, ui.satir(ust, alt))

    private fun izinVar(izin: String) = checkSelfPermission(izin) == PackageManager.PERMISSION_GRANTED

    private fun hazirMi() = a.geminiAnahtari.isNotBlank() && AsistanServisi.ornek != null && izinVar(Manifest.permission.RECORD_AUDIO)

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    // ================================================================ ana ekran

    private fun anaEkran() {
        acik = null
        kategoriKartlari.clear()
        val s = sutun()

        // Üst çubuk: ad + genel durum
        val ust = ui.yatay().apply { gravity = Gravity.CENTER_VERTICAL }
        val ad = ui.dikey()
        ad.addView(ui.yazi("JÜLİDE", 34f, Renk.ACIK_LILA, kalin = true).apply { letterSpacing = 0.12f })
        ad.addView(ui.yazi("TV ASİSTANI  ·  sürüm ${BuildConfig.VERSION_NAME}", 15f, Renk.SOLUK).apply { letterSpacing = 0.08f })
        ust.addView(ad, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val genel = ui.hap("", Durum.UYARI)
        ust.addView(genel)
        s.ekle(ust, alt = 22)
        yenilenecekler += {
            if (hazirMi()) ui.hapGuncelle(genel, "Sistem hazır", Durum.IYI)
            else ui.hapGuncelle(genel, "Kurulum gerekli", Durum.UYARI)
        }

        // Orta: büyük asistan kartı + hızlı işlemler
        val orta = ui.yatay()
        val asistan = ui.kart("🎙", "Jülide'yi kullan", "Dinlemeyi başlat  ·  kumandada Asistan tuşu", buyuk = true) {
            val sv = AsistanServisi.ornek
            if (sv == null) {
                toast("Önce erişilebilirlik servisini açın (TV ve izinler).")
                kategoriEkrani(Kategori.TV)
            } else sv.oturumBaslat()
        }
        orta.addView(asistan, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.25f).apply { rightMargin = ui.dp(18) })
        val hizli = ui.dikey()
        hizli.addView(ui.yazi("HIZLI İŞLEMLER", 14f, Renk.SOLUK, kalin = true).apply { letterSpacing = 0.1f }, ui.satir(0, 4))
        hizli.addView(ui.kart("📱", "Telefon mikrofonu", "Karekodu okut, tuşsuz konuş") { kategoriEkrani(Kategori.SES) }, ui.satir(4, 6))
        hizli.addView(ui.kart("🔑", "İzinleri ver", "Mikrofon ve kanal listesi") { izinleriIste() }, ui.satir(6, 6))
        hizli.addView(ui.kart("⬆", "Güncellemeleri denetle", "Yeni sürüm varsa kur") {
            kategoriEkrani(Kategori.SISTEM)
            guncellemeDenetle(kur = true)
        }, ui.satir(6, 0))
        orta.addView(hizli, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        s.ekle(orta, alt = 18)

        // Durum hapları (tıklanınca ilgili ekran)
        s.ekle(ui.yazi("DURUM", 14f, Renk.SOLUK, kalin = true).apply { letterSpacing = 0.1f }, ust = 8, alt = 2)
        val haplar = ui.yatay()
        fun hapEkle(guncelle: (TextView) -> Unit, git: Kategori) {
            val h = ui.hap("", Durum.UYARI) { kategoriEkrani(git) }
            haplar.addView(h, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = ui.dp(12) })
            yenilenecekler += { guncelle(h) }
        }
        hapEkle({ ui.hapGuncelle(it, if (a.geminiAnahtari.isNotBlank()) "Gemini" else "Gemini anahtarı yok", if (a.geminiAnahtari.isNotBlank()) Durum.IYI else Durum.HATA) }, Kategori.YAPAY_ZEKA)
        hapEkle({ ui.hapGuncelle(it, if (AsistanServisi.ornek != null) "Erişilebilirlik" else "Erişilebilirlik kapalı", if (AsistanServisi.ornek != null) Durum.IYI else Durum.HATA) }, Kategori.TV)
        hapEkle({ ui.hapGuncelle(it, if (izinVar(Manifest.permission.RECORD_AUDIO)) "Mikrofon" else "Mikrofon izni yok", if (izinVar(Manifest.permission.RECORD_AUDIO)) Durum.IYI else Durum.HATA) }, Kategori.SES)
        hapEkle({ ui.hapGuncelle(it, if (izinVar(KANAL_IZNI)) "Kanal listesi" else "Kanal izni yok", if (izinVar(KANAL_IZNI)) Durum.IYI else Durum.UYARI) }, Kategori.TV)
        hapEkle({ ui.hapGuncelle(it, if (a.telefonAktif) "Telefon açık" else "Telefon kapalı", if (a.telefonAktif) Durum.IYI else Durum.UYARI) }, Kategori.SES)
        s.ekle(haplar, ust = 6, alt = 20)

        // Kategoriler: 3'lü iki satır
        s.ekle(ui.yazi("AYARLAR", 14f, Renk.SOLUK, kalin = true).apply { letterSpacing = 0.1f }, ust = 6, alt = 2)
        Kategori.values().toList().chunked(3).forEach { grup ->
            val satir = ui.yatay()
            grup.forEachIndexed { i, k ->
                val kart = ui.kart(k.simge, k.baslik, k.alt) { kategoriEkrani(k) }
                kategoriKartlari[k] = kart
                satir.addView(kart, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i < grup.size - 1) rightMargin = ui.dp(18)
                })
            }
            s.ekle(satir, ust = 8, alt = 8)
        }

        ekranKur(s)
        yenilenecekler.forEach { it() }
        asistan.requestFocus()
    }

    // ================================================================ kategori ekranları

    private fun kategoriEkrani(k: Kategori) {
        acik = k
        val s = sutun()
        s.ekle(ui.yazi("‹  Geri: ana ekran", 15f, Renk.SOLUK))
        s.ekle(ui.yazi("${k.simge}  ${k.baslik}", 32f, Renk.ACIK_LILA, kalin = true), alt = 10)
        when (k) {
            Kategori.YAPAY_ZEKA -> yapayZeka(s)
            Kategori.SES -> sesVeMikrofon(s)
            Kategori.KUMANDA -> kumanda(s)
            Kategori.TV -> tvVeIzinler(s)
            Kategori.UYGULAMALAR -> uygulamalar(s)
            Kategori.SISTEM -> sistem(s)
        }
        ekranKur(s)
        yenilenecekler.forEach { it() }
        // İlk etkileşimli öğeye odaklan
        s.post { s.focusSearch(View.FOCUS_DOWN)?.requestFocus() ?: ilkOdak(s)?.requestFocus() }
    }

    private fun ilkOdak(v: View): View? {
        if (v.isFocusable && v.visibility == View.VISIBLE) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) ilkOdak(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun durumSatiri(s: LinearLayout, metin: () -> Pair<String, Durum>) {
        val h = ui.hap("", Durum.UYARI)
        s.addView(h, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(4); bottomMargin = ui.dp(4)
        })
        yenilenecekler += { val (m, d) = metin(); ui.hapGuncelle(h, m, d) }
    }

    // ---------------------------------------------------------------- 🧠 Yapay zekâ

    private fun yapayZeka(s: LinearLayout) {
        durumSatiri(s) { if (a.geminiAnahtari.isNotBlank()) "Gemini anahtarı kayıtlı" to Durum.IYI else "Gemini anahtarı girilmedi" to Durum.HATA }
        s.ekle(ui.bolum("Gemini API anahtarı"))
        s.ekle(ui.aciklama(
            "aistudio.google.com → Get API key. Uzun anahtarı telefondaki Google TV uygulamasının klavyesiyle " +
                "yapıştırabilirsiniz. Anahtar yalnızca bu TV'de, şifreli saklanır."
        ))
        val anahtar = ui.alan("AIza…", a.geminiAnahtari, sifre = true)
        s.ekle(anahtar)
        s.ekle(ui.dugme("Anahtarı kaydet ve dene", vurgulu = true) {
            val k = anahtar.text.toString().trim()
            if (k.isBlank()) {
                toast("Anahtarı girin.")
                return@dugme
            }
            toast("Anahtar deneniyor…")
            Thread {
                val sonuc = try {
                    val istek = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1&key=$k")
                        .build()
                    OkHttpClient().newCall(istek).execute().use { r ->
                        if (r.isSuccessful) {
                            a.geminiAnahtari = k
                            "Anahtar geçerli, kaydedildi."
                        } else {
                            "Anahtar geçersiz (HTTP ${r.code})."
                        }
                    }
                } catch (ex: Exception) {
                    "İnternet hatası: ${ex.message}"
                }
                runOnUiThread { toast(sonuc) }
            }.start()
        })
        s.ekle(ui.bolum("Model"))
        s.ekle(ui.aciklama("Model, ses ve konuşma ayarları uzaktan (config/ayarlar.json) güncellenir; birkaç dakikada TV'ye ulaşır."))
    }

    // ---------------------------------------------------------------- 🎙 Ses ve mikrofon

    private fun sesVeMikrofon(s: LinearLayout) {
        durumSatiri(s) { if (izinVar(Manifest.permission.RECORD_AUDIO)) "Mikrofon izni var" to Durum.IYI else "Mikrofon izni yok" to Durum.HATA }
        s.ekle(ui.dugme("Mikrofon izni ver") { izinleriIste() })

        s.ekle(ui.bolum("Telefon mikrofonu"))
        s.ekle(ui.aciklama(
            "Karekodu telefonun kamerasıyla okutun; sayfa açılır, kod kendiliğinden dolar, \"Başlat\"a basın. " +
                "Telefon mikrofon olur, tuşa basmadan konuşursunuz. Adres: tv-asistan-web.vercel.app"
        ))
        s.ekle(ui.onay("Telefon mikrofonunu kullan", a.telefonAktif) { acikMi ->
            a.telefonAktif = acikMi
            AsistanServisi.ornek?.telefonuAyarla()
            toast(if (acikMi) "Telefon mikrofonu AÇIK." else "Telefon mikrofonu KAPALI.")
        })
        val karekod = ui.yatay().apply { gravity = Gravity.CENTER_VERTICAL }
        val karekodTv = ImageView(this).apply { contentDescription = "Telefon sayfasının karekodu" }
        val kodTv = ui.yazi("", 30f, Renk.ACIK_LILA, kalin = true).apply { letterSpacing = 0.15f }
        fun kodYaz() {
            kodTv.text = a.telefonKodu
            Karekod.bitmap(Karekod.adres(a.telefonKodu), ui.dp(260))?.let { karekodTv.setImageBitmap(it) }
        }
        karekod.addView(karekodTv, LinearLayout.LayoutParams(ui.dp(260), ui.dp(260)).apply { rightMargin = ui.dp(28) })
        val kodSutun = ui.dikey()
        kodSutun.addView(ui.yazi("Eşleştirme kodu", 16f, Renk.SOLUK))
        kodSutun.addView(kodTv)
        karekod.addView(kodSutun)
        kodYaz()
        s.ekle(karekod, ust = 10, alt = 10)
        s.ekle(ui.dugme("Yeni eşleştirme kodu üret") {
            a.telefonKodu = TelefonKoprusu.yeniKod()
            kodYaz()
            AsistanServisi.ornek?.telefonuAyarla()
            toast("Yeni kod: ${a.telefonKodu} — telefondaki sayfaya bu kodu girin.")
        })

        s.ekle(ui.bolum("Deneme"))
        s.ekle(ui.dugme("Şimdi dinle") {
            val sv = AsistanServisi.ornek
            if (sv == null) toast("Önce erişilebilirlik servisini açın.") else sv.oturumBaslat()
        })

        s.ekle(ui.bolum("\"Jülide\" uyandırma kelimesi"))
        s.ekle(ui.aciklama(
            "Bu TV'nin dahili mikrofonu ses vermiyor; ses yalnız kumandanın Asistan tuşuyla ya da telefondan gelir. " +
                "Uyandırma kelimesi ancak TV'ye USB mikrofon takılıysa çalışır (Jülide USB mikrofonu otomatik seçer)."
        ))
        s.ekle(ui.onay("Uyandırma kelimesi (tuşa basmadan dinleme)", a.uyandirmaAktif) { acikMi ->
            a.uyandirmaAktif = acikMi
            toast(if (acikMi) "Uyandırma kelimesi AÇIK — bir sonraki sohbette geçerli." else "Uyandırma kelimesi KAPALI.")
        })
        durumSatiri(s) { if (Uyandirma.modelHazirMi(this)) "Uyandırma modeli hazır" to Durum.IYI else "Model: ${Uyandirma.durum}".trim() to Durum.UYARI }
        s.ekle(ui.dugme("Uyandırma modelini indir (~37 MB, bir kez)") {
            Thread { Uyandirma.modeliHazirla(this) }.start()
            toast("İndirme başladı; durum yukarıda görünür.")
        })
    }

    // ---------------------------------------------------------------- 🎮 Kumanda

    private fun kumanda(s: LinearLayout) {
        durumSatiri(s) {
            when {
                a.tusOgrenme -> "ŞİMDİ KUMANDADA BİR TUŞA BASIN…" to Durum.UYARI
                a.tetikTusu > 0 -> "Tetik tuşu: ${KeyEvent.keyCodeToString(a.tetikTusu)}" to Durum.IYI
                else -> "Tetik tuşu: varsayılan (Asistan / Arama)" to Durum.IYI
            }
        }
        s.ekle(ui.aciklama("Kısa bas = konuş / Jülide'yi sustur  ·  1 sn basılı tut = sohbeti kapat."))
        s.ekle(ui.dugme("Tetik tuşunu öğret", vurgulu = true) {
            if (AsistanServisi.ornek == null) {
                toast("Önce erişilebilirlik servisini açın.")
                return@dugme
            }
            a.tusOgrenme = true
            toast("Şimdi kumandada asistanı açacak tuşa basın (ör. mikrofon veya renkli tuş).")
        })
        s.ekle(ui.dugme("Varsayılan tuşlara dön") {
            a.tetikTusu = -1
            a.tusOgrenme = false
            toast("Varsayılan tuşlar (Asistan / Arama) kullanılacak.")
        })
    }

    // ---------------------------------------------------------------- 📺 TV ve izinler

    private fun tvVeIzinler(s: LinearLayout) {
        durumSatiri(s) { if (AsistanServisi.ornek != null) "Erişilebilirlik servisi açık" to Durum.IYI else "Erişilebilirlik servisi kapalı" to Durum.HATA }
        durumSatiri(s) { if (izinVar(Manifest.permission.RECORD_AUDIO)) "Mikrofon izni var" to Durum.IYI else "Mikrofon izni yok" to Durum.HATA }
        durumSatiri(s) { if (ServisOnarim.izinVar(this)) "Otomatik onarım etkin" to Durum.IYI else "Otomatik onarım: izin yok (ADB)" to Durum.UYARI }

        s.ekle(ui.bolum("İzinler"))
        s.ekle(ui.dugme("İzinleri ver (mikrofon + kanal listesi)", vurgulu = true) { izinleriIste() })
        s.ekle(ui.dugme("Erişilebilirlik ayarlarını aç") {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (ex: Exception) {
                toast("Bu TV'de erişilebilirlik menüsü açılamadı — aşağıdaki ADB komutunu kullanın.")
            }
        })

        s.ekle(ui.bolum("Kanal tuşları (Kanal + / Kanal − / numara)"))
        s.ekle(ui.aciklama(
            "Jülide'nin kanal değiştirebilmesi için TV'nin 'Ağ üzerinden hata ayıklama' (ADB) özelliği açık olmalı. " +
                "Düğmeye basınca TV 'Hata ayıklamaya izin verilsin mi?' diye sorar: 'Her zaman izin ver'i işaretleyip " +
                "İZİN VER'e basın. Bir kez yeterlidir. Bu TV kanal listesini uygulamalara vermediği için kanallar " +
                "numarayla açılır; 'ATV 4. kanalda' derseniz Jülide adı da öğrenir."
        ))
        s.ekle(ui.dugme("Kanal tuşlarını etkinleştir / dene", vurgulu = true) {
            toast("TV'ye bağlanılıyor… Ekranda soru çıkarsa İZİN VER'e basın.")
            Thread {
                val sonuc = try { Adb(this).dene(); "Kanal tuşları hazır ✓" } catch (e: Exception) { e.message ?: "Bağlanılamadı." }
                runOnUiThread { toast(sonuc) }
            }.start()
        })
        val harita = a.kanalHaritasi
        if (harita.isNotEmpty()) {
            s.ekle(ui.aciklama("Öğrenilen kanallar: " + harita.entries.sortedBy { it.value.toIntOrNull() ?: 0 }.joinToString(", ") { "${it.value} ${it.key}" }))
            s.ekle(ui.dugme("Öğrenilen kanalları sil") { a.kanalHaritasi = emptyMap(); toast("Silindi."); kategoriEkrani(Kategori.TV) })
        }

        s.ekle(ui.bolum("Bilgisayardan (ADB)"))
        s.ekle(ui.aciklama("Menüler yoksa ya da izin verilemiyorsa bir kez çalıştırın:"))
        s.ekle(ui.yazi(
            "adb shell settings put secure enabled_accessibility_services com.siam.tvasistan/.AsistanServisi\n" +
                "adb shell settings put secure accessibility_enabled 1\n" +
                "adb shell pm grant com.siam.tvasistan android.permission.WRITE_SECURE_SETTINGS\n" +
                "adb shell pm grant com.siam.tvasistan $KANAL_IZNI",
            14f, Renk.ACIK_LILA
        ).apply { typeface = android.graphics.Typeface.MONOSPACE })
    }

    // ---------------------------------------------------------------- 📱 Uygulamalar

    private fun uygulamalar(s: LinearLayout) {
        s.ekle(ui.aciklama(
            "PASİF yaptığınız uygulamaları Jülide görmez, açmaz ve aramaz. Kullanmadıklarınızı pasif yapmak yanlış " +
                "uygulama açma ihtimalini azaltır. Değişiklik bir sonraki sohbette tam geçerli olur."
        ))
        val sayac = ui.yazi("", 18f, Renk.LILA, kalin = true)
        s.ekle(sayac)
        val kutu = ui.dikey()
        fun yaz() {
            kutu.removeAllViews()
            val pasif = a.pasifUygulamalar
            val liste = Uygulamalar.liste(this, hepsi = true)
            sayac.text = "${liste.size} uygulamadan ${liste.count { it.paket !in pasif }} aktif"
            for (u in liste) {
                kutu.addView(ui.onay(u.ad, u.paket !in pasif) { aktif ->
                    val yeni = a.pasifUygulamalar.toMutableSet()
                    if (aktif) yeni.remove(u.paket) else yeni.add(u.paket)
                    a.pasifUygulamalar = yeni
                    sayac.text = "${liste.size} uygulamadan ${liste.count { it.paket !in yeni }} aktif"
                }, ui.satir(4, 4))
            }
        }
        Uygulamalar.onbellegiTemizle() // yeni kurulan uygulamalar listede hemen görünsün
        yaz()
        s.ekle(ui.dugme("Tümünü aktif yap", vurgulu = true) {
            a.pasifUygulamalar = emptySet()
            yaz()
        }, alt = 12)
        s.ekle(kutu)
    }

    // ---------------------------------------------------------------- 🔧 Sistem

    private fun sistem(s: LinearLayout) {
        s.ekle(ui.bolum("Güncelleme"))
        (guncellemeTv.parent as? ViewGroup)?.removeView(guncellemeTv)
        s.ekle(guncellemeTv)
        s.ekle(ui.dugme("Güncellemeleri denetle ve kur", vurgulu = true) { guncellemeDenetle(kur = true) })
        s.ekle(ui.onay("Otomatik güncelleme", a.otomatikGuncelleme) { a.otomatikGuncelleme = it })
        s.ekle(ui.dugme("Bilinmeyen kaynak iznini aç (ilk güncelleme için)") {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (ex: Exception) {
                toast("Bu TV'de menü yok: Ayarlar → Cihaz Tercihleri → Güvenlik → Bilinmeyen kaynaklar → Jülide")
            }
        })

        s.ekle(ui.bolum("Örnek komutlar"))
        s.ekle(ui.aciklama(
            "\"YouTube'u aç\"  ·  \"Prime Video'da Yüzüklerin Efendisi'ni aç\"  ·  \"ATV'yi aç\"  ·  \"HDMI 1'e geç\"\n" +
                "\"Sesi yüzde otuz yap\"  ·  \"Duraklat\"  ·  \"Saat kaç?\"  ·  \"30 dakika sonra TV'yi kapat\"  ·  \"Sohbeti kapat\""
        ))

        s.ekle(ui.bolum("Hakkında"))
        s.ekle(ui.aciklama("Jülide ${BuildConfig.VERSION_NAME}  ·  Android TV için Türkçe Gemini Live sesli asistanı  ·  SİAM"))
    }

    // ================================================================ işlemler

    private fun izinleriIste() {
        // Kanal listesi izni de istenir: verilirse Jülide kanalları adıyla bulup açar ("ATV'yi aç").
        val izinler = mutableListOf(Manifest.permission.RECORD_AUDIO, KANAL_IZNI)
        if (Build.VERSION.SDK_INT >= 33) izinler += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(izinler.toTypedArray(), 1)
    }

    private fun guncellemeDenetle(kur: Boolean) {
        guncellemeTv.text = "Denetleniyor…"
        Thread {
            val mesaj = try {
                val yeni = Guncelleme.denetle()
                when {
                    yeni == null -> "En son sürüm kullanılıyor (${BuildConfig.VERSION_NAME})."
                    !kur -> "Yeni sürüm var: ${yeni.ad} — \"Güncellemeleri denetle ve kur\"a basın."
                    !packageManager.canRequestPackageInstalls() ->
                        "Yeni sürüm ${yeni.ad} var, ama önce 'Bilinmeyen kaynak iznini aç' ile izin verin."
                    else -> {
                        runOnUiThread { guncellemeTv.text = "${yeni.ad} indiriliyor…" }
                        Guncelleme.kur(this, yeni)
                        "Kurulum ekranında Yükle'ye basın."
                    }
                }
            } catch (ex: Exception) {
                "Denetlenemedi: ${ex.message}"
            }
            runOnUiThread { guncellemeTv.text = mesaj }
        }.start()
    }
}
