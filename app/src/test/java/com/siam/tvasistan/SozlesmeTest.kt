package com.siam.tvasistan

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SÖZLEŞME TESTİ (Skill Doctor'daki "yapısal tarama" fikri). Kaynak kodu METİN olarak okur, hiçbir şey çalıştırmaz.
 *
 * Yakalanan hata türü: bir tarafta var olan bir şeyin öbür tarafta sessizce kaybolması. Örnek: v0.1.96'ya dönüşte
 * telefondaki "TV'yi güncelle" ve "Jülide'nin sesi" düğmeleri TV'de karşılıksız kaldı, kimse fark etmedi.
 *
 *  1. Gemini'ye tanımlanan her araç (bildirimler) Araclar.calistir'de karşılanıyor, karşılanan her araç tanımlı.
 *  2. Sistem talimatında adı geçen her araç gerçekten tanımlı.
 *  3. Hızlı yolun ürettiği ve tekrar korumasında muaf tutulan araç adları tanımlı.
 *  4. Telefon sayfasının gönderdiği her olay TV'de karşılanıyor; TV'nin gönderdiği her olay telefonda dinleniyor.
 */
class SozlesmeTest {

    private fun dosya(yol: String): String {
        val adaylar = listOf(File(yol), File("app/$yol"), File("../$yol"))
        return (adaylar.firstOrNull { it.exists() } ?: error("Bulunamadı: $yol")).readText()
    }

    private fun kaynak(ad: String) = dosya("src/main/java/com/siam/tvasistan/$ad")

    private fun blok(s: String, bas: String, son: String): String {
        val i = s.indexOf(bas).also { assertTrue("'$bas' bulunamadı", it >= 0) }
        val j = s.indexOf(son, i + bas.length).also { assertTrue("'$son' bulunamadı", it >= 0) }
        return s.substring(i, j)
    }

    private fun bul(r: String, s: String, m: Set<RegexOption> = emptySet()) =
        Regex(r, m).findAll(s).map { it.groupValues[1] }.toSet()

    private val araclar by lazy { kaynak("Araclar.kt") }

    /** Gemini'ye tanımlanan araçlar. */
    private val tanimli by lazy { bul("""fn\("([a-z_]+)"""", blok(araclar, "fun bildirimler", "fun sistemTalimati")) }

    private fun farkYok(neyi: String, eksik: Set<String>) =
        assertTrue("$neyi: ${eksik.sorted()}", eksik.isEmpty())

    @Test fun araclarTanimVeDagitimEslesir() {
        val bas = araclar.indexOf("    fun calistir(")
        assertTrue(bas >= 0)
        val sonra = araclar.substring(bas + 10)
        // calistir iki parçalı (kısa sarmalayıcı + asıl dağıtım): ikisini de kapsa, sonraki BAŞKA fonksiyonda dur
        val son = Regex("""\n {4}(private )?fun (?!calistir)""").find(sonra)?.range?.first ?: sonra.length
        val dagitim = bul("""^\s+"([a-z_]+)"(?:, "[a-z_]+")* ->""", sonra.substring(0, son), setOf(RegexOption.MULTILINE))

        assertTrue("Araç listesi okunamadı (${tanimli.size})", tanimli.size >= 40)
        farkYok("Gemini'ye tanımlı ama calistir'de KARŞILIĞI YOK (model çağırınca 'bilinmeyen araç' olur)", tanimli - dagitim)
        farkYok("calistir'de var ama Gemini'ye TANIMLI DEĞİL (model hiç çağıramaz)", dagitim - tanimli)
    }

    @Test fun talimattakiAraclarVar() {
        val talimat = blok(araclar, "fun sistemTalimati", "fun calistir(")
        val anilan = bul("""\b([a-z]+(?:_[a-z]+)+)\(""", talimat) + bul("""→ ([a-z]+(?:_[a-z]+)+)""", talimat)
        assertTrue("Talimatta araç adı okunamadı (${anilan.size})", anilan.size >= 10)
        farkYok("Sistem talimatı OLMAYAN araçları anıyor", anilan - tanimli)
    }

    @Test fun hizliYolVeKorumaAdlariVar() {
        val hizli = bul("""Komut\("([a-z_]+)"""", kaynak("HizliKomut.kt"))
        assertTrue(hizli.isNotEmpty())
        farkYok("Hızlı yol OLMAYAN aracı çağırıyor", hizli - tanimli)
        farkYok("Tekrar korumasında muaf tutulan ad araç değil (eski ad?)", AracKorumasi.MUAF - tanimli)
    }

    @Test fun telefonVeTvMesajlariEslesir() {
        val web = dosya("web/index.html")
        val telefonGonderir = bul("""tvyeGonder\("([a-z_]+)"""", web) +
            bul("""\.send\(\{ type: "broadcast", event: "([a-z_]+)"""", web)
        var telefonDinler = bul("""\.on\("broadcast", \{ event: "([a-z_]+)"""", web)
        Regex("""\[((?:"[a-z_]+",?\s*)+)]\.forEach\(\(e\) => \w+\.on\("broadcast"""").findAll(web).forEach { m ->
            telefonDinler = telefonDinler + bul(""""([a-z_]+)"""", m.groupValues[1])
        }

        val kopru = kaynak("TelefonKoprusu.kt")
        val servis = kaynak("AsistanServisi.kt")
        val tvKarsilar = bul(""""([a-z_]+)" ->""", blok(kopru, "override fun onMessage", "\"phx_reply\"")) +
            bul(""""([a-z_]+)" ->""", blok(servis, "olayGeldi = { olay, yuk ->", "ekranAcik ="))
        val tvGonderir = bul("""olayGonder\("([a-z_]+)"""", kopru + servis) +
            bul("""put\("event", "([a-z_]+)"\)""", kopru) - setOf("heartbeat", "phx_join", "broadcast")

        assertTrue("Telefon olayları okunamadı (${telefonGonderir.size})", telefonGonderir.size >= 6)
        assertTrue("TV olayları okunamadı (${tvGonderir.size})", tvGonderir.size >= 4)
        farkYok("Telefon gönderiyor ama TV KARŞILAMIYOR (düğme sessizce çalışmaz)", telefonGonderir - tvKarsilar)
        farkYok("TV gönderiyor ama telefon DİNLEMİYOR", tvGonderir - telefonDinler)
    }
}
