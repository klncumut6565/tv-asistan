package com.siam.tvasistan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Dinleme sürerken mikrofonun arka planda kesilmemesi için ön plan servisi
 * (foregroundServiceType="microphone"). Kendi başına ses işlemez.
 */
class MikrofonServisi : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(KANAL, "TV Asistan dinleme", NotificationManager.IMPORTANCE_LOW)
        )
        val bildirim = Notification.Builder(this, KANAL)
            .setContentTitle("TV Asistan dinliyor")
            .setSmallIcon(R.drawable.ic_mikrofon)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, bildirim)
            }
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val KANAL = "dinleme"
    }
}
