package com.siam.tvasistan

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Gemini'nin çağırabileceği TV araçları + çalıştırıcıları. */
class Araclar(private val s: AsistanServisi) {

    // ---------------------------------------------------------------- tanımlar

    private class P(val ad: String, val tip: String, val aciklama: String, val secenekler: List<String>? = null)

    private fun fn(ad: String, aciklama: String, parametreler: List<P> = emptyList(), zorunlu: List<String> = emptyList()): JSONObject {
        val j = JSONObject().put("name", ad).put("description", aciklama)
        if (parametreler.isNotEmpty()) {
            val ozellikler = JSONObject()
            for (p in parametreler) {
                val o = JSONObject().put("type", p.tip).put("description", p.aciklama)
                p.secenekler?.let { o.put("enum", JSONArray(it)) }
                ozellikler.put(p.ad, o)
            }
            val sema = JSONObject().put("type", "OBJECT").put("properties", ozellikler)
            if (zorunlu.isNotEmpty()) sema.put("required", JSONArray(zorunlu))
            j.put("parameters", sema)
        }
        return j
    }

    fun bildirimler(): JSONArray = JSONArray()
        .put(fn("uygulama_ac", "TV'de kurulu bir uygulamayı adıyla açar (ör. YouTube, Netflix, tarayıcı, Ayarlar).",
            listOf(P("ad", "STRING", "Uygulamanın adı, kullanıcının söylediği gibi")), listOf("ad")))
        .put(fn("uygulamalari_listele", "TV'de kurulu tüm uygulamaların adlarını döndürür."))
        .put(fn("icerik_ara", "Film, dizi, oyuncu veya program için TV'nin genel içerik aramasını açar (tüm uygulamalarda arar).",
            listOf(P("sorgu", "STRING", "Aranacak film/dizi/kişi adı")), listOf("sorgu")))
        .put(fn("youtube_ara", "YouTube uygulamasında arama yapar.",
            listOf(P("sorgu", "STRING", "Aranacak metin")), listOf("sorgu")))
        .put(fn("web_ara", "Tarayıcıda Google araması açar.",
            listOf(P("sorgu", "STRING", "Aranacak metin")), listOf("sorgu")))
        .put(fn("url_ac", "Bir web adresini tarayıcıda açar.",
            listOf(P("url", "STRING", "Tam adres, ör. https://www.siam.com.tr")), listOf("url")))
        .put(fn("ekrani_oku", "Şu an ekranda görünen yazıları/düğmeleri ve açık uygulamayı döndürür. Bir şeye tıklamadan önce kullan."))
        .put(fn("tikla", "Ekranda yazısı verilen öğeye tıklar (ör. 'Ara', 'Oynat', bir film adı).",
            listOf(P("metin", "STRING", "Öğenin ekrandaki yazısı")), listOf("metin")))
        .put(fn("yaz", "Odaktaki veya ekrandaki ilk yazı alanına metin yazar.",
            listOf(
                P("metin", "STRING", "Yazılacak metin"),
                P("gonder", "BOOLEAN", "true ise yazdıktan sonra ara/onayla (Enter)")
            ), listOf("metin")))
        .put(fn("tus", "Kumanda tuşu gibi davranır.",
            listOf(
                P("tus", "STRING", "Basılacak tuş", listOf("yukari", "asagi", "sol", "sag", "tamam", "geri", "ana_ekran", "son_uygulamalar")),
                P("tekrar", "INTEGER", "Kaç kez basılacağı (varsayılan 1, en çok 10)")
            ), listOf("tus")))
        .put(fn("kaydir", "Ekrandaki listeyi kaydırır.",
            listOf(P("yon", "STRING", "Kaydırma yönü", listOf("asagi", "yukari"))), listOf("yon")))
        .put(fn("ses", "TV ses seviyesini değiştirir.",
            listOf(
                P("islem", "STRING", "Yapılacak işlem", listOf("artir", "azalt", "sessiz", "sesi_ac", "ayarla")),
                P("seviye", "INTEGER", "'ayarla' için 0-100 arası yüzde; artir/azalt için adım sayısı")
            ), listOf("islem")))
        .put(fn("medya", "Oynatılan videoyu/müziği kontrol eder.",
            listOf(P("islem", "STRING", "Medya komutu", listOf("oynat", "duraklat", "oynat_duraklat", "durdur", "ileri_sar", "geri_sar", "sonraki", "onceki"))),
            listOf("islem")))
        .put(fn("oturumu_kapat", "Kullanıcı işinin bittiğini söylediğinde (tamam, teşekkürler, kapat) dinlemeyi bitirir."))
        .put(fn("guncelle", "TV Asistan'ın yeni sürümü var mı denetler; varsa indirip kurulum ekranını açar."))

