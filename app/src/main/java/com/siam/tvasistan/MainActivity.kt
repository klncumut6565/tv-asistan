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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Kurulum ekranı: giriş, izinler, erişilebilirlik ve tetik tuşu. */
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

        sutun.addView(yazi("TV Asistan", 30f, true, Color.rgb(34, 197, 94)))
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
            val izinler = mutableListOf(Manifest.permission.RECORD_AUDIO)
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
                "adb shell settings put secure accessibility_enabled 1",
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

        sutun.addView(yazi("4) Deneme", 22f, true))
        sutun.addView(dugme("Şimdi dinle") {
            val s = AsistanServisi.ornek
            if (s == null) toast("Önce erişilebilirlik servisini açın.") else s.oturumBaslat()
        })

        sutun.addView(yazi("5) Güncelleme", 22f, true))
        guncellemeTv = yazi("", 16f, renk = Color.LTGRAY)
        sutun.addView(guncellemeTv)
        sutun.addView(dugme("Güncellemeleri denetle ve kur") { guncellemeDenetle(kur = true) })
        sutun.addView(dugme("Bilinmeyen kaynak iznini aç (ilk güncelleme için)") {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                )
            } catch (ex: Exception) {
                toast("Bu TV'de menü yok: Ayarlar → Cihaz Tercihleri → Güvenlik → Bilinmeyen kaynaklar → TV Asistan")
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
            "Örnek komutlar: \"YouTube'u aç\", \"Netflix'te Kurtlar Vadisi'ni bul\", " +
                "\"Tarayıcıyı aç, Çorlu hava durumunu ara\", \"Sesi yüzde otuz yap\", \"Duraklat\", \"Tamam, teşekkürler\".",
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
        val mik = if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "✔ var" else "✘ yok"
        val tus = when {
            a.tusOgrenme -> "ŞİMDİ KUMANDADA BİR TUŞA BASIN…"
            a.tetikTusu > 0 -> KeyEvent.keyCodeToString(a.tetikTusu)
            else -> "Varsayılan (Asistan / Arama tuşu)"
        }
        durumTv.text = "Gemini anahtarı: $giris\nErişilebilirlik servisi: $servis\nMikrofon izni: $mik\nTetik tuşu: $tus"
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
