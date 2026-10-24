package com.siam.tvasistan

import android.content.Context

/** Kalıcı ayarlar (SharedPreferences). Aynı süreçte tek önbellekli örnek paylaşılır. */
class Ayarlar(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("tv_asistan", Context.MODE_PRIVATE)

    var sunucu: String
        get() = (p.getString("sunucu", null) ?: BuildConfig.API_BASE).trimEnd('/')
        set(v) = p.edit().putString("sunucu", v.trim().trimEnd('/')).apply()

    var eposta: String
        get() = p.getString("eposta", "") ?: ""
        set(v) = p.edit().putString("eposta", v).apply()

    var erisimToken: String?
        get() = p.getString("erisim", null)
        set(v) = p.edit().putString("erisim", v).apply()

    var yenilemeToken: String?
        get() = p.getString("yenileme", null)
        set(v) = p.edit().putString("yenileme", v).apply()

    /** Erişim token'ının bitiş zamanı (epoch ms). */
    var tokenBitis: Long
        get() = p.getLong("token_bitis", 0L)
        set(v) = p.edit().putLong("token_bitis", v).apply()

    /** Öğrenilmiş tetik tuşu (KeyEvent kodu). -1 = varsayılan tuşlar. */
    var tetikTusu: Int
        get() = p.getInt("tetik", -1)
        set(v) = p.edit().putInt("tetik", v).apply()

    /** true iken basılan ilk tuş tetik tuşu olarak kaydedilir. */
    var tusOgrenme: Boolean
        get() = p.getBoolean("tus_ogrenme", false)
        set(v) = p.edit().putBoolean("tus_ogrenme", v).apply()

    val girisYapildi: Boolean get() = !yenilemeToken.isNullOrBlank()

    fun cikisYap() {
        p.edit().remove("erisim").remove("yenileme").remove("token_bitis").apply()
    }
}
