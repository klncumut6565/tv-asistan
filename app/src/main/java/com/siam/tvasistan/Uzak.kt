package com.siam.tvasistan

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Uzaktan ayarlar (config/ayarlar.json). GÜVENLİK: yalnızca beyaz listedeki alanlar okunur ve
 * hepsi doğrulanır. Model talimatı (system prompt) uzaktan DEĞİŞTİRİLEMEZ — o APK'dadır.
 * Trafik: ETag ile koşullu istek; dosya değişmediyse sunucu ~300 bayt "304" döner.
 */
object Uzak {
    data class Model(val ad: String, val etiket: String, val blocking: Boolean)
    data class Yapilandirma(
        val modeller: List<Model>,
        val ses: String,
        val bostaSn: Int,
        /** Yerel konuşma algılama eşiği (RMS). Düşük = daha hassas. */
        val vadEsik: Int,
        /** true: oturum "sohbeti kapat" denene kadar açık kalır (boşta zaman aşımı yok). */
        val surekliSohbet: Boolean,
        /** Film/dizi aramasının yapılacağı abonelik uygulamaları (sırayla denenir). */
        val abonelikler: List<String>,
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    private val ADRES = "https://raw.githubusercontent.com/${BuildConfig.GUNCELLEME_REPO}/main/config/ayarlar.json"
    private val MODEL_DESENI = Regex("^models/gemini-[a-z0-9.\\-]{1,60}$")
    private val SES_DESENI = Regex("^[A-Za-z]{3,20}$")
    private val ABONELIK_DESENI = Regex("^[\\p{L}0-9 .+'-]{2,30}$")

    private val VARSAYILAN = Yapilandirma(
        modeller = listOf(
            Model("models/gemini-3.1-flash-live-preview", "Gemini 3.1 Flash Live", false),
            Model("models/gemini-3.8-live", "Gemini 3.8 Live", true),
            Model("models/gemini-2.5-flash-native-audio-preview-12-2025", "Gemini 2.5 Flash Native Audio", false),
        ),
        ses = "Kore",
        bostaSn = 20,
        vadEsik = 400,
        surekliSohbet = true,
        abonelikler = listOf("Prime Video", "TOD"),
    )

    /** Ağdan (ETag ile) dener; olmazsa son kayıtlı kopyayı, o da yoksa yerleşik varsayılanı döndürür. */
    fun getir(a: Ayarlar): Yapilandirma {
        try {
            val b = Request.Builder().url(ADRES)
            val etag = a.uzakEtag
            if (a.uzakAyar != null && etag != null) b.header("If-None-Match", etag)
            http.newCall(b.build()).execute().use { r ->
                if (r.code == 200) {
                    val metin = r.body?.string().orEmpty()
                    if (cozumle(metin) != null) {
                        a.uzakAyar = metin
                        a.uzakEtag = r.header("ETag")
                    }
                } // 304 → kayıtlı kopya geçerli
            }
        } catch (e: Exception) {
            Log.w("TvAsistan", "Uzak ayar alınamadı: ${e.message}")
        }
        return a.uzakAyar?.let { cozumle(it) } ?: VARSAYILAN
    }

    private fun cozumle(metin: String): Yapilandirma? = try {
        val j = JSONObject(metin)
        val dizi = j.optJSONArray("modeller")
        val modeller = mutableListOf<Model>()
        if (dizi != null) {
            for (i in 0 until minOf(dizi.length(), 6)) {
                val m = dizi.optJSONObject(i) ?: continue
                val ad = m.optString("ad")
                if (MODEL_DESENI.matches(ad)) {
                    modeller += Model(ad, m.optString("etiket", ad).take(40), m.optBoolean("blocking", false))
                }
            }
        }
        Yapilandirma(
            modeller = modeller.ifEmpty { VARSAYILAN.modeller },
            ses = j.optString("ses").takeIf { SES_DESENI.matches(it) } ?: VARSAYILAN.ses,
            bostaSn = j.optInt("bosta_zaman_asimi_sn", VARSAYILAN.bostaSn).coerceIn(5, 120),
            vadEsik = j.optInt("vad_esik", VARSAYILAN.vadEsik).coerceIn(100, 3000),
            surekliSohbet = j.optBoolean("surekli_sohbet", VARSAYILAN.surekliSohbet),
            abonelikler = j.optJSONArray("abonelikler")?.let { d ->
                (0 until minOf(d.length(), 6)).map { d.optString(it).trim() }.filter { ABONELIK_DESENI.matches(it) }
            }?.takeIf { it.isNotEmpty() } ?: VARSAYILAN.abonelikler,
        )
    } catch (e: Exception) {
        null
    }
}
