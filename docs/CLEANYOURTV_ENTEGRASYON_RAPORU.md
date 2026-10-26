# CleanYourTV → Jülide (tv-asistan) Entegrasyon Raporu

**Hazırlanma:** 26.10.2026 · **Aşama:** Yalnızca araştırma ve planlama. Hiçbir dosya değiştirilmedi, commit veya PR yok.

**Revizyon 1 (26.10.2026):** TV'nin Vestel üretimi olduğu ve Prime Video'nun aktif kullanıldığı kullanıcı tarafından teyit edildi. Bölüm 3.1, F4, F10, 8 ve 9 güncellendi.

**İncelenen sürümler**

| Depo | Commit | Tarih |
|---|---|---|
| `klncumut6565/tv-asistan` | `14d98a7` | 26.10.2026 06:53 |
| `Gathin23/cleanyourtv` | `76a9312` | 26.10.2026 |

Bütün dosya yolları ve satır numaraları bu iki commit'e göredir. "YENİ" diye işaretlenen dosyalar depoda **yoktur**, öneridir.

---

## 1. Yönetici özeti

- **Talep edilen P1 komutlarının çoğu Jülide'de ZATEN VAR:** YouTube'u açma, uygulama açma, ana ekrana dönme ve uygulamaları listeleme. Bunlar yeniden geliştirilmemeli (Bölüm 2).
- CleanYourTV'nin çekirdeği bilgisayarda çalışan **Python + ADB** araçlarıdır (`tools/`). Bu kod Android uygulamasına **doğrudan taşınamaz**. Değerli olan, depodaki **Android launcher'ının (Java)** ve **eklentilerin** kullandığı Android API desenleridir.
- **En yüksek değerli 4 bulgu:**
  1. **Uyku / "TV'yi kapat":** `GLOBAL_ACTION_LOCK_SCREEN` ile yapılır. Jülide zaten erişilebilirlik servisi olduğu için yeni izin gerekmez.
  2. **Bağlantı ve cihaz durumu:** ConnectivityManager ile yapılır. Tek gereken, normal seviyeli `ACCESS_NETWORK_STATE` izni.
  3. **Erişilebilirlik servisinin kendini onarması (ServiceRepair deseni):** Zorla durdurma sonrası Jülide'nin sessizce kapanmasını önler.
  4. **Vestel/Toshiba saha bilgisi:** `examples/toshiba-cosmos-android-tv/debloat.txt` dosyası. Canlı TV paketi, firmware'in yuttuğu tuşlar, korunması gereken sistem paketleri.
- **"Sesle uygulamayı devre dışı bırak" Jülide'ye ÖNERİLMEZ.** `pm disable-user` yalnızca ADB shell yetkisiyle çalışır. Jülide'nin kendi kendine ADB bağlantısı açması TV'yi ağda savunmasız bırakır. Güvenli alternatif: sesle "uygulama bilgi sayfası"nı açmak, devre dışı bırakmayı kullanıcının kumandayla yapması (Bölüm 6, F9).
- **Lisans:** Ana depo MIT. Kopyalamak serbest, telif bildirimi korunmalı. Öneri: kodu kopyalamak yerine desenleri Kotlin ile yeniden yazmak ve kaynağa atıf vermek (Bölüm 7).

---

## 2. Mevcut TV Asistanının doğrulanmış yetenekleri

### 2.1 Teknoloji ve mimari

| Konu | Bulgu | Kaynak |
|---|---|---|
| Dil / derleme | Kotlin, AGP (Kotlin DSL), JVM 17 | `app/build.gradle.kts` |
| SDK | `minSdk 26`, `targetSdk 34`, `compileSdk 35` | `app/build.gradle.kts` |
| Bağımlılıklar | okhttp 4.12.0, coroutines 1.8.1, Vosk 0.3.47 + JNA (uyandırma kelimesi) | `app/build.gradle.kts` |
| Ana servis | `AsistanServisi`, bir **AccessibilityService**. Tuş filtreleme, pencere içeriği okuma ve hareket yetkisi var | `AndroidManifest.xml`, `res/xml/erisilebilirlik.xml` |
| Mikrofon | `MikrofonServisi` (foregroundServiceType=microphone) ve `MikrofonBaslatici` (görünmez başlatıcı) | `AndroidManifest.xml`, `MikrofonServisi.kt`, `MikrofonBaslatici.kt` |
| Sesli model | `GeminiLive.kt` (Gemini Live WebSocket). Araç çağrıları **tek iş parçacıklı `aracYurutucu`** üzerinde çalışır, ana iş parçacığında değil | `GeminiLive.kt:54`, `GeminiLive.kt:232` |
| Uyandırma kelimesi | `Uyandirma.kt` (Vosk, internetsiz) | `Uyandirma.kt` |
| Araç tanımları ve yürütme | `Araclar.bildirimler()` ve `Araclar.calistir()` | `Araclar.kt:49-111`, `Araclar.kt:150-189` |
| Uzaktan ayar | `Uzak.kt` ve `config/ayarlar.json`. Beyaz listeli alanlar, ETag ile alınır. **ADB ile ilgisi yok** | `Uzak.kt` |
| Güncelleme | `Guncelleme.kt` ve `REQUEST_INSTALL_PACKAGES` | `Guncelleme.kt`, manifest |
| Dijital asistan denemesi | `AsistanAsistan` (VoiceInteractionService, gövdesi boş) | `AsistanAsistan.kt` |

### 2.2 Sesli komut akışı

1. Tetik tuşu (`AsistanServisi.onKeyEvent`, satır 200) veya uyandırma kelimesi geldiğinde `oturumBaslat()` çağrılır.
2. `Uzak.getir()` ve `araclar.sistemTalimati()` **IO dispatcher** üzerinde çalışır, ardından `GeminiLive.baglan()` yapılır (`AsistanServisi.kt:273-284`).
3. Model bir araç çağırınca: `GeminiLive` → `aracYurutucu` → `AsistanServisi.aracCagrisi()` (satır 707) → `Araclar.calistir()`.
4. Bağlantı koparsa `yenidenBaglan()` en fazla 5 kez artan beklemeyle yeniden dener (`AsistanServisi.kt:498-553`).

### 2.3 Mevcut araçlar (yeniden önerilmeyecek)

