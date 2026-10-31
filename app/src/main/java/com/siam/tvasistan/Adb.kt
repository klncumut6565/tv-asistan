package com.siam.tvasistan

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * TV'nin kendi ağ hata ayıklamasına (127.0.0.1:5555) bağlanıp KUMANDA TUŞU gönderen küçük ADB istemcisi.
 *
 * Neden: erişilebilirlik servisi yalnız yön/geri/ana ekran gibi genel eylemleri yapabilir; KANAL+/KANAL−
 * ve rakam tuşlarını gönderemez. Bu TV'de (MediaTek canlı TV) kanallar Android TV kanal sağlayıcısında da
 * yok (content://android.media.tv/channel boş), bu yüzden kanal değiştirmenin tek güvenilir yolu gerçek
 * tuş basmaktır: "input keyevent".
 *
 * GÜVENLİK: dışarıya yalnız [tuslar] ve [dokun] açıktır; yalnız tamsayılardan "input keyevent ..." /
 * "input tap X Y" komutu kurar; serbest kabuk komutu çalıştırılamaz. İlk bağlantıda TV ekranında "Hata ayıklamaya izin verilsin mi?"
 * sorusu çıkar; "Her zaman izin ver" işaretlenip onaylanınca bir daha sorulmaz. Anahtar yalnız bu TV'de,
 * uygulamanın özel klasöründe saklanır.
 *
 * Protokol: ADB v1 (CNXN → AUTH imza / açık anahtar → OPEN "shell:" → WRTE/OKAY → CLSE).
 */
class Adb(private val ctx: Context) {
    companion object {
        private const val ETIKET = "TvAsistan"
        private const val CNXN = 0x4e584e43
        private const val AUTH = 0x48545541
        private const val OPEN = 0x4e45504f
        private const val OKAY = 0x59414b4f
        private const val CLSE = 0x45534c43
        private const val WRTE = 0x45545257
        private const val SURUM = 0x01000001
        private const val EN_COK_VERI = 256 * 1024

        /** PKCS#1 v1.5 imza dolgusu + SHA-1 DigestInfo öneki (2048 bit anahtar, 20 baytlık jeton için). */
        private val SHA1_ONEK = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
        )

        const val KANAL_YUKARI = 166
        const val KANAL_ASAGI = 167

        /** "7" → KEYCODE_7 (14). */
        fun rakamTusu(c: Char): Int = 7 + (c - '0')
    }

    class Hata(mesaj: String) : Exception(mesaj)

    @Volatile var onayBekleniyor = false
        private set

    private val kilit = Any()

    /**
     * Tuşları sırayla basar (ör. [166] = kanal yukarı; [14, 9] = "72"). Hata olursa [Hata] fırlatır;
     * mesajı kullanıcıya söylenebilir Türkçedir.
     */
    fun tuslar(kodlar: List<Int>) {
        require(kodlar.isNotEmpty() && kodlar.size <= 12 && kodlar.all { it in 1..400 })
        komut("input keyevent " + kodlar.joinToString(" "))
    }

    /**
     * GERÇEK dokunma (input tap): dokunmatik ekranı olmayan TV'de de çalışır; tarayıcılar bunu gerçek tıklama
     * olarak alır. GÜVENLİK: yalnız tamsayı koordinat kabul edilir, serbest komut kurulamaz.
     */
    fun dokun(x: Int, y: Int, cift: Boolean = false) {
        require(x in 0..8000 && y in 0..8000)
        val t = "input tap $x $y"
        komut(if (cift) "$t; sleep 0.12; $t" else t)
    }

    /** Bağlantıyı dener (ayar ekranındaki "etkinleştir" düğmesi). */
    fun dene() = komut("echo ok")

    private fun komut(k: String): String = synchronized(kilit) {
        val soket = Socket()
        try {
            try {
                soket.connect(InetSocketAddress("127.0.0.1", 5555), 1500)
            } catch (e: Exception) {
                throw Hata(
                    "TV'nin ağ hata ayıklaması kapalı. Ayarlar → Sistem → Geliştirici seçenekleri → " +
                        "'Ağ üzerinden hata ayıklama' (ADB) açık olmalı."
                )
            }
            soket.soTimeout = 4000
            val giris = DataInputStream(soket.getInputStream())
            val cikis = soket.getOutputStream()
            baglan(soket, giris, cikis)
            calistir(giris, cikis, k)
        } catch (e: Hata) {
            throw e
        } catch (e: Exception) {
            Log.w(ETIKET, "ADB: ${e.javaClass.simpleName}: ${e.message}")
            throw Hata(if (onayBekleniyor) "TV'deki hata ayıklama onayı verilmedi." else "TV'ye ADB ile bağlanılamadı (${e.javaClass.simpleName}).")
        } finally {
            onayBekleniyor = false
            try { soket.close() } catch (_: Exception) {}
        }
    }

    // ---------------------------------------------------------------- protokol

    private class Mesaj(val komut: Int, val a0: Int, val a1: Int, val veri: ByteArray)

    private fun yaz(o: OutputStream, komut: Int, a0: Int, a1: Int, veri: ByteArray = ByteArray(0)) {
        var top = 0
        for (b in veri) top += (b.toInt() and 0xff)
        val bb = ByteBuffer.allocate(24 + veri.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(komut).putInt(a0).putInt(a1).putInt(veri.size).putInt(top).putInt(komut.inv())
        bb.put(veri)
        o.write(bb.array())
        o.flush()
    }

    private fun oku(g: DataInputStream): Mesaj {
        val bas = ByteArray(24)
        g.readFully(bas)
        val bb = ByteBuffer.wrap(bas).order(ByteOrder.LITTLE_ENDIAN)
        val komut = bb.int
        val a0 = bb.int
        val a1 = bb.int
        val uz = bb.int
        if (uz < 0 || uz > EN_COK_VERI * 4) throw Hata("ADB: geçersiz yanıt.")
        val veri = ByteArray(uz)
        if (uz > 0) g.readFully(veri)
        return Mesaj(komut, a0, a1, veri)
    }

    private fun baglan(soket: Socket, g: DataInputStream, o: OutputStream) {
        yaz(o, CNXN, SURUM, EN_COK_VERI, "host::julide\u0000".toByteArray())
        val anahtar = anahtar()
        var imzaGonderildi = false
        while (true) {
            val m = try { oku(g) } catch (e: java.net.SocketTimeoutException) {
                if (onayBekleniyor) throw Hata(
                    "TV ekranında 'Hata ayıklamaya izin verilsin mi?' sorusu çıktı. 'Bu bilgisayardan her zaman izin ver' " +
                        "kutusunu işaretleyip İZİN VER'e basın, sonra komutu tekrarlayın."
                )
                throw Hata("ADB yanıt vermedi.")
            }
            when (m.komut) {
                CNXN -> return
                AUTH -> {
                    if (m.a0 != 1) continue // yalnız jeton (TOKEN) beklenir
                    if (!imzaGonderildi) {
                        imzaGonderildi = true
                        yaz(o, AUTH, 2, 0, imzala(anahtar, m.veri))
                    } else {
                        // İmza tanınmadı: açık anahtarı gönder → TV kullanıcıya onay sorar.
                        yaz(o, AUTH, 3, 0, acikAnahtar(anahtar.public as RSAPublicKey))
                        onayBekleniyor = true
                        Log.i(ETIKET, "ADB: TV'de hata ayıklama onayı bekleniyor")
                        soket.soTimeout = 30_000
                    }
                }
                else -> {}
            }
        }
    }

    private fun calistir(g: DataInputStream, o: OutputStream, k: String): String {
        val yerel = 1
        yaz(o, OPEN, yerel, 0, "shell:$k\u0000".toByteArray())
        val cikti = StringBuilder()
        var uzak = 0
        while (true) {
            val m = oku(g)
            when (m.komut) {
                OKAY -> uzak = m.a0
                WRTE -> {
                    cikti.append(String(m.veri))
                    yaz(o, OKAY, yerel, m.a0)
                }
                CLSE -> {
                    if (m.a0 == 0) throw Hata("ADB komutu reddedildi.")
                    yaz(o, CLSE, yerel, uzak)
                    return cikti.toString()
                }
            }
        }
    }

    // ---------------------------------------------------------------- anahtar

    private fun anahtar(): KeyPair {
        val ozel = File(ctx.filesDir, "adb_ozel.der")
        val acik = File(ctx.filesDir, "adb_acik.der")
        if (ozel.exists() && acik.exists()) try {
            val kf = KeyFactory.getInstance("RSA")
            return KeyPair(
                kf.generatePublic(X509EncodedKeySpec(acik.readBytes())),
                kf.generatePrivate(PKCS8EncodedKeySpec(ozel.readBytes()))
            )
        } catch (e: Exception) {
            Log.w(ETIKET, "ADB anahtarı okunamadı, yenisi üretiliyor: ${e.message}")
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        ozel.writeBytes(kp.private.encoded)
        acik.writeBytes(kp.public.encoded)
        return kp
    }

    private fun imzala(kp: KeyPair, jeton: ByteArray): ByteArray {
        // RSA_sign(NID_sha1, jeton): 00 01 FF..FF 00 | DigestInfo(SHA-1) | jeton  (toplam 256 bayt)
        val uz = 256
        val dolgu = ByteArray(uz)
        val icerik = SHA1_ONEK + jeton
        dolgu[0] = 0; dolgu[1] = 1
        val ffSon = uz - icerik.size - 1
        for (i in 2 until ffSon) dolgu[i] = 0xff.toByte()
        dolgu[ffSon] = 0
        System.arraycopy(icerik, 0, dolgu, ffSon + 1, icerik.size)
        val c = Cipher.getInstance("RSA/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, kp.private)
        return c.doFinal(dolgu)
    }

    /** Android'in adb açık anahtar biçimi (RSAPublicKey yapısı, base64) + " kullanıcı@makine\0". */
    private fun acikAnahtar(k: RSAPublicKey): ByteArray {
        val kelime = 64
        val r32 = BigInteger.ONE.shiftLeft(32)
        var n = k.modulus
        val r = BigInteger.ONE.shiftLeft(kelime * 32)
        var rr = r.modPow(BigInteger.valueOf(2), n)
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32))
        val bb = ByteBuffer.allocate(4 + 4 + kelime * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(kelime)
        bb.putInt(n0inv.toInt())
        repeat(kelime) {
            val d = n.divideAndRemainder(r32); n = d[0]; bb.putInt(d[1].toInt())
        }
        repeat(kelime) {
            val d = rr.divideAndRemainder(r32); rr = d[0]; bb.putInt(d[1].toInt())
        }
        bb.putInt(k.publicExponent.toInt())
        val b64 = Base64.encodeToString(bb.array(), Base64.NO_WRAP)
        return "$b64 julide@tv\u0000".toByteArray()
    }
}
