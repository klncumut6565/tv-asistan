package com.siam.tvasistan

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService

/**
 * Jülide'yi TV'nin "Dijital asistan" uygulaması yapmak için (deneme).
 * Asistan tuşuna basılınca sistem bu servisi çağırır; oturum hemen kapanır ve
 * Jülide'nin mevcut dinleme akışı (AsistanServisi.tetiklendi) çalıştırılır.
 */
class AsistanAsistan : VoiceInteractionService()

class AsistanOturumServisi : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AsistanOturum(this)
}

class AsistanOturum(ctx: Context) : VoiceInteractionSession(ctx) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Handler(Looper.getMainLooper()).post { AsistanServisi.ornek?.tetiklendi() }
        hide()
    }
}

/** Sistemin tanıma servisi gerektirmesi için boş bırakılmış yer tutucu. */
class AsistanTanima : RecognitionService() {
    override fun onStartListening(intent: android.content.Intent?, callback: Callback?) {}
    override fun onCancel(callback: Callback?) {}
    override fun onStopListening(callback: Callback?) {}
}
