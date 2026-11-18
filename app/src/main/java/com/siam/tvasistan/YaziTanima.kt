package com.siam.tvasistan

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * YEREL YAZI TANIMA (OCR) — Google ML Kit, TV'nin kendisinde çalışır, internet ve Gemini kotası harcamaz.
 * Latin alfabesi modeli Türkçe harfleri tanır.
 *
 * Kullanım: erişilebilirlik ağacı ekranı okuyamadığında (Prime Video, TOD gibi ekranı resim olarak çizen
 * uygulamalar) ekran görüntüsündeki yazılar KONUMLARIYLA çıkarılır. Afişlerdeki süslü başlıkları kaçırabilir;
 * o durumda ekrana_bak (Gemini görsel) yedek olarak kalır.
 *
 * Arka plan iş parçacığında çağrılmalı (Tasks.await ana iş parçacığında çalışmaz).
 */
class YaziTanima {
    data class Satir(val metin: String, val kutu: Rect)

    private val tanici by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Görüntüdeki satırlar (yukarıdan aşağı, soldan sağa). Hata olursa boş liste. */
    fun oku(bmp: Bitmap): List<Satir> = try {
        val t0 = android.os.SystemClock.elapsedRealtime()
        val sonuc = Tasks.await(tanici.process(InputImage.fromBitmap(bmp, 0)), 8, TimeUnit.SECONDS)
        val satirlar = sonuc.textBlocks.flatMap { b ->
            b.lines.mapNotNull { l ->
                val k = l.boundingBox ?: return@mapNotNull null
                val m = l.text.trim()
                if (m.length < 2) null else Satir(m, k)
            }
        }.sortedWith(compareBy({ it.kutu.top / 20 }, { it.kutu.left }))
        Log.i("TvAsistan", "TANI yazı tanıma: ${bmp.width}x${bmp.height}, ${satirlar.size} satır, ${android.os.SystemClock.elapsedRealtime() - t0} ms")
        satirlar
    } catch (e: Exception) {
        Log.w("TvAsistan", "Yazı tanıma yapılamadı: ${e.message}")
        emptyList()
    }
}
