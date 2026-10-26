# TV Asistan

**Jülide** — Android TV için bağımsız, Türkçe **Gemini Live** sesli asistanı.
Hedef cihaz: **Toshiba 50UA3D63DT** (Android TV).

Sesli komutla:
- Uygulama açma — "YouTube'u aç", "Netflix'i aç", "Ayarları aç"
- İçerik arama — "Kurtlar Vadisi'ni bul", "YouTube'da Barış Manço"
- Tarayıcı — "Tarayıcıyı aç, Çorlu hava durumunu ara"
- Ekranda gezinme — "Ara'ya tıkla", "Aşağı in", "Geri", "Ana ekran"
- Ses ve medya — "Sesi yüzde otuz yap", "Duraklat", "Devam et"
- Cihaz durumu — "İnternet var mı?", "TV'nin durumu ne?"
- TV'yi kapatma — "Televizyonu kapat", "30 dakika sonra TV'yi kapat", "Zamanlayıcıyı iptal et"
- Ayarlar — "Wi-Fi ayarlarını aç", "Ses ayarlarını aç", "YouTube'un uygulama bilgisini aç"
  (devre dışı bırakma/kaldırma düğmesine Jülide basmaz; kullanıcı kumandayla basar)

CleanYourTV incelemesi ve yol haritası: [`docs/CLEANYOURTV_ENTEGRASYON_RAPORU.md`](docs/CLEANYOURTV_ENTEGRASYON_RAPORU.md)

## Mimari

| Parça | Görev |
|---|---|
| `AsistanServisi` (Erişilebilirlik Servisi) | Kumanda tuşunu yakalar, oturumu yönetir, ekranı okur/tıklar/yazar |
| `GeminiLive` | Gemini Live WebSocket istemcisi (model zinciriyle yedekli bağlantı) |
| `Uzak` | `config/ayarlar.json` — model, ses, zaman aşımı, konuşma eşiği (beyaz liste + ETag) |
| `Kasa` | Gemini anahtarını Android Keystore (AES-256-GCM) ile şifreler |
| `SesKapisi` | Yerel konuşma algılama: sessizlikte Gemini'ye ses göndermez |
| `Guncelleme` | GitHub Releases'tan yeni APK'yı indirip kurulum ekranını açar |
| `Araclar` | Gemini'nin çağırdığı TV araçları (`uygulama_ac`, `icerik_ara`, `tikla`, `yaz`, `tus`, `ses`, `medya` …) |
| `Mikrofon` / `Oynatici` | 16 kHz PCM giriş, 24 kHz PCM çıkış |
| `Panel` | Ekranın altında beliren durum/transkript paneli |

Gemini API anahtarı **repoda ve APK'da yoktur** (repo herkese açık). Anahtar TV'deki ayar ekranından bir kez girilir ve yalnızca o TV'de saklanır.

## Güncellemeler

| Ne değişti | TV'ye nasıl ulaşır |
|---|---|
| `config/ayarlar.json` (model, ses, zaman aşımı, konuşma eşiği) | **Birkaç dakika içinde** — ETag ile denetlenir, APK gerekmez. Yalnızca doğrulanmış alanlar okunur; model talimatı uzaktan değişmez |
| Kod (yeni araç, hata düzeltmesi) | Push → Actions APK'yı Releases'a koyar → TV 6 saatte bir denetler, **ana ekrandayken** indirip kurulum ekranını açar → bir kez **Yükle** |

Elle: uygulamada "Güncellemeleri denetle ve kur" veya sesli "kendini güncelle".
İlk otomatik güncellemeden önce TV Asistan'a "bilinmeyen kaynaklardan yükleme" izni verilmeli (uygulamadaki düğme).
Android, Play Store dışı uygulamaların **sessiz** kurulmasına izin vermez; bu yüzden tek onay şart.

## Derleme

`main` dalına her push'ta GitHub Actions APK'yı derler ve **Releases**'a koyar.
Her sürüm aynı anahtarla imzalanır; güncelleme kaldırma gerektirmez.
İmza anahtarı **repoda değildir**; GitHub Secrets'ta durur (`IMZA_KEYSTORE_B64`, `IMZA_SIFRE`). Secret'lar yoksa derleme bilinçli olarak durur.

## TV'ye kurulum

**Downloader ile (önerilen):**
1. Play Store → **Downloader** (AFTVnews) kur.
2. Adres: `https://github.com/klncumut6565/tv-asistan/releases/latest/download/tv-asistan.apk`
3. İlk seferde "Bilinmeyen kaynaklar" iznini Downloader'a ver.