    fun sistemTalimati(ek: String = ""): String {
        val uygulamalar = try {
            Uygulamalar.liste(s).joinToString(", ") { it.ad }.take(1500)
        } catch (_: Exception) {
            ""
        }
        return """
Sen Toshiba Android TV üzerinde çalışan Türkçe sesli asistansın ("TV Asistan"). Her zaman Türkçe, KISA ve doğal konuş (genelde tek cümle).
Görevin kullanıcının sesli komutlarını araçlarla TV'de gerçekten uygulamak:
- "X'i aç" → uygulama_ac. Bulunamazsa benzer adları söyle.
- "Şu filmi/diziyi bul" (uygulama belirtilmemişse) → icerik_ara.
- "Netflix'te / Prime'da / Disney'de X'i bul" → önce uygulama_ac, sonra ekrani_oku ile ekrana bak, "Ara" öğesine tikla, yaz ile adı yaz (gonder=true), sonra sonucu tikla.
- "YouTube'da X" → youtube_ara.
- "Tarayıcıyı aç, X'i ara" → web_ara. Belirli site → url_ac.
- Ekranda gezinme → ekrani_oku, tikla, tus, kaydir. Tıklamadan önce emin değilsen mutlaka ekrani_oku çağır; ekranda olmayan bir şeyi uydurma.
- Ses → ses; oynat/duraklat → medya.
- Kullanıcı "tamam", "teşekkürler", "kapat", "bu kadar" derse kısa bir veda et ve oturumu_kapat çağır.
- "Kendini güncelle", "yeni sürüm var mı" → guncelle.
Bir araç hata dönerse bunu dürüstçe ve kısaca söyle, gerekirse başka yol dene.
TV'de kurulu uygulamalar: $uygulamalar
$ek
""".trim()
    }

    // ---------------------------------------------------------------- çalıştırıcı

    fun calistir(ad: String, a: JSONObject): JSONObject = try {
        when (ad) {
            "uygulama_ac" -> uygulamaAc(a.optString("ad"))
            "uygulamalari_listele" -> tamam(Uygulamalar.liste(s).joinToString(", ") { it.ad })
            "icerik_ara" -> icerikAra(a.optString("sorgu"))
            "youtube_ara" -> youtubeAra(a.optString("sorgu"))
            "web_ara" -> webAra(a.optString("sorgu"))
            "url_ac" -> urlAc(a.optString("url"))
            "ekrani_oku" -> ekraniOku()
            "tikla" -> tikla(a.optString("metin"))
            "yaz" -> yaz(a.optString("metin"), a.optBoolean("gonder", false))
            "tus" -> tus(a.optString("tus"), a.optInt("tekrar", 1))
            "kaydir" -> kaydir(a.optString("yon"))
            "ses" -> ses(a.optString("islem"), if (a.has("seviye")) a.optInt("seviye") else null)
            "medya" -> medya(a.optString("islem"))
            "oturumu_kapat" -> {
                s.oturumuKapatIste()
                tamam("Oturum kapanacak.")
            }
            "guncelle" -> {
                val yeni = Guncelleme.denetle()
                if (yeni == null) {
                    tamam("Zaten en son sürüm kullanılıyor (${BuildConfig.VERSION_NAME}).")
                } else {
                    s.oturumuKapatIste()
                    s.guncellemeSonraKur(yeni)
                    tamam("Yeni sürüm ${yeni.ad} bulundu; oturum kapanınca indirilip kurulum ekranı açılacak.")
                }
            }
            else -> hata("Bilinmeyen araç: $ad")
        }
    } catch (e: Exception) {
        hata(e.message ?: e.toString())
    }

