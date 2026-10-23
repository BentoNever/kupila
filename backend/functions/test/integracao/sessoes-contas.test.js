'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { ctx, avancar, limpar, utilizador } = require('./ambiente');
const sessoes = require('../../src/servicos/sessoes');
const contas = require('../../src/servicos/contas');
const { exigirSessao } = require('../../src/comum');

const dadosCliente = (email) => ({ papel: 'CLIENTE', nome: 'Maria Cliente', email, telefonePrincipal: '+244 923 000 000',
  provincia: 'Luanda', municipio: 'Belas', bairro: 'Talatona', canalAlertas: 'EMAIL', politicaVersao: '2026.08.28-4', politicaLidaAteFim: true });

test.beforeEach(limpar);

test('caso 01: cadastro público cria Cliente, nunca Administrador', async () => {
  const a = await utilizador('maria@exemplo.ao');
  const r = await contas.registar(ctx, a, dadosCliente('maria@exemplo.ao'));
  assert.equal(r.papel, 'CLIENTE');
  const u = await ctx.db.collection('users').doc(a.uid).get();
  assert.equal(u.get('role'), 'CLIENTE');
  assert.equal(u.get('phonePrimary'), '923000000');      // +244 sem duplicar
  assert.equal(u.get('policyAcceptance').version, '2026.08.28-4');
  assert.equal((await ctx.auth.getUser(a.uid)).customClaims.papel, 'CLIENTE');
  const b = await utilizador('intruso@exemplo.ao');
  await assert.rejects(contas.registar(ctx, b, { ...dadosCliente('intruso@exemplo.ao'), papel: 'GESTOR' }), /só permite Cliente ou Proprietário/);
  await assert.rejects(contas.registar(ctx, a, dadosCliente('maria@exemplo.ao')), /já foi concluído/);
  await assert.rejects(contas.registar(ctx, b, { ...dadosCliente('intruso@exemplo.ao'), politicaVersao: 'antiga' }), /versão actual/);
  await assert.rejects(contas.registar(ctx, b, { ...dadosCliente('intruso@exemplo.ao'), municipio: 'Inexistente' }), /Província e Município/);
});

test('caso 03: duas instalações disputam a sessão; takeover revoga a anterior', async () => {
  const a = await utilizador('pedro@exemplo.ao', { papel: 'PROPRIETARIO', estado: 'ACTIVE' });
  const s1 = await sessoes.reivindicar(ctx, a, { instalacaoId: 'inst-aaaaaaaa', dispositivo: 'Galaxy A54' });
  assert.ok(s1.sessaoId);
  const conflito = await sessoes.reivindicar(ctx, a, { instalacaoId: 'inst-bbbbbbbb', dispositivo: 'Tecno' });
  assert.equal(conflito.conflito.dispositivo, 'Galaxy A54');
  const s2 = await sessoes.reivindicar(ctx, a, { instalacaoId: 'inst-bbbbbbbb', dispositivo: 'Tecno', forcar: true });
  assert.ok(s2.sessaoId && s2.sessaoId !== s1.sessaoId);
  await assert.rejects(exigirSessao(ctx, a.uid, s1.sessaoId), /encerrada noutro dispositivo/);
  await exigirSessao(ctx, a.uid, s2.sessaoId);
  assert.deepEqual(await sessoes.sinal(ctx, a, { sessaoId: s1.sessaoId }), { vigente: false });
  const auditoria = await ctx.db.collection('auditLog').where('action', '==', 'SESSION_TAKEOVER').get();
  assert.equal(auditoria.size, 1);
});

test('sessão parada há mais de 180 s não bloqueia nova instalação', async () => {
  const a = await utilizador('ana@exemplo.ao', { papel: 'CLIENTE', estado: 'ACTIVE' });
  await sessoes.reivindicar(ctx, a, { instalacaoId: 'inst-cccccccc' });
  avancar(181000);
  const r = await sessoes.reivindicar(ctx, a, { instalacaoId: 'inst-dddddddd' });
  assert.ok(r.sessaoId);
});

test('caso 02: Administrador cria conta sem trocar a sua sessão e respeita autoria', async () => {
  const adm = await utilizador('admin@kupila.ao', { papel: 'GESTOR', estado: 'ACTIVE' });
  const adm2 = await utilizador('admin2@kupila.ao', { papel: 'GESTOR', estado: 'ACTIVE' });
  const s = await sessoes.reivindicar(ctx, adm, { instalacaoId: 'inst-admin001' });
  const s2 = await sessoes.reivindicar(ctx, adm2, { instalacaoId: 'inst-admin002' });
  const criado = await contas.criarPorAdmin(ctx, adm, { ...dadosCliente('novo@exemplo.ao'), papel: 'PROPRIETARIO', senhaInicial: 'inicial1', sessaoId: s.sessaoId });
  const ficha = await ctx.db.collection('users').doc(criado.uid).get();
  assert.equal(ficha.get('createdBy'), adm.uid);
  // A sessão do Administrador continua a ser a mesma.
  await exigirSessao(ctx, adm.uid, s.sessaoId);
  await contas.editarDadosPorAdmin(ctx, adm, { ...dadosCliente('novo@exemplo.ao'), nome: 'Novo Nome', uid: criado.uid, versao: 1, sessaoId: s.sessaoId });
  await assert.rejects(contas.editarDadosPorAdmin(ctx, adm2, { ...dadosCliente('novo@exemplo.ao'), uid: criado.uid, versao: 2, sessaoId: s2.sessaoId }), /só o estado pode ser alterado/);
  await contas.alterarEstado(ctx, adm2, { uid: criado.uid, estado: 'SUSPENDED', motivo: 'Teste', sessaoId: s2.sessaoId });
  assert.equal((await ctx.auth.getUser(criado.uid)).customClaims.estado, 'SUSPENDED');
  await assert.rejects(contas.alterarEstado(ctx, adm, { uid: adm.uid, estado: 'ARCHIVED', sessaoId: s.sessaoId }), /própria conta/);
});
