package com.siam.tvasistan

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Basit komutların akışı:  ANLA (HizliKomut) → KARAR → YAP (yerel, anında) → DOĞRULA (araç sonucu).
 *
 * Gemini Live sesi yine duyar ve aynı aracı birazdan kendisi de çağırır. Bu sınıf o çağrıyı
 * YAKALAR: iş zaten yapıldıysa ikinci kez yapılmaz, yerel sonuç Gemini'ye verilir; Gemini yalnızca
 * kısaca onaylar. Böylece kanal/ses/HDMI/medya komutları modeli beklemeden uygulanır ve
 * "sonraki kanal" gibi komutlar iki kez çalışmaz.
 *
 * Güvenlik:
 *  - Model bu turda bir araç çağırdıysa yerel yol hiç devreye girmez (çift işlem yok).
 *  - Yerel iş hata dönerse saklanmaz; model normal yoldan dener.
 *  - Saklanan sonuç [GECERLILIK_MS] sonra ya da yeni kullanıcı turunda silinir.
 */
class HizliYol(
    private val calistir: (String, JSONObject) -> JSONObject,
    private val bildir: (String) -> Unit,
) {
    companion object {
        private const val ETIKET = "TvAsistan"
        /** Son yazı parçasından sonra bu kadar sessizlik → söz bitti sayılır. */
        private const val BEKLEME_MS = 450L
        private const val GECERLILIK_MS = 8_000L
        private const val YEREL_BEKLEME_MS = 6_000L
    }

    private class Kayit(val arac: String, val zaman: Long) {
        val bitti = CountDownLatch(1)
        @Volatile var sonuc: JSONObject? = null
    }

    private val ana = Handler(Looper.getMainLooper())
    private val isci = Executors.newSingleThreadExecutor { r -> Thread(r, "hizli-yol").apply { isDaemon = true } }
    private val kilit = Any()

    private var sonMetin = ""
    private var turdaModelAraciGeldi = false
    private var turdaYerelCalisti = false
    private val kayitlar = ArrayDeque<Kayit>()

    private val denetle = Runnable { sozBitti() }

    /** Yeni kullanıcı turu başladı. */
    fun yeniTur() { synchronized(kilit) {
        ana.removeCallbacks(denetle)
        sonMetin = ""
        turdaModelAraciGeldi = false
        turdaYerelCalisti = false
        val simdi = SystemClock.elapsedRealtime()
        kayitlar.removeAll { simdi - it.zaman > GECERLILIK_MS }
    } }

    /** Turun o ana kadarki tam yazısı (Gemini'nin girdi dökümü). */
    fun metin(tum: String) {
        synchronized(kilit) {
            sonMetin = tum
            ana.removeCallbacks(denetle)
            if (!turdaModelAraciGeldi && !turdaYerelCalisti) ana.postDelayed(denetle, BEKLEME_MS)
        }
    }

    fun kapat() {
        synchronized(kilit) {
            ana.removeCallbacks(denetle)
            kayitlar.clear()
        }
    }

    private fun sozBitti() {
        val secim: Pair<HizliKomut.Komut, Kayit> = synchronized(kilit) {
            if (turdaModelAraciGeldi || turdaYerelCalisti) return
            val k = HizliKomut.coz(sonMetin) ?: return
            turdaYerelCalisti = true
            val y = Kayit(k.arac, SystemClock.elapsedRealtime())
            kayitlar.addLast(y)
            k to y
        }
        val (komut, kayit) = secim
        Log.i(ETIKET, "TANI hızlı yol: '$sonMetin' → $komut")
        bildir("Hızlı: ${komut.arac.replace('_', ' ')}")
        isci.execute {
            val r = try { calistir(komut.arac, komut.args) } catch (e: Exception) { JSONObject().put("hata", e.message ?: e.toString()) }
            synchronized(kilit) {
                if (r.has("hata")) kayitlar.remove(kayit) else kayit.sonuc = r
            }
            kayit.bitti.countDown()
        }
    }

    /**
     * Model bir araç çağırdı. Aynı iş yerelde yapıldıysa (ya da yapılıyorsa) onun sonucu döner,
     * araç tekrar çalıştırılmaz. null → normal çalıştır.
     */
    fun modelCagrisi(arac: String): JSONObject? {
        val kayit: Kayit = synchronized(kilit) {
            turdaModelAraciGeldi = true
            ana.removeCallbacks(denetle)
            val simdi = SystemClock.elapsedRealtime()
            kayitlar.removeAll { simdi - it.zaman > GECERLILIK_MS }
            kayitlar.firstOrNull { it.arac == arac } ?: return null
        }
        if (!kayit.bitti.await(YEREL_BEKLEME_MS, TimeUnit.MILLISECONDS)) return null
        synchronized(kilit) {
            val r = kayit.sonuc ?: return null      // yerelde hata oldu → model normal denesin
            if (!kayitlar.remove(kayit)) return null // başka çağrı zaten kullandı
            Log.i(ETIKET, "TANI hızlı yol: model çağrısı '$arac' yerel sonuçla karşılandı")
            return JSONObject(r.toString())
                .put("yerel_uygulandi", true)
                .put("not", "Bu komut Jülide tarafından ZATEN uygulandı; tekrar çağırma, yalnızca çok kısa onayla.")
        }
    }
}