| Talep edilen komut | Jülide'deki karşılığı | Kaynak |
|---|---|---|
| "YouTube'u aç" | `uygulama_ac` (genel), `youtube_ara` | `Araclar.kt:50`, `Araclar.kt:297` |
| "Netflix'i aç" | `uygulama_ac` (genel). **Abonelik yok**, talimat Netflix önermeyi yasaklıyor | `Araclar.kt:50`, `config/ayarlar.json` (`abonelikler`) |
| "Ana ekrana dön" | `tus` → `ana_ekran` → `GLOBAL_ACTION_HOME` | `Araclar.kt:497` |
| "Yüklü uygulamaları listele" | `uygulamalari_listele` → `Uygulamalar.liste()` (LEANBACK_LAUNCHER ve LAUNCHER, 60 sn önbellek) | `Uygulamalar.kt:32-63` |
| "Şu uygulamayı aç" | `uygulama_ac` → `Uygulamalar.bul()` (Türkçe normalleştirmeli puanlama) ve `ac()` | `Uygulamalar.kt:81-117` |
| Uygulamayı kapatma | `uygulama_kapat` → `killBackgroundProcesses`. Ana ekran ve Jülide korunuyor | `Araclar.kt:220-241`, `Uygulamalar.kt:124` |
| Aktif/pasif uygulama seçimi | `Ayarlar.pasifUygulamalar` | `Ayarlar.kt` |
| Gezinme | `tus` (DPAD, geri, son uygulamalar), `kaydir`, `tikla`, `numaralari_goster`, `numaraya_tikla`, `yaz` | `Araclar.kt:483-590`, `Araclar.kt:698-750` |
| Ekran okuma | `ekrani_oku` (Accessibility node ağacı) | `Araclar.kt:395` |
| Ses ve medya | `ses` (AudioManager STREAM_MUSIC), `medya` (KEYCODE_MEDIA_*) | `Araclar.kt:751-790` |
| Canlı kanal | `kanal_ac`, `kanal_degistir` (TvContract, ekrandan arama yedeği) | `Kanallar.kt`, `Araclar.kt:594-690` |
| Abonelikte arama | `abonelikte_ara` (Prime Video, TOD) | `Araclar.kt:243` |
| Hassas ekran koruması | `KORUMALI_PAKETLER` (paket yükleyici ve izin denetleyici), `HASSAS` regex ve sesli onay (`onayli`) | `Araclar.kt:315-325` |

### 2.4 Olmayan yetenekler (doğrulandı)

- **ADB bağlantısı uygulama içinde YOK.** ADB yalnızca kurulum talimatında, bilgisayardan kullanılıyor (`README.md:53-69`, `MainActivity.kt:131-137`).
- HDMI/kaynak değiştirme yok (`TvInputManager` kullanımı yok).
- Cihaz/bağlantı durumu aracı yok. `ACCESS_NETWORK_STATE` izni yok.
- TV'yi kapatma veya uyku zamanlayıcısı yok.
- Erişilebilirlik servisi kapanırsa kendini geri açma mekanizması yok. Kullanıcı ADB komutunu elle çalıştırmak zorunda (`MainActivity.kt:136-137`).
- Açılışta otomatik başlatma (`BOOT_COMPLETED`) yok.
- Uygulama kategorisi bilgisi yok.

---

## 3. CleanYourTV'nin önemli dosyaları ve işlevleri

Depo üç katmandan oluşur:
1. **`tools/`**: Bilgisayarda çalışan Python ve shell araçları, ADB üzerinden.
2. **`launcher/`**: Android ana ekran uygulaması (Java, paket `com.gathin.tvhome`).
3. **`addons/`**: Bağımsız küçük Android TV uygulamaları, farklı katkıcılardan.

| # | İnceleme alanı | Dosya(lar) | Ne yapıyor |
|---|---|---|---|
| 1 | ADB bağlantısı | `tools/tv.py`, `tools/native-adb.md`, `tools/requirements.txt` | `adb-shell==0.4.4` Python kütüphanesiyle TCP 5555'e bağlanır. İstenirse Google `adb` ile eşleştirilmiş (TLS) kablosuz hata ayıklama kullanır |
| 2 | Paket listeleme | `tools/disable.sh`, `tools/privacy.py`, `launcher/.../AppLibraryDialog.java:226-231` | Bilgisayar tarafı: `pm list packages`. Launcher tarafı: `queryIntentActivities` ile LEANBACK_LAUNCHER/LAUNCHER (Jülide ile **aynı yöntem**) |
| 3 | Uygulama başlatma | `launcher/.../LaunchActions.java`, `OpenOnStart.java` | Pakete özel eylem varsa onu, yoksa normal launch intent'i kullanır |
| 4 | Ana ekran ve gezinme | `launcher/.../HomeKeyService.java`, `InputKeyService.java`, `RemoteKeys.java` | Erişilebilirlik servisiyle Home/Input tuşlarını yakalayıp yönlendirir |
| 5 | Devre dışı bırakma / geri alma | `tools/disable.sh`, `tools/restore.sh`, `tools/recovery.py`, `tools/check.py` | `pm disable-user --user 0`. Önce durum kaydı alınır (`~/.cleanyourtv/`). `restore.sh` kaydı geri oynatır. `check.py` firmware güncellemesinden sonra sapmayı bulur. **Kaldırma (uninstall) yok** |
| 6 | Sistem / üretici paketleri | `tools/disable.sh:61-62` (`CORE_RE`, `CORE_OK`), `:74` (setupwraith Wi-Fi koruması), `:101-…` (Home koruması), `examples/<model>/debloat.txt` (333 model) | Çekirdek paketleri kapatmayı reddeder. Wi-Fi'ın sahibi olan paketi atlar. Home'u yanıtlayan son launcher'ı kapatmaz |
| 7 | Launcher mimarisi | `launcher/.../MainActivity.java` (~3000+ satır), `Config.java`, `ThemeProvider.java`, `themes/` | Tek Activity, yapılandırma ve tema dosyasıyla yönetilen dock/ızgara |
| 8 | Kumanda ve TV arayüzü | `GridNavigation.java`, `VolumeKeys.java`, `InputKeyService.java`, `QuickSettingsActivity.java` | DPAD odak yönetimi, ses tuşları, Input tuşu, hızlı ayarlar paneli |
| 9 | Hata ve kopma yönetimi | `ServiceRepair.java`, `GuardRevival.java`, `GuardKeep.java`, `tools/adb-closed.py` | Kapatılan erişilebilirlik servisini geri açar. Firmware'in öldürdüğü süreci NotificationListener yeniden bağlanmasıyla diriltir. ADB portunun kapandığını doğrular |
| + | Kaynak (HDMI) değiştirme | `MainActivity.java:2860` (giriş listesi), `:2949-2995` (`openInput`), `InputSources.java`, `InputNames.java` | `TvInputManager.getTvInputList()` ve `TvContract.buildChannelUriForPassthroughInput()`, canlı TV paketine yönlendirilmiş `ACTION_VIEW` |
| + | Uyku / TV'yi kapatma | `addons/sleep-timer/.../SleepService.java:20`, `addons/sleep-timer/README.md` | `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`. Android 9+ cihazı uyutur |
| + | ADB durum denetimi | `addons/remote-access/.../Access.java` | `adb_enabled`, kablosuz hata ayıklama ve `service.adb.tcp.port` bilgisinden "ADB ağdan açık mı" sonucunu çıkarır |
| + | Ayarlar yedek yolu | `launcher/.../SystemSettings.java` | Standart Settings intent'i yanıtsızsa üreticinin ayar bileşenine geçer |
| + | Ağ durumu | `MainActivity.java:835-844` | `registerDefaultNetworkCallback` ve VPN callback |

