package com.siam.tvasistan

import com.siam.tvasistan.DinlemeKapisi.Yol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DinlemeKapisiTest {
    private fun yol(medya: Boolean, odak: Boolean, kalabalik: Boolean, pencereAcik: Boolean, surekli: Boolean = true) =
        DinlemeKapisi.yol(surekli, 10_000, if (pencereAcik) 12_000 else 5_000, medya, odak, kalabalik)

    /** Kalabalık modu KAPALIYKEN eski davranışın birebir aynısı. */
    @Test fun normalModEskiDavranis() {
        assertEquals(Yol.GEMINI, yol(medya = false, odak = false, kalabalik = false, pencereAcik = false))
        assertEquals(Yol.YEREL, yol(medya = true, odak = false, kalabalik = false, pencereAcik = false))
        assertEquals(Yol.GEMINI, yol(medya = true, odak = true, kalabalik = false, pencereAcik = false))
        assertEquals(Yol.GEMINI, yol(medya = true, odak = false, kalabalik = false, pencereAcik = true))
        assertEquals(Yol.GEMINI, yol(medya = true, odak = false, kalabalik = false, pencereAcik = false, surekli = false))
    }

    @Test fun kalabalikModu() {
        assertEquals(Yol.YEREL, yol(medya = false, odak = false, kalabalik = true, pencereAcik = false))
        assertEquals(Yol.YEREL, yol(medya = true, odak = true, kalabalik = true, pencereAcik = false))
        assertEquals(Yol.GEMINI, yol(medya = false, odak = false, kalabalik = true, pencereAcik = true))
        assertEquals(Yol.GEMINI, yol(medya = true, odak = false, kalabalik = true, pencereAcik = true))
    }

    @Test fun telefon() {
        assertTrue(DinlemeKapisi.telefonPencereAcsin(false, 10_000, 0))
        assertFalse(DinlemeKapisi.telefonPencereAcsin(true, 10_000, 5_000))
        assertTrue(DinlemeKapisi.telefonPencereAcsin(true, 10_000, 12_000))
    }
}
