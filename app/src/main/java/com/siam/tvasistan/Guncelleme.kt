package com.siam.tvasistan

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Kendi kendini güncelleme: GitHub Releases'taki son sürümü denetler, APK'yı
 * indirir ve Android'in kurulum onayını açar. (Android, Play Store dışı uygulamalarda
 * sessiz kurulumu YASAKLAR — kullanıcı bir kez "Yükle"ye basar.)
 */
object Guncelleme {
    data class Surum(val kod: Int, val ad: String, val apkUrl: String)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile var kuruluyor = false
        private set

    /** Yeni sürüm varsa döndürür, yoksa null. Arka plan iş parçacığında çağrılmalı. */
    fun denetle(): Surum? {
        val istek = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GUNCELLEME_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "tv-asistan")
            .build()
        http.newCall(istek).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Sürüm bilgisi alınamadı (HTTP ${r.code}).")
            val j = JSONObject(r.body?.string().orEmpty())
            val etiket = j.optString("tag_name")
            val kod = etiket.substringAfterLast('.').toIntOrNull() ?: return null
            val varliklar = j.optJSONArray("assets") ?: return null
            var url: String? = null
            for (i in 0 until varliklar.length()) {
                val v = varliklar.optJSONObject(i) ?: continue
                if (v.optString("name").endsWith(".apk")) url = v.optString("browser_download_url")
            }
            if (url.isNullOrBlank() || kod <= BuildConfig.VERSION_CODE) return null
            return Surum(kod, etiket.removePrefix("v"), url)
        }
    }

    /** APK'yı indirir ve kurulum onayını başlatır. Arka plan iş parçacığında çağrılmalı. */
    fun kur(ctx: Context, s: Surum) {
        if (kuruluyor) return
        kuruluyor = true
        try {
            val yukleyici = ctx.packageManager.packageInstaller
            val prm = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            prm.setAppPackageName(ctx.packageName)
            val id = yukleyici.createSession(prm)
            yukleyici.openSession(id).use { oturum ->
                http.newCall(Request.Builder().url(s.apkUrl).header("User-Agent", "tv-asistan").build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("APK indirilemedi (HTTP ${r.code}).")
                    val govde = r.body ?: throw IOException("APK boş.")
                    oturum.openWrite("tv-asistan.apk", 0, govde.contentLength()).use { cikis ->
                        govde.byteStream().copyTo(cikis)
                        oturum.fsync(cikis)
                    }
                }
                val bayrak = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, GuncellemeAlici::class.java), bayrak)
                oturum.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            kuruluyor = false
            throw e
        }
    }

    internal fun bitti() {
        kuruluyor = false
    }
}

/** Kurulum durumunu alır; onay gerekiyorsa sistemin "Yükle" ekranını açar. */
class GuncellemeAlici : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (val durum = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val onay: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    i.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (onay != null) {
                    onay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        c.startActivity(onay)
                    } catch (e: Exception) {
                        Log.e("TvAsistan", "Kurulum onayı açılamadı", e)
                        Guncelleme.bitti()
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Guncelleme.bitti()
                Toast.makeText(c, "Jülide güncellendi.", Toast.LENGTH_LONG).show()
            }
            else -> {
                Guncelleme.bitti()
                val m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "kod $durum"
                Toast.makeText(c, "Güncelleme yapılamadı: $m", Toast.LENGTH_LONG).show()
            }
        }
    }
}
