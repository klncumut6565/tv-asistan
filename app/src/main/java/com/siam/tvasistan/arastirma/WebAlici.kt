package com.siam.tvasistan.arastirma

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Araştırma HTTP istemcisi (yol haritası 5.3 / 7): Gemini Live'ın sonsuz zaman aşımlı istemcisinden AYRI.
 * Yalnız GET; https; en çok 4 yönlendirme; boyut sınırı; DNS sonrası özel IP engeli (SSRF).
 */
object WebAlici {
    private const val TARAYICI = "Mozilla/5.0 (Linux; Android 14; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private val guvenliDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val l = Dns.SYSTEM.lookup(hostname)
            if (l.any { Kaynak.ozelIp(it.address) }) throw IOException("Özel/yerel ağ adresi engellendi: $hostname")
            return l
        }
    }

    val http: OkHttpClient = OkHttpClient.Builder()
        .dns(guvenliDns)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .followRedirects(false) // yönlendirmeler elle izlenir ve her adım denetlenir
        .followSslRedirects(false)
        .build()

    class Yanit(val adres: String, val metin: String, val tur: String)

    fun getir(adres: String, sinir: Long = 700_000, httpIzin: Boolean = false): Yanit {
        var url = adres
        repeat(5) {
            if (!Kaynak.guvenliAdres(url) && !(httpIzin && url.startsWith("http://"))) throw IOException("Güvensiz adres: $url")
            val istek = Request.Builder().url(url).get()
                .header("User-Agent", TARAYICI)
                .header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.5")
                .header("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.5")
                .build()
            http.newCall(istek).execute().use { r ->
                if (r.isRedirect) {
                    val yer = r.header("Location") ?: throw IOException("Boş yönlendirme")
                    url = r.request.url.resolve(yer)?.toString() ?: throw IOException("Geçersiz yönlendirme")
                    return@repeat
                }
                if (r.code == 401 || r.code == 403 || r.code == 429) throw EngelHatasi("HTTP ${r.code}")
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val govde = r.body ?: throw IOException("Boş yanıt")
                val tur = govde.contentType()?.toString().orEmpty()
                if (tur.isNotEmpty() && listOf("html", "xml", "json", "text").none { tur.contains(it) }) throw IOException("Desteklenmeyen içerik: $tur")
                val kaynak = govde.source()
                kaynak.request(sinir)
                val boy = minOf(kaynak.buffer.size, sinir)
                return Yanit(url, kaynak.buffer.clone().readString(boy, govde.contentType()?.charset() ?: Charsets.UTF_8), tur)
            }
        }
        throw IOException("Çok fazla yönlendirme")
    }

    class EngelHatasi(m: String) : IOException(m)
}