### 3.1 Toshiba / Vestel saha notları (`examples/toshiba-cosmos-android-tv/debloat.txt`)

Bu dosya belgelenen cihaz için doğrulanmış bilgiler içeriyor: Vestel Cosmos170 / yeoksam, Android TV 11, MediaTek, `low_ram`, yaklaşık 1 GB RAM.

- Canlı TV paketi: `com.mediatek.wwtv.tvcenter`. **Asla kapatılmamalı.**
- `com.cltv.fast` canlı TV **değil**. Etkinlikleri Vestel tarafından devre dışı bırakılmış bir "tuzak" paket.
- Korunması gerekenler: `com.vestel.tv.service`, `com.vestel.tv.backlight`, `com.vestel.tv.keycontroller`, `com.vestel.tv.servicepatch`, `com.vestel.tv.rcupdater`, `com.mediatek.tvinput`, `com.mediatek.tv.service`, `com.mediatek.tvinputservice.arbitratorservice`.
- Kumandadaki **YouTube ve Prime tuşları Android'e ulaşmıyor**, firmware altında işleniyor.
- **Ses tuşları erişilebilirlik filtresine gelmeden firmware tarafından tüketiliyor.**
- Bekleme modunda Wi-Fi ve ADB kapanıyor. Erişilebilirlik servisleri günlerce süren bekleme döngüsünden sağ çıkıyor, yalnızca zorla durdurma onları kapatıyor.
- Ekran görüntüsü alırken `exec-out screencap` bozuk çıkıyor.

> ✅ **Kullanıcı teyidi (26.10.2026):** 50UA3D63DT, Toshiba markalı ve **Vestel üretimi**. Vestel/MediaTek saha notları bu TV için geçerli kabul edilir.
>
> ⚠️ **Hâlâ doğrulanması gereken:** Firmware'in belgelenen birimle **aynı sürüm** (Cosmos170 / yeoksam, Android 11) olup olmadığı. Belgelenen birimde mikrofon yok, sizin TV'nizde var, yani model veya donanım farklı. Paket adları aynı olabilir ama kanıtlanmalı. Kontrol komutları:
> - `adb shell getprop ro.build.fingerprint`
> - `adb shell getprop ro.product.device`
> - `adb shell pm list packages | grep -E "vestel|mediatek"`
>
> 🚫 **Bu liste Prime Video'yu (`com.amazon.amazonvideo.livingroom`) ve YouTube'u devre dışı bırakıyor.** Prime Video bu TV'de **aktif olarak kullanılıyor**. Liste olduğu gibi **KESİNLİKLE UYGULANMAMALI**.

---

## 4. Mimari ve teknoloji karşılaştırması

| Boyut | Jülide (tv-asistan) | CleanYourTV |
|---|---|---|
| Çalıştığı yer | TV üzerinde Android uygulaması | Bilgisayar (Python/bash) ve TV'de launcher |
| Dil | Kotlin | Python, bash, PowerShell, Java (launcher ve eklentiler) |
| Yetki modeli | Uygulama izinleri ve AccessibilityService | **ADB shell (uid 2000)**, launcher için `WRITE_SECURE_SETTINGS` (ADB ile verilir) |
| Paket yönetimi | Listeleme, açma, arka plan sonlandırma | `pm disable-user` / `pm enable` (yalnızca shell) |
| Kumanda | Tetik tuşu, `GLOBAL_ACTION_*` | Home/Input tuşu yönlendirme, ses tuşları |
| Hata dayanıklılığı | Gemini yeniden bağlanma | ServiceRepair, GuardRevival, kurtarma kaydı |
| Test | Yok | JVM birim testleri (`launcher/app/src/test`), `tests/` |

**Uyumluluk sonucu:**
- `tools/*.py` dosyaları Jülide'ye **taşınamaz**. Python çalışma ortamı ve shell UID gerekir.
- `launcher/` ve `addons/` içindeki Java desenleri, Jülide'nin Kotlin koduna **yeniden yazılarak uyarlanabilir**. Aynı Android API'leri kullanılıyor.

---

## 5. Entegre edilebilir özellikler tablosu

Yöntem kodları:
- **D:** Doğrudan uyarlanabilir.
- **A:** Farklı Android API'siyle yeniden yazılır.
- **B:** Bilgisayar veya ADB gerekir.
- **Y:** Özel izin veya kullanıcı tarafından açılan hata ayıklama gerekir.
- **S:** Sürüm veya üretici sınırlı.
- **X:** Önerilmez.

