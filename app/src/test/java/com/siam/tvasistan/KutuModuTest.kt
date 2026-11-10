package com.siam.tvasistan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KutuModuTest {
    @Test fun tvplusTanınır() {
        for (k in listOf("TV+", "tv +", "TV Plus", "tvplus", "TV artı", "Turkcell TV+", "kutu", "TV kutusu")) assertTrue(k, KutuModu.tvplusMu(k))
        for (k in listOf("TV", "televizyon", "HDMI 2", "anten", "TV8", "PlayStation")) assertFalse(k, KutuModu.tvplusMu(k))
    }

    @Test fun hdmiAdi() {
        assertEquals("HDMI 2", KutuModu.hdmiAdi("HDMI 2"))
        assertEquals("HDMI 2", KutuModu.hdmiAdi("hdmi2"))
        assertEquals("HDMI 3", KutuModu.hdmiAdi("3"))
        assertNull(KutuModu.hdmiAdi("HDMI 7"))
        assertNull(KutuModu.hdmiAdi("yok"))
    }
}
