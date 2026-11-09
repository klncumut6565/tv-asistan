package com.siam.tvasistan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BellekTest {

    @Test fun ekleKaydetYukle() {
        val b = Bellek(null)
        assertEquals(Bellek.Sonuc.Eklendi, b.ekle("Umut maçları TRT Spor'dan izler", "tercih", 1))
        val b2 = Bellek(b.json())
        assertEquals(1, b2.liste.size)
        assertEquals("tercih", b2.liste[0].kategori)
    }

    @Test fun kopyaTazelenir() {
        val b = Bellek(null)
        b.ekle("Akşamları haber izlerim", "rutin", 1)
        assertEquals(Bellek.Sonuc.Tazelendi, b.ekle("akşamları  haber izlerim.", "rutin", 2))
        assertEquals(1, b.liste.size)
        assertEquals(2L, b.liste[0].zaman)
    }

    @Test fun hassasReddedilir() {
        val b = Bellek(null)
        for (m in listOf(
            "Wi-Fi şifrem kedi123", "API anahtarım AIzaSyA1234567890abcdefghijklmnop",
            "kartım 4543 1234 5678 9012", "TC kimlik numaram 12345678901", "IBAN TR12 0006 1000 0000 1234 5678 90",
            "şeker hastalığım var", "her sabah ilaç içiyorum",
        )) assertTrue(m, b.ekle(m, "diger", 1) is Bellek.Sonuc.Reddedildi)
        assertEquals(0, b.liste.size)
    }

    @Test fun normalBilgiHassasDegil() {
        assertNull(Bellek.hassasMi("Annemin adı Ayşe, pazar günleri gelir"))
        assertNull(Bellek.hassasMi("Telefonum 0532 123 45 67"))
        assertNotNull(Bellek.hassasMi("parolam 1234"))
    }

    @Test fun sinirAsilincaEnEskiSilinir() {
        val b = Bellek(null)
        for (i in 1..Bellek.EN_COK + 5) b.ekle("bilgi numarası $i", "diger", i.toLong())
        assertEquals(Bellek.EN_COK, b.liste.size)
        assertTrue(b.liste.none { it.metin == "bilgi numarası 1" })
    }

    @Test fun unut() {
        val b = Bellek(null)
        b.ekle("Favori dizim Kuruluş Osman", "tercih", 1)
        b.ekle("Akşam 9'da haber izlerim", "rutin", 2)
        assertEquals(listOf("Favori dizim Kuruluş Osman"), b.unut("kuruluş"))
        assertEquals(1, b.liste.size)
        assertEquals(1, b.unut("hepsi").size)
        assertEquals(0, b.liste.size)
    }

    @Test fun bozukJsonBosBellek() {
        assertEquals(0, Bellek("{bozuk").liste.size)
        assertEquals("", Bellek("[]").talimatBolumu())
    }

    @Test fun talimatVeriEtiketli() {
        val b = Bellek(null)
        b.ekle("Umut erken yatar", "rutin", 1)
        val t = b.talimatBolumu()
        assertTrue(t.contains("VERİDİR"))
        assertTrue(t.contains("[rutin] Umut erken yatar"))
    }
}
