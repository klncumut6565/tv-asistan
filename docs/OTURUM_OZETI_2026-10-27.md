# Oturum Özeti — 26/27.10.2026

## Tamamlanan işler

| Sürüm | Commit | İş |
|---|---|---|
| — | `fd50496` öncesi | CleanYourTV incelemesi → `docs/CLEANYOURTV_ENTEGRASYON_RAPORU.md` |
| v0.1.49 | `fd50496` | Aşama 1: `cihaz_durumu`, `ayarlari_ac`, HASSAS düğme koruması (`Cihaz.kt`) |
| — | `37b1575` | Aşama 0: cihaz doğrulaması rapora işlendi |
| v0.1.50 | `d4c57cf` | Aşama 2: `tv_kapat`, `uyku_zamanlayicisi` (`Uyku.kt`) — TV'de doğrulandı |
| v0.1.51 | `871c4a6` | Aşama 3: erişilebilirlik servisi kendini onarır (`ServisOnarim.kt`); `pm grant WRITE_SECURE_SETTINGS` verildi |
| v0.1.52 | `c87d170` | Mikrofon: 15 sn temiz yenileme (yanlış varsayım, sonra değiştirildi) |
| v0.1.53 | `fefb6c3` | Mikrofon: bas-konuş düzeni — TV'de 9/9 basışta duydu |
| v0.1.54 | `708f3bd` | Mikrofon: tuşa basılınca kayıt anında yenilenir |

## Cihaz gerçekleri (Toshiba 50UA3D63DT)

- Parmak izi `Toshiba/EMIR_Toshiba/shandao:14/UKNV.260528.001`, Android 14, Vestel/MediaTek, ~1,74 GB RAM, `ro.adb.secure=1`.
- Canlı TV `com.mediatek.wwtv.tvcenter`; Prime Video `com.amazon.amazonvideo.livingroom`; TOD `com.beonetv.tod`.
- HDMI: HW5 = HDMI 1, HW6 = HDMI 2, HW7 = HDMI 3 (`com.mediatek.tvinput/.hdmi.HDMIInputService/HWx`). AV yok.
- Varsayılan asistan: `com.google.android.katniss`. Eller serbest kapalı.

## MİKROFON — kesin teşhis

- TV'nin dahili mikrofon yolu (`builtin-mic`, MediaTek VOC HAL) açılıyor ama **hep sıfır** veriyor. `soundtrigger_middleware`: "Modules have not yet been enumerated" (eller serbest açıkken de).
- Ses **yalnızca kumandanın mikrofonundan** geliyor. Kumanda mikrofonu Asistan tuşuyla açılıyor; `com.google.android.tv.remote.service` sesi kayıt yapan uygulamalara enjekte ediyor (usage 15, "mixr" ses politikası), ~15 sn sonra kesiyor.
  - Kanıt: `dumpsys activity service com.google.android.tv.remote.service` → `TV REMOTE ... Assistant: 5, Mic Open: 5/5:5, Frames received: 3351/1127`.
- Kumanda sesi açık duran kayda kendiliğinden gelmiyor; **her basışta kaydın yenilenmesi gerekiyor** (v0.1.54 bunu anında yapıyor).
- Sonuç: **"Jülide" uyandırma kelimesi ve kesintisiz dinleme bu donanımda mümkün değil.** Sürekli dinleme için tek yol harici USB mikrofon (`Ses.kt` USB mikrofonu otomatik tercih eder).

## Açık işler / sonraki adımlar

1. v0.1.54'ü TV'de test et (sözün başı kaybolmuyor mu).
2. Uyandırma kelimesi (`Uyandirma.kt`) ve `SessizCikis` bu TV'de işlevsiz: kapatma/sadeleştirme kararı.
3. Panel/talimat metinlerini bas-konuş düzenine göre güncelle ("Jülide de" ipuçları).
4. Rapordaki sıradaki aşamalar: Aşama 4 (`SistemPaketleri.kt`, canlı TV önceliği), Aşama 5 (HDMI geçişi — giriş kimlikleri hazır), Aşama 6 (kategoriler).
5. ADB kullanılmıyorken kapatılabilir (onarım izni kalıcıdır).
