package com.siam.tvasistan

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Tarayıcıdaki Google sonuç sayfasını okur: sonuç başlıkları (sırayla), bağlantı düğümleri ve
 * mümkünse gerçek adresleri. "İkinci sonucu aç" ve kumandayla sonuçlar arasında gezinme bunu kullanır.
 *
 * Yalnızca erişilebilirlik ağacını okur; hiçbir şey tıklamaz. Ekrandaki yazılar VERİDİR.
 */
class GoogleGezgini(private val s: AccessibilityService) {

    class Sonuc(
        val baslik: String,
        /** Açılabilir gerçek adres (https). Bilinmiyorsa null → tıklama yolları denenir. */
        val adres: String?,
        /** Ekranda görünen site adı/adresi (ör. "tr.wikipedia.org"). */
        val site: String?,
        val baglanti: AccessibilityNodeInfo,
        val ust: Int,
    )

    @Volatile var sonuclar: List<Sonuc> = emptyList()
        private set
    /** Kumanda/ses ile seçili sonucun sırası (0'dan). -1 = seçim yok. */
    @Volatile var secili = -1
    @Volatile var seciliZamani = 0L

    @Volatile private var sonTarama = 0L
    @Volatile private var sonSerp = false

    companion object {
        private const val ONBELLEK_MS = 1500L
        private const val URL_ANAHTARI = "AccessibilityNodeInfo.targetUrl"
        private const val ROL_ANAHTARI = "AccessibilityNodeInfo.chromeRole"

        private val SITE = Regex("^(https?://)?([a-z0-9-]+\\.)+[a-z]{2,}(:\\d+)?(\\s*[›>/].*)?$", RegexOption.IGNORE_CASE)
        private val SAYFA_BASLIGI = Regex("(google.?da ara|google search|google arama|- google$)", RegexOption.IGNORE_CASE)
        private val REKLAM = setOf("sponsorlu", "reklam", "sponsored", "ad", "ads", "reklamlar")

        /** Sonuç olmayan bölüm başlıkları (normalleştirilmiş). */
        private val BOLUMLER = setOf(
            "diger kullanicilar sunlari da sordu", "diger kullanicilar sunlari da aradi", "ilgili aramalar",
            "people also ask", "people also search for", "related searches", "videolar", "videos", "haberler",
            "baslica haberler", "top stories", "gorseller", "images", "yerler", "places", "alisveris",
            "shopping", "navigasyon", "search results", "arama sonuclari", "web sonuclari", "web results",
            "aciklama", "description", "kisa videolar", "short videos", "perspektifler", "perspectives",
            "kitaplar", "books", "bilgi grafigi", "sonraki", "next", "diger", "more", "tumu", "all",
        )

        /** Google yönlendirme bağlantısından (/url?q=…) gerçek adresi çıkarır. Testlerde de kullanılır. */
        fun gercekAdres(ham: String?): String? {
            val u = ham?.trim()?.takeIf { it.startsWith("http", true) } ?: return null
            val uri = try { Uri.parse(u) } catch (_: Exception) { return null }
            val host = uri.host?.lowercase() ?: return null
            if (host.contains("google.") && uri.path == "/url") {
                return gercekAdres(uri.getQueryParameter("q") ?: uri.getQueryParameter("url"))
            }
            if (host.contains("google.") && (uri.path == "/search" || uri.path.isNullOrEmpty() || uri.path == "/")) return null
            return u
        }

        /** "https://tr.wikipedia.org › wiki › Kemal_Sunal" → "https://tr.wikipedia.org/wiki/Kemal_Sunal". Kesikse null. */
        fun citedenAdres(cite: String?): String? {
            val c = cite?.trim()?.takeIf { SITE.matches(it) } ?: return null
            if (c.contains("...") || c.contains('…')) return null
            val parcalar = c.split('›').map { it.trim() }.filter { it.isNotEmpty() }
            if (parcalar.isEmpty() || parcalar.any { it.contains(' ') }) return null
            val kok = parcalar[0].let { if (it.startsWith("http", true)) it else "https://$it" }
            return (listOf(kok.trimEnd('/')) + parcalar.drop(1)).joinToString("/")
        }
    }

