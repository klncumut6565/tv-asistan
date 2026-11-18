package com.siam.tvasistan

/**
 * ODAK BULUCU (Android'siz, birim testli).
 *
 * Prime Video / TOD gibi ekranı resim olarak çizen uygulamalarda erişilebilirlik ağacı "hangi öğe seçili"
 * bilgisini vermez. Yön tuşundan ÖNCE ve SONRA alınan iki kare karşılaştırılır:
 *  - Odak taşınınca genelde iki bölge değişir: eski odak (vurgusu söner) ve yeni odak (beyaz çerçeve /
 *    büyüme / aydınlanma). Değişen bölgeler bağlı bileşenlere ayrılır; içi en çok AYDINLANAN bölge yeni odaktır.
 *  - Ekranın çok büyük kısmı değiştiyse sayfa kaydı ya da yeni sayfa açıldı demektir: odak belirsiz.
 *  - Hiç değişmediyse tuş görünür bir etki yapmadı (liste sonu, odak kenarda).
 *  - Ekranın %30'undan büyük tek bölge (ör. otomatik oynayan fragman) odak sayılmaz.
 *
 * Girdi: aynı boyutta iki parlaklık ızgarası (0..255), satır satır.
 * (mirroir-mcp'deki kare parmak izi / kare karşılaştırma yönteminden uyarlanmıştır, Apache-2.0.)
 */
object OdakBulucu {

    enum class Durum { BULUNDU, DEGISMEDI, SAYFA_DEGISTI, BELIRSIZ }

    /** Izgara koordinatlarında kutu (x1, y1 dahil). */
    data class Kutu(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val gen get() = x1 - x0 + 1
        val yuk get() = y1 - y0 + 1
        val alan get() = gen * yuk
    }

    data class Sonuc(val durum: Durum, val kutu: Kutu? = null, val guvenYuksek: Boolean = false, val degisenOran: Double = 0.0)

    private const val FARK_ESIGI = 24          // bir hücre "değişti" sayılması için parlaklık farkı
    private const val EN_AZ_ORAN = 0.002       // bunun altı: değişmedi
    private const val SAYFA_ORANI = 0.45       // bunun üstü: sayfa değişti
    private const val EN_BUYUK_KUTU = 0.30     // bundan büyük bölge odak sayılmaz (fragman, arka plan)
    private const val EN_AZ_HUCRE = 6

    fun bul(once: IntArray, sonra: IntArray, g: Int, y: Int): Sonuc {
        require(once.size == g * y && sonra.size == g * y)
        val degisti = BooleanArray(g * y) { kotlin.math.abs(once[it] - sonra[it]) > FARK_ESIGI }
        val oran = degisti.count { it }.toDouble() / (g * y)
        if (oran < EN_AZ_ORAN) return Sonuc(Durum.DEGISMEDI, degisenOran = oran)
        if (oran > SAYFA_ORANI) return Sonuc(Durum.SAYFA_DEGISTI, degisenOran = oran)

        // İnce çerçeve parçaları tek bölge olsun diye 1 hücre genişlet
        val genis = BooleanArray(g * y)
        for (yy in 0 until y) for (xx in 0 until g) if (degisti[yy * g + xx]) {
            for (dy in -1..1) for (dx in -1..1) {
                val nx = xx + dx; val ny = yy + dy
                if (nx in 0 until g && ny in 0 until y) genis[ny * g + nx] = true
            }
        }
        val bolgeler = bilesenler(genis, degisti, g, y)
            .filter { (k, n) -> n >= EN_AZ_HUCRE && k.alan <= EN_BUYUK_KUTU * g * y }
            .map { it.first }
        if (bolgeler.isEmpty()) return Sonuc(Durum.BELIRSIZ, degisenOran = oran)

        // Her bölgenin aydınlanması: sonra − önce ortalama parlaklık
        val puanli = bolgeler.map { k -> k to (ortalama(sonra, k, g) - ortalama(once, k, g)) }.sortedByDescending { it.second }
        val (enIyi, artis) = puanli.first()
        if (artis <= 2.0) return Sonuc(Durum.BELIRSIZ, degisenOran = oran)
        val ikinci = puanli.getOrNull(1)?.second
        // Güven: yeni odak belirgin aydınlandı VE (tek bölge ya da diğeri karardı)
        val guven = artis >= 8.0 && (ikinci == null || ikinci <= -3.0)
        return Sonuc(Durum.BULUNDU, enIyi, guven, oran)
    }

    /** Genişletilmiş maskede bağlı bileşenler; her biri için kutu ve içindeki GERÇEK değişen hücre sayısı. */
    private fun bilesenler(maske: BooleanArray, gercek: BooleanArray, g: Int, y: Int): List<Pair<Kutu, Int>> {
        val etiket = IntArray(g * y)
        val sonuc = mutableListOf<Pair<Kutu, Int>>()
        val kuyruk = IntArray(g * y)
        var no = 0
        for (bas in 0 until g * y) {
            if (!maske[bas] || etiket[bas] != 0) continue
            no++
            var bas2 = 0; var son = 0
            kuyruk[son++] = bas; etiket[bas] = no
            var x0 = g; var y0 = y; var x1 = -1; var y1 = -1; var n = 0
            while (bas2 < son) {
                val i = kuyruk[bas2++]
                val xx = i % g; val yy = i / g
                if (gercek[i]) n++
                if (xx < x0) x0 = xx; if (xx > x1) x1 = xx
                if (yy < y0) y0 = yy; if (yy > y1) y1 = yy
                if (xx > 0 && maske[i - 1] && etiket[i - 1] == 0) { etiket[i - 1] = no; kuyruk[son++] = i - 1 }
                if (xx < g - 1 && maske[i + 1] && etiket[i + 1] == 0) { etiket[i + 1] = no; kuyruk[son++] = i + 1 }
                if (yy > 0 && maske[i - g] && etiket[i - g] == 0) { etiket[i - g] = no; kuyruk[son++] = i - g }
                if (yy < y - 1 && maske[i + g] && etiket[i + g] == 0) { etiket[i + g] = no; kuyruk[son++] = i + g }
            }
            sonuc += Kutu(x0, y0, x1, y1) to n
        }
        return sonuc
    }

    private fun ortalama(iz: IntArray, k: Kutu, g: Int): Double {
        var t = 0L
        for (yy in k.y0..k.y1) for (xx in k.x0..k.x1) t += iz[yy * g + xx]
        return t.toDouble() / k.alan
    }
}