| Kod | Özellik / komut | Öncelik | Yöntem | CleanYourTV kaynağı |
|---|---|---|---|---|
| F1 | "TV'nin bağlantı / cihaz durumu" | **P1** | D | `MainActivity.java:835-844`, `addons/remote-access/.../Access.java` |
| F2 | "TV'yi kapat", "30 dakika sonra kapat" | **P1** | D + S (API 28+) | `addons/sleep-timer/.../SleepService.java` |
| F3 | Erişilebilirlik servisinin kendini onarması | **P1** | D + Y (tek seferlik ADB izni) | `launcher/.../ServiceRepair.java` |
| F4 | Vestel korumalı paket ve canlı TV bilgisi | **P1** (önce cihaz doğrulaması) | D (yalnızca veri) | `examples/toshiba-cosmos-android-tv/debloat.txt`, `tools/disable.sh:61-62` |
| F5 | "Ayarları / ağ ayarlarını / X'in uygulama bilgisini aç" | **P1** | A | `launcher/.../SystemSettings.java` |
| F6 | "HDMI 1'e geç", "kaynakları göster" | **P2** | D + S | `MainActivity.java:2860`, `:2949-2995`, `InputNames.java` |
| F7 | Süreç diriltme (GuardRevival) | **P2** | D + Y | `launcher/.../GuardRevival.java`, `GuardKeep.java` |
| F8 | "Uygulamaları kategorilere ayır" | **P2** | A (CleanYourTV'de **karşılığı yok**) | — (yalnızca TV/TV-dışı ayrımı var: `AppLibraryDialog.java:226-231`) |
| F9 | "Şu uygulamayı devre dışı bırak / etkinleştir" | **P3** | B + X (Jülide içinde) | `tools/disable.sh`, `tools/restore.sh`, `tools/recovery.py` |
| F10 | TV'nin bir kerelik temizliği (debloat) | **P3** | B (Jülide dışında) | `tools/disable.sh`, Toshiba listesi |
| F11 | Kumanda tuşu yeniden atama | **P3** | S | `InputKeyService.java`. Vestel'de tuşlar Android'e ulaşmıyor |
| F12 | Kendi launcher'ımız | **P3** (sonraki faz) | — | `launcher/` tümü |

---

## 6. Öneri ayrıntıları: yöntem, izin, uyumluluk

### F1 — `cihaz_durumu` aracı · P1

- **Mevcut dosya:** `Araclar.kt`. Araç tanımı `bildirimler()` içine (satır 49-111), yürütme `calistir()` içine (satır 150-189), talimat satırı `sistemTalimati()` içine (satır 113-148).
- **Öneri:** Tek seferlik okuma yapan bir araç. Sürekli dinleyici (callback) **olmamalı**.
  - `ConnectivityManager.activeNetwork` ve `getNetworkCapabilities()` ile: Wi-Fi mı Ethernet mi, internet doğrulanmış mı (`NET_CAPABILITY_VALIDATED`), VPN var mı.
  - `Build.MODEL`, `Build.VERSION.RELEASE`, `BuildConfig.VERSION_NAME`.
  - Jülide servis durumu: bağlı mı, Gemini oturumu açık mı.
  - `Settings.Global.ADB_ENABLED` okuması. İzin gerektirmez. Sonucu "ADB açık/kapalı" bilgisi olarak güvenlik amaçlı verilir.
  - Boş/toplam RAM: `ActivityManager.getMemoryInfo`.
- **İzin:** `android.permission.ACCESS_NETWORK_STATE`. Normal seviye, kurulumda otomatik verilir.
- **Uyumluluk:** API 23+ (minSdk 26 yeterli). Tüm Android TV'lerde çalışır.
- **Gerekçe:** Kullanıcı "internet var mı", "neden cevap vermiyorsun" diye sorabilir. Gemini kopmalarının teşhisini kolaylaştırır.
- **Yan etki:** Yok. Okuma birkaç milisaniye sürer, `aracYurutucu` üzerinde çalışır.
- **Belirsizlik:** `service.adb.tcp.port` sistem özelliği uygulama içinden güvenilir okunamaz (gizli API). Bu yüzden yalnızca "ADB açık/kapalı" verilir, port bilgisi verilmez.

### F2 — `tv_kapat` / `uyku_zamanlayicisi` · P1

- **Mevcut dosyalar:** `Araclar.kt` (yeni araç) ve `AsistanServisi.kt` (servis örneği zaten `s` olarak `Araclar`'a geçiyor).
- **Öneri:**
  - Hemen kapatma: `s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)`. Önce `Build.VERSION.SDK_INT >= 28` kontrolü yapılır.
  - Zamanlayıcı: `AlarmManager.set()` (kesin olmayan alarm, ek izin gerektirmez) ile bir `PendingIntent`. Alıcı servis örneğine kapatma çağrısı yapar.
  - `aracYurutucu` içinde `sleep` **KULLANILMAMALI**, aksi halde sonraki tüm araç çağrıları bloke olur.
  - "Zamanlayıcıyı iptal et" ve "kaç dakika kaldı" komutları da eklenir.
  - Kapatmadan önce Gemini oturumu `oturumuKapatIste()` ile kapatılır.
- **İzin:** Yeni izin yok. Erişilebilirlik yetkisi zaten var.
- **Uyumluluk:** Android 9 (API 28) ve üzeri. Jülide'nin hedef TV'si Android 11 civarı olduğu için uygun.
- **Belirsizlik:** Bazı firmware'ler "lock screen" eylemini uyku yerine yalnızca ekranı kapatma olarak yorumlayabilir. Vestel'de **test edilmedi**.
  - **Test:** `adb shell input keyevent` kullanmadan, sesle "TV'yi kapat" deyip `adb shell dumpsys power | grep mWakefulness` çıktısında `Asleep` görmek.
- **Yan etki:** TV uyuyunca Wi-Fi kapanır (Vestel notu). Jülide'nin uyandırma kelimesi bekleme modunda çalışmaz. Bu beklenen bir durum.

### F3 — Servis kendini onarma (`ServisOnarim`, YENİ) · P1

- **Sorun:** Android, bir uygulama zorla durdurulduğunda o uygulamanın erişilebilirlik servisini etkin listesinden çıkarır. Jülide tamamen susar. Şu an tek çözüm bilgisayardan ADB komutu çalıştırmak (`MainActivity.kt:136-137`).
- **CleanYourTV'deki çözüm** (`ServiceRepair.java:40-95`):
  1. Servis bağlandığında kendini SharedPreferences'a kaydeder. Kullanıcı servisi kendisi kapatırsa kayıt silinir. Zorla durdurmada kayıt kalır.
  2. Uygulama yeniden başladığında, `WRITE_SECURE_SETTINGS` izni varsa `enabled_accessibility_services` listesine kendini geri ekler.
  3. Android'in servisi "çökmüş" olarak işaretlemesi durumu için **ekle → çıkar → ekle** döngüsünü 1'er saniye arayla, ayrı iş parçacığında uygular.
  4. Her yazmadan önce listenin değişmediğini kontrol eder (`replaceList`), böylece başka bir değişikliği ezmez.
- **Jülide'ye uyarlama:**
  - `AsistanServisi.onServiceConnected()` (satır 121) içinde kayıt yapılır. `onUnbind` içinde, kullanıcı servisi kapattığında kayıt silinir.
  - **Tetikleyici sorunu:** CleanYourTV'de onarımı Home ekranının başlaması tetikler. Jülide launcher olmadığı için tetikleyici şunlar olmalı:
    - (a) `MainActivity.onResume`,
    - (b) YENİ bir `BOOT_COMPLETED` alıcısı,
    - (c) isteğe bağlı olarak F7.
  - Yalnızca **kendi** bileşeni (`com.siam.tvasistan/.AsistanServisi`) eklenir. Diğer servislere dokunulmaz.
- **İzin:**
  - `WRITE_SECURE_SETTINGS`: Bir kez `adb shell pm grant com.siam.tvasistan android.permission.WRITE_SECURE_SETTINGS` ile verilir. Bu izin APK güncellemelerinde korunur, uygulama kaldırılınca kaybolur.
  - `RECEIVE_BOOT_COMPLETED`: Normal seviye.
- **Uyumluluk:** Tüm sürümler. İzin verilmemişse sessizce hiçbir şey yapmaz, mevcut davranış korunur.
- **Yan etki:** Kullanıcı Jülide'yi bilerek kapattıysa geri açılmamalı. Kayıt mantığı bunu ayırt eder. `onUnbind` ile `onDestroy` davranışı TV'de **test edilmeli**.

### F4 — Vestel korumalı paketler ve canlı TV ipucu · P1 (cihaz doğrulamasından sonra)

- **Ön koşul:** Vestel üretimi teyit edildi. Kalan tek ön koşul: listedeki paket adlarının bu TV'de gerçekten kurulu olduğunu `pm list packages` ile görmek. Kurulu olmayan paketler listeye **eklenmez**.
- **Öneri:**
  - YENİ `SistemPaketleri.kt` (yalnızca sabitler):
    - `CORE_RE` / `CORE_OK` mantığının Kotlin karşılığı (kaynak `tools/disable.sh:61-62`),
    - Vestel/MediaTek koruma listesi,
    - `com.cltv.fast` hariç tutma kuralı.
  - Kullanım yerleri:
    1. `Uygulamalar.sonlandir()` (satır 124) korumalı paketleri sonlandırmasın.
    2. `Kanallar.canliTvPaketi()` (satır 77) `com.mediatek.wwtv.tvcenter` kuruluysa onu önceliklendirsin.
    3. Gelecekteki F9 için hazır koruma listesi olarak kullanılsın.
- **İzin:** Yok.
- **Gerekçe:** `canliTvPaketi()` şu an ilk çözümleyen etkinliği seçiyor. Birden fazla uygulama kanal URI'sini yanıtlarsa yanlış paket seçilebilir.
- **Yan etki:** Liste yanlış cihaza uygulanırsa etkisi yalnızca "önceliklendirme" olur, kırıcı değildir.

### F5 — Ayar sayfalarını açma · P1

- **Öneri:** `ayarlari_ac` aracı, `sayfa` parametresi alır:
  - `genel` → `Settings.ACTION_SETTINGS`
  - `ag` → `ACTION_WIFI_SETTINGS`
  - `erisilebilirlik` → `ACTION_ACCESSIBILITY_SETTINGS`
  - `uygulama` → `ACTION_APPLICATION_DETAILS_SETTINGS` ve `package:<paket>`
  - Standart intent çözülmezse `SystemSettings.java` desenindeki gibi `resolveActivity` kontrolüyle üretici yedeğine geçilir.
- **İzin:** Yok. Manifest `<queries>` bloğuna `android.settings.*` action'larını eklemek gerekebilir. Android 11+ paket görünürlüğü nedeniyle **test edilmeli**.
- **Gerekçe:** F9'un güvenli alternatifi. "Netflix'i devre dışı bırak" denirse Jülide uygulama bilgi sayfasını açar. **Devre dışı bırak düğmesine kullanıcı kumandayla basar.**
- **Koruma:** `tikla` aracı bu sayfada "Devre dışı bırak", "Kaldır" ve "Durmaya zorla" düğmelerine **basmamalı**.
  - Mevcut `HASSAS` regex'i (`Araclar.kt:320`) "sil, kaldır, uninstall" içeriyor ama **"devre dışı" ve "disable" kelimelerini içermiyor**. Bunlar eklenmeli.
  - Ayrıca `com.android.tv.settings` paketi `KORUMALI_PAKETLER` mantığına benzer şekilde ele alınmalı.

### F6 — HDMI / kaynak değiştirme · P2

- **Öneri:** YENİ `Girisler.kt`:
  - `TvInputManager.getTvInputList()` ile `isPassthroughInput() && !isHidden(ctx)` olan girişler listelenir. Ad için önce `loadCustomLabel`, sonra `loadLabel` kullanılır (`InputNames.java` deseni).
  - Açmak için `TvContract.buildChannelUriForPassthroughInput(inputId)` ile `ACTION_VIEW` gönderilir. **Önce canlı TV paketine** (`setPackage`) yönlendirilir, olmazsa genel intent kullanılır. Böylece seçici penceresi çıkmaz (`MainActivity.java:2971-2995`).
  - Araçlar: `kaynak_degistir(ad)` ("HDMI 1", "HDMI 2", "TV", "AV") ve `kaynaklari_listele`.
- **İzin:** Giriş listesi için özel izin gerekmez. Bazı firmware'ler (`ACCESS_TV_SOURCE` gibi üreticiye özel izin) `SecurityException` fırlatabilir. CleanYourTV bunu yakalayıp yedeğe düşüyor, biz de öyle yapmalıyız.
- **Uyumluluk:** `android.software.live_tv` özelliği olan TV'ler. MediaTek'te `com.mediatek.tvinput` mevcut, bu yüzden muhtemelen çalışır ama **doğrulanmadı**. CEC cihazları ana HDMI portunun alt girişi olarak gelir (`getParentId`).
- **Test:** `adb shell dumpsys tv_input` çıktısındaki giriş kimliklerini görüp her girişe sesle geçmek.
- **Yan etki:** Kaynak değişince Jülide'nin tetikleyici akışına bir etkisi yok. Panel üstte kalır.

### F7 — GuardRevival (süreç diriltme) · P2

- **Kaynak:** `GuardRevival.java`. Bir `NotificationListenerService` olan bu yapıda, onaylı dinleyici zorla durdurmadan sonra da sistem tarafından yeniden bağlanır, bu da uygulama sürecini başlatır. **Hiçbir bildirim okunmaz.**
- **Jülide'de:** `onListenerConnected()` içinde F3'teki onarım çağrılır.
- **İzin:** Kullanıcı onayı gerekir. Ya Ayarlar → Bildirim erişimi ekranından, ya da `adb shell cmd notification allow_listener com.siam.tvasistan/.<Sinif>` ile.
- **Neden P2:**
  - 1 GB RAM'li TV'de faydası yüksek olabilir, ama sorun gerçekten yaşanıyor mu önce logcat'te görülmeli.
  - "Bildirim erişimi" izni kullanıcı gözünde ağır bir izin.
  - Vestel'de davranışı bilinmiyor.
- **Yan etki:** Ek bellek kullanımı çok küçük (boş servis).

### F8 — Uygulama kategorileri · P2

- **CleanYourTV'de karşılığı yok.** Yalnızca TV uygulaması mı, telefon uygulaması mı ayrımı yapıyor (`AppLibraryDialog.java:226-231`).
- **Öneri:**
  - `ApplicationInfo.category` (API 26+) ile `CATEGORY_VIDEO`, `AUDIO`, `GAME`, `NEWS` vb. okunur ve `Uygulamalar.Uyg` sınıfına `kategori` alanı eklenir.
  - Kategori `CATEGORY_UNDEFINED` gelirse yedek olarak bilinen paket tablosu kullanılır (YouTube, Prime, TOD → video).
  - `uygulamalari_listele` aracına `kategori` filtresi eklenir.
- **Belirsizlik:** Birçok TV uygulaması kategori bildirmez. Sonucun ne kadar dolu geleceği TV'de ölçülmeli.
- **Yan etki:** `loadLabel` gibi kategori okuması da önbelleğe alınmalı (`Uygulamalar.kt:41-63`).

### F9 — Sesle devre dışı bırakma / etkinleştirme · P3, Jülide içinde ÖNERİLMEZ

- **Neden yapılamıyor:** `pm disable-user` ve `PackageManager.setApplicationEnabledSetting()` başka paketler için `CHANGE_COMPONENT_ENABLED_STATE` ister. Bu izin yalnızca sistem veya shell uygulamalarına verilir. Normal bir APK'ya **verilemez**.
- **Seçenekler:**

| Seçenek | Nasıl | Değerlendirme |
|---|---|---|
| a) Bilgisayardan `tools/disable.sh` / `restore.sh` | Jülide dışında, bir kerelik | ✅ **Önerilen.** Yedek kaydı, koruma kontrolleri ve geri alma hazır |
| b) Jülide içinde ADB istemcisi (ör. `dadb`/`libadb`) ile `127.0.0.1:5555` | Yeni bağımlılık, RSA anahtarı, ağ ADB'si sürekli açık | ❌ Ağdaki herhangi bir cihaz için saldırı yüzeyi. `ro.adb.secure=0` olan firmware'de onaysız shell verir (`tools/disable.sh:95` notu) |
| c) Device Owner | Fabrika ayarına dönüş ve `dpm set-device-owner` | ❌ Kullanıcı verisi silinir, orantısız |
| d) Uygulama bilgi sayfası (F5) | Kullanıcı kumandayla basar | ✅ Jülide için güvenli alternatif |

