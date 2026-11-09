# Oturum Özeti — 6–10 Kasım 2026

## Tamamlananlar (hepsi main'de, CI yeşil)
| Commit | İş |
|---|---|
| b554bf8 | Hızlı yol: kanal/HDMI/ses/medya komutları LLM beklemeden yerelde (HizliKomut, HizliYol) |
| eb38bb9 | Tarayıcı: Google sonuçlarını sırayla açma (arama_sonucu), kumandayla sonuçlar arasında gezinme, ADB ile kaydırma, "Tarayıcı" varsayılan tarayıcı |
| a693563 | Kanal hafızası ("4. kanalda Show TV var"), kayıtlı numara önce; "kanal listesini göster" kartı |
| 430895c | Ses tanılama (yalnız log: write() dönüşü, ERKEN duraklatma, tur no) + araç tekrar koruması |
| f34b997 | Kalabalık modu (ayar, varsayılan KAPALI) — DinlemeKapisi |
| ddfb5e5 | Gemini sessizlik süresi uzaktan ayar (config `sessizlik_ms`, varsayılan 800) |
| 904c25d | Kalıcı bellek: hatirla / unut / hatirladiklarim |

## Açık işler / TV'de doğrulanacaklar
- Hiçbiri TV'de denenmedi. `adb logcat | findstr TANI` ile test edilmeli.
- Hızlı yol bekleme süresi (6 sn) < canlı TV hazırlama (7,5 sn) → nadir çift kanal atlama riski (performans testinde bulundu, düzeltilmedi).
- Ses kopması tekrar ederse `TANI tur #N` satırındaki `ERKEN` sayısına bakılacak.
- Kalabalık modu "Jülide" kelimesi yalnız telefon/USB mikrofonla çalışır (dahili mikrofon ses vermiyor).
- Cümleler bölünürse `config/ayarlar.json` → `"sessizlik_ms": 1200`.

## Kararlar
- Hermes/Zoey'den yalnız küçük fikirler alındı; skill motoru, iş akışı motoru, konuşmacı ayrıştırma, ses sağlayıcı soyutlaması ERTELENDİ.
- Bellek yalnız açık kullanıcı isteğiyle kaydeder; hassas veri reddedilir.
