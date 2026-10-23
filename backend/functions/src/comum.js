'use strict';
/**
 * Infra comum do Central: erros tipados, autorização por claims, sessão exclusiva, auditoria e idempotência.
 * Os serviços recebem um contexto `ctx` injectado ({ db, auth, agora, novoId, FieldValue }) para poderem ser
 * testados contra o Firebase Emulator sem depender do runtime das Functions.
 */

class ErroKupila extends Error {
  /** @param {string} codigo códigos do SDK de Functions: invalid-argument, permission-denied, failed-precondition, not-found, already-exists, unauthenticated, resource-exhausted, unavailable */
  constructor(codigo, mensagem, detalhes) { super(mensagem); this.codigo = codigo; this.detalhes = detalhes; }
}
const erro = (codigo, mensagem, detalhes) => new ErroKupila(codigo, mensagem, detalhes);

const PAPEL = Object.freeze({ CLIENTE: 'CLIENTE', PROPRIETARIO: 'PROPRIETARIO', ADMIN: 'GESTOR' });
const ESTADO_CONTA = Object.freeze({ ACTIVA: 'ACTIVE', INACTIVA: 'INACTIVE', SUSPENSA: 'SUSPENDED', ARQUIVADA: 'ARCHIVED' });

/** Identidade do pedido: só a partir do token verificado (claims definidos pelo Central). */
function identidade(auth) {
  if (!auth || !auth.uid) throw erro('unauthenticated', 'Sessão inválida. Entre novamente.');
  const t = auth.token || {};
  return { uid: auth.uid, papel: t.papel || null, estado: t.estado || null, email: t.email || '' };
}

function exigirActivo(id) {
  if (id.estado !== ESTADO_CONTA.ACTIVA) throw erro('permission-denied', 'A sua conta não está activa para esta operação.');
  return id;
}
function exigirAdmin(id) {
  exigirActivo(id);
  if (id.papel !== PAPEL.ADMIN) throw erro('permission-denied', 'Operação reservada ao Administrador.');
  return id;
}
function exigirPapel(id, ...papeis) {
  exigirActivo(id);
  if (!papeis.includes(id.papel)) throw erro('permission-denied', 'O seu perfil não permite esta operação.');
  return id;
}

/**
 * Sessão exclusiva (secção 9): toda a mutação traz `sessaoId`; se não for a vigente, é negada centralmente
 * (uma instalação substituída não consegue alterar nada).
 */
async function exigirSessao(ctx, uid, sessaoId) {
  if (!sessaoId) throw erro('failed-precondition', 'Sessão do dispositivo em falta. Entre novamente.', { motivo: 'SESSAO_EM_FALTA' });
  const s = await ctx.db.collection('activeSessions').doc(uid).get();
  if (!s.exists || s.get('sessionId') !== sessaoId || s.get('revogada') === true)
    throw erro('failed-precondition', 'Esta sessão foi encerrada noutro dispositivo.', { motivo: 'SESSAO_SUBSTITUIDA' });
}

/** Auditoria (secção 34): actor, acção, entidade, antes/depois resumido, motivo e resultado. Sem segredos. */
function auditoria(ctx, tx, { actor, accao, entidade, entidadeId, antes = null, depois = null, motivo = '', resultado = 'OK' }) {
  const ref = ctx.db.collection('auditLog').doc(ctx.novoId());
  const reg = { actorId: actor.uid, actorPapel: actor.papel || null, action: accao, entityType: entidade, entityId: entidadeId,
    before: antes, after: depois, reason: motivo, result: resultado, createdAt: ctx.agora() };
  if (tx) tx.set(ref, reg); else return ref.set(reg);
  return null;
}

/**
 * Idempotência: o cliente envia `idempotencyKey`. A primeira execução guarda a resposta; repetições devolvem-na
 * sem repetir efeitos (secções 19-20). Executa `fn(tx)` dentro de uma transacção.
 */
async function idempotente(ctx, uid, chave, fn) {
  if (!chave || !/^[A-Za-z0-9_-]{8,80}$/.test(chave)) throw erro('invalid-argument', 'Identificador de operação inválido.');
  const ref = ctx.db.collection('secrets').doc('idempotencia').collection('chaves').doc(`${uid}_${chave}`);
  return ctx.db.runTransaction(async (tx) => {
    const ja = await tx.get(ref);
    if (ja.exists) return { ...ja.get('resposta'), repetido: true };
    const resposta = await fn(tx);
    tx.set(ref, { uid, resposta, criadaEm: ctx.agora() });
    return resposta;
  });
}

function texto(v, max, nome) {
  const s = String(v ?? '').trim();
  if (s.length > max) throw erro('invalid-argument', `${nome} excede ${max} caracteres.`);
  return s;
}

const EMAIL_RX = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
function telefone(v) {
  let d = String(v || '').replace(/\D/g, '');
  if (d.startsWith('00244')) d = d.slice(5); else if (d.startsWith('244') && d.length === 12) d = d.slice(3);
  return d;
}
const telefoneValido = (v) => /^9\d{8}$/.test(telefone(v));

module.exports = { ErroKupila, erro, PAPEL, ESTADO_CONTA, identidade, exigirActivo, exigirAdmin, exigirPapel, exigirSessao,
  auditoria, idempotente, texto, EMAIL_RX, telefone, telefoneValido };
