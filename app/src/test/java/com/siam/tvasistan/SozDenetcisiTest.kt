package com.siam.tvasistan

import com.siam.tvasistan.SozDenetcisi.AracKaydi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SozDenetcisiTest {

    private fun ok(ad: String) = AracKaydi(ad, true)
    private fun hata(ad: String, m: String = "açılamadı") = AracKaydi(ad, false, m)

    @Test fun araçHiçÇağrılmadanAçtımDemekİhlaldir() {
        val i = SozDenetcisi.degerlendir("Tamam, tarayıcıyı açtım.", emptyList())
        assertNotNull(i)
        assertEquals("actim", i!!.iddia)
    }

    @Test fun araçHataDönünceAçtımDemekİhlaldir() {
        val i = SozDenetcisi.degerlendir("Tarayıcıyı açtım.", listOf(hata("uygulama_ac", "başlatıldı ama ekrana gelmedi")))
        assertNotNull(i)
        assertTrue(i!!.neden.contains("uygulama_ac"))
    }

    @Test fun başarılıAraçlaAçtımDemekDoğrudur() {
        assertNull(SozDenetcisi.degerlendir("Tarayıcıyı açtım.", listOf(ok("uygulama_ac"))))
        assertNull(SozDenetcisi.degerlendir("ATV açıldı.", listOf(hata("uygulama_ac"), ok("kanal_ac"))))
    }

    @Test fun yalnızEkranOkumakİşiYapmakSayılmaz() {
        assertNotNull(SozDenetcisi.degerlendir("Filmi açtım.", listOf(ok("ekrani_oku"), ok("ekrana_bak"))))
    }

    @Test fun olumsuzCümlelerİddiaDeğildir() {
        assertNull(SozDenetcisi.degerlendir("Özür dilerim, tarayıcıyı açamadım.", listOf(hata("uygulama_ac"))))
        assertNull(SozDenetcisi.degerlendir("Sayfa açılmadı, başka yol deneyeyim mi?", emptyList()))
        assertNull(SozDenetcisi.degerlendir("Kapatayım mı?", emptyList()))
    }

    @Test fun bilgiCevabındakiOlayAnlatımıDenetlenmez() {
        // internette_arastir sonucu: "Mağaza 1990'da açıldı" bir bilgi, eylem iddiası değil
        assertNull(SozDenetcisi.degerlendir("Mağaza 1990 yılında açıldı.", listOf(ok("internette_arastir"))))
    }

    @Test fun niyetSöyleyipAraçÇağırmamakİhlaldir() {
        assertNotNull(SozDenetcisi.degerlendir("Hemen açıyorum.", emptyList()))
        assertNull(SozDenetcisi.degerlendir("Hemen açıyorum.", listOf(hata("uygulama_ac"))))
        assertNull(SozDenetcisi.degerlendir("YouTube'da arıyorum.", listOf(ok("akilli_ara"))))
    }

    @Test fun farklıFiillerTanınır() {
        listOf("Kanalı değiştirdim", "Sesi kıstım", "Oynat'a tıkladım", "HDMI 1'e geçtim", "Şarkıyı çaldım",
            "Uygulamayı kapattım", "Ana ekrana döndüm", "Zamanlayıcıyı kurdum").forEach {
            assertNotNull(it, SozDenetcisi.kesinIddia(it))
        }
        listOf("Anladım", "Baktım ama bulamadım", "Geçtiğimiz hafta", "Bugün hava güzel").forEach {
            assertNull(it, SozDenetcisi.kesinIddia(it))
        }
    }

    @Test fun boşMetinDenetlenmez() {
        assertNull(SozDenetcisi.degerlendir("  ", emptyList()))
    }
}
