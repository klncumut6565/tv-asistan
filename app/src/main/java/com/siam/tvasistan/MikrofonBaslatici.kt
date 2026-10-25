package com.siam.tvasistan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Görünmez, anlık açılıp kapanan ekran. Android 14, ARKA PLANDAN başlatılan mikrofon servisine
 * yalnızca uygulama öndeyken ses verir; uygulama değişince (YouTube, ana ekran) mikrofonu
 * sessizliğe çevirir. Mikrofon servisini bu ekran öndeyken başlatınca servis "kullanım sırasında"
 * mikrofon yetkisini kalıcı alır ve diğer uygulamalara geçince de duymaya devam eder.
 */
class MikrofonBaslatici : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        try {
            startForegroundService(Intent(this, MikrofonServisi::class.java))
        } catch (e: Exception) {
            Log.w("TvAsistan", "Mikrofon servisi öndeyken başlatılamadı: ${e.message}")
        }
        // Servis ön plana geçebilsin diye kısa süre öne kal, sonra iz bırakmadan kapan
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
            overridePendingTransition(0, 0)
        }, 350)
    }

    companion object {
        /** Erişilebilirlik servisi arka plandan ekran açabilir; olmazsa servisi doğrudan başlat. */
        fun baslat(ctx: Context) {
            try {
                ctx.startActivity(
                    Intent(ctx, MikrofonBaslatici::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY
                    )
                )
            } catch (e: Exception) {
                Log.w("TvAsistan", "Mikrofon başlatıcı açılamadı: ${e.message}")
                try { ctx.startForegroundService(Intent(ctx, MikrofonServisi::class.java)) } catch (_: Exception) {}
            }
        }
    }
}
