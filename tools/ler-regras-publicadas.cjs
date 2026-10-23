'use strict';
// Lê (só leitura) as regras Firestore e Storage publicadas num projecto Firebase, com a sessão do firebase-tools.
// Uso: node tools/ler-regras-publicadas.cjs <projectId> <pastaDestino>
const path = require('path');
const fs = require('fs');
const os = require('os');

const projecto = process.argv[2], destino = process.argv[3];
const npmGlobal = path.join(process.env.APPDATA, 'npm', 'node_modules', 'firebase-tools');
const { getAccessToken } = require(path.join(npmGlobal, 'lib', 'auth.js'));
const cfg = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json'), 'utf8'));

(async () => {
  const t = await getAccessToken(cfg.tokens.refresh_token, ['https://www.googleapis.com/auth/cloud-platform']);
  const token = t.access_token;
  const get = async (url) => { const r = await fetch(url, { headers: { Authorization: `Bearer ${token}` } }); if (!r.ok) throw new Error(`${r.status} ${url} ${await r.text()}`); return r.json(); };
  fs.mkdirSync(destino, { recursive: true });
  const rels = await get(`https://firebaserules.googleapis.com/v1/projects/${projecto}/releases`);
  for (const rel of rels.releases || []) {
    const rs = await get(`https://firebaserules.googleapis.com/v1/${rel.rulesetName}`);
    const nome = rel.name.split('/releases/')[1].replace(/[^A-Za-z0-9._-]+/g, '_');
    for (const f of rs.source.files) {
      const alvo = path.join(destino, `${nome}__${path.basename(f.name)}`);
      fs.writeFileSync(alvo, f.content, 'utf8');
      console.log(`${rel.name.split('/releases/')[1]}  actualizada=${rel.updateTime}  -> ${alvo} (${f.content.length} car.)`);
    }
  }
})().catch((e) => { console.error(e.message); process.exit(1); });