- **Riskli işlemlerde sesli komut yeterli mi?** **HAYIR.** İleride (b) yapılsa bile şu korumalar şart:
  1. Sesli onay ve ekranda kumandayla "Evet" basma (çift onay).
  2. `CORE_RE`, Vestel listesi, canlı TV paketi, Home sahibi ve Jülide'nin kendisi için kesin red.
  3. İşlemden önce durum kaydı (`recovery.py` deseni).
  4. Yalnızca `disable-user`, asla `uninstall`.
  5. "Son değişikliği geri al" komutu.

### F10 — TV'nin bir kerelik temizliği · P3

- Jülide entegrasyonu değil, **operasyonel** bir işlem. 1 GB RAM'de arka plandaki telemetri ve öneri servislerinin kapanması Vosk ve Gemini için bellek açar.
- **Kesin kurallar:**
  - Toshiba listesi **kopyalanarak düzenlenmeli**. Şu satırlar mutlaka çıkarılmalı:
    - `com.amazon.amazonvideo.livingroom` → **Prime Video aktif kullanılıyor.** Ayrıca Jülide'nin `abonelikte_ara` aracı buna bağımlı ve kumandadaki Prime tuşu bu uygulamayı açıyor.
    - `com.google.android.youtube.tv` → Jülide'nin `youtube_ara` aracı buna bağımlı.
  - TOD uygulaması da (aktif abonelik) korunanlar listesine yazılmalı. Paket adı Aşama 0'da `pm list packages` ile belirlenecek.
  - `com.google.android.tvlauncher` **kapatılmamalı**. Jülide launcher değil.
  - `com.android.providers.tv` zaten `CORE_RE` ile korunuyor. Bu önemli, çünkü `Kanallar.kt` TvProvider'a bağımlı.
