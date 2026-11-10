package com.siam.tvasistan

/**
 * TV+ kutusu kısayolları: kutu ekranı GÖRÜLEMEDİĞİ için (TV, HDMI görüntüsünü yazılıma vermiyor — ekran görüntüsü
 * 0 bayt) kutudaki işler kullanıcının bir kez öğrettiği SABİT tuş dizileriyle yapılır.
 * Dizi metni: "geri*3, sol*5, yukari*3, sag, asagi*2, tamam" (x ya da * ile tekrar). Saf mantık, birim testli.
 */
object KutuKisayol {

    const val EN_COK_TUS = 40

    /** İzinli tuşlar → Android tuş kodu. GÜÇ tuşu bilerek YOK: TV'yi kapatır. */
    val TUSLAR = linkedMapOf(
        "tamam" to 23, "yukari" to 19, "asagi" to 20, "sol" to 21, "sag" to 22, "geri" to 4,
        "kanal_yukari" to 166, "kanal_asagi" to 167,
        "0" to 7, "1" to 8, "2" to 9, "3" to 10, "4" to 11, "5" to 12, "6" to 13, "7" to 14, "8" to 15, "9" to 16,
    )

    private val ESANLAMLI = mapOf(
        "ok" to "tamam", "sec" to "tamam", "enter" to "tamam", "orta" to "tamam",
        "yukarı" to "yukari", "aşağı" to "asagi", "sağ" to "sag", "sağa" to "sag", "sola" to "sol",
        "asagiya" to "asagi", "yukariya" to "yukari", "saga" to "sag", "back" to "geri", "geriye" to "geri",
        "up" to "yukari", "down" to "asagi", "left" to "sol", "right" to "sag",
    )

    /** Dizi metnini tuş adlarına çevirir; geçersiz parça varsa hata mesajıyla null. */
    fun coz(dizi: String): Pair<List<String>?, String?> {
        val sonuc = mutableListOf<String>()
        for (ham in dizi.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }) {
            val m = Regex("^(.+?)\\s*(?:[*x×]\\s*(\\d{1,2}))?$", RegexOption.IGNORE_CASE).find(ham) ?: return null to "Anlaşılmadı: '$ham'"
            val adHam = m.groupValues[1].trim().lowercase().replace(' ', '_')
            val ad = ESANLAMLI[adHam] ?: ESANLAMLI[Uygulamalar.normal(adHam)] ?: Uygulamalar.normal(adHam).replace(' ', '_')
            if (ad !in TUSLAR) return null to "Bilinmeyen tuş: '$ham' (izinli: ${TUSLAR.keys.joinToString()})"
            val n = m.groupValues[2].toIntOrNull() ?: 1
            if (n !in 1..20) return null to "Tekrar sayısı 1–20 olmalı: '$ham'"
            repeat(n) { sonuc += ad }
            if (sonuc.size > EN_COK_TUS) return null to "Çok uzun: en çok $EN_COK_TUS tuş."
        }
        if (sonuc.isEmpty()) return null to "Tuş dizisi boş."
        return sonuc to null
    }

    /** Tuş adı listesini kısa metne çevirir: [sol, sol, sol, tamam] → "sol*3, tamam". */
    fun metin(tuslar: List<String>): String {
        val parcalar = mutableListOf<String>()
        var i = 0
        while (i < tuslar.size) {
            var j = i
            while (j < tuslar.size && tuslar[j] == tuslar[i]) j++
            parcalar += if (j - i > 1) "${tuslar[i]}*${j - i}" else tuslar[i]
            i = j
        }
        return parcalar.joinToString(", ")
    }

    fun kodlar(tuslar: List<String>): List<Int> = tuslar.map { TUSLAR.getValue(it) }

    /** Kayıtlı kısayollar içinde adı en iyi eşleşeni bulur. */
    fun bul(kisayollar: Map<String, String>, ad: String): String? {
        val n = Uygulamalar.normal(ad).removeSuffix(" uygulamasi").removeSuffix(" uygulamasini").trim()
        if (n.isEmpty()) return null
        return kisayollar.keys.firstOrNull { it == n }
            ?: kisayollar.keys.firstOrNull { it.replace(" ", "") == n.replace(" ", "") }
            ?: kisayollar.keys.firstOrNull { n.contains(it) || it.contains(n) }
    }
}
