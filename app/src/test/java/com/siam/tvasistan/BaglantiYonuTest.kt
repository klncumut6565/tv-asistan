package com.siam.tvasistan

import com.siam.tvasistan.BaglantiYonu.Kutu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tarayıcı bağlantı modu: yön tuşunun hangi bağlantıya gideceği. */
class BaglantiYonuTest {
    // Arama sonuçları sayfası benzeri: üstte arama kutusu, altında alt alta sonuç başlıkları, sağda bir düğme
    private val arama = Kutu(100, 40, 900, 90)        // 0
    private val sonuc1 = Kutu(100, 200, 700, 240)     // 1
    private val sonuc2 = Kutu(100, 380, 650, 420)     // 2
    private val sonuc3 = Kutu(100, 560, 720, 600)     // 3
    private val dugme = Kutu(1500, 200, 1700, 240)    // 4 (sonuc1 ile aynı satır, sağda)
    private val l = listOf(arama, sonuc1, sonuc2, sonuc3, dugme)

    @Test fun asagiBirSonrakiSatir() {
        assertEquals(1, BaglantiYonu.sonraki(l, arama, "asagi"))
        assertEquals(2, BaglantiYonu.sonraki(l, sonuc1, "asagi"))
        assertEquals(3, BaglantiYonu.sonraki(l, sonuc2, "asagi"))
        assertNull(BaglantiYonu.sonraki(l, sonuc3, "asagi"))
    }

    @Test fun yukariGeri() {
        assertEquals(2, BaglantiYonu.sonraki(l, sonuc3, "yukari"))
        assertEquals(0, BaglantiYonu.sonraki(l, sonuc1, "yukari"))
        assertNull(BaglantiYonu.sonraki(l, arama, "yukari"))
    }

    @Test fun sagVeSolAyniSatir() {
        assertEquals(4, BaglantiYonu.sonraki(l, sonuc1, "sag"))
        assertEquals(1, BaglantiYonu.sonraki(l, dugme, "sol"))
    }

    @Test fun ilkSecimOkumaSirasi() {
        assertEquals(0, BaglantiYonu.ilk(l, "asagi", 0, 1080))
        assertEquals(3, BaglantiYonu.ilk(l, "yukari", 0, 1080))
        // Üst sınır aramayı dışarıda bırakırsa ilk sonuç seçilir
        assertEquals(1, BaglantiYonu.ilk(l, "asagi", 150, 1080))
    }
}