- **Ölçüm:** Öncesinde ve sonrasında `adb shell dumpsys meminfo | head -40` ile karşılaştırma.

### F11 / F12

- **F11:** Vestel notuna göre YouTube/Prime tuşları Android'e ulaşmıyor, ses tuşları filtreye gelmeden tüketiliyor. Bu iki tuş **tetik tuşu olarak öğretilemez**. `AsistanServisi.onKeyEvent` içindeki tuş öğrenme özelliğinde kullanıcıya bu not gösterilebilir. Kod değişikliği gerekmez.
- **F12:** Kendi launcher'ımız sonraki fazın konusu. `launcher/` mimarisi (Config, temalar, GridNavigation, birim testleri) referans olarak saklanmalı.

---

## 7. Güvenlik, lisans ve performans riskleri

### 7.1 Lisans (gerçek dosyaya göre)

- **`LICENSE`: MIT License, "Copyright (c) 2026 Gathin".**
  - Kullanma, kopyalama, değiştirme, birleştirme, yayma, alt lisanslama ve satma serbest.
  - Tek koşul: telif bildirimi ve izin metni, yazılımın tüm kopyalarına veya önemli bölümlerine eklenmeli.
  - Yazılım "OLDUĞU GİBİ" sunulur, garanti yoktur.
