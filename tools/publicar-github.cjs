'use strict';
/**
 * Publica o projecto no repositório público BentoNever/kupila (decisão D-28) pela API do GitHub, sem `git` local:
 * cria o repositório se não existir, envia os ficheiros num único commit (Git Data API) e, opcionalmente, cria um
 * release com o APK. Usa a GitHub CLI já autenticada (o token não sai desta máquina excepto para api.github.com).
 *
 * Uso: node tools/publicar-github.cjs [--apk <caminho> --versao-code <n> --versao-nome <x.y.z[-dev]> --pacote <id> --novidades "a;b"
 *        --titulo <t> --notas <ficheiro.md> --prerelease]
 * O release leva a linha `KUPILA-MANIFEST: {json}` lida pelo actualizador da app (tag, nome do APK e SHA-256 conferidos lá).
 */
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const DONO = 'BentoNever', REPO = 'kupila';
const raiz = path.join(__dirname, '..');
const gh = (() => {
  if (spawnSync('gh', ['--version'], { windowsHide: true }).status === 0) return 'gh';
  const base = path.join(process.env.LOCALAPPDATA || '', 'pdvd-tools');
  const d = fs.existsSync(base) ? fs.readdirSync(base).filter((n) => /^gh-/.test(n)).sort().pop() : null;
  if (!d) throw new Error('GitHub CLI não encontrada.');
  return path.join(base, d, 'bin', 'gh.exe');
})();

function api(metodo, rota, corpo, tentativa = 1) {
  try { return apiUmaVez(metodo, rota, corpo); }
  catch (e) {
    // Rede instável: 401/5xx/reset intermitentes. Repete com espera crescente; erros persistentes sobem.
    if (tentativa >= 5 || /Not Found|already exists|Reference already exists|422/.test(String(e.message))) throw e;
    spawnSync(process.execPath, ['-e', `setTimeout(()=>{}, ${tentativa * 3000})`]);
    return api(metodo, rota, corpo, tentativa + 1);
  }
}
function apiUmaVez(metodo, rota, corpo) {
  const args = ['api', '-X', metodo, rota, '-H', 'Accept: application/vnd.github+json'];
  if (corpo) args.push('--input', '-');
  const r = spawnSync(gh, args, { input: corpo ? JSON.stringify(corpo) : undefined, encoding: 'utf8', windowsHide: true, maxBuffer: 256 * 1024 * 1024, timeout: 600000 });
  if (r.status !== 0) { const e = new Error(`${metodo} ${rota}: ${r.stderr || r.stdout}`); e.saida = r.stdout; throw e; }
  return r.stdout ? JSON.parse(r.stdout) : null;
}

// Ficheiros do projecto (nunca configuração Firebase local, chaves, dependências ou saídas de build).
const EXCLUIR = [/(^|\/)node_modules\//, /^dist\//, /(^|\/)build\//, /(^|\/)\.gradle\//, /(^|\/)\.kotlin\//, /(^|\/)\.idea\//, /^docs\/build-logs\//,
  /kupila-firebase\.properties$/, /google-services\.json$/, /local\.properties$/, /\.keystore$/, /\.jks$/, /firebase-debug.*\.log$/,
  /firestore-debug\.log$/, /ui-debug\.log$/, /^firestore\.rules$/ /* cópia do pedido gravada com nome .rules, não são regras */,
  /^Kupila%201\.0\.239\.html$/ /* origem com a configuração web do projecto de produção: fica só local */];
function listar(dir, rel = '') {
  const out = [];
  for (const n of fs.readdirSync(dir)) {
    const r = rel ? `${rel}/${n}` : n, a = path.join(dir, n);
    const st = fs.statSync(a);
    if (EXCLUIR.some((x) => x.test(st.isDirectory() ? r + '/' : r))) continue;
    if (st.isDirectory()) out.push(...listar(a, r)); else out.push(r);
  }
  return out;
}

