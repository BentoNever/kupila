'use strict';
/**
 * Regras Firestore no modo Spark (D-27), testadas pela API REST do emulador com tokens não assinados (o emulador aceita-os).
 * O papel e o estado vêm de users/{uid} (semeados com "Bearer owner", que ignora as regras).
 */
const test = require('node:test');
const assert = require('node:assert/strict');

const HOST = process.env.FIRESTORE_EMULATOR_HOST;
if (!HOST) throw new Error('Testes de regras só correm no emulador.');
const RAIZ = `projects/demo-kupila/databases/(default)/documents`;
const BASE = `http://${HOST}/v1/${RAIZ}`;
const POLITICA = '2026.08.28-4';

const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
function token(uid, email = `${uid}@exemplo.ao`) {
  const agora = Math.floor(Date.now() / 1000);
  return `${b64({ alg: 'none', typ: 'JWT' })}.${b64({ sub: uid, user_id: uid, email, iat: agora, exp: agora + 3600, aud: 'demo-kupila',
    iss: 'https://securetoken.google.com/demo-kupila', firebase: { sign_in_provider: 'password' } })}.`;
}
const T = { cliente: token('cliente'), dono: token('dono'), outro: token('outro'), adm: token('adm'), susp: token('susp'), novo: token('novo'), novo2: token('novo2') };

function valor(v) {
  if (v === null) return { nullValue: null };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === 'string') return { stringValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(valor) } };
  return { mapValue: { fields: campos(v) } };
}
const campos = (o) => Object.fromEntries(Object.entries(o).map(([k, v]) => [k, valor(v)]));

