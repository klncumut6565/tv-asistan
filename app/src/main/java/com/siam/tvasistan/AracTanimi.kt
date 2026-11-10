package com.siam.tvasistan

import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini'ye bildirilen araç tanımları. Android'e bağlı DEĞİL: hem uygulama hem davranış testi (JVM) aynı tanımları kullanır.
 */
object AracTanimi {

    internal class P(val ad: String, val tip: String, val aciklama: String, val secenekler: List<String>? = null)

    internal fun fn(ad: String, aciklama: String, parametreler: List<P> = emptyList(), zorunlu: List<String> = emptyList()): JSONObject {
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
        .put(fn("uygulama_kapat", "Açık olan bir uygulamayı kapatır. Ad verilmezse şu an ekrandaki uygulamayı kapatır.",
            listOf(P("ad", "STRING", "Kapatılacak uygulamanın adı; boş bırakılırsa ekrandaki uygulama"))))
        .put(fn("uygulamalari_listele", "TV'de kurulu tüm uygulamaların adlarını döndürür."))
        .put(fn("akilli_ara", "Kullanıcı bir film/dizi/şarkı/video/herhangi bir şeyi AÇ/İZLE/DİNLE/ARA dediğinde HER ZAMAN BUNU kullan. Platform söylenmemişse ŞU AN AÇIK uygulamada arar (YouTube açıksa YouTube'da, Prime/TOD açıksa orada, tarayıcı açıksa tarayıcıda). Açık uygulama uygun değilse ve birden çok seçenek varsa HİÇBİR ŞEY AÇMAZ, 'soru_sor' döndürür: o zaman kullanıcıya hangi platformdan açacağını sor. Platform söylenmişse ('tarayıcıdan', 'YouTube'da', 'Prime'da', 'Spotify'dan') oradan arar.",
            listOf(
                P("sorgu", "STRING", "Aranacak şey, ör. 'Cehennem Melekleri' ya da 'Yıldız Tilbe Delikanlım'"),
                P("platform", "STRING", "YALNIZCA kullanıcı açıkça söylediyse: tarayıcı, YouTube, Prime Video, TOD... Söylemediyse BOŞ bırak."),
                P("tur", "STRING", "film, dizi, muzik, video ya da diger", listOf("film", "dizi", "muzik", "video", "diger"))
            ), listOf("sorgu")))
        .put(fn("saka_modu", "ŞAKA (TEKRARLAMA) MODU: açıkken Jülide kullanıcının söylediğini kelimesi kelimesine, hiçbir şey eklemeden tekrar eder. 'Söylediklerimi tekrarla', 'dediklerimi tekrar et', 'şaka modunu aç' → acik=true. 'Şaka modunu kapat', 'tekrarlamayı bırak', 'normal moda dön' → acik=false.",
            listOf(P("acik", "BOOLEAN", "true: modu aç, false: kapat")), listOf("acik")))
        .put(fn("internette_arastir", "İNTERNET ARAŞTIRMASI (ücretsiz): genel ya da güncel BİLGİ sorularında kullan (bugünkü maçlar, skor, puan durumu, haber, kişi, olay, bilim, tarih, tarif, ürün, mevzuat...). Tarayıcı AÇMAZ. Birden çok kaynağı bulur, en güvenilirlerin sayfasını okur, kopyaları ayıklar ve kanıt düzeyiyle (güçlü/orta/yetersiz) kaynaklı bir kanıt paketi döndürür. Döviz için doviz_kuru, hava için hava_durumu daha nettir.",
            listOf(
                P("sorgu", "STRING", "Kullanıcının sorusu doğal haliyle (ör. 'bugün hangi maçlar var', 'Galatasaray'ın bir sonraki maçı ne zaman'). Takip sorusunda önceki konuyu ekle."),
                P("guncellik", "STRING", "anlik: canlı/bugün (skor, bugünkü maçlar); yakin: son günler/haftalar; kalici: değişmeyen bilgi", listOf("anlik", "yakin", "kalici")),
                P("konu", "STRING", "Konu sınıfı", listOf("genel", "spor", "haber", "ekonomi", "bilim", "saglik", "hukuk", "teknoloji", "urun", "kultur")),
                P("baglam", "STRING", "Takip sorusuysa önceki konuşmadan kısa bağlam (ör. 'Galatasaray'); yoksa boş")
            ), listOf("sorgu", "guncellik")))
        .put(fn("doviz_kuru", "Döviz kurunu TCMB'nin resmî gösterge kurlarından verir (dolar, euro, sterlin, frank, riyal...). 'Dolar kaç TL' gibi sorularda internette_arastir yerine BUNU kullan.",
            listOf(P("birim", "STRING", "İstenen birim(ler), ör. 'dolar', 'euro ve sterlin'; boşsa dolar ve euro")))) 
        .put(fn("hava_durumu", "Bir şehrin şu anki havasını ve 3 günlük tahminini verir (Open-Meteo). Şehir söylenmezse son kullanılan şehir; o da yoksa kullanıcıya şehri sor.",
            listOf(P("sehir", "STRING", "Şehir/ilçe adı, ör. 'Çorlu', 'İstanbul'"))))
        .put(fn("imlec", "Ekranda sesle yönetilen İMLEÇ (tarayıcıda link/düğme/video tıklamak için). Mod otomatik seçilir: sayfadaki linkler algılanabiliyorsa EL işareti, algılanamıyorsa FARE oku. islem: goster, gizle, tasi (yon+miktar), git (x,y yüzde), oge (bir yazının üstüne götür), tikla, cift_tikla.",
            listOf(
                P("islem", "STRING", "Ne yapılacak", listOf("goster", "gizle", "tasi", "git", "oge", "tikla", "cift_tikla")),
                P("yon", "STRING", "tasi için yön", listOf("sag", "sol", "yukari", "asagi")),
                P("miktar", "INTEGER", "tasi için adım (1 = biraz, 3 = orta, 6 = çok)"),
                P("x", "NUMBER", "git için yatay konum yüzdesi (0 sol, 50 orta, 100 sağ)"),
                P("y", "NUMBER", "git için dikey konum yüzdesi (0 üst, 50 orta, 100 alt)"),
                P("metin", "STRING", "oge için ekrandaki yazı (link/düğme adı)"),
                P("mod", "STRING", "İsteğe bağlı: kullanıcı açıkça isterse fare ya da el", listOf("fare", "el"))
            ), listOf("islem")))
        .put(fn("sayfa_boyutu", "Tarayıcıda SAYFAYI yakınlaştırır/uzaklaştırır (iki parmak hareketi). 'Sayfayı büyüt / küçült / yazılar küçük' gibi isteklerde.",
            listOf(
                P("islem", "STRING", "buyut ya da kucult", listOf("buyut", "kucult")),
                P("miktar", "INTEGER", "1-3 (biraz = 1, çok = 3)")
            ), listOf("islem")))
        .put(fn("video_tam_ekran", "Sayfadaki videoyu TAM EKRAN yapar: önce tam ekran düğmesini arar, yoksa videoya dokunup kontrolleri açar, olmazsa videoya çift tıklar."))
        .put(fn("video_oynat", "Sayfadaki videoyu BAŞLATIR: ortadaki oynat (▶) düğmesine imleçle tıklar; düğme algılanamazsa video alanının ortasına tıklar."))
        .put(fn("spotify_ara", "SPOTIFY'da şarkı/sanatçı/albüm arar (kullanıcının Spotify ÜYELİĞİ var). Sonuçları açar; sonra ekrani_oku ile listeye bakıp istenen şarkıya tikla. Kullanıcı 'herhangi biri / fark etmez' derse hemen_cal=true ile en iyi eşleşmeyi doğrudan çalar.",
            listOf(
                P("sorgu", "STRING", "Şarkı, sanatçı ya da ikisi, ör. 'Ahmet Kaya' ya da 'Ahmet Kaya Kendine İyi Bak'"),
                P("hemen_cal", "BOOLEAN", "true: listeyi göstermeden en iyi eşleşmeyi çal (kullanıcı belirli şarkı istemediyse ve 'herhangi biri' dediyse)")
            ), listOf("sorgu")))
        .put(fn("abonelikte_ara", "Filmi/diziyi kullanıcının ABONE olduğu uygulamada arar: uygulamayı açar, arama kutusuna adı yazar. Sonra ekrani_oku ile sonucu bulup tikla, ardından Oynat/İzle'ye tikla.",
            listOf(
                P("ad", "STRING", "Film veya dizi adı"),
                P("uygulama", "STRING", "Abonelik uygulaması (boşsa ilk abonelik)")
            ), listOf("ad")))
        .put(fn("icerik_ara", "TV'nin genel içerik araması (tüm uygulamalar). Film/dizi için KULLANMA; abonelikte_ara kullan.",
            listOf(P("sorgu", "STRING", "Aranacak film/dizi/kişi adı")), listOf("sorgu")))
        .put(fn("youtube_oynat", "YouTube'da istenen videoyu/şarkıyı DOĞRUDAN BAŞLATIR (arama sonucuna tıklamaya gerek yok). 'YouTube'da X'i aç/oynat/çal/izle', 'Tarkan'ın şarkısını aç' gibi isteklerde BUNU kullan. Sonuçtaki diğer videolar listesini de döndürür; kullanıcı 'ikincisini aç' derse sira=2 ile tekrar çağır.",
            listOf(
                P("sorgu", "STRING", "Ne aranacak, ör. 'Yıldız Tilbe Delikanlım', 'Ahmet Kaya şarkıları'"),
                P("sira", "INTEGER", "Kaçıncı sonuç açılsın (1 = ilk; kullanıcı 'ikincisi/başka bir tane' derse 2, 3...)")
            ), listOf("sorgu")))
        .put(fn("youtube_ara", "YouTube uygulamasında yalnızca ARAMA SONUÇLARINI gösterir (video başlatmaz). Kullanıcı 'YouTube'da ara / sonuçları göster' derse kullan; video açmak için youtube_oynat.",
            listOf(P("sorgu", "STRING", "Aranacak metin")), listOf("sorgu")))
        .put(fn("web_ara", "Tarayıcıda Google araması açar.",
            listOf(P("sorgu", "STRING", "Aranacak metin")), listOf("sorgu")))
        .put(fn("url_ac", "Bir web adresini tarayıcıda açar.",
            listOf(P("url", "STRING", "Tam adres, ör. https://www.siam.com.tr")), listOf("url")))
        .put(fn("ekrana_bak", "Ekranın KÜÇÜK bir görüntüsünü sana gönderir; sen ekranı GÖRÜRSÜN. Yalnızca ekrani_oku boş/yetersiz döndüğünde kullan (Prime Video, TOD, Netflix gibi ekranı resim olarak çizen uygulamalar). Kota harcar: gereksiz yere ve arka arkaya çağırma; yön tuşlarıyla birkaç adım ilerledikten sonra tekrar bak.",
            listOf(P("ne_ariyorum", "STRING", "Ekranda ne aradığın, ör. 'Yüzüklerin Efendisi kapağı', 'Oynat düğmesi'"))))
        .put(fn("ekrani_oku", "Şu an ekranda görünen yazıları/düğmeleri ve açık uygulamayı döndürür. Bir şeye tıklamadan önce kullan."))
        .put(fn("tikla", "Ekranda yazısı verilen öğeye tıklar (ör. 'Ara', 'Oynat', bir film adı).",
            listOf(
                P("metin", "STRING", "Öğenin ekrandaki yazısı"),
                P("onayli", "BOOLEAN", "Satın alma/silme/kurulum/izin gibi hassas öğeler için: kullanıcı SESLİ olarak 'evet' dedikten sonra true")
            ), listOf("metin")))
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
        .put(fn("kaydir", "Sayfayı/listeyi kaydırır. Önce listeyi kaydırmayı, olmazsa ekranda kaydırma hareketini, o da olmazsa yön tuşlarını dener.",
            listOf(
                P("yon", "STRING", "Kaydırma yönü", listOf("asagi", "yukari", "sol", "sag")),
                P("miktar", "INTEGER", "Kaç adım (1 = biraz, 3 = epey, 5 = çok). Varsayılan 1")
            ), listOf("yon")))
        .put(fn("arama_sonucu", "TARAYICIDAKİ GOOGLE SONUÇ SAYFASI için: sonuçları sırayla listeler, istenen sıradaki sonucu AÇAR, sonuçlar arasında ileri/geri seçim yapar (seçili sonuç el işaretiyle gösterilir). 'İkinci sonucu aç', 'üçüncüye gir', 'ilk sonuç', 'sonraki sonuç', 'bunu aç' gibi isteklerde tikla YERİNE bunu kullan.",
            listOf(
                P("islem", "STRING", "listele: sonuçları sırayla döndür; ac: 'sira' numaralı sonucu aç; sonraki/onceki: seçimi bir sonuç kaydır; sec: seçili sonucu aç", listOf("listele", "ac", "sonraki", "onceki", "sec")),
                P("sira", "INTEGER", "Yalnız 'ac' için: kaçıncı sonuç (1 = ilk sonuç; reklamlar sayılmaz)")
            ), listOf("islem")))
        .put(fn("kanal_ac", "Canlı yayında bir TV kanalını açar (ör. ATV, Show TV, TRT 1, Kanal D) ya da kanal numarasına geçer ('7').",
            listOf(P("kanal", "STRING", "Kanal adı veya numarası, kullanıcının söylediği gibi")), listOf("kanal")))
        .put(fn("hatirla", "Kullanıcının AÇIKÇA hatırlamanı istediği bir bilgiyi KALICI olarak kaydeder (sonraki sohbetlerde de bilinir). YALNIZ kullanıcı 'bunu hatırla', 'unutma', 'aklında tut', 'kaydet' derse ya da sen 'Bunu hatırlayayım mı?' diye sorup EVET derse kullan. Kendiliğinden kayıt YAPMA. Şifre, kimlik/kart numarası, sağlık bilgisi kaydedilmez.",
            listOf(
                P("bilgi", "STRING", "Kaydedilecek bilgi, kısa ve kendi başına anlaşılır bir cümle (ör. 'Umut maçları TRT Spor'dan izlemeyi sever')"),
                P("kategori", "STRING", "tercih, kisi, cihaz, rutin ya da diger", listOf("tercih", "kisi", "cihaz", "rutin", "diger"))
            ), listOf("bilgi")))
        .put(fn("unut", "Kullanıcı 'bunu unut', '... bilgisini sil', 'hatırladıklarını sil' derse kaydedilmiş bilgiyi siler. 'hepsi' tüm kayıtları siler (önce kullanıcıya emin olup olmadığını sor).",
            listOf(P("aranan", "STRING", "Silinecek kaydın içinde geçen kelime(ler) ya da 'hepsi'")), listOf("aranan")))
        .put(fn("hatirladiklarim", "Kullanıcı 'beni hakkımda ne hatırlıyorsun', 'neleri kaydettin' derse kalıcı bellekteki kayıtları döndürür.", listOf(), listOf()))
        .put(fn("kanal_listesi", "Kanal listesini EKRANDA gösterir. goster: Jülide'nin öğrendiği (kaydedilen) kanalları numaralarıyla ekrana yazar ve listeyi döndürür; gizle: listeyi kapatır; tv: TV'nin kendi kanal listesini açar (canlı yayın + Tamam).",
            listOf(P("islem", "STRING", "goster (varsayılan), gizle ya da tv", listOf("goster", "gizle", "tv"))), listOf("islem")))
        .put(fn("kanal_kaydet", "Bir kanal adının kanal numarasını KALICI olarak hatırlar (ör. ATV = 4). 'Birinci kanalda TRT 1 var', '4. kanalda Show TV var', 'ATV 4. kanalda' gibi sözlerde HEMEN kullan; sonra 'Show TV'yi aç' denince o numara açılır. sil=true ile kaydı siler.",
            listOf(
                P("kanal", "STRING", "Kanal adı"),
                P("numara", "INTEGER", "Kanal numarası"),
                P("sil", "BOOLEAN", "true: bu kanal kaydını sil")
            ), listOf("kanal")))
        .put(fn("kanal_degistir", "Canlı yayında sonraki/önceki kanala geçer. Canlı TV açık değilse önce açar.",
            listOf(
                P("yon", "STRING", "Yön", listOf("sonraki", "onceki")),
                P("adet", "INTEGER", "Kaç kanal (varsayılan 1, en çok 10)")
            ), listOf("yon")))
        .put(fn("numaralari_goster", "Ekrandaki TÜM tıklanabilir öğelerin (simgeler dahil) üstüne numara rozeti koyar ve 'numara: ad' listesini döndürür. Kullanıcı bir yeri işaret ederse ('şuraya', 'şuna', 'bu düğmeye') ya da tikla yazıyla bulamazsa kullan."))
        .put(fn("numaraya_tikla", "numaralari_goster ile gösterilen numaralı öğeye tıklar.",
            listOf(
                P("numara", "INTEGER", "Rozetteki numara"),
                P("onayli", "BOOLEAN", "Hassas öğe için kullanıcı SESLİ 'evet' dedikten sonra true")
            ), listOf("numara")))
        .put(fn("ses", "TV ses seviyesini değiştirir.",
            listOf(
                P("islem", "STRING", "Yapılacak işlem", listOf("artir", "azalt", "sessiz", "sesi_ac", "ayarla")),
                P("seviye", "INTEGER", "'ayarla' için 0-100 arası yüzde; artir/azalt için adım sayısı")
            ), listOf("islem")))
        .put(fn("medya", "Oynatılan videoyu/müziği kontrol eder.",
            listOf(P("islem", "STRING", "Medya komutu", listOf("oynat", "duraklat", "oynat_duraklat", "durdur", "ileri_sar", "geri_sar", "sonraki", "onceki"))),
            listOf("islem")))
        .put(fn("oturumu_kapat", "Sohbeti tamamen bitirir ve Jülide'yi kapatır. YALNIZCA kullanıcı açıkça 'sohbeti kapat', 'Jülide kapan', 'seni kapat', 'görüşürüz' dediğinde çağır."))
        .put(fn("guncelle", "Jülide'nin (bu uygulamanın) yeni sürümü var mı denetler; varsa indirip kurulum ekranını açar."))
        .put(fn("cihaz_durumu", "TV'nin bağlantı ve cihaz durumunu döndürür: internet/ağ tipi, model ve Android sürümü, Jülide sürümü, boş bellek, ses seviyesi, ADB (hata ayıklama) açık mı."))
        .put(fn("ayarlari_ac", "TV'nin bir ayar sayfasını açar. Bir uygulamanın durdurma/devre dışı bırakma/önbellek sayfası için sayfa=uygulama_bilgisi ve uygulama adı ver.",
            listOf(
                P("sayfa", "STRING", "Açılacak ayar sayfası", Cihaz.SAYFALAR.keys.toList() + "uygulama_bilgisi"),
                P("uygulama", "STRING", "Yalnızca sayfa=uygulama_bilgisi için: uygulamanın adı")
            ), listOf("sayfa")))
        .put(fn("tv_kapat", "TELEVİZYONUN KENDİSİNİ kapatır (bekleme/uyku). İKİ ADIMLIDIR: önce onayli=false ile çağır (TV kapanmaz, kullanıcıya sorman istenir), kullanıcı SESLİ 'evet/kapat/onaylıyorum' derse onayli=true ile tekrar çağır. Uygulama kapatmak için uygulama_kapat, sohbeti bitirmek için oturumu_kapat kullan.",
            listOf(P("onayli", "BOOLEAN", "Kullanıcı 'Televizyonu kapatayım mı?' sorusuna SESLİ olarak evet dediyse true; ilk çağrıda false"))))
        .put(fn("uyku_zamanlayicisi", "Belirli bir süre sonra TV'yi kapatan zamanlayıcıyı kurar, iptal eder veya kalan süreyi söyler.",
            listOf(
                P("islem", "STRING", "Yapılacak işlem", listOf("kur", "iptal", "durum")),
                P("dakika", "INTEGER", "Yalnızca 'kur' için: kaç dakika sonra kapansın (1-${Uyku.EN_COK_DAKIKA}). 'Bir saat' = 60, 'yarım saat' = 30")
            ), listOf("islem")))
        .put(fn("kaynak_degistir", "TV'nin giriş kaynağını değiştirir: HDMI girişleri (ör. 'HDMI 1', 'HDMI 2', 'HDMI 3' ya da kullanıcının verdiği ad, ör. 'PlayStation') veya 'TV' (anten/uydu canlı yayını).",
            listOf(P("kaynak", "STRING", "Giriş adı: 'HDMI 1', 'HDMI 2', 'HDMI 3', 'TV' ya da girişin adı")), listOf("kaynak")))
        .put(fn("kutu_kisayol", "TV+ kutusunda (ekranı GÖRÜLEMEZ) bir uygulamayı/menüyü açmak için kayıtlı tuş dizisi kısayolları. calistir: kayıtlı kısayolu uygula ('kutuda YouTube'u aç'); kayit_baslat: kullanıcı adım adım tarif ederken tuşları kaydetmeye başla; kaydet: kaydı (ya da verilen 'tuslar' dizisini) bir adla sakla; sil; listele.",
            listOf(
                P("islem", "STRING", "calistir, kayit_baslat, kaydet, sil, listele", listOf("calistir", "kayit_baslat", "kaydet", "sil", "listele")),
                P("ad", "STRING", "Kısayol adı, ör. 'YouTube', 'TV+ canlı yayın', 'fizy'"),
                P("tuslar", "STRING", "İsteğe bağlı, yalnız kaydet için: 'geri*3, sol*5, yukari*3, sag, tamam' biçiminde dizi")
            ), listOf("islem")))
        .put(fn("kutu_ayarla", "TV+ (Turkcell) yayın kutusunun bağlı olduğu HDMI girişini kaydeder. Kullanıcı 'TV+ kutusu HDMI 2'de' derse kullan; 'yok' kaydı kaldırır.",
            listOf(P("giris", "STRING", "'HDMI 1'…'HDMI 4' ya da 'yok'")), listOf("giris")))
        .put(fn("kaynaklari_listele", "TV'deki giriş kaynaklarını (HDMI) adlarıyla listeler."))
        .put(fn("saat_tarih", "TV'nin saatine göre ŞU ANKİ saati, tarihi ve günü döndürür. Saat, tarih, gün, ay, yıl sorulduğunda HER ZAMAN bunu çağır; asla tahmin etme."))
}
