// Captura de referência visual do HTML de origem (paridade de design, docs/paridade-visual.md).
// Abre o HTML num Chrome headless com largura de telemóvel e executa "passos" (JS na página) seguidos de screenshot.
// O acesso ao Firebase de produção é BLOQUEADO no arranque do Chrome (host-resolver-rules): nenhum pedido chega ao projecto real.
// Uso: node tools/referencia-html/capturar.mjs <ficheiro.html> <pasta-saida> <passos.json>
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const [html, saida, passosF] = process.argv.slice(2);
const passos = JSON.parse(fs.readFileSync(passosF, 'utf8').replace(/^\uFEFF/, ''));
fs.mkdirSync(saida, { recursive: true });
const chrome = ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(fs.existsSync);
const bloqueio = ['firestore.googleapis.com', 'identitytoolkit.googleapis.com', 'securetoken.googleapis.com', 'firebasestorage.googleapis.com',
  'firebaseinstallations.googleapis.com', 'firebase.googleapis.com', '*.firebaseio.com', '*.cloudfunctions.net', 'www.googleapis.com', '*.firebaseapp.com']
  .map((h) => `MAP ${h} 0.0.0.0`).join(', ');
const perfil = path.join(os.tmpdir(), 'kupila-ref-perfil');   // caminho ASCII: o Chrome falha com acentos no user-data-dir
const porta = 9300 + Math.floor(Math.random() * 500);
const proc = spawn(chrome, ['--headless=new', `--remote-debugging-port=${porta}`, `--user-data-dir=${perfil}`, `--host-resolver-rules=${bloqueio}`,
  '--no-first-run', '--disable-extensions', '--hide-scrollbars', 'about:blank'], { stdio: 'ignore' });
const espera = (ms) => new Promise((r) => setTimeout(r, ms));

let ws, seq = 0; const pend = new Map();
function cmd(method, params = {}) {
  const id = ++seq; ws.send(JSON.stringify({ id, method, params }));
  return new Promise((res, rej) => pend.set(id, { res, rej }));
}
async function ligar() {
  for (let i = 0; i < 150; i++) {
    try {
      const l = await (await fetch(`http://127.0.0.1:${porta}/json/list`)).json();
      const p = l.find((x) => x.type === 'page'); if (p) return p.webSocketDebuggerUrl;
    } catch (_) { /* a arrancar */ }
    await espera(200);
  }
  throw new Error('Chrome não respondeu.');
}

try {
  ws = new WebSocket(await ligar());
  await new Promise((r) => ws.addEventListener('open', r));
  ws.addEventListener('message', (m) => { const d = JSON.parse(m.data); if (d.id && pend.has(d.id)) { const p = pend.get(d.id); pend.delete(d.id); d.error ? p.rej(new Error(d.error.message)) : p.res(d.result); } });
  await cmd('Page.enable'); await cmd('Runtime.enable');
  // Galaxy A54: 412 x 915 dp.
  await cmd('Emulation.setDeviceMetricsOverride', { width: 412, height: 915, deviceScaleFactor: 2, mobile: true });
  await cmd('Emulation.setUserAgentOverride', { userAgent: 'Mozilla/5.0 (Linux; Android 14; SM-A546E) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36' });
  await cmd('Page.navigate', { url: 'file:///' + path.resolve(html).replace(/\\/g, '/') });
  await espera(6000);
  for (const p of passos) {
    if (p.recarregar) { await cmd('Runtime.evaluate', { expression: 'try{localStorage.clear();sessionStorage.clear()}catch(_){}' }); await cmd('Page.navigate', { url: 'file:///' + path.resolve(html).replace(/\\/g, '/') }); await espera(5000); }
    if (p.ficheiro) await cmd('Runtime.evaluate', { expression: fs.readFileSync(path.resolve(path.dirname(passosF), p.ficheiro), 'utf8') });
    if (p.js) {
      const r = await cmd('Runtime.evaluate', { expression: `(async()=>{${p.js}})()`, awaitPromise: true, returnByValue: true });
      if (r.exceptionDetails) console.log(p.nome, 'ERRO JS:', r.exceptionDetails.exception?.description || r.exceptionDetails.text);
      else if (r.result?.value !== undefined) console.log(p.nome, '→', JSON.stringify(r.result.value).slice(0, 400000));
    }
    await espera(p.espera ?? 800);
    if (p.foto !== false) {
      const s = await cmd('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
      fs.writeFileSync(path.join(saida, `${p.nome}.png`), Buffer.from(s.data, 'base64'));
      console.log('foto', p.nome);
    }
  }
} finally {
  try { ws?.close(); } catch (_) { }
  proc.kill();
}
