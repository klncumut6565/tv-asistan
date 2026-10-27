package com.siam.tvasistan

import android.content.Context
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gemini API anahtarını TELEFONLA kurma.
 *
 * TV ekranındaki karekod: https://…/anahtar.html?kod=<KOD>#s=<SIR>
 *  - KOD: bu kurulum için üretilen tek kullanımlık kanal kodu ("julide-anahtar-<KOD>").
 *  - SIR: 32 baytlık rastgele AES-256 anahtarı. Adresin "#" sonrasında olduğu için tarayıcı onu hiçbir
 *    sunucuya göndermez (Vercel de Supabase de görmez).
 * Telefon sayfası kullanıcının yapıştırdığı API anahtarını SIR ile AES-GCM şifreler ve kanala yollar;
 * yalnız bu TV çözebilir. Anahtar hiçbir sunucuda/veritabanında SAKLANMAZ; yalnız bu TV'de, Keystore ile
 * şifreli tutulur (Ayarlar.geminiAnahtari). Her TV kendi kullanıcısının anahtarını kullanır.
 */
class AnahtarKurulum(
    private val ctx: Context,
    /** Sonuç (okhttp iş parçacığında): başarılı mı, kullanıcıya gösterilecek mesaj. */
    private val sonuc: (Boolean, String) -> Unit,
) {
    companion object {
        private const val ETIKET = "TvAsistan"
        private val DESEN = Regex("^[A-Za-z0-9_\\-]{30,80}$")
        private val http = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

        /**
         * Anahtarı Gemini'ye sorarak dener. true = geçerli. İnternet hatasında null döner
         * (anahtar doğrulanamadı ama yanlış olduğu da bilinmiyor).
         */
        fun dene(k: String): Boolean? = try {
            val istek = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1")
                .header("x-goog-api-key", k)
                .build()
            http.newCall(istek).execute().use { r ->
                when {
                    r.isSuccessful -> true
                    r.code in listOf(400, 401, 403) -> false
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    val kod: String = TelefonKoprusu.yeniKod()
    private val sir = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val adres: String = "${Karekod.SAYFA}/anahtar.html?kod=$kod#s=" +
        Base64.encodeToString(sir, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private val kopru = TelefonKoprusu(
        kod,
        sesGeldi = {},
        kanalOn = "julide-anahtar-",
        olayGeldi = { olay, yuk -> if (olay == "anahtar") geldi(yuk) },
    )

    fun baslat() = kopru.baslat()
    fun durdur() = kopru.durdur()

    private fun yanit(ok: Boolean, mesaj: String) {
        kopru.olayGonder("sonuc", JSONObject().put("ok", ok).put("m", mesaj))
        sonuc(ok, mesaj)
    }

    private fun geldi(yuk: JSONObject) {
        val anahtar = try {
            val iv = Base64.decode(yuk.getString("iv"), Base64.DEFAULT)
            val c = Base64.decode(yuk.getString("c"), Base64.DEFAULT)
            val sifre = Cipher.getInstance("AES/GCM/NoPadding")
            sifre.init(Cipher.DECRYPT_MODE, SecretKeySpec(sir, "AES"), GCMParameterSpec(128, iv))
            sifre.updateAAD("julide-anahtar-$kod".toByteArray())
            String(sifre.doFinal(c), Charsets.UTF_8).trim()
        } catch (e: Exception) {
            Log.w(ETIKET, "Anahtar kurulumu: çözülemedi (${e.javaClass.simpleName})")
            yanit(false, "Şifre çözülemedi. TV'deki karekodu yeniden okutun.")
            return
        }
        if (!DESEN.matches(anahtar)) {
            yanit(false, "Bu bir Gemini API anahtarına benzemiyor. AI Studio'daki anahtarı tam olarak kopyalayın.")
            return
        }
        when (dene(anahtar)) {
            true -> {
                Ayarlar(ctx).geminiAnahtari = anahtar
                Log.i(ETIKET, "Anahtar kurulumu: telefondan geçerli anahtar kaydedildi")
                yanit(true, "Anahtar geçerli ve TV'ye kaydedildi. Jülide hazır!")
            }
            false -> yanit(false, "Google bu anahtarı kabul etmedi. Anahtarı yeniden kopyalayın ya da yeni bir anahtar oluşturun.")
            null -> {
                Ayarlar(ctx).geminiAnahtari = anahtar
                yanit(true, "Anahtar TV'ye kaydedildi (TV şu an Google'a ulaşamadığı için denenemedi).")
            }
        }
    }
}
