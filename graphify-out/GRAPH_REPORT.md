# Graph Report - tv-asistan  (2026-11-16)

## Corpus Check
- 93 files · ~80,461 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 15 file(s) not represented in the graph (top: .xml 5, (none) 4, .bat 2)

## Summary
- 1447 nodes · 3118 edges · 84 communities (45 shown, 39 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 116 edges (avg confidence: 0.89)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `5c3ce6b7`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- KaynakTest
- GeminiLive.kt
- TelefonKoprusu
- Kaynak
- KaynakTuru
- AramaSaglayicilari.kt
- Araclar
- Panel
- MainActivity
- Cihaz.kt
- Adb
- AnahtarKurulum.kt
- Kasa.kt
- AsistanServisi
- KureKatmani
- Mod
- .getir
- lg.js
- paket.js
- assertnull
- 2.1 Teknoloji ve mimari
- steps
- with
- AsistanOturum
- UzakSessizlikTest.kt
- 7. Güvenlik, lisans ve performans riskleri
- Ses.kt
- Uyandirma.kt
- pages.yml
- TvArayuz
- with
- with
- Bellek
- HizliYol
- Imlec.kt
- CleanYourTV → Jülide (tv-asistan) Entegrasyon Raporu
- SozlesmeTest
- Cihaz.kt
- arastirma.js
- AsistanAsistan.kt
- Oturum
- GoogleGezgini
- app.js
- 6. Öneri ayrıntıları: yöntem, izin, uyumluluk
- EkranGoruntusu
- assertequals
- .kutuKisayol
- Guncelleme
- DinlemeKapisiTest
- Mikrofon
- Uyandirma
- .bul
- MikrofonServisi.kt
- Arastirmaci.kt
- HizliKomutTest
- TV Asistan
- WebAlici.kt
- MikrofonBaslatici.kt
- KanalHafizasiTest
- Baglam
- .tarayiciAdiMi
- Oturum Özeti — 26/27.10.2026
- Oturum Özeti — 27.10 → 01.11.2026
- GoogleGezginiTest.kt
- Oturum Özeti — 6–10 Kasım 2026
- Üçüncü Taraf Bildirimleri
- Jülide — LG webOS sürümü
- GestureResultCallback
- Durum
- araclar.js
- tv-temizlik.sh
- vercel.json

## God Nodes (most connected - your core abstractions)
1. `Araclar` - 117 edges
2. `AsistanServisi` - 71 edges
3. `MainActivity` - 35 edges
4. `KaynakTest` - 24 edges
5. `istek()` - 24 edges
6. `Adb` - 23 edges
7. `Bellek` - 21 edges
8. `TvArayuz` - 21 edges
9. `GeminiLive` - 20 edges
10. `GoogleGezgini` - 20 edges

## Surprising Connections (you probably didn't know these)
- `F8 — Uygulama kategorileri · P2` --references--> `Uyg`  [INFERRED]
  docs/CLEANYOURTV_ENTEGRASYON_RAPORU.md → app/src/main/java/com/siam/tvasistan/Uygulamalar.kt
- `F2 — `tv_kapat` / `uyku_zamanlayicisi` · P1` --references--> `Araclar`  [INFERRED]
  docs/CLEANYOURTV_ENTEGRASYON_RAPORU.md → app/src/main/java/com/siam/tvasistan/Araclar.kt
- `Mimari` --references--> `Araclar`  [INFERRED]
  README.md → app/src/main/java/com/siam/tvasistan/Araclar.kt
- `2.1 Teknoloji ve mimari` --references--> `AsistanAsistan`  [INFERRED]
  docs/CLEANYOURTV_ENTEGRASYON_RAPORU.md → app/src/main/java/com/siam/tvasistan/AsistanAsistan.kt
- `2.1 Teknoloji ve mimari` --references--> `AsistanServisi`  [INFERRED]
  docs/CLEANYOURTV_ENTEGRASYON_RAPORU.md → app/src/main/java/com/siam/tvasistan/AsistanServisi.kt

## Import Cycles
- None detected.

## Communities (84 total, 39 thin omitted)

### Community 1 - "GeminiLive.kt"
Cohesion: 0.06
Nodes (12): GeminiLive, Dinleyici, WebSocketListener, KaliciHata, KotaHatasi, kotaMi(), Uzak, Model (+4 more)

### Community 3 - "Kaynak"
Cohesion: 0.17
Nodes (3): Arastirmaci, Kayit, Kaynak

### Community 4 - "KaynakTuru"
Cohesion: 0.12
Nodes (16): IcerikDurumu, ALINDI, ALINMADI, BASARISIZ, ENGELLI, KISMI, KanitDuzeyi, GUCLU (+8 more)

### Community 5 - "AramaSaglayicilari.kt"
Cohesion: 0.25
Nodes (8): AramaSaglayici, BingRss, ddgAyristir(), DuckDuckGo, enc(), rssAyristir(), Vikipedi, Aday

### Community 6 - "Araclar"
Cohesion: 0.06
Nodes (11): Araclar, Numarali, Oge, Ayarlar, KutuModu, SistemPaketleri, Uygulamalar, Uyg (+3 more)

### Community 8 - "MainActivity"
Cohesion: 0.11
Nodes (8): MainActivity, Kategori, KUMANDA, SES, SISTEM, TV, UYGULAMALAR, YAPAY_ZEKA

### Community 9 - "Cihaz.kt"
Cohesion: 0.12
Nodes (10): AcilisAlici, ServisOnarim, background_color, display, icons, lang, name, short_name (+2 more)

### Community 10 - "Adb"
Cohesion: 0.08
Nodes (4): Adb, Hata, Mesaj, Uyku

### Community 14 - "KureKatmani"
Cohesion: 0.15
Nodes (4): KureKatmani, Halka, Kivilcim, Parcacik

### Community 16 - "Mod"
Cohesion: 0.22
Nodes (9): Mod, BAGLANIYOR, BEKLIYOR, BILGI, DINLIYOR, HATA, ISLEM, KONUSUYOR (+1 more)

### Community 18 - "lg.js"
Cohesion: 0.11
Nodes (33): ac(), adresAc(), baglan(), baslat(), bildirim(), enter(), giris(), girisDegistir() (+25 more)

### Community 19 - "paket.js"
Cohesion: 0.14
Nodes (32): at(), b(), ce(), constructor(), decrypt(), destroy(), digest(), digestInto() (+24 more)

### Community 22 - "2.1 Teknoloji ve mimari"
Cohesion: 0.40
Nodes (3): 2.1 Teknoloji ve mimari, 2.4 Olmayan yetenekler (doğrulandı), 2. Mevcut TV Asistanının doğrulanmış yetenekleri

### Community 23 - "steps"
Cohesion: 0.06
Nodes (31): runs-on, steps, timeout-minutes, GEMINI_TEST_ANAHTARI, GEMINI_TEST_MODELI, GEMINI_TEST_VAKALAR, model, vakalar (+23 more)

### Community 24 - "with"
Cohesion: 0.07
Nodes (30): runs-on, steps, IMZA_DOSYASI, IMZA_KEYSTORE_B64, IMZA_SIFRE, jobs, derle, kontrol (+22 more)

### Community 27 - "7. Güvenlik, lisans ve performans riskleri"
Cohesion: 0.50
Nodes (4): 7.1 Lisans (gerçek dosyaya göre), 7.2 Güvenlik, 7.3 Performans ve kararlılık, 7. Güvenlik, lisans ve performans riskleri

### Community 29 - "Ses.kt"
Cohesion: 0.15
Nodes (5): Oynatici, pcmRms(), SesKapisi, sesSeviyesi(), Mimari

### Community 33 - "pages.yml"
Cohesion: 0.08
Nodes (25): concurrency, cancel-in-progress, group, name, url, jobs, yayinla, name (+17 more)

### Community 34 - "TvArayuz"
Cohesion: 0.06
Nodes (8): KanalListesiPenceresi, Karekod, TvArayuz, Durum, HATA, IYI, UYARI, Renk

### Community 35 - "with"
Cohesion: 0.09
Nodes (22): jobs, yansit, name, on, push, workflow_dispatch, permissions, contents (+14 more)

### Community 36 - "with"
Cohesion: 0.09
Nodes (22): jobs, paketle, name, on, push, workflow_dispatch, runs-on, steps (+14 more)

### Community 38 - "Bellek"
Cohesion: 0.17
Nodes (7): Bellek, Kayit, Sonuc, Eklendi, Reddedildi, Tazelendi, BellekTest

### Community 39 - "HizliYol"
Cohesion: 0.12
Nodes (5): HizliKomut, Komut, HizliYol, Kayit, KanalHafizasi

### Community 40 - "Imlec.kt"
Cohesion: 0.14
Nodes (4): Imlec, Mod, EL, FARE

### Community 41 - "CleanYourTV → Jülide (tv-asistan) Entegrasyon Raporu"
Cohesion: 0.18
Nodes (11): 10. Değiştirilmesi gerekebilecek dosyalar, 1. Yönetici özeti, 3.1 Toshiba / Vestel saha notları (`examples/toshiba-cosmos-android-tv/debloat.txt`), 3.2 Aşama 0 — Toshiba 50UA3D63DT doğrulama sonuçları (27.10.2026, ADB), 3. CleanYourTV'nin önemli dosyaları ve işlevleri, 4. Mimari ve teknoloji karşılaştırması, 5. Entegre edilebilir özellikler tablosu, 8. Önerilen uygulama sırası (+3 more)

### Community 42 - "SozlesmeTest"
Cohesion: 0.11
Nodes (6): AracTanimi, P, DavranisTesti, GunlukKotaDoldu, Sonuc, SozlesmeTest

### Community 43 - "Cihaz.kt"
Cohesion: 0.09
Nodes (5): Cihaz, Girisler, Giris, Kanallar, Kanal

### Community 44 - "arastirma.js"
Cohesion: 0.23
Nodes (18): alanAdi(), anaMetin(), arastir(), benzerlik(), birimBul(), ddg(), doviz(), erisimTesti() (+10 more)

### Community 46 - "AsistanAsistan.kt"
Cohesion: 0.24
Nodes (3): AsistanAsistan, AsistanOturumServisi, AsistanTanima

### Community 47 - "Oturum"
Cohesion: 0.20
Nodes (8): ayarlar(), b64ToPcm(), baglam(), cal(), Oturum, sesDogrula(), sustur(), tercihliSes()

### Community 49 - "app.js"
Cohesion: 0.30
Nodes (14): anahtarDurumu(), anahtarKurulumu(), b64(), b64Coz(), baslat(), bitir(), durum(), gunluk() (+6 more)

### Community 51 - "6. Öneri ayrıntıları: yöntem, izin, uyumluluk"
Cohesion: 0.13
Nodes (10): 6. Öneri ayrıntıları: yöntem, izin, uyumluluk, F10 — TV'nin bir kerelik temizliği · P3, F2 — `tv_kapat` / `uyku_zamanlayicisi` · P1, F3 — Servis kendini onarma (`ServisOnarim`, YENİ) · P1, F4 — Vestel korumalı paketler ve canlı TV ipucu · P1 (cihaz doğrulamasından sonra), F5 — Ayar sayfalarını açma · P1, F6 — HDMI / kaynak değiştirme · P2, F7 — GuardRevival (süreç diriltme) · P2 (+2 more)

### Community 52 - "EkranGoruntusu"
Cohesion: 0.18
Nodes (7): EkranGoruntusu, TakeScreenshotCallback, Sonuc, Ayni, Hata, Siyah, Yeni

### Community 55 - ".kutuKisayol"
Cohesion: 0.11
Nodes (4): AracKorumasi, KutuKisayol, AracKorumasiTest, KutuKisayolTest

### Community 59 - "DinlemeKapisiTest"
Cohesion: 0.23
Nodes (5): DinlemeKapisi, Yol, GEMINI, YEREL, DinlemeKapisiTest

### Community 62 - ".bul"
Cohesion: 0.32
Nodes (3): YouTubeBulucu, Video, YouTubeBulucuTest

### Community 69 - "TV Asistan"
Cohesion: 0.22
Nodes (7): Bilinen sınırlar, Derleme, Güncellemeler, İlk ayar (TV'de), İlk testte kontrol edilecekler, TV Asistan, TV'ye kurulum

### Community 70 - "WebAlici.kt"
Cohesion: 0.33
Nodes (3): WebAlici, EngelHatasi, Yanit

### Community 73 - "Baglam"
Cohesion: 0.33
Nodes (6): Baglam, ABONELIK, DIGER, SPOTIFY, TARAYICI, YOUTUBE

### Community 75 - "Oturum Özeti — 26/27.10.2026"
Cohesion: 0.20
Nodes (6): SessizCikis, Açık işler / sonraki adımlar, Cihaz gerçekleri (Toshiba 50UA3D63DT), MİKROFON — kesin teşhis, Oturum Özeti — 26/27.10.2026, Tamamlanan işler

### Community 76 - "Oturum Özeti — 27.10 → 01.11.2026"
Cohesion: 0.33
Nodes (5): Açık işler / sonraki adımlar, Kararlar, Oturum Özeti — 27.10 → 01.11.2026, Tamamlananlar (Android / Toshiba 50UA3D63DT, son sürüm v0.1.86), webOS (PEAQ PTV32WH-3026A, webOS 23) — webos/ klasörü, Android'den bağımsız

### Community 78 - "Oturum Özeti — 6–10 Kasım 2026"
Cohesion: 0.40
Nodes (4): Açık işler / TV'de doğrulanacaklar, Kararlar, Oturum Özeti — 6–10 Kasım 2026, Tamamlananlar (hepsi main'de, CI yeşil)

### Community 79 - "Üçüncü Taraf Bildirimleri"
Cohesion: 0.40
Nodes (4): CleanYourTV, Vosk, ZXing (core), Üçüncü Taraf Bildirimleri

### Community 80 - "Jülide — LG webOS sürümü"
Cohesion: 0.40
Nodes (4): Bilinen sınırlar, Jülide — LG webOS sürümü, Kurulum (bir kez), Neler çalışır

### Community 82 - "Durum"
Cohesion: 0.50
Nodes (4): Durum, BAGLANIYOR, BOSTA, DINLIYOR

## Knowledge Gaps
- **46 isolated node(s):** `YOUTUBE`, `SPOTIFY`, `ABONELIK`, `TARAYICI`, `DIGER` (+41 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 356 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **39 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Araclar` connect `Araclar` to `TvArayuz`, `Baglam`, `Adb`, `AsistanServisi`, `GoogleGezgini`, `AsistanServisi.kt`, `6. Öneri ayrıntıları: yöntem, izin, uyumluluk`, `EkranGoruntusu`, `2.1 Teknoloji ve mimari`, `.kutuKisayol`, `Ses.kt`?**
  _High betweenness centrality (0.207) - this node is a cross-community bridge._
- **Are the 7 inferred relationships involving `Araclar` (e.g. with `Adb` and `Ayarlar`) actually correct?**
  _`Araclar` has 7 INFERRED edges - model-reasoned connections that need verification._
- **What connects `YOUTUBE`, `SPOTIFY`, `ABONELIK` to the rest of the system?**
  _46 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `GeminiLive.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.061343204653622425 - nodes in this community are weakly interconnected._
- **Why does `AsistanServisi` connect `AsistanServisi` to `GeminiLive.kt`, `TelefonKoprusu`, `Araclar`, `Panel`, `Cihaz.kt`, `Adb`, `.oturumBaslat`, `.telefonSesi`, `2.1 Teknoloji ve mimari`, `Ses.kt`, `HizliYol`, `AsistanServisi.kt`, `6. Öneri ayrıntıları: yöntem, izin, uyumluluk`, `.kutuKisayol`, `Guncelleme`, `Mikrofon`, `Uyandirma`, `Oturum Özeti — 26/27.10.2026`, `Durum`?**
  _High betweenness centrality (0.200) - this node is a cross-community bridge._
- **Are the 4 inferred relationships involving `AsistanServisi` (e.g. with `AracKorumasi` and `HizliYol`) actually correct?**
  _`AsistanServisi` has 4 INFERRED edges - model-reasoned connections that need verification._
- **Should `KaynakTuru` be split into smaller, more focused modules?**
  _Cohesion score 0.11764705882352941 - nodes in this community are weakly interconnected._