/*
 * LG webOS kontrol katmanı.
 *  - SSAP: TV'nin yerel kontrol bağlantısı (LG ThinQ uygulamasının da kullandığı). ws://127.0.0.1:3000,
 *    olmazsa wss://127.0.0.1:3001. İlk bağlantıda TV ekranında "izin ver" sorusu çıkar; anahtar saklanır.
 *  - Giriş soketi: kumanda tuşları (UP/DOWN/ENTER/BACK...), LG imleci (move/click/scroll).
 *  - Luna (PalmServiceBridge): SSAP yoksa uygulama başlatma yedeği.
 * Kayıt manifestosu: aiowebostv (Apache-2.0) ile aynı izin listesi.
 */
(function () {
  "use strict";

  const IZINLER = ["APP_TO_APP", "CLOSE", "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK",
    "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_MEDIA_RECORDING", "CONTROL_INPUT_TEXT", "CONTROL_INPUT_TV",
    "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_POWER", "CONTROL_TV_SCREEN", "LAUNCH", "LAUNCH_WEBAPP", "READ_APP_STATUS",
    "READ_COUNTRY_INFO", "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST", "READ_INSTALLED_APPS", "READ_LGE_SDX",
    "READ_LGE_TV_INPUT_EVENTS", "READ_NETWORK_STATE", "READ_NOTIFICATIONS", "READ_POWER_STATE", "READ_RUNNING_APPS",
    "READ_SETTINGS", "READ_TV_CHANNEL_LIST", "READ_TV_CURRENT_TIME", "READ_UPDATE_INFO", "SEARCH", "TEST_OPEN",
    "TEST_PROTECTED", "TEST_SECURE", "UPDATE_FROM_REMOTE_APP", "WRITE_NOTIFICATION_ALERT", "WRITE_NOTIFICATION_TOAST",
    "WRITE_SETTINGS"];

  const ADRESLER = ["ws://127.0.0.1:3000", "ws://localhost:3000", "wss://127.0.0.1:3001"];
  const ANAHTAR_KAYIT = "julide_lg_anahtar";

  let ws = null, girisWs = null, kayitli = false, sira = 0, baglaniyor = null;
  const bekleyen = new Map();
  let durumDinleyici = () => {};

  function oku(k) { try { return localStorage.getItem(k); } catch (_) { return null; } }
  function yaz(k, v) { try { localStorage.setItem(k, v); } catch (_) {} }

  function ac(adres) {
    return new Promise((coz, reddet) => {
      let s;
      try { s = new WebSocket(adres); } catch (e) { return reddet(e); }
      const zaman = setTimeout(() => { try { s.close(); } catch (_) {} reddet(new Error("zaman aşımı")); }, 4000);
      s.onopen = () => { clearTimeout(zaman); coz(s); };
      s.onerror = () => { clearTimeout(zaman); reddet(new Error("bağlanamadı")); };
    });
  }

  function mesajIsle(e) {
    let j; try { j = JSON.parse(e.data); } catch (_) { return; }
    const b = bekleyen.get(j.id);
    if (!b) return;
    if (j.type === "response" && b.kayit && j.payload && j.payload.pairingType === "PROMPT") {
      durumDinleyici("TV ekranındaki izin sorusunu kumandayla onaylayın…");
      return; // asıl yanıt ("registered") onaydan sonra gelir
    }
    bekleyen.delete(j.id);
    clearTimeout(b.zaman);
    if (j.type === "error") b.reddet(new Error(j.error || "TV hatası"));
    else b.coz(j);
  }

  function gonder(tur, uri, payload, sure, kayit) {
    return new Promise((coz, reddet) => {
      if (!ws || ws.readyState !== 1) return reddet(new Error("LG kontrol bağlantısı yok"));
      const id = (kayit ? "kayit_" : "j_") + (++sira);
      const zaman = setTimeout(() => { bekleyen.delete(id); reddet(new Error("TV yanıt vermedi")); }, sure || 6000);
      bekleyen.set(id, { coz, reddet, zaman, kayit });
      const m = { type: tur, id };
      if (uri) m.uri = uri;
      if (payload) m.payload = payload;
      ws.send(JSON.stringify(m));
    });
  }

  async function baglan() {
    if (ws && ws.readyState === 1 && kayitli) return true;
    if (baglaniyor) return baglaniyor;
    baglaniyor = (async () => {
      let son = null;
      for (const a of ADRESLER) {
        try { ws = await ac(a); ws.onmessage = mesajIsle; ws.onclose = () => { kayitli = false; girisWs = null; }; break; }
        catch (e) { son = e; ws = null; }
      }
      if (!ws) throw new Error("TV'nin kontrol bağlantısı açılamadı (" + (son && son.message) + "). TV ayarlarında 'LG Connect Apps / Mobil TV Açık' seçeneği açık olmalı.");
      const payload = { forcePairing: false, pairingType: "PROMPT", manifest: { manifestVersion: 1, appVersion: "1.1", permissions: IZINLER } };
      const k = oku(ANAHTAR_KAYIT);
      if (k) payload["client-key"] = k;
      const r = await gonder("register", null, payload, 60000, true);
      if (r.payload && r.payload["client-key"]) yaz(ANAHTAR_KAYIT, r.payload["client-key"]);
      kayitli = true;
      durumDinleyici("LG kontrol bağlantısı hazır");
      return true;
    })();
    try { return await baglaniyor; } finally { baglaniyor = null; }
  }

  async function istek(uri, payload) {
    await baglan();
    const r = await gonder("request", "ssap://" + uri, payload || {});
    if (r.payload && r.payload.returnValue === false) throw new Error(r.payload.errorText || "TV isteği reddetti");
    return r.payload || {};
  }

  async function giris() {
    if (girisWs && girisWs.readyState === 1) return girisWs;
    const r = await istek("com.webos.service.networkinput/getPointerInputSocket");
    girisWs = await ac(r.socketPath);
    girisWs.onclose = () => { girisWs = null; };
    return girisWs;
  }

  async function girisGonder(m) { (await giris()).send(m); }

  /** Luna yedeği (yalnız uygulama başlatma gibi herkese açık hizmetler). */
  function luna(uri, params) {
    return new Promise((coz, reddet) => {
      if (typeof PalmServiceBridge === "undefined") return reddet(new Error("Luna yok"));
      const b = new PalmServiceBridge();
      const zaman = setTimeout(() => reddet(new Error("Luna yanıt vermedi")), 5000);
      b.onservicecallback = (s) => {
        clearTimeout(zaman);
        let j = {}; try { j = JSON.parse(s); } catch (_) {}
        if (j.returnValue === false) reddet(new Error(j.errorText || "Luna hatası")); else coz(j);
      };
      b.call(uri, JSON.stringify(params || {}));
    });
  }

  let uygulamaOnbellek = null;

  window.LG = {
    durumDinle(f) { durumDinleyici = f; },
    baglan, istek, luna,
    hazirMi() { return !!(ws && ws.readyState === 1 && kayitli); },

    async uygulamalar() {
      if (uygulamaOnbellek) return uygulamaOnbellek;
      const r = await istek("com.webos.applicationManager/listLaunchPoints");
      uygulamaOnbellek = (r.launchPoints || []).map((a) => ({ id: a.id, ad: a.title }));
      return uygulamaOnbellek;
    },
    async baslat(id, params) {
      try { return await istek("system.launcher/launch", params ? { id, params } : { id }); }
      catch (e) { return await luna("luna://com.webos.applicationManager/launch", params ? { id, params } : { id }); }
    },
    adresAc(url) { return istek("system.launcher/open", { target: url }); },
    onplandaki() { return istek("com.webos.applicationManager/getForegroundAppInfo"); },

    sesAyarla(seviye) { return istek("audio/setVolume", { volume: Math.max(0, Math.min(100, seviye | 0)) }); },
    sesArtir() { return istek("audio/volumeUp"); },
    sesAzalt() { return istek("audio/volumeDown"); },
    sessiz(acik) { return istek("audio/setMute", { mute: !!acik }); },
    sesDurumu() { return istek("audio/getVolume"); },

    medya(islem) { return istek("media.controls/" + islem); },

    kanalYukari() { return istek("tv/channelUp"); },
    kanalAsagi() { return istek("tv/channelDown"); },
    async kanallar() { const r = await istek("tv/getChannelList"); return r.channelList || []; },
    kanalAc(channelId) { return istek("tv/openChannel", { channelId }); },
    async girisler() { const r = await istek("tv/getExternalInputList"); return r.devices || []; },
    girisDegistir(inputId) { return istek("tv/switchInput", { inputId }); },

    kapat() { return istek("system/turnOff"); },
    bildirim(m) { return istek("system.notifications/createToast", { message: String(m).slice(0, 200) }); },

    yaziYaz(metin, degistir) { return istek("com.webos.service.ime/insertText", { text: metin, replace: degistir ? 1 : 0 }); },
    enter() { return istek("com.webos.service.ime/sendEnterKey"); },

    tus(ad) { return girisGonder("type:button\nname:" + ad + "\n\n"); },
    imlecTasi(dx, dy) { return girisGonder("type:move\ndx:" + (dx | 0) + "\ndy:" + (dy | 0) + "\ndown:0\n\n"); },
    tikla() { return girisGonder("type:click\n\n"); },
    kaydir(dx, dy) { return girisGonder("type:scroll\ndx:" + (dx | 0) + "\ndy:" + (dy | 0) + "\n\n"); },
  };
})();
