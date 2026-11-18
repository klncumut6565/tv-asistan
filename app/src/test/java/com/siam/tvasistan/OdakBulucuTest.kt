package com.siam.tvasistan

import com.siam.tvasistan.OdakBulucu.Durum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdakBulucuTest {
    private val g = 160
    private val y = 90

    /** Koyu arka plan üstünde 5 kart; [odak] numaralı kart beyaz çerçeveli ve biraz daha aydınlık. */
    private fun ekran(odak: Int): IntArray {
        val iz = IntArray(g * y) { 30 }
        for (k in 0 until 5) {
            val x0 = 8 + k * 30; val y0 = 40; val x1 = x0 + 24; val y1 = y0 + 30
            for (yy in y0..y1) for (xx in x0..x1) {
                val kenar = xx == x0 || xx == x1 || yy == y0 || yy == y1
                iz[yy * g + xx] = when {
                    k == odak && kenar -> 250          // beyaz çerçeve
                    k == odak -> 120                    // vurgulu kart
                    else -> 80
                }
            }
        }
        return iz
    }

    @Test fun sagaGecinceYeniOdakBulunur() {
        val s = OdakBulucu.bul(ekran(1), ekran(2), g, y)
        assertEquals(Durum.BULUNDU, s.durum)
        val k = s.kutu!!
        // 3. kart: x 68..92
        assertTrue("kutu ${k}", k.x0 in 64..70 && k.x1 in 90..96)
        assertTrue(s.guvenYuksek)
    }

    @Test fun ekranDegismezseDegismediDoner() {
        assertEquals(Durum.DEGISMEDI, OdakBulucu.bul(ekran(3), ekran(3), g, y).durum)
    }

    @Test fun sayfaTamamenDegisirseOdakBelirsiz() {
        val once = ekran(0)
        val sonra = IntArray(g * y) { 200 }
        assertEquals(Durum.SAYFA_DEGISTI, OdakBulucu.bul(once, sonra, g, y).durum)
    }

    @Test fun buyukFragmanBolgesiOdakSayilmaz() {
        val once = ekran(1)
        val sonra = ekran(2)
        // Üstte oynayan fragman: ekranın ~%30'u aydınlanıyor (odak sayılmamalı)
        for (yy in 0 until 30) for (xx in 0 until 145) { once[yy * g + xx] = 40; sonra[yy * g + xx] = 140 }
        val s = OdakBulucu.bul(once, sonra, g, y)
        assertNotNull(s.kutu)
        assertTrue("kutu ${s.kutu}", s.kutu!!.y0 >= 38) // fragman değil, kart seçildi
    }
}
