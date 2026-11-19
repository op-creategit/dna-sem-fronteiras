// Monta index.html e compositions/captions.html a partir de timing.json,
// e injeta os tempos da narração em cada cena (entre /*@T*/ e /*@T-end*/).
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { execFileSync } from "node:child_process";
const fdur = (f) => parseFloat(execFileSync("ffprobe", ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", f]).toString());
const root = new URL("..", import.meta.url).pathname;
const timing = JSON.parse(readFileSync(root + "timing.json", "utf8"));
const sfx = JSON.parse(readFileSync(root + "sfx.json", "utf8"));
const MIN = { s14: 11 }; // cenas que seguram mais que a fala (leitura)
let t = 0;
const scenes = timing.scenes.map((s) => {
  const d = Math.max(s.duration, MIN[s.id] || 0);
  const o = { ...s, start: +t.toFixed(3), duration: +d.toFixed(3) };
  t += d;
  return o;
});
const total = +t.toFixed(3);
const T = {};
for (const s of scenes) for (const p of s.phrases) T[p.id] = [p.start, p.end, p.cap];
// injeta tempos nas cenas
for (const s of scenes) {
  const f = root + `compositions/${s.id}.html`;
  if (!existsSync(f)) { console.warn("faltando", f); continue; }
  const mine = Object.fromEntries(Object.entries(T).filter(([k]) => k.startsWith(s.id)));
  const src = readFileSync(f, "utf8").replace(/\/\*@T\*\/[\s\S]*?\/\*@T-end\*\//, `/*@T*/const T = ${JSON.stringify(mine)}; const D = ${s.duration};/*@T-end*/`);
  writeFileSync(f, src);
}
// tempo estimado de uma palavra dentro da frase (proporcional aos caracteres)
const K = (id, w, frac = 0) => {
  const [a, b, c] = T[id];
  if (w) { const i = c.indexOf(w); if (i < 0) throw new Error(`palavra "${w}" não está em ${id}`); return a + (i / c.length) * (b - a); }
  return a + frac * (b - a);
};
// legendas: grupos de até 6 palavras, tempo proporcional aos caracteres
const groups = [];
for (const s of scenes) for (const p of s.phrases) {
  // frases -> segmentos por pontuação; cada segmento em partes equilibradas de até 6 palavras
  const segs = []; let cur = [];
  for (const w of p.cap.split(/\s+/)) { cur.push(w); if (/[.:?!;,]$/.test(w) && cur.length >= 3) { segs.push(cur); cur = []; } }
  if (cur.length) { if (cur.length <= 2 && segs.length) segs[segs.length - 1].push(...cur); else segs.push(cur); }
  const chunks = [];
  for (const sg of segs) { const n = Math.ceil(sg.length / 6), sz = Math.ceil(sg.length / n); for (let i = 0; i < sg.length; i += sz) chunks.push(sg.slice(i, i + sz).join(" ")); }
  const len = chunks.reduce((n, c) => n + c.length + 1, 0);
  let acc = 0;
  for (const c of chunks) {
    const a = s.start + p.start + (acc / len) * (p.end - p.start);
    acc += c.length + 1;
    const b = s.start + p.start + (acc / len) * (p.end - p.start);
    groups.push({ text: c, start: +a.toFixed(3), end: +b.toFixed(3) });
  }
}
// cada grupo fica até o próximo começar (no máximo +0.35 s depois da fala)
for (let i = 0; i < groups.length; i++) {
  const nx = groups[i + 1];
  groups[i].hold = +Math.min(groups[i].end + 0.35, nx ? nx.start - 0.02 : total).toFixed(3);
}
const esc = (x) => x.replace(/&/g, "&amp;").replace(/</g, "&lt;");
const cap = `<template>
  <style>
    #root { position: absolute; inset: 0; pointer-events: none; }
    .cap-g { position: absolute; left: 0; right: 0; bottom: 86px; text-align: center; opacity: 0; }
    .cap-g span { display: inline-block; max-width: 1500px; padding: 12px 30px 14px; border-radius: 14px;
      background: rgba(6, 11, 18, 0.72); color: #F6F1E8; font: 600 46px/1.2 Outfit, sans-serif; letter-spacing: 0.005em; }
  </style>
  <div id="root" data-composition-id="captions" data-width="1920" data-height="1080">
${groups.map((g, i) => `    <div id="cap-${i}" class="cap-g"><span>${esc(g.text)}</span></div>`).join("\n")}
  </div>
  <script>
    const G = ${JSON.stringify(groups.map((g) => [g.start, g.hold]))};
    const tl = gsap.timeline({ paused: true });
    G.forEach(([a, b], i) => {
      const el = "#cap-" + i;
      tl.fromTo(el, { opacity: 0, y: 8 }, { opacity: 1, y: 0, duration: 0.14, ease: "power2.out" }, Math.max(0.05, a));
      tl.to(el, { opacity: 0, duration: 0.1, ease: "power2.in" }, b - 0.1);
      tl.set(el, { opacity: 0, visibility: "hidden" }, b);
    });
    window.__timelines["captions"] = tl;
  </script>
</template>
`;
writeFileSync(root + "compositions/captions.html", cap);
// efeitos sonoros: whoosh em cada corte (alternando takes) + cues do sfx.json
const whooshes = ["whoosh-short", "whoosh", "whoosh-short", "whoosh-cinematic"];
const sfxList = [];
scenes.slice(1).forEach((s, i) => sfxList.push({ f: whooshes[i % whooshes.length], at: s.start - 0.18, v: 0.32 }));
for (const c of sfx) {
  const s = scenes.find((x) => x.id === c.scene);
  const at = s.start + (c.phrase ? K(c.phrase, c.word, c.frac || 0) : 0) + (c.off || 0);
  sfxList.push({ f: c.sound, at, v: c.v ?? 0.45 });
}
sfxList.sort((a, b) => a.at - b.at);
const laneEnd = [];
for (const x of sfxList) {
  x.at = Math.max(0, x.at); x.d = +Math.min(fdur(root + `assets/sfx/${x.f}.mp3`), total - x.at).toFixed(3);
  let l = laneEnd.findIndex((e) => e <= x.at); if (l < 0) { l = laneEnd.length; laneEnd.push(0); }
  laneEnd[l] = x.at + x.d; x.lane = 4 + l;
}
const narrD = +Math.min(fdur(root + "assets/narration.mp3"), total).toFixed(3);
const vars = [
  { id: "color-ground", type: "color", label: "Fundo", default: "#0D1520" },
  { id: "color-ink", type: "color", label: "Texto", default: "#F3EEE6" },
  { id: "color-accent", type: "color", label: "Destaque (fosfato)", default: "#F5A93B" },
  { id: "font-display", type: "string", label: "Fonte de títulos", default: "Outfit" },
];
const index = `<!doctype html>
<html lang="pt-BR" data-composition-variables='${JSON.stringify(vars)}'>
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1920, height=1080" />
    <script src="assets/vendor/gsap.min.js"></script>
    <link rel="stylesheet" href="assets/shared.css" />
  </head>
  <body>
    <div id="root" data-composition-id="main" data-start="0" data-duration="${total}" data-width="1920" data-height="1080">
      <div id="bg" class="bg"></div>
${scenes.filter((s) => existsSync(root + `compositions/${s.id}.html`)).map((s) => `      <div id="${s.id}" data-composition-id="${s.id}" data-composition-src="compositions/${s.id}.html" data-start="${s.start}" data-duration="${s.duration}" data-track-index="1" data-track-kind="graphics" data-width="1920" data-height="1080"></div>`).join("\n")}
      <div id="captions" data-composition-id="captions" data-composition-src="compositions/captions.html" data-start="0" data-duration="${total}" data-track-index="2" data-track-kind="captions" data-width="1920" data-height="1080"></div>
      <audio id="narracao" class="clip" src="assets/narration.mp3" data-start="0" data-duration="${narrD}" data-track-index="3" data-volume="1"></audio>
${sfxList.map((x, i) => `      <audio id="sfx-${i}" class="clip" src="assets/sfx/${x.f}.mp3" data-start="${x.at.toFixed(3)}" data-duration="${x.d}" data-track-index="${x.lane}" data-volume="${x.v}"></audio>`).join("\n")}
    </div>
    <script>
      window.__timelines["main"] = gsap.timeline({ paused: true });
    </script>
  </body>
</html>
`;
writeFileSync(root + "index.html", index);
console.log("total", total, "s ·", groups.length, "legendas ·", sfxList.length, "sfx");
