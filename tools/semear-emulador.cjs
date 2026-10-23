'use strict';
/**
 * Semeia o EMULADOR Firebase (projecto demo-kupila) com contas e anúncios de demonstração no esquema da origem, para testes
 * visuais e funcionais do APK. Recusa-se a correr sem as variáveis de emulador (nunca escreve na produção).
 * Uso: node tools/semear-emulador.cjs
 */
process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const path = require('path');
const admin = require(path.join(__dirname, '..', 'backend', 'functions', 'node_modules', 'firebase-admin'));
admin.initializeApp({ projectId: 'demo-kupila' });
const db = admin.firestore(), auth = admin.auth();

const cores = ['#0f766e', '#1d4ed8', '#b45309', '#7c3aed', '#be123c', '#15803d', '#0369a1', '#a16207'];
const img = (t, c) => 'data:image/svg+xml;base64,' + Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="640" height="480"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${c}"/><stop offset="1" stop-color="#0f172a"/></linearGradient></defs><rect width="100%" height="100%" fill="url(#g)"/><text x="50%" y="52%" fill="#fff" font-size="44" font-family="sans-serif" text-anchor="middle">${t}</text></svg>`).toString('base64');

// PNG de cor sólida 2x2 (o Coil não descodifica SVG sem módulo extra; os anúncios reais são JPEG).
const zlib = require('zlib');
function png(hex) {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  const crc = (buf) => { let c, t = []; for (let n = 0; n < 256; n++) { c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } let x = 0xffffffff; for (const v of buf) x = t[(x ^ v) & 0xff] ^ (x >>> 8); return (x ^ 0xffffffff) >>> 0; };
  const bloco = (tipo, dados) => { const l = Buffer.alloc(4); l.writeUInt32BE(dados.length); const td = Buffer.concat([Buffer.from(tipo), dados]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(64, 0); ihdr.writeUInt32BE(64, 4); ihdr[8] = 8; ihdr[9] = 2;
  const linha = Buffer.concat([Buffer.from([0]), Buffer.from(Array(64).fill([r, g, b]).flat())]);
  const idat = zlib.deflateSync(Buffer.concat(Array(64).fill(linha)));
  return 'data:image/png;base64,' + Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), bloco('IHDR', ihdr), bloco('IDAT', idat), bloco('IEND', Buffer.alloc(0))]).toString('base64');
}

async function conta(uid, email, nome, papel, extra = {}) {
  try { await auth.deleteUser(uid); } catch (_) { }
  await auth.createUser({ uid, email, password: 'Teste#2026', displayName: nome });
  const agora = new Date().toISOString();
  await db.doc(`users/${uid}`).set({ role: papel, status: 'ACTIVE', name: nome, email, phonePrimary: '923000001', phoneAlt: '', province: 'Luanda',
    municipality: 'Talatona', neighborhood: 'Talatona', photoDataUrl: '', usagePolicyAccepted: true, usagePolicyVersion: '2026.08.28-4',
    createdAt: agora, updatedAt: agora, authProvider: 'PASSWORD', alertChannel: 'AUTO', ...extra });
}

async function principal() {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Só corre contra o emulador.');
  await conta('demo-admin', 'admin@demo.kupila', 'Administrador Kupila', 'GESTOR');
  await conta('demo-dono', 'dono@demo.kupila', 'Proprietário Demo', 'PROPRIETARIO');
  await conta('demo-cliente', 'cliente@demo.kupila', 'Cliente Demo', 'CLIENTE');
  const base = [
    ['Vivenda T4 com piscina em Talatona', 'IMOVEL', 'Vivenda / Moradia', 'VENDA', 185000000, 'Luanda', 'Talatona'],
    ['Apartamento T3 no Kilamba', 'IMOVEL', 'Apartamento', 'ARRENDAMENTO', 350000, 'Luanda', 'Belas'],
    ['Toyota Hilux 2019 4x4', 'VIATURA', 'Pick-up', 'VENDA', 32000000, 'Benguela', 'Lobito'],
    ['Terreno 20x30 em Viana', 'TERRENO', 'Residencial', 'VENDA', 9500000, 'Luanda', 'Viana'],
    ['Honda CB 125 2023', 'MOTORIZADA', 'Trabalho / Entrega', 'VENDA', 1450000, 'Huambo', 'Huambo'],
    ['Loja no centro do Lubango', 'IMOVEL', 'Loja / Espaço comercial', 'ARRENDAMENTO', 600000, 'Huíla', 'Lubango'],
    ['Gerador 15 kVA diesel', 'OUTRO', 'Máquinas / Ferramentas', 'VENDA', 4200000, 'Luanda', 'Cazenga'],
    ['Hyundai Tucson 2021', 'VIATURA', 'SUV / Todo-terreno', 'VENDA', 27500000, 'Luanda', 'Ingombota'],
  ];
  const agora = Date.now();
  for (const [i, b] of base.entries()) {
    const id = `DEMO-${i + 1}`;
    const criado = new Date(agora - (i + 1) * 5 * 86400000).toISOString();
    await db.doc(`products/${id}`).set({
      id, ownerId: i % 2 ? 'demo-dono' : 'demo-admin', title: b[0], category: b[1], subcategory: b[2], intent: b[3], price: b[4], priceText: String(b[4]),
      province: b[5], location: b[6], municipality: b[6], image: png(cores[i]), images: [png(cores[i]), png(cores[(i + 3) % 8])],
      status: i === 2 ? 'RESERVED' : 'ACTIVE', createdAt: criado, updatedAt: criado, publisherRole: i % 2 ? 'PROPRIETARIO' : 'GESTOR',
      rentalTerms: b[3] === 'ARRENDAMENTO' ? { priceUnit: 'MONTHLY', minimumPreset: '3_MONTHS' } : null,
      engagementStats: { qualifiedViews: 40 - i * 4, interest24h: i === 1 ? 4 : 0, reservationCount: i === 3 ? 3 : 0 },
      categoryDetails: {}, verification: 'PENDENTE',
    });
    await db.doc(`productPrivate/${id}`).set({ neighborhood: 'Centro', referencePoint: 'Junto ao mercado', ownerSnapshot: { name: 'Proprietário Demo', phonePrimary: '923881902' } });
  }
  await db.doc('settings/platform').set({ depositPercent: 5, rentalDepositMonths: 1, commissionPercent: 15, listingFeePercent: 1, reservationDurationMinutes: 1440,
    sessionInactivityMinutes: 30, paymentEntity: '', promoFeaturedPercent: 0.5, promoTopPercent: 1, promoPriorityPercent: 0.3, promoDurationDays: 7, updatedAt: new Date().toISOString() }, { merge: true });
  console.log('emulador semeado: admin@demo.kupila / dono@demo.kupila / cliente@demo.kupila (Teste#2026)');
}
principal().then(() => process.exit(0), (e) => { console.error(e); process.exit(1); });
