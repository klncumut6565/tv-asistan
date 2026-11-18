# AGENTS.md — tv-asistan (Jülide)

Claude Code, Codex ve diğer kod ajanları için proje kuralları. ECC (affaan-m/ECC, MIT) çalışma disiplininden uyarlandı.
**EN ÖNEMLİ KURAL: ÇALIŞAN SİSTEMİ BOZMA.**

## Proje özeti
- Android TV için Türkçe Gemini Live sesli asistanı **Jülide**. Hedef: Toshiba 50UA3D63DT (Vestel).
- `app/` Android (Kotlin, paket `com.siam.tvasistan`), `web/` telefon mikrofonu sayfası (GitHub Pages + Vercel yedek), `webos/` LG webOS sürümü, `config/ayarlar.json` uzaktan ayarlar.
- Mimari tablosu: `README.md`. Önceki oturumlar: `docs/OTURUM_OZETI_*.md`. Kod haritası: `graphify-out/GRAPH_REPORT.md` (önce buna bak).

## Çalışma akışı (her değişiklikte)
1. **Araştır:** graphify grafiği → ilgili dosyalar → son oturum özeti. Benzer çözüm repoda var mı bak.
2. **Kanıtla:** Kök nedeni log, hata veya kod satırıyla göster. Kanıt yoksa önce yalnız-log tanılama ekle (`TANI` etiketi), düzeltmeye geçme.
3. **Planla:** Değişecek ve DEĞİŞMEYECEK dosyalar, risk, test yolu.
4. **Onay:** Ses hattı, Gemini bağlantısı, imza/CI, güncelleme mekanizması, ADB/güç komutları gibi riskli işlerde push'tan ÖNCE kullanıcı onayı.
5. **İzole uygula:** En küçük diff. İlgisiz refactor, yeniden adlandırma, format değişikliği YOK.
6. **Doğrula:** `./gradlew :app:testDebugUnitTest` ve `./gradlew :app:assembleDebug`. Kırmızıysa DUR.
7. **Bağımsız incele:** Diff'i "başkası yazdı" gözüyle oku. Değişiklik tüm yollara uygulandı mı: hızlı yol (`HizliYol`/`HizliKomut`) ve Gemini aracı (`Araclar`), TV mikrofonu ve telefon mikrofonu (`TelefonKoprusu`), Android ve webOS?
8. **Regresyon listesi:** aşağıdaki ilgili maddeleri geç.
9. **Raporla:** Build/test sonucu + "TV'de doğrulandı / doğrulanmadı" ayrı yazılır. Yerel test geçti diye TV'de çalışıyor sayılmaz.

## DOKUNULMAZLAR (açık istek olmadan değişmez)
- `Panel.kt` yarı saydam animasyonlu orb overlay ve asistan mantığı.
- Çalışan ses hattı: `Mikrofon`, `MikrofonServisi`, `MikrofonBaslatici`, `Oynatici`, `SesKapisi`, `DinlemeKapisi`, `GeminiLive`. Bir ses sorunu çözülürken diğer ses yollarına dokunulmaz.
- İmza: anahtar yalnız GitHub Secrets'ta (`IMZA_KEYSTORE_B64`, `IMZA_SIFRE`). Repoya keystore veya şifre KONMAZ.
- Gemini API anahtarı repoda ve APK'da YOK (repo herkese açık). Yalnız TV'de `Kasa` ile şifreli saklanır.
- `versionCode` = `GITHUB_RUN_NUMBER`; elle sürüm numarası yazılmaz.
- Android kodu, webOS çalışması için değiştirilmez.

## Regresyon kontrol listesi
**Ses ve oturum**
- [ ] Eşzamanlı başlat/durdur, `live == null`, tekrar bağlanma ve coroutine çakışması güvenli mi?
- [ ] Mikrofon yaşam döngüsü: servis yeniden başlayınca, uygulama değişince konuşma sürüyor mu ("sohbeti kapat"a kadar)?
- [ ] Uzun cevaplarda kesik kesik konuşma yok mu? (`TANI tur #N` → `ERKEN` sayısı)
- [ ] Gemini model zinciri / kota bitince yedek modele ve ücretsiz alternatife geçiş çalışıyor mu?

**Davranış**
- [ ] Yalnızca TÜRKÇE ve KADIN sesi.
- [ ] "Açtım" yalnız gerçekten açıldıysa; aksi halde başarı iddia edilmez.
- [ ] "Televizyonu kapat" önce "kapatayım mı?" diye sorar ("sayfayı kapat" TV'yi kapatmaz).
- [ ] Hızlı yol (kanal, HDMI, ses, medya) LLM beklemeden yerelde çalışır.
- [ ] Platform söylenmezse açık uygulamada arar; birden çok platform mümkünse önce sorar.
- [ ] Film aramaları yalnız Prime Video ve TOD (+ YouTube/Tarayıcı); müzik YouTube veya Spotify.
- [ ] Kanal hafızası, kalabalık modu (varsayılan KAPALI), "Jülide" uyandırma (varsayılan KAPALI), şaka modu bozulmadı.
- [ ] Devre dışı bırakma / kaldırma düğmelerine Jülide basmaz.

**Dağıtım**
- [ ] `config/ayarlar.json` değiştiyse yalnız beyaz listedeki alanlar; JSON geçerli.
- [ ] `web/` değiştiyse hem GitHub Pages hem Vercel yedeği çalışır (ev Wi-Fi vercel.app açmıyor → birincil GitHub Pages).
- [ ] CI (`apk.yml`) yeşil, Releases'a APK düştü.

## Test komutları
```bash
./gradlew :app:testDebugUnitTest          # birim testleri (app/src/test)
./gradlew :app:assembleDebug              # derleme
# Davranış testi Gemini kotası harcar: yalnız elle, Actions → "Davranış testi (Gemini)"
# TV'de: adb connect 192.168.1.86 && adb logcat | grep TANI
```
Yeni hata düzeltmesinde mümkünse önce hatayı yakalayan birim testi yaz (ör. `HizliKomutTest`, `AracKorumasiTest` deseni), sonra düzelt.

## Push ve rapor
- Push sonrası yalnız commit hash ve kısa özet. İndirme/ZIP linki paylaşılmaz.
- Büyük iş bitince veya bağlam dolmadan `docs/OTURUM_OZETI_YYYY-AA-GG.md` yazılır: tamamlananlar (commit), kararlar, açık işler, TV'de doğrulanacaklar, dokunulmaması gerekenler.
- Aynı hata iki kez tekrarlandıysa kalıcı kuralı bu dosyaya ekle.