    private fun tamam(m: String) = JSONObject().put("sonuc", m)
    private fun hata(m: String) = JSONObject().put("hata", m)

    private fun baslat(i: Intent): Boolean = try {
        s.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun enc(q: String) = URLEncoder.encode(q, "UTF-8")

    // --- uygulamalar ve arama

    private fun uygulamaAc(ad: String): JSONObject {
        val bulunan = Uygulamalar.bul(s, ad)
        val ilk = bulunan.firstOrNull()
            ?: return hata("'$ad' adında uygulama bulunamadı. Kurulu uygulamalar: " +
                Uygulamalar.liste(s).joinToString(", ") { it.ad }.take(800))
        return if (Uygulamalar.ac(s, ilk.paket)) tamam("${ilk.ad} açıldı.") else hata("${ilk.ad} açılamadı.")
    }

    private fun icerikAra(q: String): JSONObject {
        if (q.isBlank()) return hata("Arama metni boş.")
        val genel = Intent("android.search.action.GLOBAL_SEARCH").putExtra(SearchManager.QUERY, q)
        if (baslat(genel)) return tamam("TV aramasında '$q' açıldı.")
        val katniss = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.katniss").putExtra(SearchManager.QUERY, q)
        if (baslat(katniss)) return tamam("Google TV aramasında '$q' açıldı.")
        val web = webAra("$q izle")
        return if (web.has("sonuc")) tamam("TV araması yok, tarayıcıda arandı: '$q'.") else web
    }

    private fun youtubeAra(q: String): JSONObject {
        if (q.isBlank()) return hata("Arama metni boş.")
        val yt = "com.google.android.youtube.tv"
        if (baslat(Intent(Intent.ACTION_SEARCH).setPackage(yt).putExtra(SearchManager.QUERY, q).putExtra("query", q)))
            return tamam("YouTube'da '$q' arandı.")
        val url = "https://www.youtube.com/results?search_query=${enc(q)}"
        if (baslat(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(yt))) return tamam("YouTube'da '$q' arandı.")
        if (baslat(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) return tamam("YouTube araması tarayıcıda açıldı.")
        return hata("YouTube açılamadı.")
    }

    private fun webAra(q: String): JSONObject {
        if (q.isBlank()) return hata("Arama metni boş.")
        return if (baslat(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${enc(q)}"))))
            tamam("Tarayıcıda '$q' arandı.")
        else hata("TV'de tarayıcı bulunamadı. Play Store'dan bir tarayıcı (ör. TV Bro, Puffin TV) kurulmalı.")
    }

    private fun urlAc(u: String): JSONObject {
        if (u.isBlank()) return hata("Adres boş.")
        val adres = if (u.startsWith("http")) u else "https://$u"
        return if (baslat(Intent(Intent.ACTION_VIEW, Uri.parse(adres)))) tamam("$adres açıldı.")
        else hata("TV'de tarayıcı bulunamadı.")
    }

    // --- ekran

    private class Oge(val dugum: AccessibilityNodeInfo, val metin: String)

    private fun kokler(): List<AccessibilityNodeInfo> {
        val l = try {
            s.windows
                .filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
                .mapNotNull { it.root }
        } catch (_: Exception) {
            emptyList()
        }
        return l.ifEmpty { listOfNotNull(s.rootInActiveWindow) }
            .filter { it.packageName?.toString() != s.packageName }
    }

    private fun ogeler(): List<Oge> {
        val sonuc = mutableListOf<Oge>()
        for (k in kokler()) gez(k, sonuc, 0)
        return sonuc
    }

    private fun gez(n: AccessibilityNodeInfo, l: MutableList<Oge>, derinlik: Int) {
        if (derinlik > 40 || l.size >= 400) return
        if (n.isVisibleToUser) {
            val m = n.text?.toString()?.takeIf { it.isNotBlank() }
                ?: n.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                ?: n.hintText?.toString()?.takeIf { it.isNotBlank() && n.isEditable }
            if (m != null) l += Oge(n, m.trim().replace(Regex("\\s+"), " "))
        }
        for (i in 0 until n.childCount) n.getChild(i)?.let { gez(it, l, derinlik + 1) }
    }

    private fun tiklanabilir(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var t: AccessibilityNodeInfo? = n
        var adim = 0
        while (t != null && adim < 8) {
            if (t.isClickable) return t
            t = t.parent
            adim++
        }
        return null
    }

    private fun ekraniOku(): JSONObject {
        val paket = s.aktifPaket
        val uygAdi = paket?.let { p -> Uygulamalar.liste(s).firstOrNull { it.paket == p }?.ad } ?: paket ?: "bilinmiyor"
        val l = ogeler()
        if (l.isEmpty()) return tamam("Açık uygulama: $uygAdi. Ekrandaki öğeler okunamadı (uygulama içeriğini gizliyor olabilir).")
        val satirlar = l.distinctBy { it.metin }.take(80).map { o ->
            val etiketler = mutableListOf<String>()
            if (tiklanabilir(o.dugum) != null) etiketler += "tıklanabilir"
            if (o.dugum.isFocused || o.dugum.isAccessibilityFocused) etiketler += "odakta"
            if (o.dugum.isEditable) etiketler += "yazı alanı"
            o.metin.take(80) + if (etiketler.isEmpty()) "" else " [${etiketler.joinToString(", ")}]"
        }
        return JSONObject().put("acik_uygulama", uygAdi).put("ogeler", JSONArray(satirlar))
    }

    private fun tikla(metin: String): JSONObject {
        val h = Uygulamalar.normal(metin)
        if (h.isEmpty()) return hata("Tıklanacak metin boş.")
        val aday = ogeler()
            .map { o -> o to Uygulamalar.normal(o.metin) }
            .mapNotNull { (o, n) ->
                val p = when {
                    n == h -> 4
                    n.startsWith(h) -> 3
                    n.contains(h) -> 2
                    n.length >= 3 && h.contains(n) -> 1
                    else -> 0
                }
                if (p > 0) Triple(o, p, n.length) else null
            }
            .sortedWith(compareByDescending<Triple<Oge, Int, Int>> { it.second }.thenBy { it.third })
            .firstOrNull()?.first
            ?: return hata("Ekranda '$metin' bulunamadı. Önce ekrani_oku ile ekrana bak.")

        tiklanabilir(aday.dugum)?.let { t ->
            if (t.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return tamam("'${aday.metin}' seçildi.")
        }
        aday.dugum.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val r = Rect()
        aday.dugum.getBoundsInScreen(r)
        if (!r.isEmpty && dokun(r.centerX().toFloat(), r.centerY().toFloat())) return tamam("'${aday.metin}' öğesine dokunuldu.")
        return hata("'${aday.metin}' bulundu ama tıklanamadı.")
    }

    private fun dokun(x: Float, y: Float): Boolean {
        val yol = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(yol, 0, 60))
            .build()
        return s.dispatchGesture(g, null, null)
    }

    private fun yaziAlani(): AccessibilityNodeInfo? {
        s.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { if (it.isEditable) return it }
        for (k in kokler()) bulDuzenlenebilir(k, 0)?.let { return it }
        return null
    }

    private fun bulDuzenlenebilir(n: AccessibilityNodeInfo, derinlik: Int): AccessibilityNodeInfo? {
        if (derinlik > 40) return null
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { c -> bulDuzenlenebilir(c, derinlik + 1)?.let { return it } }
        }
        return null
    }

    private fun yaz(metin: String, gonder: Boolean): JSONObject {
        val alan = yaziAlani() ?: return hata("Ekranda yazı alanı yok. Önce arama kutusuna tıkla.")
        alan.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val b = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, metin)
        }
        if (!alan.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) return hata("Metin yazılamadı.")
        if (gonder && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            alan.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
        return tamam("'$metin' yazıldı" + if (gonder) " ve gönderildi." else ".")
    }

    private fun tus(t: String, tekrar: Int): JSONObject {
        val n = tekrar.coerceIn(1, 10)
        var basarili = 0
        repeat(n) {
            if (tekBas(t)) basarili++
            SystemClock.sleep(150)
        }
        return if (basarili > 0) tamam("'$t' tuşu $basarili kez basıldı.") else hata("'$t' tuşu uygulanamadı.")
    }

    private fun tekBas(t: String): Boolean {
        when (t) {
            "geri" -> return s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "ana_ekran" -> return s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "son_uygulamalar" -> return s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val eylem = when (t) {
                "yukari" -> AccessibilityService.GLOBAL_ACTION_DPAD_UP
                "asagi" -> AccessibilityService.GLOBAL_ACTION_DPAD_DOWN
                "sol" -> AccessibilityService.GLOBAL_ACTION_DPAD_LEFT
                "sag" -> AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT
                "tamam" -> AccessibilityService.GLOBAL_ACTION_DPAD_CENTER
                else -> return false
            }
            return s.performGlobalAction(eylem)
        }
        // Android 10-12: odak tabanlı gezinme
        val odak = s.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (t == "tamam") return odak.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val yon = when (t) {
            "yukari" -> View.FOCUS_UP
            "asagi" -> View.FOCUS_DOWN
            "sol" -> View.FOCUS_LEFT
            "sag" -> View.FOCUS_RIGHT
            else -> return false
        }
        val sonraki = odak.focusSearch(yon) ?: return false
        return sonraki.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun kaydir(yon: String): JSONObject {
        var en: AccessibilityNodeInfo? = null
        var enAlan = 0
        fun ara(n: AccessibilityNodeInfo, d: Int) {
            if (d > 40) return
            if (n.isScrollable && n.isVisibleToUser) {
                val r = Rect()
                n.getBoundsInScreen(r)
                val alan = r.width() * r.height()
                if (alan > enAlan) {
                    enAlan = alan
                    en = n
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { ara(it, d + 1) }
        }
        for (k in kokler()) ara(k, 0)
        val hedef = en ?: return hata("Kaydırılabilir liste bulunamadı.")
        val eylem = if (yon == "yukari") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        return if (hedef.performAction(eylem)) tamam("Kaydırıldı.") else hata("Daha fazla kaydırılamıyor.")
    }

    // --- ses ve medya

    private fun ses(islem: String, seviye: Int?): JSONObject {
        val am = s.getSystemService(AudioManager::class.java)
        val akis = AudioManager.STREAM_MUSIC
        val bayrak = AudioManager.FLAG_SHOW_UI
        when (islem) {
            "artir", "azalt" -> {
                val adim = (seviye ?: 2).coerceIn(1, 15)
                val yon = if (islem == "artir") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                repeat(adim) { am.adjustStreamVolume(akis, yon, bayrak) }
            }
            "sessiz" -> am.adjustStreamVolume(akis, AudioManager.ADJUST_MUTE, bayrak)
            "sesi_ac" -> am.adjustStreamVolume(akis, AudioManager.ADJUST_UNMUTE, bayrak)
            "ayarla" -> {
                val yuzde = (seviye ?: return hata("Seviye verilmedi.")).coerceIn(0, 100)
                am.setStreamVolume(akis, am.getStreamMaxVolume(akis) * yuzde / 100, bayrak)
            }
            else -> return hata("Bilinmeyen ses işlemi: $islem")
        }
        val max = am.getStreamMaxVolume(akis).coerceAtLeast(1)
        return tamam("Ses seviyesi: %${am.getStreamVolume(akis) * 100 / max}")
    }

    private fun medya(islem: String): JSONObject {
        val kod = when (islem) {
            "oynat" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "duraklat" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "oynat_duraklat" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "durdur" -> KeyEvent.KEYCODE_MEDIA_STOP
            "ileri_sar" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "geri_sar" -> KeyEvent.KEYCODE_MEDIA_REWIND
            "sonraki" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "onceki" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return hata("Bilinmeyen medya komutu: $islem")
        }
        // Oturum kapanınca medya uygulaması ses odağını geri alır; komut ondan sonra da geçerli olsun diye
        // oynat komutu oturum bitiminde tekrar gönderilir.
        s.medyaTusu(kod)
        return tamam("Medya komutu gönderildi: $islem")
    }
}
