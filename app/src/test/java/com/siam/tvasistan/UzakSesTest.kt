package com.siam.tvasistan

import org.junit.Assert.assertEquals
import org.junit.Test

class UzakSesTest {
    @Test fun gecerli_ses_kabul_edilir() {
        assertEquals("Sulafat", Uzak.sesDogrula("Sulafat"))
        assertEquals("Sulafat", Uzak.sesDogrula(" sulafat "))
        assertEquals("Kore", Uzak.sesDogrula("Kore"))
    }

    @Test fun gecersiz_ses_kore_olur() {
        assertEquals("Kore", Uzak.sesDogrula("Quip"))
        assertEquals("Kore", Uzak.sesDogrula(""))
        assertEquals("Kore", Uzak.sesDogrula(null))
        assertEquals("Kore", Uzak.sesDogrula("Achird"))   // erkek ses kabul edilmez
        assertEquals("Kore", Uzak.sesDogrula("Puck"))
        assertEquals(14, Uzak.SESLER.size)
    }
}
