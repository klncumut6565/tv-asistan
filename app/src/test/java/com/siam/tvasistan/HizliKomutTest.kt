package com.siam.tvasistan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HizliKomutTest {

    private fun k(m: String) = HizliKomut.coz(m)?.let { c ->
        c.arac + " " + c.args.keys().asSequence().sorted().joinToString(",") { "$it=${c.args.get(it)}" }
    }

    @Test fun kanalAdlari() {
        assertEquals("kanal_ac kanal=ATV", k("ATV'yi aç"))
        assertEquals("kanal_ac kanal=ATV", k("Jülide ATV'yi açar mısın"))
        assertEquals("kanal_ac kanal=Show TV", k("Show TV'ye geç"))
        assertEquals("kanal_ac kanal=TRT 1", k("TRT 1'i aç."))
        assertEquals("kanal_ac kanal=TRT 1", k("hey jülide trt bir'i aç lütfen"))
        assertEquals("kanal_ac kanal=Kanal D", k("Kanal D'yi aç"))
        assertEquals("kanal_ac kanal=Star", k("Star'ı aç"))
        assertEquals("kanal_ac kanal=TRT Haber", k("TRT Haber kanalını aç"))
        assertEquals("kanal_ac kanal=7", k("7. kanalı aç"))
    }

    @Test fun hdmi() {
        assertEquals("kaynak_degistir kaynak=HDMI 1", k("HDMI 1'e geç"))
        assertEquals("kaynak_degistir kaynak=HDMI 2", k("HDMI ikiye geç"))
        assertEquals("kaynak_degistir kaynak=HDMI 3", k("hdmi 3 girişine geç"))
    }

    @Test fun kanalDegistir() {
        assertEquals("kanal_degistir adet=1,yon=sonraki", k("Sonraki kanal"))
        assertEquals("kanal_degistir adet=1,yon=sonraki", k("bir sonraki kanala geç"))
        assertEquals("kanal_degistir adet=1,yon=onceki", k("önceki kanala dön"))
        assertEquals("kanal_degistir adet=1,yon=onceki", k("kanalı aşağı"))
    }

    @Test fun sesVeMedya() {
        assertEquals("ses islem=azalt,seviye=2", k("Sesi kıs"))
        assertEquals("ses islem=artir,seviye=2", k("sesi biraz artır"))
        assertEquals("ses islem=sessiz", k("sessize al"))
        assertEquals("medya islem=duraklat", k("Videoyu durdur"))
        assertEquals("medya islem=oynat", k("oynat"))
        assertEquals("medya islem=ileri_sar", k("biraz ileri sar"))
    }

    /** Bunlar Gemini'ye kalmalı: olumsuz, soru, belirsiz ya da uygulama adı. */
    @Test fun eslesmemeli() {
        for (m in listOf(
            "ATV'yi açma", "ATV açık mı", "ATV'de ne var", "YouTube'u aç", "Netflix'i aç",
            "sesi aç", "devam et", "dur", "kapat", "televizyonu kapat", "sayfayı kapat",
            "sonraki kanalda ne var", "TRT 1'de akşam ne var", "hdmi", "bugün hangi maçlar var",
            "Kemal Sunal filmi aç", "sesi biraz açar mısın", "",
        )) assertNull(m, k(m))
    }
}
