'use strict';
/**
 * Sessão exclusiva por conta (secção 9; origem `claimExclusiveSession`, HTML 10216-10226).
 * Parâmetros da origem: heartbeat 45 s, sessão considerada viva até 180 s sem sinal.
 * Diferença deliberada: a reivindicação é feita pelo Central, atómica e auditada, e a anterior é REVOGADA
 * (tokens de refresh revogados + `exigirSessao` nega qualquer mutação com o id antigo).
 */
const { erro, identidade, exigirActivo, auditoria, texto } = require('../comum');

const HEARTBEAT_MS = 45000;
const SESSAO_VIVA_MS = 180000;

function viva(s, agora) {
  if (!s || s.status !== 'ACTIVE' || s.revogada) return false;
  return agora - Number(s.lastSeenAt || s.startedAt || 0) <= SESSAO_VIVA_MS;
}

/**
 * @param dados { instalacaoId, dispositivo, forcar, sessaoActual }
 * @returns { sessaoId } | { conflito: { dispositivo, vistoEm, desde } }
 */
async function reivindicar(ctx, auth, dados) {
  const id = exigirActivo(identidade(auth));
  const instalacao = texto(dados?.instalacaoId, 80, 'Instalação');
  if (!/^[A-Za-z0-9-]{8,80}$/.test(instalacao)) throw erro('invalid-argument', 'Identificador de instalação inválido.');
  const dispositivo = texto(dados?.dispositivo, 80, 'Dispositivo') || 'Android';
  const forcar = dados?.forcar === true;
  const ref = ctx.db.collection('activeSessions').doc(id.uid);
  const agora = ctx.agora();
  let anterior = null;
  const res = await ctx.db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const cur = snap.exists ? snap.data() : null;
    // Mesma instalação a retomar a sua sessão vigente: renova sem conflito.
    if (cur && cur.installId === instalacao && cur.sessionId === dados?.sessaoActual && !cur.revogada) {
      tx.update(ref, { lastSeenAt: agora, updatedAt: agora, deviceLabel: dispositivo });
      return { sessaoId: cur.sessionId, retomada: true };
    }
    if (cur && viva(cur, agora) && cur.installId !== instalacao && !forcar) {
      return { conflito: { dispositivo: cur.deviceLabel || 'Outro dispositivo', vistoEm: cur.lastSeenAt || null, desde: cur.startedAt || null } };
    }
    anterior = cur;
    const sessaoId = 'KSX-' + ctx.novoId();
    tx.set(ref, {
      userId: id.uid, role: id.papel, sessionId: sessaoId, installId: instalacao, deviceLabel: dispositivo,
      status: 'ACTIVE', revogada: false, startedAt: agora, lastSeenAt: agora, updatedAt: agora,
      takenOverAt: cur && viva(cur, agora) ? agora : null, previousInstallId: cur ? cur.installId || null : null,
    });
    auditoria(ctx, tx, { actor: id, accao: cur && viva(cur, agora) ? 'SESSION_TAKEOVER' : 'SESSION_START', entidade: 'SESSION', entidadeId: id.uid,
      antes: cur ? { installId: cur.installId || null, deviceLabel: cur.deviceLabel || null } : null,
      depois: { installId: instalacao, deviceLabel: dispositivo } });
    return { sessaoId };
  });
  // Tomada de sessão: a instalação anterior perde o refresh token (terá de entrar de novo).
  if (res.sessaoId && !res.retomada && anterior && anterior.installId && anterior.installId !== instalacao && ctx.auth) {
    await ctx.auth.revokeRefreshTokens(id.uid).catch(() => {});
  }
  return res;
}

/** Heartbeat de 1.º plano da sessão vigente. */
async function sinal(ctx, auth, dados) {
  const id = identidade(auth);
  const ref = ctx.db.collection('activeSessions').doc(id.uid);
  const agora = ctx.agora();
  return ctx.db.runTransaction(async (tx) => {
    const s = await tx.get(ref);
    if (!s.exists || s.get('sessionId') !== dados?.sessaoId || s.get('revogada') === true) return { vigente: false };
    tx.update(ref, { lastSeenAt: agora, foreground: dados?.primeiroPlano !== false, updatedAt: agora });
    return { vigente: true, proximoEmMs: HEARTBEAT_MS };
  });
}

/** Logout explícito: liberta a sessão (outra instalação pode entrar sem "tomar"). */
async function terminar(ctx, auth, dados) {
  const id = identidade(auth);
  const ref = ctx.db.collection('activeSessions').doc(id.uid);
  const agora = ctx.agora();
  await ctx.db.runTransaction(async (tx) => {
    const s = await tx.get(ref);
    if (!s.exists || s.get('sessionId') !== dados?.sessaoId) return;
    tx.update(ref, { status: 'ENDED', endedAt: agora, updatedAt: agora });
    auditoria(ctx, tx, { actor: id, accao: 'SESSION_END', entidade: 'SESSION', entidadeId: id.uid });
  });
  return { ok: true };
}

module.exports = { reivindicar, sinal, terminar, viva, HEARTBEAT_MS, SESSAO_VIVA_MS };
