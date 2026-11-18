package com.siam.tvasistan

/**
 * SÖZ DENETÇİSİ — "yapmadığı işi yaptım deme" koruması (Android'siz, birim testli).
 *
 * Gemini bazen aracı hiç çağırmadan ya da araç HATA döndürdüğü halde "Tarayıcıyı açtım" der.
 * Talimattaki kural tek başına yetmiyor; bu yüzden her tur bittiğinde Jülide'nin SÖYLEDİĞİ metin
 * ile o turda GERÇEKTEN çalışan araçlar karşılaştırılır. Tutmazsa modele düzeltme mesajı gider
 * ve Jülide kullanıcıya "Aslında yapamadım" diye düzeltir.
 *
 * Karar kuralları:
 *  - KESİN iddia ("açtım", "açıldı", "kapattım", "geçtim", "tıkladım"...): o turda BAŞARILI bir
 *    eylem aracı yoksa ihlal. (Yalnız dünya bilgisi aracı çalıştıysa — hava, döviz, araştırma —
 *    "Mağaza 1990'da açıldı" gibi bilgi cümlesi olabilir: denetlenmez.)
 *  - NİYET iddiası ("açıyorum", "geçiyorum", "oynatıyorum"): o turda HİÇ eylem aracı denenmediyse ihlal.
 *  - Olumsuzlar ("açamadım", "açılmadı") hiçbir kalıba uymaz.
 */
object SozDenetcisi {

    data class AracKaydi(val ad: String, val basarili: Boolean, val hata: String? = null)

    /** Başarısı bir eylemin yapıldığını GÖSTERMEYEN araçlar (yalnız okur/gösterir). */
    val OKUMA_ARACLARI = setOf(
        "ekrani_oku", "ekrana_bak", "uygulamalari_listele", "cihaz_durumu", "kaynaklari_listele",
        "internette_arastir", "doviz_kuru", "hava_durumu", "saat_tarih",
    )

    /** Dünya bilgisi araçları: cevapta geçen "açıldı/kapandı" bir olayı anlatıyor olabilir. */
    private val BILGI_ARACLARI = setOf("internette_arastir", "doviz_kuru", "hava_durumu", "saat_tarih")

    // Kök + geçmiş zaman 1. tekil ("açtım", "kapattım", "değiştirdim"…), Türkçe harfler sadeleştirilmiş.
    private val KOKLER = listOf(
        "ac", "kapat", "baslat", "oynat", "cal", "gec", "degistir", "tikla", "bas", "yaz", "sec",
        "kur", "ayarla", "kaydet", "kis", "artir", "yukselt", "azalt", "durdur", "duraklat",
        "sar", "ara", "goster", "getir", "gonder", "dondur", "don", "gir", "cik", "sustur",
    )
    private val KESIN = Regex(
        "\\b(" + KOKLER.joinToString("|") + ")(t|d)(i|u)m\\b" +
            // edilgen, tamamlanmış: "açıldı", "kapatıldı", "başlatıldı", "kapandı", "değiştirildi", "ayarlandı", "kaydedildi"
            "|\\b(acildi|kapandi|kapatildi|baslatildi|oynatildi|degistirildi|ayarlandi|kaydedildi|gosterildi|yazildi|secildi)\\b" +
            "|\\b(acilmistir|acilmis durumda)\\b"
    )
    private val NIYET = Regex(
        "\\b(aciyorum|kapatiyorum|baslatiyorum|oynatiyorum|caliyorum|geciyorum|degistiriyorum|tikliyorum|" +
            "basiyorum|yaziyorum|seciyorum|kuruyorum|ayarliyorum|kaydediyorum|kisiyorum|artiriyorum|" +
            "yukseltiyorum|azaltiyorum|durduruyorum|duraklatiyorum|ariyorum|gosteriyorum|getiriyorum|donuyorum|" +
            "oynatiliyor|aciliyor)\\b"
    )

    data class Ihlal(val iddia: String, val neden: String)

    /** Metindeki ilk KESİN iddia ya da null. */
    fun kesinIddia(metin: String): String? = KESIN.find(sade(metin))?.value

    /** Metindeki ilk NİYET iddiası ya da null. */
    fun niyetIddiasi(metin: String): String? = NIYET.find(sade(metin))?.value

    /**
     * Bir turun değerlendirmesi: [asistanMetni] Jülide'nin o turda söyledikleri, [araclar] o turda çalışan araçlar.
     * İhlal yoksa null.
     */
    fun degerlendir(asistanMetni: String, araclar: List<AracKaydi>): Ihlal? {
        if (asistanMetni.isBlank()) return null
        val eylemler = araclar.filter { it.ad !in OKUMA_ARACLARI }
        val basariliEylem = eylemler.any { it.basarili }
        val bilgiCalisti = araclar.any { it.ad in BILGI_ARACLARI && it.basarili }

        kesinIddia(asistanMetni)?.let { iddia ->
            if (basariliEylem) return null
            if (eylemler.isEmpty() && bilgiCalisti) return null // bilgi cevabı ("... açıldı" bir olayı anlatıyor)
            val neden = if (eylemler.isEmpty()) "bu işi yapan hiçbir araç çağrılmadı"
            else "çağrılan araç(lar) BAŞARISIZ oldu: " + eylemler.joinToString("; ") { "${it.ad}: ${(it.hata ?: "hata").take(160)}" }
            return Ihlal(iddia, neden)
        }
        niyetIddiasi(asistanMetni)?.let { iddia ->
            if (eylemler.isEmpty() && !bilgiCalisti) return Ihlal(iddia, "işi yapacağını söyledin ama hiçbir araç çağırmadın")
        }
        return null
    }

    /** Modele gönderilecek düzeltme mesajı (kullanıcıya değil, Jülide'ye). */
    fun duzeltmeMesaji(i: Ihlal): String =
        "[SİSTEM DENETİMİ — bu mesaj kullanıcıdan DEĞİL, Jülide'nin denetim sisteminden] " +
            "Az önce kullanıcıya \"${i.iddia}\" anlamına gelen bir şey söyledin, ama ${i.neden}. Görev TAMAMLANMADI. " +
            "Hemen kullanıcıya kısa ve dürüst düzelt (ör. \"Özür dilerim, aslında yapamadım.\"). " +
            "Sonra uygun aracı çağırarak BİR KEZ daha dene; araç doğrulanmış sonuç dönmezse yalnızca \"yapamadım\" de ve nedenini kısaca söyle. " +
            "Bu mesajdan kullanıcıya söz etme."

    /** Türkçe harfleri sadeleştirir, küçük harfe çevirir (Uygulamalar.normal'den bağımsız; test edilebilir). */
    fun sade(t: String): String {
        val b = StringBuilder(t.length)
        for (c in t.lowercase(java.util.Locale("tr"))) b.append(
            when (c) {
                'ç' -> 'c'; 'ğ' -> 'g'; 'ı' -> 'i'; 'ö' -> 'o'; 'ş' -> 's'; 'ü' -> 'u'; 'â' -> 'a'; 'î' -> 'i'; 'û' -> 'u'
                '\'', '’' -> ' '
                else -> if (c.isLetterOrDigit()) c else ' '
            }
        )
        return b.toString().replace(Regex(" +"), " ").trim()
    }
}
