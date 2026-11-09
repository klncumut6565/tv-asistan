package com.siam.tvasistan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AracKorumasiTest {

    private fun j(vararg p: Pair<String, Any>) = JSONObject().apply { p.forEach { put(it.first, it.second) } }

    @Test fun ucuncuAyniCagriEngellenir() {
        val k = AracKorumasi()
        val a = j("yon" to "sonraki", "adet" to 1)
        assertNull(k.denetle("kanal_degistir", a))
        assertNull(k.denetle("kanal_degistir", a))
        assertNotNull(k.denetle("kanal_degistir", a))
    }

    @Test fun anahtarSirasiVeBuyukHarfOnemsiz() {
        val k = AracKorumasi()
        assertNull(k.denetle("ses", j("islem" to "artir", "seviye" to 2)))
        assertNull(k.denetle("ses", j("seviye" to 2, "islem" to "ARTIR")))
        assertNotNull(k.denetle("ses", j("islem" to " artir ", "seviye" to 2)))
    }

    @Test fun farkliArgumanSerbest() {
        val k = AracKorumasi()
        for (i in 1..5) assertNull(k.denetle("kanal_ac", j("kanal" to "$i")))
    }

    @Test fun muafAraclarSinirsiz() {
        val k = AracKorumasi()
        repeat(8) { assertNull(k.denetle("tus", j("tus" to "asagi"))) }
        repeat(5) { assertNull(k.denetle("tv_kapat", j())) }
    }

    @Test fun sifirlaninca_yeniTur() {
        val k = AracKorumasi()
        val a = j("uygulama" to "YouTube")
        k.denetle("uygulama_ac", a); k.denetle("uygulama_ac", a)
        assertNotNull(k.denetle("uygulama_ac", a))
        k.sifirla()
        assertNull(k.denetle("uygulama_ac", a))
    }

    @Test fun normalBicim() {
        assertEquals("{a=1,b=x}", AracKorumasi.normal(j("b" to "X", "a" to 1)))
    }
}
