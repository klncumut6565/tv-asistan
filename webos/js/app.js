/*
 * Jülide (webOS) — ana ekran, telefon mikrofonu köprüsü, anahtar kurulumu, sohbet oturumu, teşhis.
 * Telefon sayfası ve anahtar sayfası Android sürümüyle AYNIDIR (aynı Supabase kanal protokolü).
 */
(function () {
  "use strict";
  const SUPABASE_URL = "https://eiacwimxjpggomngsnyo.supabase.co";
  const SUPABASE_ANON = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVpYWN3aW14anBnZ29tbmdzbnlvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODIxNDE5ODAsImV4cCI6MjA5NzcxNzk4MH0.OTUmblVqYTatRt8IUqjuDCAC0ABdYTg6Gy6mBS-wI8w";
  const SAYFA = "https://klncumut6565.github.io/tv-asistan";

  const $ = (id) => document.getElementById(id);
  const oku = (k) => { try { return localStorage.getItem(k); } catch (_) { return null; } };
  const yaz = (k, v) => { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch (_) {} };

  function yeniKod() {
    const h = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    const r = crypto.getRandomValues(new Uint8Array(8));
    return Array.from(r, (b) => h[b % h.length]).join("");
  }
  let kod = oku("julide_kod");
  if (!kod) { kod = yeniKod(); yaz("julide_kod", kod); }

  function durum(m, tur) {
    const e = $("durum"); e.textContent = m; e.className = "durum " + (tur || "");
    if (kanal && bagli && m) kanal.send({ type: "broadcast", event: "durum", payload: { m: String(m).slice(0, 500) } });
  }
  function gunluk(m) {
    const e = $("gunluk"); const s = document.createElement("div");
    s.textContent = new Date().toLocaleTimeString("tr-TR") + "  " + m; e.prepend(s);
    while (e.childNodes.length > 40) e.removeChild(e.lastChild);
  }

  // ---------------------------------------------------------------- telefon köprüsü
  let istemci = null, kanal = null, bagli = false;
  function koprubaglan() {
    if (!window.supabase) { durum("Bağlantı kütüphanesi yüklenemedi", "hata"); return; }
    istemci = istemci || window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON, { realtime: { params: { eventsPerSecond: 20 } } });
    kanal = istemci.channel("julide-" + kod, { config: { broadcast: { self: false, ack: false } } });
    kanal.on("broadcast", { event: "ses" }, ({ payload }) => telefonSesi(payload || {}));
    kanal.on("broadcast", { event: "merhaba" }, () => { gunluk("Telefon bağlandı"); $("telefon").textContent = "Telefon bağlı ✓"; halBildir(); });
    kanal.on("broadcast", { event: "hoscakal" }, () => { $("telefon").textContent = "Telefon ayrıldı"; });
    kanal.on("broadcast", { event: "ac" }, () => { if (hal === "bosta") baslat(); });
    kanal.on("broadcast", { event: "kapat" }, () => bitir());
    kanal.on("broadcast", { event: "tv_kapat" }, () => { bitir(); LG.kapat().catch((e) => gunluk("TV kapatılamadı: " + e.message)); });
    kanal.subscribe((st) => {
      if (st === "SUBSCRIBED") { bagli = true; gunluk("Telefon kanalı hazır (" + kod + ")"); halBildir(); }
      else if (st === "CHANNEL_ERROR" || st === "TIMED_OUT" || st === "CLOSED") { bagli = false; gunluk("Telefon kanalı koptu: " + st); }
    });
  }
  function halBildir() {
    if (kanal && bagli) kanal.send({ type: "broadcast", event: "hal", payload: { oturum: hal !== "bosta", konusuyor: Gemini.caliyor() } });
  }
  let sonKonusma = false;
  setInterval(() => { const k = Gemini.caliyor(); if (k !== sonKonusma) { sonKonusma = k; halBildir(); } }, 250);

  function telefonSesi(p) {
    if (!p.d || hal !== "acik" || !oturum) return;
    // Yankı koruması: Jülide konuşurken telefon bunu bilmiyorsa (jk=false) gelen ses TV hoparlörüdür.
    if (p.jk === false && Gemini.caliyor()) return;
    if (p.b === 1 && Gemini.caliyor()) Gemini.sustur(); // kullanıcı Jülide'nin sözünü kesti
    oturum.sesGonder(p.d);
  }

  // ---------------------------------------------------------------- sohbet oturumu
  let oturum = null, hal = "bosta", denemeler = 0;
  async function baslat() {
    if (hal !== "bosta") return;
    const anahtar = oku("julide_gemini");
    if (!anahtar) { durum("Önce Gemini API anahtarını telefonla kaydedin (sağdaki karekod).", "hata"); return; }
    Gemini.sesiAc();
    hal = "baglaniyor"; halBildir(); durum("Bağlanıyor…");
    try { await LG.baglan(); } catch (e) { gunluk("LG kontrolü yok: " + e.message); }
    oturum = new Gemini.Oturum({
      arac: async (ad, a) => {
        gunluk("Araç: " + ad);
        try { const r = await Araclar.calistir(ad, a, { bitir }); if (r && r.hata) gunluk("  ✗ " + r.hata); return r; }
        catch (e) { gunluk("  ✗ " + e.message); return { hata: e.message }; }
      },
      kapandi: (neden) => { gunluk("Gemini bağlantısı kapandı: " + (neden || "")); yenidenBaglan(); },
      kullanici: (t) => { $("soz").textContent = "Siz: " + t; },
      asistan: (t) => { $("cevap").textContent += t; },
      turBitti: () => { const c = $("cevap").textContent.trim(); if (c) durum("Jülide: " + c.slice(0, 300)); $("cevap").textContent = ""; },
    });
    try {
      const model = await oturum.baglan(anahtar, Araclar.talimat(), Araclar.BILDIRIMLER);
      hal = "acik"; denemeler = 0; halBildir();
      durum("Jülide dinliyor · " + model, "iyi");
      $("sohbet").textContent = "Sohbeti bitir";
    } catch (e) { hal = "bosta"; oturum = null; halBildir(); durum("Hata: " + e.message, "hata"); }
  }
  async function yenidenBaglan() {
    if (hal !== "acik" || !oturum) return;
    if (++denemeler > 5) { durum("Gemini bağlantısı tekrar tekrar koptu.", "hata"); return bitir(); }
    durum("Yeniden bağlanıyor…");
    try { await oturum.baglan(oku("julide_gemini"), Araclar.talimat(), Araclar.BILDIRIMLER); durum("Jülide dinliyor", "iyi"); }
    catch (e) { durum("Hata: " + e.message, "hata"); bitir(); }
  }
  function bitir() {
    if (oturum) oturum.kapat();
    oturum = null; hal = "bosta"; halBildir();
    durum("Sohbet kapalı. Telefondan “Jülide'yi aç”a basın.");
    $("sohbet").textContent = "Sohbeti başlat";
  }

  // ---------------------------------------------------------------- API anahtarı (telefonla, uçtan uca şifreli)
  let anahtarKanal = null;
  function b64(u8) { let s = ""; u8.forEach((b) => (s += String.fromCharCode(b))); return btoa(s); }
  function b64Coz(s) { return Uint8Array.from(atob(s), (c) => c.charCodeAt(0)); }
  function anahtarKurulumu() {
    const k = yeniKod();
    const sir = crypto.getRandomValues(new Uint8Array(32));
    const sirMetin = b64(sir).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    const adres = SAYFA + "/anahtar.html?kod=" + k + "#s=" + sirMetin;
    $("anahtarQr").innerHTML = JulideLib.qrSvg(adres, 5);
    if (anahtarKanal) try { istemci.removeChannel(anahtarKanal); } catch (_) {}
    anahtarKanal = istemci.channel("julide-anahtar-" + k, { config: { broadcast: { self: false, ack: false } } });
    const yanit = (ok, m) => anahtarKanal.send({ type: "broadcast", event: "sonuc", payload: { ok, m } });
    anahtarKanal.on("broadcast", { event: "anahtar" }, async ({ payload }) => {
      let anahtar;
      try {
        const acik = JulideLib.aesGcmCoz(sir, b64Coz(payload.iv), b64Coz(payload.c), new TextEncoder().encode("julide-anahtar-" + k));
        anahtar = new TextDecoder().decode(acik).trim();
      } catch (_) { return yanit(false, "Şifre çözülemedi. TV'deki karekodu yeniden okutun."); }
      if (!/^[A-Za-z0-9_\-]{30,80}$/.test(anahtar)) return yanit(false, "Bu bir Gemini API anahtarına benzemiyor.");
      try {
        const r = await fetch("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1&key=" + encodeURIComponent(anahtar));
        if (r.status === 400 || r.status === 401 || r.status === 403) return yanit(false, "Google bu anahtarı kabul etmedi.");
      } catch (_) { /* denenemedi: yine de kaydet */ }
      yaz("julide_gemini", anahtar);
      yanit(true, "Anahtar TV'ye kaydedildi. Jülide hazır!");
      gunluk("Gemini anahtarı kaydedildi");
      anahtarDurumu();
      anahtarKurulumu(); // aynı karekod ikinci kez kullanılamasın
    });
    anahtarKanal.subscribe();
  }
  function anahtarDurumu() {
    $("anahtarDurum").textContent = oku("julide_gemini") ? "Anahtar kayıtlı ✓ (değiştirmek için karekodu okutun)" : "Anahtar yok — karekodu telefonla okutun";
  }

  // ---------------------------------------------------------------- teşhis
  async function teshis() {
    const s = $("teshisSonuc"); s.textContent = "Ölçülüyor…";
    const satir = [];
    try { await LG.baglan(); satir.push("LG kontrol bağlantısı: ✓"); } catch (e) { satir.push("LG kontrol bağlantısı: ✗ " + e.message); }
    if (LG.hazirMi()) {
      try { const l = await LG.uygulamalar(); satir.push("Uygulama listesi: ✓ (" + l.length + ")"); } catch (e) { satir.push("Uygulama listesi: ✗ " + e.message); }
      try { await LG.sesDurumu(); satir.push("Ses kontrolü: ✓"); } catch (e) { satir.push("Ses kontrolü: ✗ " + e.message); }
      try { const g = await LG.girisler(); satir.push("Giriş kaynakları: ✓ " + g.map((d) => d.label).join(", ")); } catch (e) { satir.push("Giriş kaynakları: ✗ " + e.message); }
      try { await LG.imlecTasi(0, 0); satir.push("İmleç/tuş soketi: ✓"); } catch (e) { satir.push("İmleç/tuş soketi: ✗ " + e.message); }
    }
    satir.push("Luna (yedek): " + (typeof PalmServiceBridge !== "undefined" ? "✓ var" : "✗ yok"));
    const w = await Arastirma.erisimTesti();
    Object.keys(w).forEach((k) => satir.push("İnternet · " + k + ": " + w[k]));
    satir.push("Telefon kanalı: " + (bagli ? "✓" : "✗"));
    satir.push("Gemini anahtarı: " + (oku("julide_gemini") ? "✓" : "✗ yok"));
    s.textContent = satir.join("\n");
    gunluk("Teşhis tamamlandı");
  }

  // ---------------------------------------------------------------- kumandayla gezinme
  function odaklanabilir() { return Array.from(document.querySelectorAll("button")); }
  document.addEventListener("keydown", (e) => {
    const l = odaklanabilir();
    const i = l.indexOf(document.activeElement);
    if (e.keyCode === 38 || e.keyCode === 37) { l[(i - 1 + l.length) % l.length].focus(); e.preventDefault(); }
    else if (e.keyCode === 40 || e.keyCode === 39) { l[(i + 1) % l.length].focus(); e.preventDefault(); }
  });

  // ---------------------------------------------------------------- başlangıç
  window.addEventListener("load", () => {
    $("kod").textContent = kod;
    $("telefonQr").innerHTML = JulideLib.qrSvg(SAYFA + "/?kod=" + kod, 5);
    $("adres").textContent = SAYFA.replace("https://", "") + "  ·  kod: " + kod;
    LG.durumDinle((m) => { gunluk(m); if (/izin/.test(m)) durum(m); });
    koprubaglan();
    anahtarKurulumu();
    anahtarDurumu();
    $("sohbet").addEventListener("click", () => (hal === "bosta" ? baslat() : bitir()));
    $("lg").addEventListener("click", () => LG.baglan().then(() => durum("LG kontrol bağlantısı hazır", "iyi")).catch((e) => durum(e.message, "hata")));
    $("teshis").addEventListener("click", teshis);
    $("yeniKod").addEventListener("click", () => { kod = yeniKod(); yaz("julide_kod", kod); location.reload(); });
    document.addEventListener("visibilitychange", () => gunluk(document.hidden ? "Uygulama arka plana geçti" : "Uygulama ön planda"));
    $("sohbet").focus();
    durum(oku("julide_gemini") ? "Hazır. Telefondan “Jülide'yi aç”a ya da “Sohbeti başlat”a basın." : "Önce Gemini API anahtarını kaydedin.");
    // LG eşleştirmesini erkenden iste (ilk seferde TV'de izin sorusu çıkar)
    LG.baglan().catch((e) => gunluk("LG: " + e.message));
  });
})();
