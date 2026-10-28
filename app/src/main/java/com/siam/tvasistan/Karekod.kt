package com.siam.tvasistan

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Telefon sayfasının karekodu: okutulunca sayfa eşleştirme koduyla birlikte açılır. */
object Karekod {
    const val SAYFA = "https://klncumut6565.github.io/tv-asistan"

    /** Eşleştirme kodu gömülü sayfa adresi (sayfa ?kod= değerini otomatik doldurur). */
    fun adres(kod: String): String = "$SAYFA/?kod=$kod"

    /** Beyaz zeminli, kenar boşluklu karekod. Üretilemezse null. */
    fun bitmap(metin: String, boyut: Int): Bitmap? = try {
        val ipuclari = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val m = QRCodeWriter().encode(metin, BarcodeFormat.QR_CODE, boyut, boyut, ipuclari)
        val piksel = IntArray(m.width * m.height)
        for (y in 0 until m.height) {
            val satir = y * m.width
            for (x in 0 until m.width) piksel[satir + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        }
        Bitmap.createBitmap(piksel, m.width, m.height, Bitmap.Config.ARGB_8888)
    } catch (_: Exception) {
        null
    }
}
