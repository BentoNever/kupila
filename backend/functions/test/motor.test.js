'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { resumo, divHalfUp } = require('../src/financeiro/motor');

const casos = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', '..', 'contratos', 'casos-financeiros.json'), 'utf8')).casos;

test('casos partilhados com o APK', () => {
  assert.ok(casos.length >= 9);
  for (const c of casos) {
    const r = resumo(c.entrada);
    const e = c.esperado;
    assert.equal(r.valido, e.valido, `${c.id}.valido ${JSON.stringify(r.problemas)}`);
    for (const k of ['base', 'sinal', 'comissao', 'liquido', 'saldo', 'taxa', 'publicacao', 'liquidoPrevisto', 'reembolso', 'baseComissao'])
      if (e[k] !== undefined) assert.equal(r[k], e[k], `${c.id}.${k}`);
    if (e.promocoes) for (const [k, v] of Object.entries(e.promocoes)) assert.equal(r.promocoes[k], v, `${c.id}.promo.${k}`);
  }
});

test('HALF_UP com sinal', () => {
  assert.equal(divHalfUp(5n, 2n), 3n);
  assert.equal(divHalfUp(-5n, 2n), -3n);
  assert.equal(divHalfUp(4n, 3n), 1n);
});

test('isenção sem motivo é recusada; com motivo o sinal mantém-se', () => {
  const base = { modalidade: 'VENDA', preco: '1000000', sinalVenda: '5', mesesSinal: 1, comissao: '15', taxaAnuncio: '1', promocoes: [] };
  assert.equal(resumo({ ...base, isencoes: { comissao: true } }).valido, false);
  const r = resumo({ ...base, isencoes: { comissao: true, taxaAnuncio: true, motivo: 'Publicado pelo Administrador' } });
  assert.equal(r.valido, true);
  assert.equal(r.sinal, '50000.00');
  assert.equal(r.comissao, '0.00');
  assert.equal(r.taxa, '0.00');
});
