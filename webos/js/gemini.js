/*
 * Gemini Live (tarayıcı WebSocket) + ses çalma. Android'deki GeminiLive.kt ile aynı kurallar:
 * modeller uzaktan ayardan (config/ayarlar.json), kota dolunca sıradaki model, oturum devam tutamacı.
 * Giriş: 16-bit PCM 16 kHz mono (telefondan). Çıkış: 16-bit PCM 24 kHz mono.
 */
(function () {
  "use strict";
  const AYAR_ADRESI = "https://raw.githubusercontent.com/klncumut6565/tv-asistan/main/config/ayarlar.json";
  const VARSAYILAN_MODELLER = [
    { ad: "models/gemini-3.1-flash-live-preview", etiket: "Gemini 3.1 Flash Live" },
    { ad: "models/gemini-2.5-flash-native-audio-preview-12-2025", etiket: "Gemini 2.5 Flash Native Audio" },
  ];
  const kotaDolu = {}; // model → zaman

  const kotaMi = (m) => /quota|resource_exhausted|resource exhausted|rate limit|exceeded/i.test(m || "");

  // ---- ses çıkışı
  let ctx = null, sonraki = 0;
  const calanlar = new Set();
  function baglam() {
    if (!ctx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      try { ctx = new AC({ sampleRate: 24000 }); } catch (_) { ctx = new AC(); }
    }
    if (ctx.state === "suspended") ctx.resume().catch(() => {});
    return ctx;
  }
  function cal(pcm16) {
    const c = baglam();
    const n = pcm16.length;
    const buf = c.createBuffer(1, n, 24000);
    const ch = buf.getChannelData(0);
    for (let i = 0; i < n; i++) ch[i] = pcm16[i] / 32768;
    const src = c.createBufferSource();
    src.buffer = buf; src.connect(c.destination);
    const t = Math.max(c.currentTime + 0.03, sonraki);
    src.start(t);
    sonraki = t + buf.duration;
    calanlar.add(src);
    src.onended = () => calanlar.delete(src);
  }
  function sustur() {
    calanlar.forEach((s) => { try { s.stop(); } catch (_) {} });
    calanlar.clear();
    sonraki = 0;
  }
  const caliyor = () => calanlar.size > 0;

  function b64ToPcm(b64) {
    const s = atob(b64);
    const u8 = new Uint8Array(s.length);
    for (let i = 0; i < s.length; i++) u8[i] = s.charCodeAt(i);
    return new Int16Array(u8.buffer, 0, u8.length >> 1);
  }

  async function ayarlar() {
    try {
      const r = await fetch(AYAR_ADRESI, { cache: "no-cache" });
      const j = await r.json();
      const m = (j.modeller || []).filter((x) => x && /^models\/[\w.\-]+$/.test(x.ad)).map((x) => ({ ad: x.ad, etiket: String(x.etiket || x.ad).slice(0, 40), blocking: !!x.blocking }));
      return { modeller: m.length ? m : VARSAYILAN_MODELLER, ses: /^[A-Za-z]{2,20}$/.test(j.ses || "") ? j.ses : "Kore" };
    } catch (_) { return { modeller: VARSAYILAN_MODELLER, ses: "Kore" }; }
  }

  class Oturum {
    constructor(d) { this.d = d; this.ws = null; this.tutamac = null; this.kapatildi = false; this.model = null; }

    async baglan(anahtar, talimat, araclar) {
      if (!anahtar) throw new Error("Gemini API anahtarı girilmemiş. Telefonla karekodu okutup anahtarı kaydedin.");
      const y = await ayarlar();
      const simdi = Date.now();
      const sira = y.modeller.slice().sort((a, b) => ((kotaDolu[a.ad] || 0) > simdi) - ((kotaDolu[b.ad] || 0) > simdi));
      const hatalar = [];
      let kotasiDolan = 0;
      for (const m of sira) {
        try { await this._tek(anahtar, m, y.ses, talimat, araclar); this.model = m.etiket; return m.etiket; }
        catch (e) {
          hatalar.push(m.etiket + ": " + e.message);
          if (e.anahtar) throw e;
          if (e.kota) { kotasiDolan++; kotaDolu[m.ad] = Date.now() + 3 * 3600e3; }
        }
      }
      if (kotasiDolan >= sira.length) throw new Error("Gemini kotası tüm modellerde doldu. Kota genelde TR saatiyle 10:00'da sıfırlanır.");
      throw new Error("Gemini'ye bağlanılamadı. " + (hatalar[hatalar.length - 1] || ""));
    }

    _tek(anahtar, m, ses, talimat, araclar) {
      return new Promise((coz, reddet) => {
        this.kapatildi = false;
        const url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + encodeURIComponent(anahtar);
        const ws = new WebSocket(url);
        ws.binaryType = "arraybuffer";
        this.ws = ws;
        let hazir = false;
        const zaman = setTimeout(() => { if (!hazir) { try { ws.close(); } catch (_) {} reddet(new Error("kurulum zaman aşımı")); } }, 10000);
        ws.onopen = () => {
          const bildirimler = araclar.map((a) => m.blocking ? Object.assign({ behavior: "BLOCKING" }, a) : a);
          ws.send(JSON.stringify({ setup: {
            model: m.ad,
            generationConfig: { responseModalities: ["AUDIO"], speechConfig: { languageCode: "tr-TR", voiceConfig: { prebuiltVoiceConfig: { voiceName: ses } } } },
            systemInstruction: { parts: [{ text: talimat }] },
            inputAudioTranscription: {}, outputAudioTranscription: {},
            tools: [{ functionDeclarations: bildirimler }],
            realtimeInputConfig: { automaticActivityDetection: { startOfSpeechSensitivity: "START_SENSITIVITY_LOW", prefixPaddingMs: 200, silenceDurationMs: 800 } },
            contextWindowCompression: { slidingWindow: {} },
            sessionResumption: this.tutamac ? { handle: this.tutamac } : {},
          } }));
        };
        ws.onmessage = (e) => {
          const metin = typeof e.data === "string" ? e.data : new TextDecoder().decode(e.data);
          let j; try { j = JSON.parse(metin); } catch (_) { return; }
          if (j.setupComplete) { hazir = true; clearTimeout(zaman); coz(); return; }
          this._isle(j);
        };
        ws.onclose = (e) => {
          clearTimeout(zaman);
          const neden = e.reason || "";
          if (!hazir) {
            const h = new Error("bağlantı kapandı (" + e.code + ") " + neden);
            if (/api key/i.test(neden)) { h.anahtar = true; h.message = "Gemini API anahtarı geçersiz."; }
            if (kotaMi(neden)) h.kota = true;
            reddet(h);
          } else if (!this.kapatildi && this.ws === ws) {
            if (kotaMi(neden)) kotaDolu[m.ad] = Date.now() + 3 * 3600e3;
            this.d.kapandi(neden);
          }
        };
      });
    }

    _isle(j) {
      const u = j.sessionResumptionUpdate;
      if (u && u.resumable && u.newHandle) this.tutamac = u.newHandle;
      if (j.goAway) { if (!this.kapatildi) this.d.kapandi("süre doldu"); return; }
      if (j.toolCall) { this._araclar(j.toolCall.functionCalls || []); return; }
      const sc = j.serverContent;
      if (!sc) return;
      if (sc.interrupted) { sustur(); this.d.kesildi && this.d.kesildi(); }
      const p = sc.modelTurn && sc.modelTurn.parts;
      if (p) for (const x of p) if (x.inlineData && x.inlineData.data) { cal(b64ToPcm(x.inlineData.data)); this.d.konusuyor && this.d.konusuyor(); }
      if (sc.inputTranscription && sc.inputTranscription.text) this.d.kullanici && this.d.kullanici(sc.inputTranscription.text);
      if (sc.outputTranscription && sc.outputTranscription.text) this.d.asistan && this.d.asistan(sc.outputTranscription.text);
      if (sc.turnComplete) this.d.turBitti && this.d.turBitti();
    }

    async _araclar(cagrilar) {
      const yanitlar = await Promise.all(cagrilar.map(async (c) => {
        let s;
        try { s = await this.d.arac(c.name, c.args || {}); } catch (e) { s = { hata: e.message || String(e) }; }
        return { id: c.id, name: c.name, response: s };
      }));
      if (this.ws && this.ws.readyState === 1) this.ws.send(JSON.stringify({ toolResponse: { functionResponses: yanitlar } }));
    }

    sesGonder(pcmB64) {
      if (this.ws && this.ws.readyState === 1)
        this.ws.send(JSON.stringify({ realtimeInput: { audio: { data: pcmB64, mimeType: "audio/pcm;rate=16000" } } }));
    }
    metinGonder(t) { if (this.ws && this.ws.readyState === 1) this.ws.send(JSON.stringify({ realtimeInput: { text: t } })); }
    kapat() { this.kapatildi = true; try { this.ws && this.ws.close(1000, "bitti"); } catch (_) {} this.ws = null; sustur(); }
  }

  window.Gemini = { Oturum, caliyor, sustur, sesiAc: baglam };
})();
