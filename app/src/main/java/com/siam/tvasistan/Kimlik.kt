package com.siam.tvasistan

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * TMGD Yönetim Sistemi ile kimlik doğrulama.
 *
 * 1) Supabase e-posta/şifre girişi → access + refresh token (TV'de saklanır, şifre SAKLANMAZ)
 * 2) /api/assistant/realtime/session → kısa ömürlü Gemini Live token'ı
 *    (web'deki "Canlı Konuşma" ile AYNI endpoint; kalıcı Gemini anahtarı TV'ye hiç gelmez)
 */
object Kimlik {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val JSON_TIPI = "application/json".toMediaType()

    data class CanliOturum(val token: String, val apiVersion: String)

    @Synchronized
    fun girisYap(a: Ayarlar, eposta: String, sifre: String) {
        val govde = JSONObject().put("email", eposta.trim()).put("password", sifre).toString()
        val istek = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=password")
            .header("apikey", BuildConfig.SUPABASE_KEY)
            .post(govde.toRequestBody(JSON_TIPI))
            .build()
        http.newCall(istek).execute().use { r ->
            val metin = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("Giriş başarısız: " + hataMetni(metin, r.code))
            kaydet(a, JSONObject(metin))
        }
        a.eposta = eposta.trim()
    }

    @Synchronized
    private fun yenile(a: Ayarlar): String {
        val rt = a.yenilemeToken ?: throw IOException("Önce TV Asistan uygulamasından giriş yapın.")
        val istek = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token")
            .header("apikey", BuildConfig.SUPABASE_KEY)
            .post(JSONObject().put("refresh_token", rt).toString().toRequestBody(JSON_TIPI))
            .build()
        http.newCall(istek).execute().use { r ->
            val metin = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                if (r.code in 400..499) a.cikisYap()
                throw IOException("Oturum süresi doldu — uygulamadan tekrar giriş yapın.")
            }
            kaydet(a, JSONObject(metin))
        }
        return a.erisimToken ?: throw IOException("Token alınamadı.")
    }

    @Synchronized
    private fun gecerliToken(a: Ayarlar): String {
        val at = a.erisimToken
        if (at != null && System.currentTimeMillis() < a.tokenBitis - 60_000) return at
        return yenile(a)
    }

    private fun kaydet(a: Ayarlar, j: JSONObject) {
        a.erisimToken = j.getString("access_token")
        j.optString("refresh_token").takeIf { it.isNotBlank() }?.let { a.yenilemeToken = it }
        a.tokenBitis = System.currentTimeMillis() + j.optLong("expires_in", 3600L) * 1000L
    }

    /** Gemini Live için kısa ömürlü oturum token'ı alır. */
    fun canliOturumAl(a: Ayarlar): CanliOturum {
        if (a.sunucu.isBlank()) throw IOException("Sunucu adresi girilmemiş.")
        var token = gecerliToken(a)
        for (deneme in 0..1) {
            val istek = Request.Builder()
                .url("${a.sunucu}/api/assistant/realtime/session")
                .header("Authorization", "Bearer $token")
                .post("{}".toRequestBody(JSON_TIPI))
                .build()
            var yenidenDene = false
            http.newCall(istek).execute().use { r ->
                val metin = r.body?.string().orEmpty()
                if (r.code == 401 && deneme == 0) {
                    yenidenDene = true
                } else {
                    if (r.code == 401) throw IOException("Yetki yok — hesabınızın rolü super_admin, admin veya tmgd olmalı.")
                    if (!r.isSuccessful) throw IOException(hataMetni(metin, r.code))
                    val j = JSONObject(metin)
                    return CanliOturum(j.getString("token"), j.optString("apiVersion", "v1beta"))
                }
            }
            if (yenidenDene) token = yenile(a)
        }
        throw IOException("Oturum başlatılamadı.")
    }

    private fun hataMetni(govde: String, kod: Int): String = try {
        val j = JSONObject(govde)
        listOf("error_description", "msg", "message", "error")
            .map { j.optString(it) }
            .firstOrNull { it.isNotBlank() } ?: "HTTP $kod"
    } catch (e: Exception) {
        "HTTP $kod"
    }
}
