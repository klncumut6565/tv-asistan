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

    /** Yeni sürüm çıkınca otomatik indir ve kurulum onayını göster. */
    var otomatikGuncelleme: Boolean
        get() = p.getBoolean("oto_guncelleme", true)
        set(v) = p.edit().putBoolean("oto_guncelleme", v).apply()
}
