package com.siam.tvasistan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Kurulum ekranı: giriş, izinler, erişilebilirlik ve tetik tuşu. */
/** TV sağlayıcısındaki kanal listesini okuma izni (Kanallar.kt). */
private const val KANAL_IZNI = "com.android.providers.tv.permission.READ_EPG_DATA"

class MainActivity : Activity() {
    private lateinit var a: Ayarlar
    private lateinit var durumTv: TextView
    private lateinit var guncellemeTv: TextView
    private val ana = Handler(Looper.getMainLooper())
    private val yenileyici = object : Runnable {
        override fun run() {
            durumGuncelle()
            ana.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        a = Ayarlar(this)
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val sutun = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(56), px(32), px(56), px(32))
        }

        fun yazi(t: String, boyut: Float = 18f, kalin: Boolean = false, renk: Int = Color.WHITE) =
            TextView(this).apply {
                text = t
                textSize = boyut
                setTextColor(renk)
                if (kalin) typeface = Typeface.DEFAULT_BOLD
                setPadding(0, px(6), 0, px(6))
            }

        fun alan(ipucu: String, deger: String, sifre: Boolean = false) = EditText(this).apply {
            hint = ipucu
            setText(deger)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 18f
            setSingleLine(true)
            inputType = if (sifre) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }

        fun dugme(t: String, islem: () -> Unit) = Button(this).apply {
            text = t
            textSize = 18f
            setAllCaps(false)
            setOnClickListener { islem() }
        }

        sutun.addView(yazi("Jülide", 30f, true, Color.rgb(34, 197, 94)))
        sutun.addView(yazi("Android TV sesli asistanı — sürüm ${BuildConfig.VERSION_NAME}", 16f, renk = Color.LTGRAY))

        durumTv = yazi("", 18f)
        sutun.addView(durumTv)

