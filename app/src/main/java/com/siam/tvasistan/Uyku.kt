package com.siam.tvasistan

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast

/**
 * "TV'yi kapat" ve uyku zamanlayıcısı.
 *
 * TV'yi uyutmanın normal bir uygulamaya açık tek yolu erişilebilirlik servisinin
 * GLOBAL_ACTION_LOCK_SCREEN eylemidir; Android 9'dan beri cihazı uyku/bekleme durumuna alır.
 * (Yaklaşım CleanYourTV'nin sleep-timer eklentisinden uyarlanmıştır; bkz. THIRD_PARTY_NOTICES.md.)
 *
 * Zamanlayıcı ana iş parçacığı Handler'ı ile tutulur: TV açıkken cihaz uyumadığı için güvenilirdir,
 * ek izin (SCHEDULE_EXACT_ALARM) gerektirmez. Araç yürütücüsünü hiç bekletmez. Servis kapanırsa
 * zamanlayıcı da iptal olur (iptal()).
 */
class Uyku(private val s: AsistanServisi) {

    private val ana = Handler(Looper.getMainLooper())

    /** Zamanlayıcının dolacağı an (SystemClock.elapsedRealtime). 0 = kurulu değil. */
    @Volatile private var hedef = 0L

    private val uyari = Runnable {
        Toast.makeText(s, "Jülide: TV 1 dakika sonra kapanacak. Vazgeçmek için \"zamanlayıcıyı iptal et\" deyin.", Toast.LENGTH_LONG).show()
    }

    private val zamanDoldu = Runnable {
        hedef = 0L
        Log.i(ETIKET, "Uyku zamanlayıcısı doldu, TV kapatılıyor")
        kapatmayaBasla(0L)
    }

    /** Bu TV'de uyutma destekleniyor mu (Android 9+). */
    fun destekleniyor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /**
     * TV'yi kapatır. Sohbet açıksa önce Jülide'nin vedası bitsin diye oturum kapatılır,
     * oturum kapandıktan (en çok ~10 sn) sonra TV uyutulur.
     */
    fun kapatmayaBasla(gecikmeMs: Long = VEDA_MS) {
        ana.postDelayed({
            if (s.durum != AsistanServisi.Durum.BOSTA) s.oturumuKapatIste()
            bekleVeUyut(SystemClock.elapsedRealtime() + OTURUM_BEKLEME_MS)
        }, gecikmeMs)
    }

    private fun bekleVeUyut(sonAn: Long) {
        if (s.durum != AsistanServisi.Durum.BOSTA && SystemClock.elapsedRealtime() < sonAn) {
            ana.postDelayed({ bekleVeUyut(sonAn) }, 300)
            return
        }
        if (s.durum != AsistanServisi.Durum.BOSTA) s.bitir()
        // bitir() oturum sonunda medyayı yeniden başlatabilir; uyku ondan sonra gelsin.
        ana.postDelayed({
            val oldu = destekleniyor() && s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            Log.i(ETIKET, "TV uyutma: ${if (oldu) "gönderildi" else "BAŞARISIZ"}")
            if (!oldu) Toast.makeText(s, "Jülide: TV bu yolla kapatılamadı.", Toast.LENGTH_LONG).show()
        }, 900)
    }

    /** [dakika] sonra TV'yi kapatır. Önceki zamanlayıcının yerine geçer. */
    fun kur(dakika: Int) {
        iptal()
        val ms = dakika * 60_000L
        hedef = SystemClock.elapsedRealtime() + ms
        if (dakika >= 2) ana.postDelayed(uyari, ms - 60_000L)
        ana.postDelayed(zamanDoldu, ms)
    }

    /** Kurulu zamanlayıcıyı iptal eder; kurulu idiyse true. */
    fun iptal(): Boolean {
        val vardi = hedef != 0L
        hedef = 0L
        ana.removeCallbacks(uyari)
        ana.removeCallbacks(zamanDoldu)
        return vardi
    }

    /** Kalan süre (dakika, yukarı yuvarlanmış); kurulu değilse null. */
    fun kalanDakika(): Int? {
        val h = hedef
        if (h == 0L) return null
        val kalan = (h - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        return ((kalan + 59_999L) / 60_000L).toInt()
    }

    companion object {
        private const val ETIKET = "TvAsistan"
        /** Araç sonucu döndükten sonra Jülide'nin "TV'yi kapatıyorum" demesine süre. */
        const val VEDA_MS = 2_500L
        private const val OTURUM_BEKLEME_MS = 10_000L
        const val EN_COK_DAKIKA = 240
    }
}
