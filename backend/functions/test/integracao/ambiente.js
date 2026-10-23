'use strict';
/**
 * Ambiente de integração: firebase-admin ligado ao Firebase Emulator (projecto demo-kupila).
 * Executar com `npm run test:emulador` (o firebase emulators:exec define as variáveis *_EMULATOR_HOST).
 */
const admin = require('firebase-admin');
const crypto = require('crypto');

if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Testes de integração só correm contra o emulador.');
if (!admin.apps.length) admin.initializeApp({ projectId: 'demo-kupila', storageBucket: 'demo-kupila.appspot.com' });

let relogio = Date.parse('2026-10-23T10:00:00Z');
const ctx = {
  db: admin.firestore(),
  auth: admin.auth(),
  storage: null,
  agora: () => relogio,
  novoId: () => crypto.randomUUID().replace(/-/g, ''),
  FieldValue: admin.firestore.FieldValue,
};
const avancar = (ms) => { relogio += ms; };

async function limpar() {
  const proj = 'demo-kupila';
  await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${proj}/databases/(default)/documents`, { method: 'DELETE' });
  if (process.env.FIREBASE_AUTH_EMULATOR_HOST)
    await fetch(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/emulator/v1/projects/${proj}/accounts`, { method: 'DELETE' });
}

/** Simula o `context.auth` de uma Callable com claims. */
async function utilizador(email, claims = {}) {
  const u = await ctx.auth.createUser({ email, password: 'segredo123' });
  if (Object.keys(claims).length) await ctx.auth.setCustomUserClaims(u.uid, claims);
  return { uid: u.uid, token: { email, ...claims } };
}

module.exports = { ctx, avancar, limpar, utilizador };
