package com.siam.tvasistan

import android.content.Context

/** Kalıcı ayarlar (yalnızca bu TV'de saklanır). */
class Ayarlar(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("tv_asistan", Context.MODE_PRIVATE)

    /** Kullanıcının kendi Gemini API anahtarı (aistudio.google.com). APK'da/repoda YOKTUR. */
    var geminiAnahtari: String
        get() {
            p.getString("gemini_sifreli", null)?.let { s -> Kasa.coz(s)?.let { return it } }
            val eski = p.getString("gemini_anahtari", "") ?: ""
            if (eski.isNotBlank()) geminiAnahtari = eski // düz metinden şifreliye taşı
            return eski
        }
        set(v) {
            val k = v.trim()
            val sifreli = if (k.isBlank()) null else Kasa.sifrele(k)
            val e = p.edit()
            if (sifreli != null) e.putString("gemini_sifreli", sifreli).remove("gemini_anahtari")
            else e.remove("gemini_sifreli").putString("gemini_anahtari", k) // Keystore çalışmıyorsa son çare
            e.apply()
        }

    var uzakEtag: String?
        get() = p.getString("uzak_etag", null)
        set(v) = p.edit().putString("uzak_etag", v).apply()

    /** Son başarıyla indirilen uzaktan ayar dosyası (internet yoksa bu kullanılır). */
    var uzakAyar: String?
        get() = p.getString("uzak_ayar", null)
        set(v) = p.edit().putString("uzak_ayar", v).apply()

    /** Öğrenilmiş tetik tuşu (KeyEvent kodu). -1 = varsayılan tuşlar. */
    var tetikTusu: Int
        get() = p.getInt("tetik", -1)
        set(v) = p.edit().putInt("tetik", v).apply()

    /** true iken basılan ilk tuş tetik tuşu olarak kaydedilir. */
    var tusOgrenme: Boolean
        get() = p.getBoolean("tus_ogrenme", false)
        set(v) = p.edit().putBoolean("tus_ogrenme", v).apply()

    /** Jülide'nin görmeyeceği/açmayacağı (pasif) uygulamaların paket adları. */
    var pasifUygulamalar: Set<String>
        get() = HashSet(p.getStringSet("pasif_uygulamalar", emptySet()) ?: emptySet())
        set(v) = p.edit().putStringSet("pasif_uygulamalar", HashSet(v)).apply()

    /** Yeni sürüm çıkınca otomatik indir ve kurulum onayını göster. */
    var otomatikGuncelleme: Boolean
        get() = p.getBoolean("oto_guncelleme", true)
        set(v) = p.edit().putBoolean("oto_guncelleme", v).apply()

    /** Telefon mikrofonu (Vercel sayfası) — varsayılan kapalı; ayar ekranındaki onay kutusundan açılır. */
    var telefonAktif: Boolean
        get() = p.getBoolean("telefon_aktif", false)
        set(v) = p.edit().putBoolean("telefon_aktif", v).apply()

    /** Telefon sayfasına girilecek eşleştirme kodu (ilk istekte üretilir). */
    var telefonKodu: String
        get() = p.getString("telefon_kodu", null) ?: TelefonKoprusu.yeniKod().also { telefonKodu = it }
        set(v) = p.edit().putString("telefon_kodu", v).apply()

    /**
     * "Jülide" uyandırma kelimesi. VARSAYILAN KAPALI: bu TV'nin dahili mikrofonu ses vermiyor, ses yalnız
     * kumandanın Asistan tuşuyla geliyor; sürekli dinleme ancak USB mikrofon takılınca anlamlı.
     * Kullanıcı ayar ekranındaki onay kutusundan açar.
     */
    var uyandirmaAktif: Boolean
        get() = p.getBoolean("uyandirma_aktif", false)
        set(v) = p.edit().putBoolean("uyandirma_aktif", v).apply()
}