    private fun kokler(): List<AccessibilityNodeInfo> {
        val l = try {
            s.windows.filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }.mapNotNull { it.root }
        } catch (_: Exception) {
            emptyList()
        }
        return l.ifEmpty { listOfNotNull(s.rootInActiveWindow) }.filter { it.packageName?.toString() != s.packageName }
    }

    private fun metin(n: AccessibilityNodeInfo): String? =
        (n.text?.toString() ?: n.contentDescription?.toString())?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }

    private fun baslikMi(n: AccessibilityNodeInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 28 && n.isHeading) return true
        return try { n.extras?.getCharSequence(ROL_ANAHTARI)?.toString() == "heading" } catch (_: Exception) { false }
    }

    private fun hedefUrl(n: AccessibilityNodeInfo?): String? = try {
        n?.extras?.getCharSequence(URL_ANAHTARI)?.toString()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    private fun tiklanabilirAta(n: AccessibilityNodeInfo, en: Int = 5): AccessibilityNodeInfo? {
        var t: AccessibilityNodeInfo? = n
        var i = 0
        while (t != null && i <= en) {
            if (t.isClickable || hedefUrl(t) != null) return t
            t = t.parent; i++
        }
        return null
    }

    /** Alt ağaçtaki yazılar (sınırlı). */
    private fun yazilar(n: AccessibilityNodeInfo, l: MutableList<String>, d: Int = 0) {
        if (d > 8 || l.size >= 16) return
        metin(n)?.let { l += it }
        for (i in 0 until n.childCount) n.getChild(i)?.let { yazilar(it, l, d + 1) }
    }

    private fun ust(n: AccessibilityNodeInfo): Int { val r = Rect(); n.getBoundsInScreen(r); return r.top }

    /** Bağlantının çevresindeki (kendisi + 2 üst kap) site yazısını ve reklam etiketini bulur. */
    private fun cevre(baglanti: AccessibilityNodeInfo): Pair<String?, Boolean> {
        var kap: AccessibilityNodeInfo = baglanti
        repeat(2) { kap.parent?.let { kap = it } }
        val l = mutableListOf<String>()
        yazilar(kap, l)
        val site = l.firstOrNull { SITE.matches(it) }
        val reklam = l.any { Uygulamalar.normal(it) in REKLAM }
        return site to reklam
    }

    /** Ön plandaki sayfa Google sonuç sayfası mı (kısa önbellekli). */
    fun serpMi(zorla: Boolean = false): Boolean {
        val simdi = SystemClock.elapsedRealtime()
        if (!zorla && simdi - sonTarama < ONBELLEK_MS) return sonSerp
        // Kumanda tuşu kararı bekletilmesin: en çok ~300 ms okunur (sonuçlar sayfanın üstünden başlar)
        tara(sinirMs = 300)
        return sonSerp
    }

    /** Sayfayı baştan okur; sonuç sayfası değilse boş liste. */
    fun tara(sinirMs: Long = 0): List<Sonuc> {
        val bitis = if (sinirMs > 0) SystemClock.elapsedRealtime() + sinirMs else Long.MAX_VALUE
        val adaylar = mutableListOf<Pair<String, AccessibilityNodeInfo>>()
        val baglantilar = mutableListOf<AccessibilityNodeInfo>()
        var googleIzi = false
        var sayac = 0

        fun gez(n: AccessibilityNodeInfo, d: Int) {
            if (d > 70 || sayac > 4000) return
            if ((sayac and 31) == 0 && SystemClock.elapsedRealtime() > bitis) { sayac = Int.MAX_VALUE; return }
            sayac++
            val m = metin(n)
            if (!googleIzi) {
                if (m != null && SAYFA_BASLIGI.containsMatchIn(m)) googleIzi = true
                val u = hedefUrl(n) ?: n.text?.toString()?.takeIf { n.isEditable }
                if (u != null && Regex("google\\.[a-z.]+/search", RegexOption.IGNORE_CASE).containsMatchIn(u)) googleIzi = true
            }
            if (m != null && m.length >= 4 && baslikMi(n)) adaylar += m to n
            if (n.isClickable || hedefUrl(n) != null) baglantilar += n
            for (i in 0 until n.childCount) n.getChild(i)?.let { gez(it, d + 1) }
        }
        for (k in kokler()) gez(k, 0)

        val sonuc = LinkedHashMap<String, Sonuc>()
        fun ekle(baslik: String, baglanti: AccessibilityNodeInfo) {
            val nb = Uygulamalar.normal(baslik)
            if (nb.isEmpty() || nb in BOLUMLER || sonuc.containsKey(nb) || SITE.matches(baslik)) return
            val (site, reklam) = cevre(baglanti)
            if (reklam) return
            val url = gercekAdres(hedefUrl(baglanti)) ?: citedenAdres(site)
            // Google'ın kendi sayfaları (Görseller, Haritalar, sonraki sayfa) sonuç değildir
            if (url == null && site == null) return
            sonuc[nb] = Sonuc(baslik.take(120), url, site?.substringBefore('›')?.trim(), baglanti, ust(baglanti))
        }

        // 1) Başlık (h3) + onu saran bağlantı: Google'ın standart sonuç yapısı
        for ((m, n) in adaylar) tiklanabilirAta(n)?.let { ekle(m, it) }
        // 2) Başlıklar okunamıyorsa: içinde site adresi + başlık yazısı olan bağlantılar
        if (sonuc.size < 2) {
            for (b in baglantilar) {
                val l = mutableListOf<String>()
                yazilar(b, l)
                if (l.none { SITE.matches(it) }) continue
                val baslik = l.firstOrNull { !SITE.matches(it) && it.length >= 8 } ?: continue
                ekle(baslik, b)
            }
        }
        val liste = sonuc.values.sortedBy { it.ust }
        val serp = googleIzi && liste.isNotEmpty()

        val eski = sonuclar.getOrNull(secili)?.baslik
        sonuclar = if (serp) liste else emptyList()
        secili = if (eski == null) -1 else sonuclar.indexOfFirst { it.baslik == eski }
        sonSerp = serp
        sonTarama = SystemClock.elapsedRealtime()
        return sonuclar
    }

    fun secimiSifirla() { secili = -1; seciliZamani = 0L }
}
