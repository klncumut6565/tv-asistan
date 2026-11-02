package com.siam.tvasistan.arastirma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeBulucuTest {
    @Test fun kimlik_adresten_cikarilir() {
        assertEquals("dQw4w9WgXcQ", YouTubeBulucu.kimlik("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=x"))
        assertEquals("dQw4w9WgXcQ", YouTubeBulucu.kimlik("https://youtu.be/dQw4w9WgXcQ"))
        assertNull(YouTubeBulucu.kimlik("https://www.youtube.com/channel/abc"))
    }

    @Test fun sonuc_sayfasi_ayristirilir_canli_ve_reklam_atlanir() {
        val html = """var ytInitialData = {"contents":[
            {"adSlotRenderer":{"videoId":"REKLAM00000"}},
            {"videoRenderer":{"videoId":"AAAAAAAAAA1","thumbnail":{},"title":{"runs":[{"text":"Yıldız Tilbe - Delikanlım \u0026 Klip"}]},"ownerText":{"runs":[{"text":"Yıldız Tilbe"}]}}},
            {"videoRenderer":{"videoId":"CANLI000001","title":{"runs":[{"text":"Canlı yayın"}]},"badges":[{"style":"BADGE_STYLE_TYPE_LIVE_NOW"}]}},
            {"videoRenderer":{"videoId":"AAAAAAAAAA1","title":{"runs":[{"text":"Tekrar"}]}}},
            {"videoRenderer":{"videoId":"BBBBBBBBBB2","title":{"runs":[{"text":"Ahmet Kaya - Kendine İyi Bak"}]}}}
        ]}"""
        val l = YouTubeBulucu.sonucAyristir(html)
        assertEquals(listOf("AAAAAAAAAA1", "BBBBBBBBBB2"), l.map { it.id })
        assertEquals("Yıldız Tilbe - Delikanlım & Klip", l[0].baslik)
        assertEquals("Yıldız Tilbe", l[0].kanal)
    }

    @Test fun bos_sayfa_bos_liste() {
        assertTrue(YouTubeBulucu.sonucAyristir("<html></html>").isEmpty())
    }
}
