package com.siam.tvasistan

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Jülide'nin erişilebilirlik servisi kapanırsa (ör. uygulama "Durmaya zorla" ile durdurulunca Android onu
 * etkin listesinden çıkarır) kendini geri açar. CleanYourTV launcher'ının ServiceRepair yaklaşımından
 * Kotlin ile yeniden yazılmıştır (MIT, bkz. THIRD_PARTY_NOTICES.md).
 *
 * - Servis bağlanınca kendini KAYDEDER; kullanıcı servisi Ayarlar'dan kendisi kapatırsa (onUnbind) kayıt
 *   SİLİNİR ve Jülide bir daha kendini açmaz. Zorla durdurmada süreç öldüğü için kayıt kalır.
 * - Onarım yalnızca WRITE_SECURE_SETTINGS izni varsa çalışır. Bu izin normal uygulamalara verilmez; bir kez
 *   bilgisayardan verilir:  adb shell pm grant com.siam.tvasistan android.permission.WRITE_SECURE_SETTINGS
 *   İzin yoksa hiçbir şey yapmaz, eski davranış korunur.
 * - Yalnızca KENDİ bileşenini ekler; listedeki diğer servislere dokunmaz.
 */
object ServisOnarim {
    private const val ETIKET = "TvAsistan"
    private const val TERCIH = "servis_onarim"
    private const val KAYIT = "kayitli"

    private fun tercih(ctx: Context) = ctx.applicationContext.getSharedPreferences(TERCIH, Context.MODE_PRIVATE)

    fun bilesen(ctx: Context) = ComponentName(ctx.packageName, AsistanServisi::class.java.name)

    fun kaydet(ctx: Context) = tercih(ctx).edit().putBoolean(KAYIT, true).apply()

    fun kaydiSil(ctx: Context) = tercih(ctx).edit().putBoolean(KAYIT, false).apply()

    fun izinVar(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Servis şu an Android'in etkin erişilebilirlik listesinde mi? */
    fun listede(ctx: Context): Boolean = etkinler(ctx.contentResolver).contains(bilesen(ctx))

    private fun etkinler(cr: ContentResolver): List<ComponentName> =
        (Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .split(':').mapNotNull { if (it.isBlank()) null else ComponentName.unflattenFromString(it) }

    /**
     * Gerekirse servisi listeye geri ekler. Arka plan iş parçacığında çalışır (toplam ~2 sn bekleme içerir),
     * çağıranı bekletmez. Onarım başlatıldıysa true döner.
     *
     * [listedeyseDe]: servis listede görünüp yine de bağlanmamışsa ("çökmüş" işaretli) da onar. Yalnızca
     * servise bağlanması için zaman tanındıktan sonra (MainActivity) true verilir.
     */
    fun onar(ctx: Context, listedeyseDe: Boolean = false): Boolean {
        val c = ctx.applicationContext
        if (!izinVar(c)) return false
        if (!tercih(c).getBoolean(KAYIT, false)) return false // kullanıcı bilerek kapatmış ya da hiç açılmamış
        val cr = c.contentResolver
        val ben = bilesen(c)
        val simdiki = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val digerleri = etkinler(cr).filter { it != ben }
        if (etkinler(cr).contains(ben) && (AsistanServisi.ornek != null || !listedeyseDe)) return false
        val olmadan = digerleri.joinToString(":") { it.flattenToShortString() }
        val ile = (digerleri + ben).joinToString(":") { it.flattenToShortString() }
        // Zorla durdurulan servis Android'in "çökmüş servisler" listesine de düşer ve yalnızca listeye geri
        // yazmak onu yeniden bağlamaz. Android bu işareti, servisin listeden çıktığını görünce siler:
        // bu yüzden ekle → çıkar → ekle. Her adımda liste hâlâ beklenen değerdeyse yazılır (başka bir
        // değişikliğin üzerine yazılmaz).
        Thread({
            try {
                if (!degistir(cr, simdiki, ile)) return@Thread
                Thread.sleep(1000)
                if (!degistir(cr, ile, olmadan)) return@Thread
                Thread.sleep(1000)
                if (!degistir(cr, olmadan, ile)) return@Thread
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                Log.i(ETIKET, "Servis onarımı: erişilebilirlik servisi yeniden açıldı")
            } catch (e: Exception) {
                Log.w(ETIKET, "Servis onarımı başarısız: ${e.message}")
            }
        }, "ServisOnarim").start()
        return true
    }

    private fun degistir(cr: ContentResolver, beklenen: String, yeni: String): Boolean {
        val simdi = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        if (simdi != beklenen) return false
        return Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, yeni)
    }
}

/**
 * TV açıldığında ve Jülide güncellendiğinde onarımı dener.
 * Not: "Durmaya zorla" sonrası Android uygulamayı, kullanıcı açana kadar yayın almayan "durdurulmuş"
 * duruma alır; o durumda onarım, Jülide simgesi açılınca (MainActivity.onResume) yapılır.
 */
class AcilisAlici : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ServisOnarim.onar(ctx)
        }
    }
}
