package com.siam.tvasistan

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import org.json.JSONObject

/**
 * TV'nin bağlantı ve cihaz durumu (sesli "internet var mı", "TV durumunu söyle") ve ayar sayfalarını açma.
 *
 * Yalnızca okuma yapar; sürekli dinleyici (NetworkCallback) KAYDETMEZ. Araç çağrıldığında bir kez okunur,
 * birkaç milisaniye sürer. Ağ durumu yaklaşımı CleanYourTV launcher'ının ağ göstergesinden, ADB durumu
 * remote-access eklentisinden uyarlanmıştır (MIT, bkz. THIRD_PARTY_NOTICES.md); kod kopyalanmamıştır.
 */
object Cihaz {

    fun durum(ctx: Context, oturum: String): JSONObject {
        val j = JSONObject()
        j.put("ag", ag(ctx))
        j.put("cihaz", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        j.put("julide_surumu", BuildConfig.VERSION_NAME)
        j.put("julide_oturumu", oturum)
        bellek(ctx)?.let { j.put("bellek", it) }
        ses(ctx)?.let { j.put("ses_seviyesi", it) }
        j.put("ekran", if (ctx.getSystemService(PowerManager::class.java)?.isInteractive == false) "kapalı" else "açık")
        j.put("adb", adb(ctx))
        return j
    }

    private fun ag(ctx: Context): String = try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val ag = cm?.activeNetwork
        val y = if (ag != null) cm.getNetworkCapabilities(ag) else null
        if (y == null) {
            "Bağlantı yok (Wi-Fi veya kablo bağlı değil)."
        } else {
            val tip = when {
                y.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Kablolu (Ethernet)"
                y.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                y.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobil"
                else -> "Bilinmeyen ağ"
            }
            val internet = when {
                y.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "internet çalışıyor"
                y.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "ağa bağlı ama internet doğrulanamadı"
                else -> "internet yok"
            }
            val vpn = if (y.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) ", VPN açık" else ""
            "$tip, $internet$vpn."
        }
    } catch (e: Exception) {
        "Ağ durumu okunamadı: ${e.message}"
    }

    private fun bellek(ctx: Context): String? = try {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val m = ActivityManager.MemoryInfo()
        am.getMemoryInfo(m)
        val bos = m.availMem / (1024 * 1024)
        val toplam = m.totalMem / (1024 * 1024)
        "$bos MB boş / $toplam MB" + if (m.lowMemory) " (bellek az)" else ""
    } catch (_: Exception) {
        null
    }

    private fun ses(ctx: Context): String? = try {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val sessiz = am.isStreamMute(AudioManager.STREAM_MUSIC)
        "%${am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max}" + if (sessiz) " (sessiz)" else ""
    } catch (_: Exception) {
        null
    }

    /**
     * ADB yalnızca açık/kapalı olarak bildirilir. Ağ portu (service.adb.tcp.port) uygulamadan güvenilir
     * okunamadığı için "ağdan mı, USB'den mi" ayrımı yapılmaz.
     */
    private fun adb(ctx: Context): String = try {
        if (Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1)
            "Hata ayıklama (ADB) AÇIK. Kullanılmıyorsa güvenlik için kapatılması önerilir."
        else "Hata ayıklama (ADB) kapalı."
    } catch (_: Exception) {
        "ADB durumu okunamadı."
    }

    // ------------------------------------------------------------ ayar sayfaları

    /** Sesle açılabilen ayar sayfaları ve denenecek intent eylemleri (sırayla). */
    val SAYFALAR: Map<String, List<String>> = linkedMapOf(
        "genel" to listOf(Settings.ACTION_SETTINGS),
        "ag" to listOf(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS),
        "bluetooth" to listOf(Settings.ACTION_BLUETOOTH_SETTINGS),
        "ses" to listOf(Settings.ACTION_SOUND_SETTINGS),
        "ekran" to listOf(Settings.ACTION_DISPLAY_SETTINGS),
        "tarih_saat" to listOf(Settings.ACTION_DATE_SETTINGS),
        "dil" to listOf(Settings.ACTION_LOCALE_SETTINGS),
        "erisilebilirlik" to listOf(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        "uygulamalar" to listOf(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, Settings.ACTION_APPLICATION_SETTINGS),
        "depolama" to listOf(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    )

    /** Verilen eylemleri sırayla dener; hiçbiri açılmazsa genel ayarları açar. Açılanın adını döner, yoksa null. */
    fun sayfaAc(ctx: Context, eylemler: List<String>): String? {
        for (e in eylemler + Settings.ACTION_SETTINGS) {
            if (baslat(ctx, Intent(e))) return e
        }
        return null
    }

    /** Bir uygulamanın "Uygulama bilgisi" sayfası (durdurma, devre dışı bırakma, önbellek burada). */
    fun uygulamaBilgisiAc(ctx: Context, paket: String): Boolean =
        baslat(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$paket")))

    private fun baslat(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