async function ler(caminho, tk) {
  return (await fetch(`${BASE}/${caminho}`, { headers: tk ? { Authorization: `Bearer ${tk}` } : {} })).status;
}
/** Escrita com transformações "hora do servidor" (equivalente a FieldValue.serverTimestamp()). */
async function gravar(caminho, dados, tk, { hora = [], mascara = null, existe = null } = {}) {
  const w = { update: { name: `${RAIZ}/${caminho}`, fields: campos(dados) }, updateTransforms: hora.map((f) => ({ fieldPath: f, setToServerValue: 'REQUEST_TIME' })) };
  if (mascara) w.updateMask = { fieldPaths: mascara };
  if (existe !== null) w.currentDocument = { exists: existe };
  const r = await fetch(`http://${HOST}/v1/projects/demo-kupila/databases/(default)/documents:commit`, {
    method: 'POST', headers: { Authorization: `Bearer ${tk}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ writes: [w] }) });
  return r.status;
}
const ok = async (p) => assert.equal(await p, 200);
const negado = async (p) => assert.equal(await p, 403);

const pessoa = (role, extra = {}) => ({ id: '', role, status: 'ACTIVE', email: '', createdBy: null, version: 1, policyAcceptance: null,
  name: 'Nome Teste', phonePrimary: '923000000', phoneAlt: '', province: 'Luanda', municipality: 'Belas', neighborhood: 'Talatona', alertChannel: 'AUTO', photoPath: null, ...extra });

test.beforeEach(async () => {
  await fetch(`http://${HOST}/emulator/v1/projects/demo-kupila/databases/(default)/documents`, { method: 'DELETE' });
  const sem = 'owner';
  for (const [u, role, status] of [['cliente', 'CLIENTE', 'ACTIVE'], ['dono', 'PROPRIETARIO', 'ACTIVE'], ['outro', 'PROPRIETARIO', 'ACTIVE'],
    ['adm', 'GESTOR', 'ACTIVE'], ['susp', 'CLIENTE', 'SUSPENDED']])
    await gravar(`users/${u}`, pessoa(role, { id: u, status, email: `${u}@exemplo.ao` }), sem);
  await gravar('users/criado', pessoa('CLIENTE', { id: 'criado', email: 'criado@exemplo.ao', createdBy: 'adm' }), sem);
  await gravar('products/P1', { status: 'ACTIVE', ownerId: 'dono', title: 'Casa' }, sem);
  await gravar('products/P2', { status: 'PENDING_PAYMENT', ownerId: 'dono', title: 'Rascunho' }, sem);
  await gravar('productPrivate/P1', { ownerId: 'dono', neighborhood: 'Talatona', referencePoint: 'Escola' }, sem);
  await gravar('paymentIntents/I1', { userId: 'cliente', status: 'AWAITING_PAYMENT', amount: 50000 }, sem);
  await gravar('activeSessions/cliente', { sessionId: 'S1-sessao', userId: 'cliente', installId: 'inst-aaaa', status: 'ACTIVE' }, sem);
  await gravar('notifications/cliente/items/N1', { lida: false, titulo: 'x' }, sem);
  await gravar('settings/publicIdentity', { visualTheme: 'EMERALD' }, sem);
});

const registo = (u, role, extra = {}) => pessoa(role, { id: u, email: `${u}@exemplo.ao`,
  policyAcceptance: { version: POLITICA, purpose: 'REGISTER', readToEnd: true }, photoPath: role === 'PROPRIETARIO' ? `privado/perfis/${u}/foto.jpg` : null, ...extra });
const HORA_REGISTO = ['createdAt', 'updatedAt', 'policyAcceptance.acceptedAt'];

test('caso 01: cadastro público só Cliente/Proprietário, com e-mail próprio e política vigente', async () => {
  await ok(gravar('users/novo', registo('novo', 'CLIENTE'), T.novo, { hora: HORA_REGISTO }));
  await negado(gravar('users/novo2', registo('novo2', 'GESTOR'), T.novo2, { hora: HORA_REGISTO }));
  await negado(gravar('users/novo2', registo('novo2', 'CLIENTE', { email: 'outro@exemplo.ao' }), T.novo2, { hora: HORA_REGISTO }));
  await negado(gravar('users/novo2', registo('novo2', 'CLIENTE', { policyAcceptance: { version: 'antiga', purpose: 'REGISTER' } }), T.novo2, { hora: HORA_REGISTO }));
  await negado(gravar('users/novo2', registo('novo2', 'PROPRIETARIO', { photoPath: null }), T.novo2, { hora: HORA_REGISTO }));   // foto obrigatória
  await negado(gravar('users/novo2', registo('novo2', 'CLIENTE'), T.novo2, { hora: ['createdAt'] }));                            // sem hora do servidor
  await negado(gravar('users/novo2', registo('novo2', 'CLIENTE', { status: 'SUSPENDED' }), T.novo2, { hora: HORA_REGISTO }));
  await ok(gravar('users/novo2', registo('novo2', 'PROPRIETARIO'), T.novo2, { hora: HORA_REGISTO }));
});

test('titular não muda papel, estado nem e-mail; actualiza dados com versão', async () => {
  await negado(gravar('users/cliente', { role: 'GESTOR' }, T.cliente, { mascara: ['role'] }));
  await negado(gravar('users/cliente', { status: 'ACTIVE', email: 'x@y.ao' }, T.cliente, { mascara: ['email'] }));
  await ok(gravar('users/cliente', { name: 'Novo Nome', version: 2 }, T.cliente, { mascara: ['name', 'version', 'updatedAt'], hora: ['updatedAt'] }));
  await negado(gravar('users/cliente', { name: 'Outro', version: 2 }, T.cliente, { mascara: ['name', 'version', 'updatedAt'], hora: ['updatedAt'] }));
  await negado(gravar('users/susp', { name: 'Suspenso', version: 2 }, T.susp, { mascara: ['name', 'version', 'updatedAt'], hora: ['updatedAt'] }));
});

test('caso 02: Administrador muda estado de qualquer conta; dados só das que criou; concede papel com motivo', async () => {
  await ok(gravar('users/cliente', { status: 'SUSPENDED', statusChangedBy: 'adm', statusReason: 'Teste' }, T.adm,
    { mascara: ['status', 'statusChangedBy', 'statusReason', 'updatedAt'], hora: ['updatedAt'] }));
  await negado(gravar('users/adm', { status: 'ARCHIVED', statusChangedBy: 'adm' }, T.adm, { mascara: ['status', 'statusChangedBy', 'updatedAt'], hora: ['updatedAt'] }));
  await negado(gravar('users/dono', { status: 'SUSPENDED', statusChangedBy: 'cliente' }, T.cliente, { mascara: ['status', 'statusChangedBy', 'updatedAt'], hora: ['updatedAt'] }));
  await negado(gravar('users/dono', { name: 'Alterado', version: 2 }, T.adm, { mascara: ['name', 'version', 'updatedAt'], hora: ['updatedAt'] }));       // conta pública
  await ok(gravar('users/criado', { name: 'Alterado', version: 2 }, T.adm, { mascara: ['name', 'version', 'updatedAt'], hora: ['updatedAt'] }));         // criada por ele
  await ok(gravar('users/outro', { role: 'GESTOR', roleGrantedBy: 'adm', roleGrantReason: 'Equipa de apoio' }, T.adm,
    { mascara: ['role', 'roleGrantedBy', 'roleGrantReason', 'updatedAt'], hora: ['updatedAt'] }));
  await negado(gravar('users/dono', { role: 'GESTOR', roleGrantedBy: 'dono', roleGrantReason: 'Eu mesmo' }, T.dono,
    { mascara: ['role', 'roleGrantedBy', 'roleGrantReason', 'updatedAt'], hora: ['updatedAt'] }));
  // Criação de conta por Administrador (autoria registada).
  await ok(gravar('users/nova', pessoa('PROPRIETARIO', { id: 'nova', email: 'nova@exemplo.ao', createdBy: 'adm' }), T.adm, { hora: ['createdAt', 'updatedAt'] }));
  await negado(gravar('users/nova2', pessoa('CLIENTE', { id: 'nova2', email: 'n2@exemplo.ao', createdBy: 'dono' }), T.dono, { hora: ['createdAt', 'updatedAt'] }));
});

test('caso 03: sessão exclusiva escrita pelo titular com hora do servidor; conta suspensa não reivindica', async () => {
  const s = { userId: 'cliente', role: 'CLIENTE', sessionId: 'KSX-nova-sessao', installId: 'inst-bbbbbbbb', deviceLabel: 'A54', status: 'ACTIVE', foreground: true };
  await ok(gravar('activeSessions/cliente', s, T.cliente, { hora: ['startedAt', 'lastSeenAt', 'updatedAt'] }));
  await negado(gravar('activeSessions/dono', { ...s, userId: 'dono' }, T.cliente, { hora: ['startedAt', 'lastSeenAt', 'updatedAt'] }));
  await negado(gravar('activeSessions/susp', { ...s, userId: 'susp' }, T.susp, { hora: ['startedAt', 'lastSeenAt', 'updatedAt'] }));
  await negado(gravar('activeSessions/cliente', s, T.cliente, { hora: ['startedAt'] }));   // updatedAt tem de ser a hora do servidor
});

test('catálogo público e projecção privada (caso 11)', async () => {
  await ok(ler('products/P1', T.cliente));
  await negado(ler('products/P2', T.cliente));
  await ok(ler('products/P2', T.dono));
  await negado(ler('productPrivate/P1', T.cliente));
  await negado(ler('productPrivate/P1', T.outro));
  await ok(ler('productPrivate/P1', T.dono));
  await ok(ler('productPrivate/P1', T.adm));
  await negado(ler('products/P1', null));
  await ok(ler('settings/publicIdentity', null));
  await negado(ler('users/dono', T.cliente));
});

test('cliente não escreve estados financeiros, anúncios ou ajustes (casos 15-17)', async () => {
  await negado(gravar('paymentIntents/I1', { status: 'COMPLETED' }, T.cliente, { mascara: ['status'] }));
  await negado(gravar('reservations/R1', { userId: 'cliente', status: 'ACTIVE' }, T.cliente));
  await negado(gravar('products/P3', { status: 'ACTIVE', ownerId: 'dono' }, T.dono));
  await negado(gravar('settings/platform', { commissionPercent: 0 }, T.cliente));
  await negado(gravar('kupila_probe/x', { a: 1 }, T.cliente));
});

test('auditoria só de acréscimo, do próprio actor, com hora do servidor', async () => {
  await ok(gravar('auditLog/A1', { actorId: 'cliente', action: 'SESSION_START', entityType: 'SESSION', entityId: 'cliente', result: 'OK' }, T.cliente, { hora: ['createdAt'] }));
  await negado(gravar('auditLog/A2', { actorId: 'adm', action: 'FORJADO', entityType: 'X', entityId: 'y' }, T.cliente, { hora: ['createdAt'] }));
  await negado(gravar('auditLog/A1', { actorId: 'cliente', action: 'ALTERADO', entityType: 'SESSION', entityId: 'cliente' }, T.cliente, { hora: ['createdAt'] }));
  await negado(ler('auditLog/A1', T.cliente));
  await ok(ler('auditLog/A1', T.adm));
});

test('rascunhos próprios com sessão vigente e versão crescente; suspenso não escreve', async () => {
  await ok(gravar('productWorkDrafts/cliente__PD-1', { userId: 'cliente', versao: 1, sessaoId: 'S1-sessao', titulo: 'x' }, T.cliente));
  await negado(gravar('productWorkDrafts/cliente__PD-1', { userId: 'cliente', versao: 1, sessaoId: 'S1-sessao', titulo: 'y' }, T.cliente));
  await negado(gravar('productWorkDrafts/cliente__PD-1', { userId: 'cliente', versao: 2, sessaoId: 'ANTIGA', titulo: 'y' }, T.cliente));
  await ok(gravar('productWorkDrafts/cliente__PD-1', { userId: 'cliente', versao: 2, sessaoId: 'S1-sessao', titulo: 'y' }, T.cliente));
  await negado(ler('productWorkDrafts/cliente__PD-1', T.outro));
  await negado(gravar('productWorkDrafts/susp__PD-2', { userId: 'susp', versao: 1, sessaoId: 'S1-sessao' }, T.susp));
});

test('notificações: só o titular e só marcar como lida', async () => {
  await ok(gravar('notifications/cliente/items/N1', { lida: true }, T.cliente, { mascara: ['lida'], existe: true }));
  await negado(gravar('notifications/cliente/items/N1', { titulo: 'alterado' }, T.cliente, { mascara: ['titulo'], existe: true }));
  await negado(ler('notifications/cliente/items/N1', T.outro));
});