- **Font:** `launcher/app/src/main/res/font/outfit.ttf` dosyası **SIL OFL 1.1** lisanslı (`launcher/FONT-LICENSE-OFL.txt`). Kullanılırsa OFL metni de eklenmeli.
- **Eklentiler:** `addons/remote-access`, `quiet-hours`, `headphones` (@maxhayim), `sleep-timer`, `tv-static` (@luyzfernando08) gibi eklentiler farklı katkıcılara ait. Eklenti klasörlerinde **ayrı LICENSE dosyası bulunamadı** (yalnızca bazılarında font OFL dosyası var). Kök MIT lisansının bunları kapsadığı **varsayımdır, doğrulanmadı.** Eklenti kodu kopyalanacaksa ilgili katkıcının README'si ve PR geçmişi kontrol edilmeli.
- **Öneri:** Kod kopyalanmasın, desenler Kotlin ile yeniden yazılsın. Yine de iyi uygulama olarak YENİ bir `THIRD_PARTY_NOTICES.md` dosyasına "ServiceRepair ve kaynak değiştirme yaklaşımları CleanYourTV'den (MIT, © 2026 Gathin) uyarlanmıştır" notu ve MIT metni eklensin. Doğrudan kod kopyalanırsa bu dosya **zorunludur**.

### 7.2 Güvenlik

| Risk | Açıklama | Önlem |
|---|---|---|
| Ağ ADB'si açık | TV `192.168.1.86:5555`'te ağ ADB'si açık. Aynı ağdaki her cihaz bağlanmayı deneyebilir. Onay ekranı yalnızca `ro.adb.secure=1` ise var | F3 izinleri verildikten sonra **ADB'yi kapatın**. Jülide güncellemeleri ADB'ye değil `REQUEST_INSTALL_PACKAGES` iznine dayanıyor. F1 aracı ADB durumunu sesle bildirebilir. `adb shell getprop ro.adb.secure` kontrol edilmeli |
| `WRITE_SECURE_SETTINGS` | Güçlü bir izin. Güvenli ayarların tümünü yazabilir | Kod yalnızca `enabled_accessibility_services` ve `accessibility_enabled` anahtarlarına ve yalnızca kendi bileşenine dokunsun. İzin yalnızca ADB ile verilebilir, imza anahtarı GitHub Secrets'ta korunduğu için kötü niyetli bir güncelleme riski düşük |
| Ekran içeriğiyle talimat enjeksiyonu | `tikla` ile ayar ekranlarında yıkıcı düğmelere basılması | `HASSAS` regex'ine `devre dışı`, `disable`, `durmaya zorla`, `force stop`, `verileri temizle`, `clear data` eklensin. Bunlar için `onayli=true` zorunlu olsun |
| Sistem paketinin yanlışlıkla kapatılması | Jülide bunu yapamaz (F9 eklenmedikçe). Bilgisayar tarafında `disable.sh` korumaları var | F10'da yalnızca `disable.sh` kullanılsın, elle `pm disable-user` yazılmasın |
| Kendi kendine ADB (F9-b) | Uzun süre açık TCP shell | Uygulanmayacak |

### 7.3 Performans ve kararlılık

- **Araç çağrıları `aracYurutucu` tek iş parçacığında çalışır** (`GeminiLive.kt:54`). Yeni araçlarda `sleep`/bekleme döngüsü olmamalı. Zamanlayıcılar AlarmManager veya `kapsam` coroutine ile kurulmalı.
- **Sürekli ADB bağlantısı GEREKMİYOR.** P1 ve P2 özelliklerinin hiçbiri ADB kullanmıyor. ADB yalnızca F3/F7 için bir kerelik izin vermede ve F10'da kullanılır.
- F1 için sürekli `NetworkCallback` **kaydedilmesin**. Yalnızca araç çağrıldığında okuma yapılsın.
- F3'teki ekle/çıkar/ekle döngüsü yaklaşık 2 saniyelik bir arka plan iş parçacığında çalışır, yalnızca servis eksikse tetiklenir.
- Ses tanıma, Gemini bağlantısı ve `Uyandirma.kt` **hiç değiştirilmez**. Tüm öneriler `Araclar.kt` araç katmanına ve yaşam döngüsü kancalarına eklenir.
- Kumanda: `onKeyEvent` mantığı değişmez. F2 ve F6 kumanda tuşlarını tüketmez.

---

## 8. Önerilen uygulama sırası

