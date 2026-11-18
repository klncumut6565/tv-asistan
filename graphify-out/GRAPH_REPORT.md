# Graph Report - tv-asistan  (2026-11-18)

## Corpus Check
- 98 files · ~84,096 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 15 file(s) not represented in the graph (top: .xml 5, (none) 4, .bat 2)

## Summary
- 1522 nodes · 3285 edges · 79 communities (38 shown, 41 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 119 edges (avg confidence: 0.89)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `94eb0d32`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- KaynakTest
- GeminiLive.kt
- TelefonKoprusu
- .degerlendir
- .bul
- log
- Araclar
- KanalListesiPenceresi.kt
- MainActivity
- ServisOnarim.kt
- Adb
- KutuKisayol
- Guncelleme.kt
- AsistanServisi
- Panel
- Karekod.kt
- .getir
- lg.js
- paket.js
- base64
- YaziTanima.kt
- Araclar.kt
- steps
- with
- UzakSessizlikTest.kt
- .hdmiAdi
- Panel.kt
- Ses.kt
- DinlemeKapisi
- pages.yml
- TvArayuz
- with
- with
- Bellek
- HizliYol
- Uygulamalar
- SozlesmeTest
- Cihaz.kt
- arastirma.js
- AsistanAsistan.kt
- Oturum
- GoogleGezgini
- app.js
- .onServiceConnected
- EkranGoruntusu
- AracKorumasi
- Guncelleme
- DinlemeKapisiTest.kt
- Uyandirma
- .bul
- MikrofonServisi.kt
- HizliKomutTest
- WebAlici.kt
- MikrofonBaslatici.kt
- KanalHafizasiTest
- Baglam
- Oturum Özeti — 27.10 → 01.11.2026
- GoogleGezginiTest.kt
- Oturum Özeti — 6–10 Kasım 2026
- Üçüncü Taraf Bildirimleri
- Jülide — LG webOS sürümü
- Durum
- araclar.js
- tv-temizlik.sh
- vercel.json

## God Nodes (most connected - your core abstractions)
1. `Araclar` - 124 edges
2. `AsistanServisi` - 75 edges
3. `MainActivity` - 35 edges
4. `KaynakTest` - 24 edges
5. `istek()` - 24 edges
6. `Adb` - 23 edges
7. `Bellek` - 21 edges
8. `TvArayuz` - 21 edges
9. `GeminiLive` - 20 edges
10. `GoogleGezgini` - 20 edges

## Surprising Connections (you probably didn't know these)
- `F2 — `tv_kapat` / `uyku_zamanlayicisi` · P1` --references--> `Araclar`  [INFERRED]
  docs/CLEANYOURTV_ENTEGRASYON_RAPORU.md → app/src/main/java/com/siam/tvasistan/Araclar.kt
- `Mimari` --references--> `Araclar`  [INFERRED]
  README.md → app/src/main/java/com/siam/tvasistan/Araclar.kt
- `2.1 Teknoloji ve mimari` --references--> `AsistanServisi`  [INFERRED]
  docs/CLEANYOURTV_ENTEGRASYON_RAPORU.md → app/src/main/java/com/siam/tvasistan/AsistanServisi.kt
- `Mimari` --references--> `AsistanServisi`  [INFERRED]
  README.md → app/src/main/java/com/siam/tvasistan/AsistanServisi.kt
- `Mimari` --references--> `GeminiLive`  [INFERRED]
  README.md → app/src/main/java/com/siam/tvasistan/GeminiLive.kt

## Import Cycles
- None detected.

## Communities (79 total, 41 thin omitted)

### Community 0 - "KaynakTest"
Cohesion: 0.05
Nodes (30): AramaSaglayici, BingRss, ddgAyristir(), DuckDuckGo, enc(), rssAyristir(), Vikipedi, Arastirmaci (+22 more)

### Community 1 - "GeminiLive.kt"
Cohesion: 0.06
Nodes (12): GeminiLive, Dinleyici, WebSocketListener, KaliciHata, KotaHatasi, kotaMi(), Uzak, Model (+4 more)

### Community 2 - "TelefonKoprusu"
Cohesion: 0.07
Nodes (4): AnahtarKurulum, Kasa, TelefonKoprusu, WebSocketListener

### Community 3 - ".degerlendir"
Cohesion: 0.22
Nodes (4): SozDenetcisi, AracKaydi, Ihlal, SozDenetcisiTest

### Community 4 - ".bul"
Cohesion: 0.17
Nodes (9): OdakBulucu, Durum, BELIRSIZ, BULUNDU, DEGISMEDI, SAYFA_DEGISTI, Kutu, Sonuc (+1 more)

### Community 6 - "Araclar"
Cohesion: 0.07
Nodes (6): Araclar, Numarali, OdakKaydi, Oge, Kanallar, Kanal

### Community 8 - "MainActivity"
Cohesion: 0.11
Nodes (8): MainActivity, Kategori, KUMANDA, SES, SISTEM, TV, UYGULAMALAR, YAPAY_ZEKA

### Community 9 - "ServisOnarim.kt"
Cohesion: 0.12
Nodes (10): AcilisAlici, ServisOnarim, background_color, display, icons, lang, name, short_name (+2 more)

### Community 10 - "Adb"
Cohesion: 0.08
Nodes (4): Adb, Hata, Mesaj, Uyku

### Community 14 - "Panel"
Cohesion: 0.07
Nodes (15): Panel, KureKatmani, Halka, Kivilcim, Parcacik, Mod, BAGLANIYOR, BEKLIYOR (+7 more)

### Community 18 - "lg.js"
Cohesion: 0.11
Nodes (33): ac(), adresAc(), baglan(), baslat(), bildirim(), enter(), giris(), girisDegistir() (+25 more)

### Community 19 - "paket.js"
Cohesion: 0.14
Nodes (32): at(), b(), ce(), constructor(), decrypt(), destroy(), digest(), digestInto() (+24 more)

### Community 23 - "steps"
Cohesion: 0.06
Nodes (31): runs-on, steps, timeout-minutes, GEMINI_TEST_ANAHTARI, GEMINI_TEST_MODELI, GEMINI_TEST_VAKALAR, model, vakalar (+23 more)

### Community 24 - "with"
Cohesion: 0.07
Nodes (30): runs-on, steps, IMZA_DOSYASI, IMZA_KEYSTORE_B64, IMZA_SIFRE, jobs, derle, kontrol (+22 more)

### Community 28 - "Panel.kt"
Cohesion: 0.09
Nodes (4): Imlec, Mod, EL, FARE

### Community 29 - "Ses.kt"
Cohesion: 0.05
Nodes (20): Mikrofon, AudioRecordingCallback, Oynatici, pcmRms(), SesKapisi, sesSeviyesi(), SessizCikis, Açık işler / sonraki adımlar (+12 more)

### Community 31 - "DinlemeKapisi"
Cohesion: 0.33
Nodes (4): DinlemeKapisi, Yol, GEMINI, YEREL

### Community 33 - "pages.yml"
Cohesion: 0.08
Nodes (25): concurrency, cancel-in-progress, group, name, url, jobs, yayinla, name (+17 more)

### Community 34 - "TvArayuz"
Cohesion: 0.12
Nodes (6): TvArayuz, Durum, HATA, IYI, UYARI, Renk

### Community 35 - "with"
Cohesion: 0.09
Nodes (22): jobs, yansit, name, on, push, workflow_dispatch, permissions, contents (+14 more)

### Community 36 - "with"
Cohesion: 0.09
Nodes (22): jobs, paketle, name, on, push, workflow_dispatch, runs-on, steps (+14 more)

### Community 38 - "Bellek"
Cohesion: 0.18
Nodes (7): Bellek, Kayit, Sonuc, Eklendi, Reddedildi, Tazelendi, BellekTest

### Community 39 - "HizliYol"
Cohesion: 0.13
Nodes (5): HizliKomut, Komut, HizliYol, Kayit, KanalHafizasi

### Community 41 - "Uygulamalar"
Cohesion: 0.05
Nodes (34): Ayarlar, SistemPaketleri, Uygulamalar, Uyg, TarayiciAdiTest, 10. Değiştirilmesi gerekebilecek dosyalar, 1. Yönetici özeti, 2.3 Mevcut araçlar (yeniden önerilmeyecek) (+26 more)

### Community 42 - "SozlesmeTest"
Cohesion: 0.11
Nodes (6): AracTanimi, P, DavranisTesti, GunlukKotaDoldu, Sonuc, SozlesmeTest

### Community 44 - "arastirma.js"
Cohesion: 0.23
Nodes (18): alanAdi(), anaMetin(), arastir(), benzerlik(), birimBul(), ddg(), doviz(), erisimTesti() (+10 more)

### Community 46 - "AsistanAsistan.kt"
Cohesion: 0.18
Nodes (5): AsistanAsistan, AsistanOturum, AsistanOturumServisi, AsistanTanima, 2.1 Teknoloji ve mimari

### Community 47 - "Oturum"
Cohesion: 0.20
Nodes (8): ayarlar(), b64ToPcm(), baglam(), cal(), Oturum, sesDogrula(), sustur(), tercihliSes()

### Community 49 - "app.js"
Cohesion: 0.30
Nodes (14): anahtarDurumu(), anahtarKurulumu(), b64(), b64Coz(), baslat(), bitir(), durum(), gunluk() (+6 more)

### Community 52 - "EkranGoruntusu"
Cohesion: 0.15
Nodes (7): EkranGoruntusu, Sonuc, Ayni, Hata, Siyah, Yeni, TakeScreenshotCallback

### Community 62 - ".bul"
Cohesion: 0.35
Nodes (3): YouTubeBulucu, Video, YouTubeBulucuTest

### Community 70 - "WebAlici.kt"
Cohesion: 0.32
Nodes (3): WebAlici, EngelHatasi, Yanit

### Community 73 - "Baglam"
Cohesion: 0.33
Nodes (6): Baglam, ABONELIK, DIGER, SPOTIFY, TARAYICI, YOUTUBE

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
- **50 isolated node(s):** `YOUTUBE`, `SPOTIFY`, `ABONELIK`, `TARAYICI`, `DIGER` (+45 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 373 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **41 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Araclar` connect `Araclar` to `KanalListesiPenceresi.kt`, `Baglam`, `Adb`, `Uygulamalar`, `AsistanServisi`, `EkranGoruntusu`, `YaziTanima.kt`, `Araclar.kt`, `Ses.kt`?**
  _High betweenness centrality (0.201) - this node is a cross-community bridge._
- **Are the 8 inferred relationships involving `Araclar` (e.g. with `Adb` and `Ayarlar`) actually correct?**
  _`Araclar` has 8 INFERRED edges - model-reasoned connections that need verification._
- **What connects `YOUTUBE`, `SPOTIFY`, `ABONELIK` to the rest of the system?**
  _50 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `KaynakTest` be split into smaller, more focused modules?**
  _Cohesion score 0.05002337540906966 - nodes in this community are weakly interconnected._
- **Why does `AsistanServisi` connect `AsistanServisi` to `GeminiLive.kt`, `TelefonKoprusu`, `.degerlendir`, `Araclar`, `Adb`, `Panel`, `.oturumBaslat`, `.bitir`, `Ses.kt`, `.taniYaz`, `HizliYol`, `Uygulamalar`, `AsistanAsistan.kt`, `AsistanServisi.kt`, `.onServiceConnected`, `AracKorumasi`, `Guncelleme`, `Uyandirma`, `Durum`?**
  _High betweenness centrality (0.164) - this node is a cross-community bridge._
- **Are the 4 inferred relationships involving `AsistanServisi` (e.g. with `AracKorumasi` and `HizliYol`) actually correct?**
  _`AsistanServisi` has 4 INFERRED edges - model-reasoned connections that need verification._
- **Should `GeminiLive.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.06448412698412699 - nodes in this community are weakly interconnected._