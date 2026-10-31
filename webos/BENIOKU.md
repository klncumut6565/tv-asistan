# Jülide — LG webOS sürümü

Android TV sürümünden (`app/`) **tamamen ayrıdır**; Android koduna dokunmaz. Aynı telefon mikrofonu sayfasını
(`web/`) ve aynı Gemini anahtar sayfasını kullanır.

## Neler çalışır
| Özellik | Durum |
|---|---|
| Türkçe sesli sohbet (Gemini Live), kota dolunca sıradaki model | ✅ |
| İnternet araştırması, döviz (TCMB/ECB), hava (Open-Meteo) | ✅ (TV tarayıcısının erişim izinlerine bağlı — Teşhis ekranı gösterir) |
| Uygulama açma, ses, medya, kanal, giriş kaynağı, TV'yi kapatma | ✅ LG'nin yerel kontrol bağlantısı (SSAP) ile |
| Kumanda tuşları, LG imleci (taşı/tıkla/kaydır), arama kutusuna yazma | ✅ |
| Başka uygulamanın ekranını okuma, numaralı tıklama | ❌ webOS buna izin vermez |
| Mikrofon | Telefon sayfası (TV kumandasının mikrofonu LG'nin kendi asistanına ayrılmıştır) |

## Kurulum (bir kez)
1. **LG geliştirici hesabı:** https://webostv.developer.lge.com adresinden ücretsiz üye olun.
2. **TV'de Geliştirici Modu:** LG Content Store'dan **Developer Mode** uygulamasını kurun, hesabınızla girin,
   *Dev Mode Status*'u açın (TV yeniden başlar), sonra *Key Server*'ı açın. Ekrandaki **IP adresini** not edin.
3. **TV ayarı:** Ayarlar → Genel → Cihazlar → Harici Cihazlar → **LG Connect Apps** (ve *Mobil ile TV'yi aç*) açık olsun.
4. **Bilgisayar (aynı Wi-Fi):** Node.js kurun, sonra CMD'de:
   ```
   npm i -g @webos-tools/cli
   ares-setup-device --add peaq --info "{'host':'TV_IP','port':'9922','username':'prisoner'}"
   ares-novacom --device peaq --getkey
   ```
   (`--getkey` sorduğu parolayı Developer Mode ekranındaki **Passphrase**'ten girin.)
5. **Paketi indir ve kur:** GitHub → Releases → en yeni **Jülide webOS** sürümündeki `julide-webos.ipk`:
   ```
   ares-install --device peaq julide-webos.ipk
   ares-launch --device peaq com.siam.julide
   ```
6. **İlk açılış:** TV'de "izin verilsin mi?" sorusu çıkarsa **İzin ver**. Sağdaki karekodla Gemini anahtarını telefondan
   kaydedin; soldaki karekodla telefon mikrofonu sayfasını açıp **Başlat**'a basın.
7. **Teşhis** düğmesi bu TV'nin hangi kontrollere ve sitelere izin verdiğini listeler — sorun olursa ekran fotoğrafını gönderin.

## Bilinen sınırlar
- **Geliştirici Modu süresi:** Belirli bir süre sonra kapanır; Developer Mode uygulamasındaki **Extend** ile süre dolmadan uzatın.
  Süre dolarsa uygulama kaldırılır, 5. adımı tekrarlayın.
- **Arka plan:** Jülide başka bir uygulamayı (YouTube vb.) öne açınca webOS onu arka planda askıya alabilir. Bu durumda
  sohbet o uygulama kapanınca devam eder. (Teşhis/gerçek testte netleşecek.)
