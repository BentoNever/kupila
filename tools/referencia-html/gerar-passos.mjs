// Gera passos-todos.json: cada ecrã/modal da origem aberto pela sua própria função, com capturas do topo e de partes roladas.
import fs from 'node:fs';
const ecras = [
  // [nome, utilizador, js de abertura, rolagens extra]
  ['20-detalhe', 'CLI-001', "openDetails('REF-1')", [700, 1400, 2100, 2800]],
  ['21-detalhe-reservado', 'CLI-001', "openDetails('REF-3')", [900]],
  ['22-detalhe-dono', 'OWN-001', "openDetails('REF-2')", [900, 1800]],
  ['23-checkout', 'CLI-001', "openDetails('REF-1'); await new Promise(r=>setTimeout(r,600)); document.querySelector('#modal-details button[onclick*=\"Checkout\"],#modal-details button[onclick*=\"checkout\"],#modal-details button[onclick*=\"reserv\"]')?.click()", [700, 1400]],
  ['30-sessao', 'OWN-001', 'openSessionModal()', [700]],
  ['31-notificacoes', 'OWN-001', 'openNotifications()', []],
  ['32-ajustes-admin', 'MGR-001', 'openSettings()', [700, 1400, 2100, 2800, 3500]],
  ['33-faq', 'CLI-001', 'openKupilaFaq()', [700]],
  ['34-simulador', 'CLI-001', 'openIntermediationSimulator()', [700, 1400]],
  ['35-politicas', 'CLI-001', "openKupilaPolicies('GENERAL')", [700]],
  ['36-reserva-protegida', 'CLI-001', 'openReservationProtectionInfo()', [700]],
  ['37-offline', 'CLI-001', 'openKupilaOfflineCenter()', []],
  ['38-testemunhos', 'CLI-001', 'openTrustCenter()', [700]],
  ['40-anunciar', 'OWN-001', 'startCreateListing()', [700, 1400, 2100, 2800, 3500, 4200, 4900]],
  ['41-proprietario', 'OWN-001', "switchTab('my-listings')", [700, 1400]],
  ['42-registo-proprietario', 'OWN-001', 'openOwnerRegistration()', [700, 1400]],
  ['43-autorizacoes', 'OWN-001', 'openAuthorizationCenter()', []],
  ['50-admin-pendentes', 'MGR-001', 'openAdminPendingProcesses()', [700]],
  ['51-admin-monitor', 'MGR-001', 'openPlatformMonitoring()', [700, 1400]],
  ['52-admin-utilizadores', 'MGR-001', 'openUserManagement()', [700]],
  ['53-admin-directorio', 'MGR-001', 'openAdministratorDirectory()', []],
  ['54-admin-historico', 'MGR-001', 'openOperationHistory()', []],
  ['55-admin-monetizacao', 'MGR-001', 'openMonetizationHistory()', []],
  ['56-admin-catalogo', 'MGR-001', 'openAdminCatalogCenter63()', [700]],
  ['57-admin-lixeira', 'MGR-001', 'openTrash()', []],
  ['58-admin-home', 'MGR-001', '', []],
  ['59-proprietario-admin', 'MGR-001', "switchTab('my-listings')", [700]],
];
const passos = [{ nome: '00-init', ficheiro: 'demo.js', foto: false, espera: 300, js: "return 'ok'" }];
for (const [nome, uid, abrir, rolagens] of ecras) {
  passos.push({ nome, recarregar: true, ficheiro: 'demo.js', espera: 1800, js: `__entrar('${uid}'); await new Promise(r=>setTimeout(r,400)); try{ ${abrir} }catch(e){ return 'ERRO '+e.message } return 'ok'` });
  rolagens.forEach((y, i) => passos.push({ nome: `${nome}-r${i + 1}`, espera: 500, js: `return __rolar(${y})` }));
}
fs.writeFileSync(new URL('./passos-todos.json', import.meta.url), JSON.stringify(passos, null, 1));
console.log(passos.length, 'passos');
