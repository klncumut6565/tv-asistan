package com.siam.tvasistan

import org.junit.Assert.assertEquals
import org.junit.Test

class UzakSessizlikTest {
    @Test fun aralikDisiVarsayilanaDoner() {
        assertEquals(800, Uzak.SESSIZLIK_VARSAYILAN)
        assertEquals(1200, Uzak.sessizlikDogrula(1200))
        assertEquals(500, Uzak.sessizlikDogrula(500))
        assertEquals(2000, Uzak.sessizlikDogrula(2000))
        assertEquals(800, Uzak.sessizlikDogrula(100))
        assertEquals(800, Uzak.sessizlikDogrula(10_000))
        assertEquals(800, Uzak.sessizlikDogrula(-1))
    }
}