| Aşama | İçerik | Ön koşul |
|---|---|---|
| **0** | Cihaz doğrulaması (kod yok): firmware parmak izi, Vestel/MediaTek paket listesi, Prime Video ve TOD paket adları, `dumpsys tv_input`, `ro.adb.secure`, `dumpsys meminfo` | ADB erişimi |
| **1** | F1 `cihaz_durumu` ve F5 `ayarlari_ac` ile `HASSAS` regex genişletmesi | — |
| **2** | F2 `tv_kapat` / uyku zamanlayıcısı | Aşama 1 |
| **3** | F3 servis onarımı, `BOOT_COMPLETED` alıcısı, README'ye `pm grant` talimatı | Tek seferlik ADB |
| **4** | F4 `SistemPaketleri.kt` ve canlı TV önceliği | Aşama 0 doğrulaması olumlu |
| **5** | F6 HDMI/kaynak değiştirme | Aşama 0'daki `dumpsys tv_input` çıktısı |
| **6** | F8 kategoriler (ölçüm sonrasında karar) | — |
| **7** | F7 GuardRevival (yalnızca logcat'te servis kaybı görülürse) | Aşama 3 |
| **Ayrı** | F10 bilgisayardan debloat (isteğe bağlı) | Liste kopyalanıp düzenlenmiş olmalı |

Her aşama ayrı commit/PR olarak yapılır, `apk.yml` derlemesi geçmeden sonraki aşamaya geçilmez.

---

## 9. Test ve kabul kriterleri

| Aşama | Test | Kabul kriteri |
|---|---|---|
| 0 | `getprop ro.build.fingerprint`, `pm list packages \| grep -E "vestel\|mediatek"`, `dumpsys tv_input`, `getprop ro.adb.secure` | Sonuçlar rapora eklendi. Firmware'in Cosmos170 ile aynı olup olmadığı netleşti. Prime Video ve TOD paket adları korunanlar listesine yazıldı |
| 1 | "İnternet var mı?", "TV durumunu söyle" | 2 saniye içinde Türkçe yanıt verir: ağ tipi, internet durumu, sürüm, ADB durumu. Wi-Fi kapalıyken doğru şekilde "bağlantı yok" der |
| 1 | "Ağ ayarlarını aç", "YouTube'un uygulama bilgisini aç" | Doğru sayfa açılır. "Devre dışı bırak'a bas" denince Jülide sesli onay ister, onaysız basmaz |
| 2 | "TV'yi kapat", "10 dakika sonra kapat", "zamanlayıcıyı iptal et" | `dumpsys power` çıktısında `mWakefulness=Asleep` görülür. İptal sonrası kapanmaz. Zamanlayıcı beklerken diğer sesli komutlar gecikmeden çalışır |
| 3 | `adb shell am force-stop com.siam.tvasistan` → Jülide simgesini aç / TV'yi yeniden başlat | `settings get secure enabled_accessibility_services` içinde `.AsistanServisi` yeniden görünür. Tetik tuşu çalışır. Kullanıcı servisi Ayarlar'dan kapatırsa **geri açılmaz** |
| 3 | `pm grant` verilmeden aynı test | Uygulama çökmez, eski davranış korunur |
| 4 | "Canlı TV'yi aç", "ATV'yi aç" | `com.mediatek.wwtv.tvcenter` açılır. `uygulama_kapat` korumalı paketleri sonlandırmaz |
| 5 | "HDMI 1'e geç", "TV'ye dön", "kaynakları say" | Doğru girişe geçer, seçici penceresi çıkmaz. Bilinmeyen giriş adında "şu kaynaklar var: …" diye yanıt verir |
| 6 | "Video uygulamalarını listele" | Prime Video, TOD ve YouTube video kategorisinde gelir. Kategorisi bilinmeyen uygulama "diğer" altında listelenir |
| 7 | 24 saat bekleme döngüsü ve RAM baskısı | Jülide süreci öldürüldükten sonra 1 dakika içinde servis yeniden bağlanır |
| Genel | `adb logcat -s TvAsistan GeminiLive` | Yeni `ANR`, `SecurityException` çökmesi veya tekrarlayan uyarı yok. Mevcut tüm komutlar (kanal, abonelik araması, ses) regresyon testinden geçer |

---

## 10. Değiştirilmesi gerekebilecek dosyalar

### Mevcut dosyalar (depoda var)

| Dosya | Aşama | Değişiklik |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | 1, 3, 7 | `ACCESS_NETWORK_STATE`, `WRITE_SECURE_SETTINGS`, `RECEIVE_BOOT_COMPLETED` izinleri. Açılış alıcısı. (Aşama 7) NotificationListener servisi. `<queries>` bloğuna settings action'ları |
| `app/src/main/java/com/siam/tvasistan/Araclar.kt` | 1, 2, 5, 6 | `bildirimler()` (49-111) içine yeni araç tanımları, `calistir()` (150-189) içine yürütme, `sistemTalimati()` (113-148) içine komut satırları, `HASSAS` (320) genişletmesi |
| `app/src/main/java/com/siam/tvasistan/AsistanServisi.kt` | 2, 3 | `onServiceConnected()` (121) içinde onarım kaydı, `onUnbind` ile kayıt silme, uyku eylemi için yardımcı fonksiyon |
| `app/src/main/java/com/siam/tvasistan/MainActivity.kt` | 3 | Erişilebilirlik bölümüne (120-140) "otomatik onarım" durumu ve `pm grant` komutu, `onResume` içinde onarım tetiği |
| `app/src/main/java/com/siam/tvasistan/Uygulamalar.kt` | 4, 6 | `sonlandir()` (124) koruma kontrolü, `Uyg` sınıfına `kategori` alanı, `tumu()` (46-63) |
| `app/src/main/java/com/siam/tvasistan/Kanallar.kt` | 4 | `canliTvPaketi()` (77) için bilinen paket önceliği |
| `README.md` | 3 | Kurulum bölümüne (53-69) `pm grant … WRITE_SECURE_SETTINGS` ve ADB'yi kapatma önerisi |
| `config/ayarlar.json` + `Uzak.kt` | (isteğe bağlı) | Uzaktan açılıp kapatılabilen özellik bayrakları (ör. `uyku_izinli`). Yalnızca beyaz liste mantığıyla eklenir |

### YENİ önerilen dosyalar (depoda yok)

| Dosya | Aşama | İçerik |
|---|---|---|
| `.../tvasistan/Cihaz.kt` | 1 | Ağ, cihaz ve ADB durumu okuma |
| `.../tvasistan/Uyku.kt` | 2 | AlarmManager zamanlayıcısı ve alıcısı |
| `.../tvasistan/ServisOnarim.kt` | 3 | ServiceRepair deseninin Kotlin hali |
| `.../tvasistan/AcilisAlici.kt` | 3 | `BOOT_COMPLETED` → onarım |
| `.../tvasistan/SistemPaketleri.kt` | 4 | Korumalı paket sabitleri |
| `.../tvasistan/Girisler.kt` | 5 | TvInputManager ile kaynak listeleme ve geçiş |
| `THIRD_PARTY_NOTICES.md` | 1'den önce | CleanYourTV MIT atfı |

---

*Bu rapor iki deponun yerel klonlarından (yukarıdaki commit'ler) okunan kaynak koda dayanır. "Test edilmeli", "doğrulanmadı" ve "varsayım" ile işaretlenen her madde, ilgili aşama başlamadan önce TV üzerinde denenmelidir.*
