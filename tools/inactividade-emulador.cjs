'use strict';
/**
 * Muda o limite de inactividade (minutos) no EMULADOR Firebase (demo-kupila) para testar o ecrã "Restaurar sessão".
 * Só corre contra o emulador; nunca escreve na produção. Uso: node tools/inactividade-emulador.cjs 1   (repor: 30)
 */
process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080';
const path = require('path');
const admin = require(path.join(__dirname, '..', 'backend', 'functions', 'node_modules', 'firebase-admin'));
admin.initializeApp({ projectId: 'demo-kupila' });
const minutos = Number(process.argv[2] || 30);
admin.firestore().doc('settings/platform').set({ sessionInactivityMinutes: minutos, updatedAt: new Date().toISOString() }, { merge: true })
  .then(() => { console.log('sessionInactivityMinutes =', minutos); process.exit(0); })
  .catch((e) => { console.error(e.message); process.exit(1); });