function principal() {
  const argv = process.argv.slice(2), arg = (k) => { const i = argv.indexOf(k); return i >= 0 ? argv[i + 1] : null; };
  let repo;
  try { repo = api('GET', `/repos/${DONO}/${REPO}`); }
  catch (_) {
    repo = api('POST', '/user/repos', { name: REPO, private: false, auto_init: true,
      description: 'Kupila-Gestão de negócios — marketplace angolano de anúncio, compra, aluguer e reserva protegida de bens (Android nativo + Firebase).' });
    console.log('repositório criado:', repo.html_url);
  }
  const ramo = repo.default_branch || 'main';
  let base = null;
  try { base = api('GET', `/repos/${DONO}/${REPO}/git/ref/heads/${ramo}`).object.sha; } catch (_) { /* repositório vazio */ }

  const ficheiros = listar(raiz);
  console.log(`a enviar ${ficheiros.length} ficheiros…`);
  const arvore = ficheiros.map((f, i) => {
    const conteudo = fs.readFileSync(path.join(raiz, f));
    const blob = api('POST', `/repos/${DONO}/${REPO}/git/blobs`, { content: conteudo.toString('base64'), encoding: 'base64' });
    if ((i + 1) % 25 === 0) console.log(`  ${i + 1}/${ficheiros.length}`);
    return { path: f, mode: f.endsWith('gradlew') ? '100755' : '100644', type: 'blob', sha: blob.sha };
  });
  const tree = api('POST', `/repos/${DONO}/${REPO}/git/trees`, { tree: arvore });   // árvore completa (substitui o conteúdo)
  const commit = api('POST', `/repos/${DONO}/${REPO}/git/commits`, {
    message: arg('--mensagem') || 'Kupila-Gestão de negócios: auditoria, domínio, motor financeiro, Central (Spark/Blaze), regras e app Android',
    tree: tree.sha, parents: base ? [base] : [] });
  if (base) api('PATCH', `/repos/${DONO}/${REPO}/git/refs/heads/${ramo}`, { sha: commit.sha, force: false });
  else api('POST', `/repos/${DONO}/${REPO}/git/refs`, { ref: `refs/heads/${ramo}`, sha: commit.sha });
  console.log('commit:', commit.sha);

  const apk = arg('--apk');
  if (apk) {
    const nome = arg('--versao-nome'), codigo = Number(arg('--versao-code')), pacote = arg('--pacote');
    if (!nome || !codigo || !pacote) throw new Error('--versao-nome, --versao-code e --pacote são obrigatórios com --apk.');
    const tag = `v${nome}`;
    const ficheiro = `kupila-gestao-negocios-${nome.replace(/-dev$/, '')}${pacote.endsWith('.dev') ? '-dev' : ''}.apk`;
    const copia = path.join(require('os').tmpdir(), ficheiro);
    fs.copyFileSync(apk, copia);
    const bytes = fs.readFileSync(copia);
    const manifesto = { versionCode: codigo, versionName: nome, pacote, tamanho: bytes.length,
      sha256: require('crypto').createHash('sha256').update(bytes).digest('hex'),
      apk: `https://github.com/${DONO}/${REPO}/releases/download/${tag}/${ficheiro}`,
      novidades: (arg('--novidades') || '').split(';').map((s) => s.trim()).filter(Boolean), obrigatoria: argv.includes('--obrigatoria') };
    const notas = (arg('--notas') ? fs.readFileSync(arg('--notas'), 'utf8').trim() + '\n\n' : '') + 'KUPILA-MANIFEST: ' + JSON.stringify(manifesto) + '\n';
    const fNotas = path.join(require('os').tmpdir(), `kupila-notas-${codigo}.md`);
    fs.writeFileSync(fNotas, notas);
    const args = ['release', 'create', tag, copia, '-R', `${DONO}/${REPO}`, '--target', ramo, '--title', arg('--titulo') || tag, '--notes-file', fNotas];
    if (argv.includes('--prerelease')) args.push('--prerelease');
    console.log('manifesto:', JSON.stringify(manifesto));
    const r = spawnSync(gh, args, { encoding: 'utf8', windowsHide: true, timeout: 900000 });
    if (r.status !== 0) throw new Error('release: ' + (r.stderr || r.stdout));
    console.log('release:', r.stdout.trim());
  }
}

principal();