**ADB ile (Wi-Fi):**
```
adb connect <TV_IP>:5555
adb install -r tv-asistan.apk
```

## İlk ayar (TV'de)

1. **TV Asistan**'ı aç → Gemini API anahtarını gir (aistudio.google.com) → **Kaydet ve dene**.
2. **Mikrofon izni ver**.
3. **Erişilebilirlik ayarlarını aç** → TV Asistan → Aç.
   Toshiba'da yol: Ayarlar → Cihaz Tercihleri → Erişilebilirlik.
   Menü yoksa veya "Tamam" işe yaramıyorsa (Android 14, dışarıdan yüklenen uygulamaları kısıtlar):
   ```
   adb shell appops set com.siam.tvasistan ACCESS_RESTRICTED_SETTINGS allow
   adb shell settings put secure enabled_accessibility_services com.siam.tvasistan/.AsistanServisi
   adb shell settings put secure accessibility_enabled 1
   # İsteğe bağlı, bir kez: servis "Durmaya zorla" ile kapanırsa Jülide açılınca kendini geri açsın
   adb shell pm grant com.siam.tvasistan android.permission.WRITE_SECURE_SETTINGS
   ```
4. **Tetik tuşunu öğret** → kumandada kullanmak istediğin tuşa bas.
   Varsayılan: Asistan / Arama tuşu. Google Asistan tuşu yakalanamazsa renkli bir tuş (ör. mavi) öğret.
5. Tuşa bas → ekranın ortasında yarı saydam mor enerji küresi çıkar (TMGD web asistanındakiyle aynı) → konuş. **Sürekli sohbet:** küre, uygulamalar arasında
   gezinirken de açık kalır ve dinlemeye devam eder.
   - Kapatmak: **"sohbeti kapat"** de ya da tetik tuşuna **uzun bas** (1 sn).
   - Video/müzik çalarken TV sesi mikrofona girmesin diye Jülide susar; **tetik tuşuna kısa bas**:
     video duraklar (`konusurken_duraklat: false` → yalnız sesi kısılır), sen konuştukça dinler,
     Jülide cevabını bitirince video kaldığı yerden devam eder. Jülide konuşurken kısa basış onu susturur.
   - **"Jülide" uyandırma kelimesi:** video/canlı yayın açıkken tuşa basmadan **"Jülide, …"** de.
     Ses Gemini'ye gitmez; TV'nin içinde, internetsiz (Vosk, küçük Türkçe model ~37 MB, ilk sohbette bir kez
     indirilir, SHA-256 doğrulanır) yalnız bu kelime aranır. Duyunca TV kısılır/durur ve komut iletilir.
     Kapatmak: `"uyandirma": false`; tanıyıcının yazımları: `"uyandirma_kelimeleri"`.
   - Mikrofon önce yankı engelleyici kaynağı (VOICE_COMMUNICATION) dener: TV donanımı destekliyorsa
     hoparlörden çıkan sesi mikrofondan çıkarır. Desteklemiyorsa kendiliğinden normal kaynağa döner
     (`yanki_engelle: false` ile kapatılabilir).
   - `config/ayarlar.json` → `"surekli_sohbet": false` yapılırsa eski davranış (boşta zaman aşımıyla kapanma).

## İlk testte kontrol edilecekler

- [ ] Tetik tuşu yakalanıyor mu (Asistan tuşu / renkli tuş)
- [ ] Mikrofon sesi geliyor mu (kumanda mikrofonu mu, harici USB/Bluetooth mikrofon mu gerekiyor)
- [ ] Gemini Live bağlanıyor mu (panelde model adı görünür)
- [ ] Otomatik güncelleme: yeni push sonrası ana ekranda kurulum ekranı geliyor mu
- [ ] "YouTube'u aç" → uygulama açılıyor mu
- [ ] "Netflix'te X'i bul" → arama kutusuna yazıyor mu
- [ ] Hata olursa: `adb logcat -s TvAsistan GeminiLive`

## Bilinen sınırlar

- Kumanda mikrofonunun uygulamalara açık olup olmadığı cihaza bağlıdır; kapalıysa USB veya Bluetooth mikrofon gerekir.
- Netflix gibi bazı uygulamalar ekran içeriğini erişilebilirliğe kapatabilir; açma ve arama çalışır, içeride tıklama sınırlı kalabilir.
- Android 10–12'de yön tuşları odak üzerinden taklit edilir (Android 13+ gerçek D-pad).
- Asistan konuşurken mikrofon modele gönderilmez (TV hoparlörü yankısını önlemek için). Araya girmek için tetik tuşuna bas.
