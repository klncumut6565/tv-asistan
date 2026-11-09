package com.siam.tvasistan

import org.json.JSONArray
import org.json.JSONObject

/**
 * Araç tekrar döngüsü koruması (Hermes "tool guardrails" fikri, küçük hâli).
 *
 * Model AYNI aracı AYNI argümanlarla bir tur içinde [sinir] kezden fazla çağırırsa araç çalıştırılmaz;
 * modele açık bir "tekrar" hatası döner. Böylece "sonraki kanal" ya da "sesi artır" gibi komutlar
 * döngüye girip TV'yi zaplatmaz / sesi beklenmedik yükseltmez.
 *
 * Gezinme ve okuma araçları muaftır: kullanıcı menüde art arda "aşağı" isteyebilir, ekranı tekrar okumak zararsızdır.
 */
class AracKorumasi(private val sinir: Int = 2) {

    companion object {
        val MUAF = setOf(
            "tus", "kaydir", "imlec", "ekrani_oku", "ekrana_bak", "numaralari_goster", "arama_sonucu",
            "saat_tarih", "cihaz_durumu", "uygulamalari_listele", "kaynaklari_listele", "kanal_listesi",
            "hatirladiklarim", "oturumu_kapat", "tv_kapat", // kullanıcının durdurma/kapatma kontrolü her zaman erişilebilir
        )

        /** Anahtarları sıralı, iç içe nesneleri de kapsayan kararlı argüman metni. */
        fun normal(a: Any?): String = when (a) {
            is JSONObject -> a.keys().asSequence().sorted().joinToString(",", "{", "}") { k -> "$k=${normal(a.opt(k))}" }
            is JSONArray -> (0 until a.length()).joinToString(",", "[", "]") { normal(a.opt(it)) }
            is String -> a.trim().lowercase()
            null, JSONObject.NULL -> "null"
            else -> a.toString()
        }
    }

    private val sayac = HashMap<String, Int>()

    /** Çağrı izinliyse null; engellendiyse modele verilecek hata sonucu. */
    @Synchronized
    fun denetle(ad: String, args: JSONObject): JSONObject? {
        if (ad in MUAF) return null
        val imza = ad + normal(args)
        val n = (sayac[imza] ?: 0) + 1
        sayac[imza] = n
        if (n <= sinir) return null
        return JSONObject()
            .put("hata", "TEKRAR ENGELLENDİ: '$ad' aynı ayarla bu turda zaten ${n - 1} kez çalıştı.")
            .put("not", "Aynı çağrıyı TEKRARLAMA. Önceki sonucu kullanıcıya kısaca söyle; işe yaramadıysa farklı bir yol dene ya da kullanıcıya sor.")
            .put("engellendi", true)
    }

    /** Yeni kullanıcı turu ya da model cevabı bitti. */
    @Synchronized
    fun sifirla() = sayac.clear()
}
