package com.siam.tvasistan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
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
        sutun.addView(yazi("TMGD Yönetim Sistemi sesli asistanı — Android TV", 16f, renk = Color.LTGRAY))

        durumTv = yazi("", 18f)
        sutun.addView(durumTv)

        sutun.addView(yazi("1) Giriş", 22f, true))
        val sunucu = alan("Sunucu adresi (https://…)", a.sunucu)
        val eposta = alan("E-posta", a.eposta)
        val sifre = alan("Şifre", "", sifre = true)
        sutun.addView(sunucu)
        sutun.addView(eposta)
        sutun.addView(sifre)
        sutun.addView(dugme("Giriş yap") {
            val s = sunucu.text.toString()
            val e = eposta.text.toString()
            val p = sifre.text.toString()
            if (e.isBlank() || p.isBlank()) {
                toast("E-posta ve şifre girin.")
                return@dugme
            }
            a.sunucu = s
            toast("Giriş yapılıyor…")
            Thread {
                val sonuc = try {
                    Kimlik.girisYap(a, e, p)
                    "Giriş başarılı."
                } catch (ex: Exception) {
                    ex.message ?: "Giriş başarısız."
                }
                runOnUiThread {
                    sifre.setText("")
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
        sutun.addView(dugme("Çıkış yap") {
            a.cikisYap()
            durumGuncelle()
        })
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
    }

    override fun onPause() {
        ana.removeCallbacks(yenileyici)
        super.onPause()
    }

    private fun durumGuncelle() {
        val giris = if (a.girisYapildi) "✔ ${a.eposta}" else "✘ yapılmadı"
        val servis = if (AsistanServisi.ornek != null) "✔ açık" else "✘ kapalı"
        val mik = if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "✔ var" else "✘ yok"
        val tus = when {
            a.tusOgrenme -> "ŞİMDİ KUMANDADA BİR TUŞA BASIN…"
            a.tetikTusu > 0 -> KeyEvent.keyCodeToString(a.tetikTusu)
            else -> "Varsayılan (Asistan / Arama tuşu)"
        }
        durumTv.text = "Giriş: $giris\nErişilebilirlik servisi: $servis\nMikrofon izni: $mik\nTetik tuşu: $tus"
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
