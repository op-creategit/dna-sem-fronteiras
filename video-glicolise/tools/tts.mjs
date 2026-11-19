// Gera a narração (Kokoro local, pt-BR) frase a frase e monta timing.json + assets/narration.wav
// Uso: HF=<plugin-cli.mjs> HYPERFRAMES_PYTHON=<venv python> node tools/tts.mjs
import { readFileSync, writeFileSync, existsSync, mkdirSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
const root = new URL("..", import.meta.url).pathname;
const S = JSON.parse(readFileSync(root + "script.json", "utf8"));
const HF = process.env.HF;
const LEAD = 0.5, GAP = 0.35, TAIL = 0.8;
mkdirSync(root + "assets/voice", { recursive: true });
const cacheF = root + "assets/voice/.cache.json";
const cache = existsSync(cacheF) ? JSON.parse(readFileSync(cacheF, "utf8")) : {};
const dur = (f) => parseFloat(execFileSync("ffprobe", ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", f]).toString());
const timing = { scenes: [], total: 0 };
let t = 0;
const concat = [];
const sil = (d, name) => { const f = root + `assets/voice/.sil-${name}.wav`; execFileSync("ffmpeg", ["-loglevel", "error", "-y", "-f", "lavfi", "-i", "anullsrc=r=24000:cl=mono", "-t", d.toFixed(3), f]); concat.push(f); };
for (const sc of S.cenas) {
  const scene = { id: sc.id, start: +t.toFixed(3), phrases: [] };
  let lt = LEAD; sil(LEAD, sc.id + "-lead");
  sc.frases.forEach((p, i) => {
    const say = p.say || p.cap;
    const h = createHash("sha1").update(say + S.voz + S.velocidade).digest("hex");
    const out = root + `assets/voice/${p.id}.wav`;
    if (cache[p.id] !== h || !existsSync(out)) {
      execFileSync("node", [HF, "tts", say, "-v", S.voz, "-l", "pt-br", "-s", String(S.velocidade), "-o", out], { stdio: "ignore" });
      execFileSync("ffmpeg", ["-loglevel", "error", "-y", "-i", out, "-ar", "24000", "-ac", "1", out + ".tmp.wav"]);
      execFileSync("mv", [out + ".tmp.wav", out]);
      cache[p.id] = h;
    }
    const d = dur(out);
    scene.phrases.push({ id: p.id, cap: p.cap, start: +lt.toFixed(3), end: +(lt + d).toFixed(3) });
    concat.push(out); lt += d;
    const g = (i < sc.frases.length - 1 ? GAP : 0) + (p.pausa || 0);
    if (g > 0) { sil(g, p.id + "-gap"); lt += g; }
  });
  sil(TAIL, sc.id + "-tail"); lt += TAIL;
  scene.duration = +lt.toFixed(3);
  timing.scenes.push(scene);
  t += lt;
  process.stdout.write(`${sc.id} ${lt.toFixed(1)}s\n`);
}
timing.total = +t.toFixed(3);
writeFileSync(cacheF, JSON.stringify(cache, null, 1));
writeFileSync(root + "timing.json", JSON.stringify(timing, null, 1));
const list = root + "assets/voice/.list.txt";
writeFileSync(list, concat.map((f) => `file '${f}'`).join("\n"));
execFileSync("ffmpeg", ["-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", list, "-ar", "44100", "-ac", "1", root + "assets/narration.wav"]);
execFileSync("ffmpeg", ["-loglevel", "error", "-y", "-i", root + "assets/narration.wav", "-codec:a", "libmp3lame", "-b:a", "128k", root + "assets/narration.mp3"]);
console.log("total", timing.total.toFixed(1), "s");
