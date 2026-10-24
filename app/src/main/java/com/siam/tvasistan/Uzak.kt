package com.siam.tvasistan

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Uzaktan ayarlar: repodaki config/ayarlar.json her oturum başında okunur.
 * Model listesi, ses, ek talimat ve zaman aşımı APK güncellemesi olmadan değişir.
 */
object Uzak {
    data class Model(val ad: String, val etiket: String, val blocking: Boolean)
    data class Yapilandirma(
        val modeller: List<Model>,
        val ses: String,
        val ekTalimat: String,
        val bostaSn: Int,
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    private val ADRES = "https://raw.githubusercontent.com/${BuildConfig.GUNCELLEME_REPO}/main/config/ayarlar.json"

    private val VARSAYILAN = Yapilandirma(
        modeller = listOf(
            Model("models/gemini-3.1-flash-live-preview", "Gemini 3.1 Flash Live", false),
            Model("models/gemini-3.8-live", "Gemini 3.8 Live", true),
            Model("models/gemini-2.5-flash-native-audio-preview-12-2025", "Gemini 2.5 Flash Native Audio", false),
        ),
        ses = "Kore",
        ekTalimat = "",
        bostaSn = 20,
    )

    /** Ağdan dener; olmazsa son kayıtlı kopyayı, o da yoksa yerleşik varsayılanı döndürür. */
    fun getir(a: Ayarlar): Yapilandirma {
        try {
            val istek = Request.Builder().url("$ADRES?t=${System.currentTimeMillis() / 60_000}").build()
            http.newCall(istek).execute().use { r ->
                val metin = r.body?.string().orEmpty()
                if (r.isSuccessful && cozumle(metin) != null) a.uzakAyar = metin
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
            for (i in 0 until dizi.length()) {
                val m = dizi.optJSONObject(i) ?: continue
                val ad = m.optString("ad")
                if (ad.isNotBlank()) modeller += Model(ad, m.optString("etiket", ad), m.optBoolean("blocking", false))
            }
        }
        Yapilandirma(
            modeller = modeller.ifEmpty { VARSAYILAN.modeller },
            ses = j.optString("ses").ifBlank { VARSAYILAN.ses },
            ekTalimat = j.optString("ek_talimat"),
            bostaSn = j.optInt("bosta_zaman_asimi_sn", VARSAYILAN.bostaSn).coerceIn(5, 300),
        )
    } catch (e: Exception) {
        null
    }
}
