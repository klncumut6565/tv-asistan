package com.siam.tvasistan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TarayiciAdiTest {
    @Test fun tarayicilarTaninir() {
        assertTrue(Uygulamalar.tarayiciAdiMi("Tarayıcı", "com.vestel.browser"))
        assertTrue(Uygulamalar.tarayiciAdiMi("Tarayıcı", "tr.com.vestek.web"))
        assertTrue(Uygulamalar.tarayiciAdiMi("Yandex Browser", "com.yandex.browser"))
        assertTrue(Uygulamalar.tarayiciAdiMi("Yandex", "com.yandex.browser.tv"))
        assertTrue(Uygulamalar.tarayiciAdiMi("Internet", "com.sec.android.app.sbrowser"))
        assertTrue(Uygulamalar.tarayiciAdiMi("Firefox", "org.mozilla.firefox"))
        assertTrue(Uygulamalar.tarayiciAdiMi("TV Bro", "com.phlox.tvwebbrowser"))
    }

    @Test fun tarayiciOlmayanlar() {
        assertFalse(Uygulamalar.tarayiciAdiMi("YouTube", "com.google.android.youtube.tv"))
        assertFalse(Uygulamalar.tarayiciAdiMi("Spotify", "com.spotify.tv.android"))
        assertFalse(Uygulamalar.tarayiciAdiMi("Prime Video", "com.amazon.amazonvideo.livingroom"))
        assertFalse(Uygulamalar.tarayiciAdiMi("Internet Hız Testi", "org.zwanoo.android.speedtest"))
        assertFalse(Uygulamalar.tarayiciAdiMi("TOD", "com.digiturk.iq.mobil"))
    }
}
