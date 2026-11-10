package com.siam.tvasistan

/**
 * TV+ (ya da başka bir yayın kutusu) bir HDMI girişine bağlıdır. Kutu ekrandayken TV'ye gönderilen kumanda tuşları
 * HDMI CEC üzerinden kutuya geçer (kullanıcının TV'sinde denendi: aşağı, tamam, kanal+ çalışıyor).
 * Bu yüzden kutu modunda kanal komutları TV'nin anten kanal listesine değil, doğrudan tuş olarak kutuya gider.
 */
object KutuModu {

    private val TVPLUS = Regex("tv\\s*(\\+|plus|pılus|pilus|artı|arti)|turkcell|\\bkutu", RegexOption.IGNORE_CASE)

    /** Kaynak adı TV+ kutusunu mu anlatıyor ("TV+", "TV Plus", "Turkcell", "kutu")? Normalleştirmeden ÖNCE bakılır: "TV+" normalleşince "tv" olur. */
    fun tvplusMu(kaynak: String): Boolean = TVPLUS.containsMatchIn(kaynak)

    /** "HDMI 2", "hdmi2", "2" → "HDMI 2"; geçersizse null. */
    fun hdmiAdi(giris: String): String? {
        val n = Regex("(\\d)").find(giris)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return if (n in 1..4) "HDMI $n" else null
    }
}
