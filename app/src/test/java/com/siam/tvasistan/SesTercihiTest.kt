package com.siam.tvasistan

import org.junit.Assert.assertEquals
import org.junit.Test

class SesTercihiTest {
    private val uzak = Uzak.VARSAYILAN.copy(ses = "Kore", duyguUyumu = false)

    @Test fun telefonSecimiUzakAyardanOncelikli() {
        assertEquals("Sulafat", Uzak.tercihUygula(uzak, "Sulafat", -1).ses)
        assertEquals("Sulafat", Uzak.tercihUygula(uzak, " sulafat ", -1).ses)
    }

    @Test fun bosYaDaGecersizSecimUzakAyardaKalir() {
        assertEquals("Kore", Uzak.tercihUygula(uzak, "", -1).ses)
        assertEquals("Kore", Uzak.tercihUygula(uzak, "Achird", -1).ses) // erkek ses kabul edilmez
    }

    @Test fun duyguTercihi() {
        assertEquals(false, Uzak.tercihUygula(uzak, "", -1).duyguUyumu)
        assertEquals(true, Uzak.tercihUygula(uzak, "", 1).duyguUyumu)
        assertEquals(false, Uzak.tercihUygula(uzak.copy(duyguUyumu = true), "", 0).duyguUyumu)
    }
}
