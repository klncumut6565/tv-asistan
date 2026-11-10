package com.siam.tvasistan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KutuKisayolTest {
    @Test fun diziCozulur() {
        val (l, h) = KutuKisayol.coz("geri*3, sol x5, yukarı*2, sağ, aşağı, tamam")
        assertNull(h)
        assertEquals(listOf("geri", "geri", "geri", "sol", "sol", "sol", "sol", "sol", "yukari", "yukari", "sag", "asagi", "tamam"), l)
        assertEquals("geri*3, sol*5, yukari*2, sag, asagi, tamam", KutuKisayol.metin(l!!))
    }

    @Test fun gucTusuVeBilinmeyenReddedilir() {
        assertNotNull(KutuKisayol.coz("guc").second)
        assertNotNull(KutuKisayol.coz("sol, ziplat").second)
        assertNotNull(KutuKisayol.coz("sol*50").second)
        assertNotNull(KutuKisayol.coz("").second)
    }

    @Test fun kodlar() {
        assertEquals(listOf(21, 23, 12), KutuKisayol.kodlar(listOf("sol", "tamam", "5")))
    }

    @Test fun adEslesir() {
        val k = mapOf("youtube" to "sag, tamam", "tv arti canli" to "tamam")
        assertEquals("youtube", KutuKisayol.bul(k, "YouTube'u"))
        assertEquals("youtube", KutuKisayol.bul(k, "YouTube uygulaması"))
        assertNull(KutuKisayol.bul(k, "Netflix"))
    }
}
