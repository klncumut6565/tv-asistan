package com.siam.tvasistan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KanalHafizasiTest {

    @Test fun anahtar() {
        assertEquals("show", KanalHafizasi.anahtar("Show TV"))
        assertEquals("show", KanalHafizasi.anahtar("show"))
        assertEquals("show", KanalHafizasi.anahtar("SHOW TV kanalı"))
        assertEquals("trt1", KanalHafizasi.anahtar("TRT 1"))
        assertEquals("trt1", KanalHafizasi.anahtar("trt bir"))
        assertEquals("trt1", KanalHafizasi.anahtar("TRT1"))
        assertEquals("atv", KanalHafizasi.anahtar("ATV"))
        assertEquals("kanald", KanalHafizasi.anahtar("Kanal D"))
    }

    @Test fun kayitCumlesi() {
        assertEquals("trt 1" to 1, KanalHafizasi.kayitCumlesi("Birinci kanalda TRT 1 var"))
        assertEquals("show tv" to 4, KanalHafizasi.kayitCumlesi("4. kanalda Show TV var"))
        assertEquals("show tv" to 4, KanalHafizasi.kayitCumlesi("Jülide dördüncü kanalda show tv var"))
        assertEquals("atv" to 5, KanalHafizasi.kayitCumlesi("ATV 5. kanalda"))
        assertEquals("trt 1" to 1, KanalHafizasi.kayitCumlesi("TRT 1 birinci kanalda"))
        assertEquals("kanal d" to 12, KanalHafizasi.kayitCumlesi("12. kanalda Kanal D var"))
    }

    @Test fun kayitDegil() {
        for (m in listOf("4. kanalda ne var", "birinci kanalı aç", "TRT 1 kanalda", "hangi kanalda maç var",
            "ATV'yi aç", "sonraki kanal", "şu an hangi kanaldayız"))
            assertNull(m, KanalHafizasi.kayitCumlesi(m))
    }

    @Test fun bul() {
        val h = mapOf("show tv" to "4", "trt bir" to "1")
        assertEquals("4", KanalHafizasi.bul(h, "Show"))
        assertEquals("4", KanalHafizasi.bul(h, "Show TV'yi"))
        assertEquals("1", KanalHafizasi.bul(h, "TRT 1"))
        assertNull(KanalHafizasi.bul(h, "ATV"))
    }

    @Test fun hizliYol() {
        assertEquals("kanal_kaydet", HizliKomut.coz("4. kanalda Show TV var")?.arac)
        assertEquals("kanal_listesi", HizliKomut.coz("kanal listesini göster")?.arac)
        assertEquals("kanal_listesi", HizliKomut.coz("kanalları göster")?.arac)
        assertEquals("kanal_ac", HizliKomut.coz("Show TV'yi aç")?.arac)
    }
}
