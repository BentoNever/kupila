'use strict';
// Extrai KUPILA_FAQ_ITEMS (Administrador) e PUBLIC_FAQ (restantes perfis) do HTML de origem.
// Avalia apenas o literal do array (dados {q,a}) num contexto vazio, sem acesso ao resto do script.
// Uso: node tools/extrair_faq.js
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const raiz = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(raiz, 'Kupila%201.0.239.html'), 'utf8');

function literal(nome) {
  const ini = html.indexOf(`const ${nome}`);
  if (ini < 0) throw new Error(nome);
  const a = html.indexOf('[', ini);
  let d = 0, i = a, str = null, esc = false;
  for (; i < html.length; i++) {
    const c = html[i];
    if (str) { if (esc) esc = false; else if (c === '\\') esc = true; else if (c === str) str = null; continue; }
    if (c === '"' || c === "'" || c === '`') { str = c; continue; }
    if (c === '[') d++;
    else if (c === ']') { d--; if (d === 0) break; }
  }
  const linha = html.slice(0, ini).split('\n').length;
  return { linha, itens: vm.runInNewContext('(' + html.slice(a, i + 1) + ')', Object.create(null), { timeout: 1000 }) };
}

const completo = literal('KUPILA_FAQ_ITEMS');
const publico = literal('PUBLIC_FAQ');
const limpar = (x) => ({ pergunta: String(x.q || x.question || '').trim(), resposta: String(x.a || x.answer || '').trim() });
const out = {
  versao: 'faq-v1',
  fonte: `Kupila 1.0.239 · KUPILA_FAQ_ITEMS (linha ${completo.linha}) e PUBLIC_FAQ (linha ${publico.linha})`,
  administrador: completo.itens.map(limpar),
  publico: publico.itens.map(limpar),
};
const destino = path.join(raiz, 'android', 'app', 'src', 'main', 'assets', 'conteudo', 'faq-v1.json');
fs.writeFileSync(destino, JSON.stringify(out, null, 1), 'utf8');
console.log(`faq: administrador=${out.administrador.length} publico=${out.publico.length}`);
