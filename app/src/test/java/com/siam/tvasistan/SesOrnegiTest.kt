package com.siam.tvasistan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class SesOrnegiTest {
    @Test fun ttsModeliSecilir() {
        val liste = """{"models":[
            {"name":"models/gemini-2.5-pro-preview-tts","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-2.5-flash-preview-tts","supportedGenerationMethods":["generateContent","countTokens"]},
            {"name":"models/gemini-2.5-flash-tts","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-live-tts","supportedGenerationMethods":["bidiGenerateContent"]}]}"""
        assertEquals("models/gemini-2.5-flash-tts", SesOrnegi.ttsModeliSec(liste))
        assertNull(SesOrnegi.ttsModeliSec("""{"models":[{"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]}]}"""))
    }

    @Test fun sesVerisiCozulur() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val cevap = """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/L16;codec=pcm;rate=24000","data":"${Base64.getEncoder().encodeToString(pcm)}"}}]}}]}"""
        assertArrayEquals(pcm, SesOrnegi.sesVerisi(cevap))
        assertNull(SesOrnegi.sesVerisi("""{"candidates":[{"content":{"parts":[{"text":"selam"}]}}]}"""))
    }

    @Test fun herSesinTarifiVar() {
        for (s in Uzak.SESLER) assertEquals(s, true, SesOrnegi.TARIF.containsKey(s))
    }
}
