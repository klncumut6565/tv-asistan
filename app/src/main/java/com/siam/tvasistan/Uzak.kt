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
    /** Gemini sessizlik süresi: varsayılan 800 ms (eski davranış), güvenli aralık 500–2000 ms. */
    const val SESSIZLIK_VARSAYILAN = 800

    fun sessizlikDogrula(ms: Int): Int = if (ms in 500..2000) ms else SESSIZLIK_VARSAYILAN

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
        /** Mikrofonda TV'nin kendi sesini bastıran kaynağı dene (destekleyen cihazlarda). */
        val yankiEngelle: Boolean,
        /** Video açıkken tuşa basınca: true = videoyu duraklat (en temiz), false = sesini kıs. */
        val konusurkenDuraklat: Boolean,
        /** Video/canlı yayın açıkken "Jülide" uyandırma kelimesiyle dinlemeye başla (internetsiz). */
        val uyandirma: Boolean,
        /** Uyandırma eşleşmesinde kabul edilen yazımlar (tanıyıcı "Jülide"yi farklı yazabilir). */
        val uyandirmaKelimeleri: List<String>,
        /** Gemini "affective dialog": cevap tonunu kullanıcının ses tonuna uydurur. Varsayılan KAPALI. */
        val duyguUyumu: Boolean = false,
        /** Gemini'nin konuşma bitti saymak için beklediği sessizlik (ms). Uzun = cümle bölünmez, cevap biraz gecikir. */
        val sessizlikMs: Int = SESSIZLIK_VARSAYILAN,
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    private val ADRES = "https://raw.githubusercontent.com/${BuildConfig.GUNCELLEME_REPO}/main/config/ayarlar.json"
    private val MODEL_DESENI = Regex("^models/gemini-[a-z0-9.\\-]{1,60}$")
    /**
     * YALNIZ KADIN sesleri (Jülide kadın asistan): Google Cloud Gemini-TTS ses tablosunda "Female" olan 14 resmî ses
     * (docs.cloud.google.com/text-to-speech/docs/gemini-tts). Listede olmayan ya da erkek ses adı → "Kore".
     */
    val SESLER = setOf(
        "Kore", "Sulafat", "Vindemiatrix", "Achernar", "Aoede", "Callirrhoe", "Despina",
        "Autonoe", "Erinome", "Gacrux", "Laomedeia", "Leda", "Pulcherrima", "Zephyr",
    )

    /** Ses adını doğrular (büyük/küçük harf duyarsız); geçersizse Kore. */
    /**
     * Telefon sayfasından yapılan ses seçimi uzaktan ayardan ÖNCELİKLİDİR (yalnız beyaz listedeki kadın sesleri).
     * duyguTercihi: -1 = uzaktan ayar, 0 = kapalı, 1 = açık. Testlerde de kullanılır.
     */
    fun tercihUygula(y: Yapilandirma, sesTercihi: String, duyguTercihi: Int): Yapilandirma {
        val ses = sesTercihi.takeIf { t -> SESLER.any { it.equals(t.trim(), true) } }?.let { sesDogrula(it) } ?: y.ses
        val duygu = when (duyguTercihi) { 0 -> false; 1 -> true; else -> y.duyguUyumu }
        return y.copy(ses = ses, duyguUyumu = duygu)
    }

    fun sesDogrula(ad: String?): String = SESLER.firstOrNull { it.equals(ad?.trim(), ignoreCase = true) } ?: "Kore"
    private val KELIME_DESENI = Regex("^[\\p{L} ]{3,20}$")
    private val ABONELIK_DESENI = Regex("^[\\p{L}0-9 .+'-]{2,30}$")

    internal val VARSAYILAN = Yapilandirma(
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
        yankiEngelle = true,
        konusurkenDuraklat = true,
        uyandirma = true,
        uyandirmaKelimeleri = listOf("jülide", "julide", "cülide", "jülida", "gülide", "jüli de", "jülde", "ülide"),
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
        val y = a.uzakAyar?.let { cozumle(it) } ?: VARSAYILAN
        return tercihUygula(y, a.sesTercihi, a.duyguTercihi)
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
            ses = sesDogrula(j.optString("ses")),
            bostaSn = j.optInt("bosta_zaman_asimi_sn", VARSAYILAN.bostaSn).coerceIn(5, 120),
            vadEsik = j.optInt("vad_esik", VARSAYILAN.vadEsik).coerceIn(100, 3000),
            surekliSohbet = j.optBoolean("surekli_sohbet", VARSAYILAN.surekliSohbet),
            abonelikler = j.optJSONArray("abonelikler")?.let { d ->
                (0 until minOf(d.length(), 6)).map { d.optString(it).trim() }.filter { ABONELIK_DESENI.matches(it) }
            }?.takeIf { it.isNotEmpty() } ?: VARSAYILAN.abonelikler,
            yankiEngelle = j.optBoolean("yanki_engelle", VARSAYILAN.yankiEngelle),
            konusurkenDuraklat = j.optBoolean("konusurken_duraklat", VARSAYILAN.konusurkenDuraklat),
            uyandirma = j.optBoolean("uyandirma", VARSAYILAN.uyandirma),
            uyandirmaKelimeleri = j.optJSONArray("uyandirma_kelimeleri")?.let { d ->
                (0 until minOf(d.length(), 15)).map { d.optString(it).trim() }.filter { KELIME_DESENI.matches(it) }
            }?.takeIf { it.isNotEmpty() } ?: VARSAYILAN.uyandirmaKelimeleri,
            duyguUyumu = j.optBoolean("duygu_uyumu", false),
            sessizlikMs = sessizlikDogrula(j.optInt("sessizlik_ms", SESSIZLIK_VARSAYILAN)),
        )
    } catch (e: Exception) {
        null
    }
}
