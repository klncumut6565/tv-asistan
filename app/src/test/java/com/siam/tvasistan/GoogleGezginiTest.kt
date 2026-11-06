package com.siam.tvasistan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleGezginiTest {
    @Test fun citeAdresi() {
        assertEquals("https://tr.wikipedia.org/wiki/Kemal_Sunal", GoogleGezgini.citedenAdres("https://tr.wikipedia.org › wiki › Kemal_Sunal"))
        assertEquals("https://www.imdb.com", GoogleGezgini.citedenAdres("www.imdb.com"))
        assertNull(GoogleGezgini.citedenAdres("https://www.sozcu.com.tr › 2026 › gundem › ..."))
        assertNull(GoogleGezgini.citedenAdres("Kemal Sunal - Vikipedi"))
    }
}