        sutun.addView(yazi("1) Gemini API anahtarı", 22f, true))
        sutun.addView(yazi(
            "aistudio.google.com → Get API key. Uzun anahtarı telefondaki Google TV uygulamasının klavyesiyle yapıştırabilirsiniz. Anahtar yalnızca bu TV'de saklanır.",
            14f, renk = Color.LTGRAY
        ))
        val anahtar = alan("AIza…", a.geminiAnahtari, sifre = true)
        sutun.addView(anahtar)
        sutun.addView(dugme("Anahtarı kaydet ve dene") {
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
                runOnUiThread {
                    toast(sonuc)
                    durumGuncelle()
                }
            }.start()
        })

        sutun.addView(yazi("2) İzinler", 22f, true))
        sutun.addView(dugme("Mikrofon izni ver") {
            // Kanal listesi izni de istenir: verilirse Jülide kanalları adıyla bulup açar ("ATV'yi aç").
            val izinler = mutableListOf(Manifest.permission.RECORD_AUDIO, KANAL_IZNI)
            if (Build.VERSION.SDK_INT >= 33) izinler += Manifest.permission.POST_NOTIFICATIONS
            requestPermissions(izinler.toTypedArray(), 1)
        })
        sutun.addView(dugme("Erişilebilirlik ayarlarını aç") {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (ex: Exception) {
                toast("Bu TV'de erişilebilirlik menüsü açılamadı — aşağıdaki ADB komutunu kullanın.")
            }
        })
        sutun.addView(yazi(
            "Menü yoksa bilgisayardan:\n" +
                "adb shell settings put secure enabled_accessibility_services com.siam.tvasistan/.AsistanServisi\n" +
                "adb shell settings put secure accessibility_enabled 1\n\n" +
                "Otomatik onarım (servis \"Durmaya zorla\" ile kapanırsa Jülide açılınca kendini geri açar), bir kez:\n" +
                "adb shell pm grant com.siam.tvasistan android.permission.WRITE_SECURE_SETTINGS",
            14f, renk = Color.LTGRAY
        ))

        sutun.addView(yazi("3) Kumanda tuşu", 22f, true))
        sutun.addView(dugme("Tetik tuşunu öğret") {
            if (AsistanServisi.ornek == null) {
                toast("Önce erişilebilirlik servisini açın.")
                return@dugme
            }
            a.tusOgrenme = true
            toast("Şimdi kumandada asistanı açacak tuşa basın (ör. mikrofon veya renkli tuş).")
        })
        sutun.addView(dugme("Varsayılan tuşlara dön") {
            a.tetikTusu = -1
            a.tusOgrenme = false
            durumGuncelle()
        })

        sutun.addView(CheckBox(this).apply {
            text = "\"Jülide\" uyandırma kelimesi (tuşa basmadan dinleme)"
            textSize = 18f
            setTextColor(Color.WHITE)
            isChecked = a.uyandirmaAktif
            isFocusable = true
            setPadding(px(8), px(12), px(8), px(12))
            setOnCheckedChangeListener { _, acik ->
                a.uyandirmaAktif = acik
                toast(if (acik) "Uyandırma kelimesi AÇIK — bir sonraki sohbette geçerli." else "Uyandırma kelimesi KAPALI.")
            }
        })
        sutun.addView(yazi(
            "Bu TV'nin dahili mikrofonu ses vermiyor; ses yalnız kumandanın Asistan tuşuyla geliyor. " +
                "Uyandırma kelimesi ancak TV'ye USB mikrofon takılıysa çalışır (Jülide USB mikrofonu otomatik seçer).",
            14f, renk = Color.LTGRAY
        ))
        sutun.addView(dugme("\"Jülide\" uyandırma modelini şimdi indir (~37 MB, bir kez)") {
            Thread { Uyandirma.modeliHazirla(this) }.start()
            toast("İndirme başladı; durum yukarıda görünür.")
        })

        sutun.addView(yazi("4) Deneme", 22f, true))
        sutun.addView(dugme("Şimdi dinle") {
            val s = AsistanServisi.ornek
            if (s == null) toast("Önce erişilebilirlik servisini açın.") else s.oturumBaslat()
        })

        sutun.addView(yazi("5) Uygulamalar", 22f, true))
        sutun.addView(yazi(
            "PASİF yaptığın uygulamaları Jülide görmez, açmaz ve aramaz. Kullanmadığın uygulamaları pasif yapmak " +
                "Jülide'nin talimatını kısaltır, yanlış uygulama açma ihtimalini azaltır. Değişiklik bir sonraki sohbette tam geçerli olur.",
            14f, renk = Color.LTGRAY
        ))
        val uygKutusu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun uygulamalariYaz() {
            uygKutusu.removeAllViews()
            val pasif = a.pasifUygulamalar
            val liste = Uygulamalar.liste(this, hepsi = true)
            val aktifSayi = liste.count { it.paket !in pasif }
            uygKutusu.addView(yazi("${liste.size} uygulamadan $aktifSayi aktif", 15f, renk = Color.rgb(216, 180, 254)))
            for (u in liste) {
                val aktif = u.paket !in pasif
                uygKutusu.addView(dugme((if (aktif) "✔ AKTİF   " else "✘ PASİF   ") + u.ad) {
                    val yeni = a.pasifUygulamalar.toMutableSet()
                    if (!yeni.remove(u.paket)) yeni.add(u.paket)
                    a.pasifUygulamalar = yeni
                    val odak = uygKutusu.indexOfChild(currentFocus ?: uygKutusu)
                    uygulamalariYaz()
                    if (odak >= 0) uygKutusu.getChildAt(odak)?.requestFocus()
                }.apply {
                    setTextColor(if (aktif) Color.WHITE else Color.GRAY)
                    gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                })
            }
        }
        Uygulamalar.onbellegiTemizle() // yeni kurulan uygulamalar listede hemen görünsün
        uygulamalariYaz()
        sutun.addView(uygKutusu)
        sutun.addView(dugme("Tümünü aktif yap") {
            a.pasifUygulamalar = emptySet()
            uygulamalariYaz()
        })

        sutun.addView(yazi("Telefon mikrofonu", 22f, true))
        sutun.addView(yazi(
            "Telefonda tv-asistan-web.vercel.app adresini açıp aşağıdaki kodu girin; telefon mikrofon olur ve " +
                "tuşa basmadan konuşursunuz. Sayfa yalnız konuşma algılayınca ses gönderir.",
            14f, renk = Color.LTGRAY
        ))
        val kodTv = yazi("", 26f, true, renk = Color.rgb(196, 160, 255))
        val karekodTv = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(px(240), px(240)).apply { topMargin = px(8); bottomMargin = px(8) }
            contentDescription = "Telefon sayfasının karekodu"
        }
        fun kodYaz() {
            kodTv.text = "Eşleştirme kodu: ${a.telefonKodu}"
            // Karekod sayfayı kod gömülü açar: okutunca kod otomatik dolar, yalnız "Başlat"a basılır.
            Karekod.bitmap(Karekod.adres(a.telefonKodu), px(240))?.let { karekodTv.setImageBitmap(it) }
        }
        sutun.addView(CheckBox(this).apply {
            text = "Telefon mikrofonunu kullan"
            textSize = 18f
            setTextColor(Color.WHITE)
            isChecked = a.telefonAktif
            isFocusable = true
            setPadding(px(8), px(12), px(8), px(12))
            setOnCheckedChangeListener { _, acik ->
                a.telefonAktif = acik
                AsistanServisi.ornek?.telefonuAyarla()
                toast(if (acik) "Telefon mikrofonu AÇIK." else "Telefon mikrofonu KAPALI.")
            }
        })
        kodYaz()
        sutun.addView(yazi("Telefonun kamerasıyla okutun:", 15f, renk = Color.LTGRAY))
        sutun.addView(karekodTv)
        sutun.addView(kodTv)
        sutun.addView(dugme("Yeni eşleştirme kodu üret") {
            a.telefonKodu = TelefonKoprusu.yeniKod()
            kodYaz()
            AsistanServisi.ornek?.telefonuAyarla()
            toast("Yeni kod: ${a.telefonKodu} — telefondaki sayfaya bu kodu girin.")
        })

        sutun.addView(yazi("6) Güncelleme", 22f, true))
        guncellemeTv = yazi("", 16f, renk = Color.LTGRAY)
        sutun.addView(guncellemeTv)
        sutun.addView(dugme("Güncellemeleri denetle ve kur") { guncellemeDenetle(kur = true) })
        sutun.addView(dugme("Bilinmeyen kaynak iznini aç (ilk güncelleme için)") {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                )
            } catch (ex: Exception) {
                toast("Bu TV'de menü yok: Ayarlar → Cihaz Tercihleri → Güvenlik → Bilinmeyen kaynaklar → Jülide")
            }
        })
        val oto = dugme("") {}
        fun otoYaz() {
            oto.text = "Otomatik güncelleme: " + if (a.otomatikGuncelleme) "AÇIK" else "KAPALI"
        }
        oto.setOnClickListener {
            a.otomatikGuncelleme = !a.otomatikGuncelleme
            otoYaz()
        }
        otoYaz()
        sutun.addView(oto)
        sutun.addView(yazi(
            "Örnek komutlar: \"YouTube'u aç\", \"Prime Video'da Yüzüklerin Efendisi'ni aç\", \"Bunu kapat\", " +
                "\"Tarayıcıyı aç, Çorlu hava durumunu ara\", \"Sesi yüzde otuz yap\", \"Duraklat\", \"Sohbeti kapat\".\n" +
                "Tetik tuşu: kısa bas = konuş / Jülide'yi sustur, 1 sn basılı tut = sohbeti kapat.",
            15f, renk = Color.LTGRAY
        ))

        val kaydirici = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(15, 23, 32))
            addView(sutun, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(kaydirici)
    }

    override fun onResume() {
        super.onResume()
        ana.post(yenileyici)
        guncellemeDenetle(kur = false)
        // Servis listeden düştüyse hemen; listede görünüp bağlanmadıysa ("çökmüş") 3 sn tanıyıp onar.
        ServisOnarim.onar(this)
        ana.postDelayed({ if (AsistanServisi.ornek == null) ServisOnarim.onar(this, listedeyseDe = true) }, 3000)
    }

    private fun guncellemeDenetle(kur: Boolean) {
        guncellemeTv.text = "Denetleniyor…"
        Thread {
            val mesaj = try {
                val yeni = Guncelleme.denetle()
                when {
                    yeni == null -> "En son sürüm kullanılıyor (${BuildConfig.VERSION_NAME})."
                    !kur -> "Yeni sürüm var: ${yeni.ad} — aşağıdaki düğmeye basın."
                    !packageManager.canRequestPackageInstalls() ->
                        "Yeni sürüm ${yeni.ad} var, ama önce 'Bilinmeyen kaynak iznini aç' düğmesiyle izin verin."
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

    override fun onPause() {
        ana.removeCallbacks(yenileyici)
        super.onPause()
    }

    private fun durumGuncelle() {
        val giris = if (a.geminiAnahtari.isNotBlank()) "✔ kayıtlı" else "✘ girilmedi"
        val servis = if (AsistanServisi.ornek != null) "✔ açık" else "✘ kapalı"
        val onarim = if (ServisOnarim.izinVar(this)) "✔ etkin" else "✘ izin yok (aşağıdaki pm grant komutu)"
        val mik = if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "✔ var" else "✘ yok"
        val kanal = if (checkSelfPermission(KANAL_IZNI) == PackageManager.PERMISSION_GRANTED) "✔ var" else "✘ yok (\"Mikrofon izni ver\" ya da ADB)"
        val tus = when {
            a.tusOgrenme -> "ŞİMDİ KUMANDADA BİR TUŞA BASIN…"
            a.tetikTusu > 0 -> KeyEvent.keyCodeToString(a.tetikTusu)
            else -> "Varsayılan (Asistan / Arama tuşu)"
        }
        val uyan = if (Uyandirma.modelHazirMi(this)) "✔ hazır" else Uyandirma.durum
        durumTv.text = "Gemini anahtarı: $giris\nErişilebilirlik servisi: $servis\nOtomatik onarım: $onarim\nMikrofon izni: $mik\nKanal listesi izni: $kanal\nTetik tuşu: $tus\n\"Jülide\" uyandırma modeli: $uyan"
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
