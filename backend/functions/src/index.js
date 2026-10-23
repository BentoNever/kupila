'use strict';
/**
 * Kupila-Gestão de negócios — Central (Cloud Functions, Callable).
 * Desenvolvimento/testes: Firebase Emulator (projecto demo-kupila). Publicação real depende da decisão D-01
 * (plano Blaze no projecto pdv-pro-313f5) — não publicar sem essa autorização.
 */
const functions = require('firebase-functions');
const admin = require('firebase-admin');
const crypto = require('crypto');
const { ErroKupila } = require('./comum');
const sessoes = require('./servicos/sessoes');
const contas = require('./servicos/contas');

admin.initializeApp();
const REGIAO = process.env.KUPILA_REGIAO || 'europe-west1';

let ctx = null;
function contexto() {
  if (ctx) return ctx;
  const db = admin.firestore();
  try { db.settings({ ignoreUndefinedProperties: true }); } catch (_) { /* já configurado */ }
  ctx = {
    db,
    auth: admin.auth(),
    storage: admin.storage().bucket(),
    agora: () => Date.now(),                       // hora OFICIAL = a do servidor (secção 34)
    novoId: () => crypto.randomUUID().replace(/-/g, ''),
    FieldValue: admin.firestore.FieldValue,
  };
  return ctx;
}

/** Converte erros de domínio em HttpsError com código e detalhes; nunca expõe pilhas ou segredos. */
function comando(fn) {
  return functions.region(REGIAO).runWith({ memory: '256MB', timeoutSeconds: 60 }).https.onCall(async (dados, context) => {
    try {
      return await fn(contexto(), context.auth, dados || {});
    } catch (e) {
      if (e instanceof ErroKupila) throw new functions.https.HttpsError(e.codigo, e.message, e.detalhes || null);
      if (e instanceof functions.https.HttpsError) throw e;
      functions.logger.error('Falha interna', { mensagem: e?.message, codigo: e?.code });
      throw new functions.https.HttpsError('internal', 'O serviço não conseguiu concluir a operação. Tente novamente.');
    }
  });
}

exports.sessoesReivindicar = comando(sessoes.reivindicar);
exports.sessoesSinal = comando(sessoes.sinal);
exports.sessoesTerminar = comando(sessoes.terminar);
exports.contasRegistar = comando(contas.registar);
exports.contasMeuPerfil = comando(contas.meuPerfil);
exports.contasActualizarMeuPerfil = comando(contas.actualizarMeuPerfil);
exports.contasCriarPorAdmin = comando(contas.criarPorAdmin);
exports.contasAlterarEstado = comando(contas.alterarEstado);
exports.contasEditarDadosPorAdmin = comando(contas.editarDadosPorAdmin);
exports.contasConcederAdministrador = comando(contas.concederAdministrador);
