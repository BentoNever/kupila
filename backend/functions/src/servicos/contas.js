'use strict';
/**
 * Contas e perfis (secções 7, 8, 26). Origem: `handleRegister` (11199), `saveOwnerRegistration` (12696),
 * `saveManagedUser` (14880), `setManagedUserAccountState` (14899), `isUserCreatedByCurrentAdmin` (14737).
 * O papel é definido aqui e publicado como custom claim; nunca é aceite do cliente fora destas regras.
 */
const { erro, identidade, exigirActivo, exigirAdmin, exigirSessao, auditoria, texto, EMAIL_RX, telefone, telefoneValido, PAPEL, ESTADO_CONTA } = require('../comum');
const { provinciaEMunicipioValidos } = require('../geografia');

const CANAIS = ['AUTO', 'EMAIL', 'SMS', 'BOTH'];
const POLITICA_VIGENTE = '2026.08.28-4';

function validarDadosPessoais(d, { exigeFotoProprietario, papel }) {
  const nome = texto(d?.nome, 120, 'Nome');
  const email = texto(d?.email, 160, 'E-mail').toLowerCase();
  const tel = telefone(d?.telefonePrincipal);
  const alt = d?.telefoneAlternativo ? telefone(d.telefoneAlternativo) : '';
  const provincia = texto(d?.provincia, 40, 'Província');
  const municipio = texto(d?.municipio, 60, 'Município');
  const bairro = texto(d?.bairro, 100, 'Bairro');
  const canal = CANAIS.includes(d?.canalAlertas) ? d.canalAlertas : 'AUTO';
  const falhas = [];
  if (nome.length < 3) falhas.push('Informe o nome completo.');
  if (!EMAIL_RX.test(email)) falhas.push('Informe um e-mail válido.');
  if (!telefoneValido(tel)) falhas.push('Informe o telefone principal com 9 dígitos.');
  if (alt && !telefoneValido(alt)) falhas.push('O telefone alternativo deve ter 9 dígitos.');
  // Residência: obrigatória para o Proprietário (`register-owner-fields`, HTML 6560); opcional para o Cliente, mas válida se indicada.
  const exigeResidencia = papel === PAPEL.PROPRIETARIO;
  if ((exigeResidencia || provincia || municipio) && !provinciaEMunicipioValidos(provincia, municipio)) falhas.push('Seleccione Província e Município válidos.');
  if (exigeResidencia && !bairro) falhas.push('Informe o bairro.');
  if (exigeFotoProprietario && papel === PAPEL.PROPRIETARIO && !d?.fotoCarregada) falhas.push('A fotografia do Proprietário é obrigatória.');
  if (falhas.length) throw erro('invalid-argument', falhas[0], { falhas });
  return { name: nome, email, phonePrimary: tel, phoneAlt: alt, province: provincia, municipality: municipio, neighborhood: bairro, alertChannel: canal };
}

async function publicarClaims(ctx, uid, papel, estado) {
  await ctx.auth.setCustomUserClaims(uid, { papel, estado });
}

/** Move a foto do perfil da área de transferência para a área privada (se existir). */
async function moverFotoPerfil(ctx, uid) {
  if (!ctx.storage) return null;
  const origem = ctx.storage.file(`uploads/${uid}/perfil/foto.jpg`);
  const [existe] = await origem.exists();
  if (!existe) return null;
  const destino = `privado/perfis/${uid}/foto.jpg`;
  await origem.move(destino);
  return destino;
}

/**
 * Cadastro público: o APK cria primeiro a conta no Firebase Auth (e-mail/senha) e chama este comando.
 * @param dados { papel: CLIENTE|PROPRIETARIO, nome, email, telefonePrincipal, telefoneAlternativo, provincia, municipio, bairro,
 *                canalAlertas, politicaVersao, politicaLidaAteFim, fotoCarregada }
 */
async function registar(ctx, auth, dados) {
  if (!auth?.uid) throw erro('unauthenticated', 'Crie a conta antes de concluir o cadastro.');
  const papel = dados?.papel;
  if (![PAPEL.CLIENTE, PAPEL.PROPRIETARIO].includes(papel)) throw erro('permission-denied', 'O cadastro público só permite Cliente ou Proprietário.');
  if (dados?.politicaVersao !== POLITICA_VIGENTE) throw erro('failed-precondition', 'Leia e aceite a versão actual das Políticas de Uso e Regras de Intermediação.');
  const p = validarDadosPessoais(dados, { exigeFotoProprietario: true, papel });
  const tokenEmail = String(auth.token?.email || '').toLowerCase();
  if (tokenEmail && tokenEmail !== p.email) throw erro('invalid-argument', 'O e-mail do cadastro não corresponde ao da conta.');
  const ref = ctx.db.collection('users').doc(auth.uid);
  const agora = ctx.agora();
  const foto = papel === PAPEL.PROPRIETARIO ? await moverFotoPerfil(ctx, auth.uid) : null;
  if (papel === PAPEL.PROPRIETARIO && !foto && ctx.storage) throw erro('failed-precondition', 'A fotografia do Proprietário não foi recebida. Envie-a novamente.');
  await ctx.db.runTransaction(async (tx) => {
    const ja = await tx.get(ref);
    if (ja.exists) throw erro('already-exists', 'Este cadastro já foi concluído. Entre com o seu e-mail.');
    tx.set(ref, { id: auth.uid, role: papel, status: ESTADO_CONTA.ACTIVA, ...p, photoPath: foto, createdBy: null, createdAt: agora, updatedAt: agora,
      policyAcceptance: { version: POLITICA_VIGENTE, purpose: 'REGISTER', acceptedAt: agora, readToEnd: dados?.politicaLidaAteFim === true }, version: 1 });
    auditoria(ctx, tx, { actor: { uid: auth.uid, papel }, accao: 'REGISTER', entidade: 'USER', entidadeId: auth.uid, depois: { role: papel } });
  });
  await publicarClaims(ctx, auth.uid, papel, ESTADO_CONTA.ACTIVA);
  return { ok: true, papel };
}

/** Perfil do próprio titular (inclui dados pessoais; nunca de terceiros). */
async function meuPerfil(ctx, auth) {
  const id = identidade(auth);
  const s = await ctx.db.collection('users').doc(id.uid).get();
  if (!s.exists) return { existe: false };
  const u = s.data();
  return { existe: true, perfil: { uid: id.uid, role: u.role, status: u.status, name: u.name, email: u.email, phonePrimary: u.phonePrimary,
    phoneAlt: u.phoneAlt || '', province: u.province, municipality: u.municipality, neighborhood: u.neighborhood, alertChannel: u.alertChannel,
    photoPath: u.photoPath || null, createdBy: u.createdBy || null, policyVersion: u.policyAcceptance?.version || null, version: u.version || 1 } };
}

/** Cadastro do Proprietário / actualização do próprio perfil (`saveOwnerRegistration`). */
async function actualizarMeuPerfil(ctx, auth, dados) {
  const id = exigirActivo(identidade(auth));
  await exigirSessao(ctx, id.uid, dados?.sessaoId);
  const ref = ctx.db.collection('users').doc(id.uid);
  const agora = ctx.agora();
  const foto = dados?.fotoCarregada ? await moverFotoPerfil(ctx, id.uid) : null;
  return ctx.db.runTransaction(async (tx) => {
    const s = await tx.get(ref);
    if (!s.exists) throw erro('not-found', 'Perfil inexistente.');
    const actual = s.data();
    if (Number(dados?.versao) !== Number(actual.version || 1)) throw erro('aborted', 'O perfil foi alterado noutro dispositivo. Reveja os dados actuais.', { motivo: 'CONFLITO_VERSAO' });
    const p = validarDadosPessoais({ ...dados, fotoCarregada: Boolean(foto || actual.photoPath) }, { exigeFotoProprietario: true, papel: actual.role });
    if (p.email !== actual.email) throw erro('failed-precondition', 'A alteração do e-mail de acesso exige o fluxo de verificação da conta.');
    tx.update(ref, { ...p, photoPath: foto || actual.photoPath || null, updatedAt: agora, version: (actual.version || 1) + 1 });
    auditoria(ctx, tx, { actor: id, accao: 'UPDATE_OWN_PROFILE', entidade: 'USER', entidadeId: id.uid });
    return { ok: true, versao: (actual.version || 1) + 1 };
  });
}

/**
 * Criação de Cliente/Proprietário pelo Administrador (`saveManagedUser`). Feita com Admin SDK: a sessão do
 * Administrador nunca é trocada (secção 26). A senha inicial passa só por este pedido e nunca é guardada.
 */
async function criarPorAdmin(ctx, auth, dados) {
  const admin = exigirAdmin(identidade(auth));
  await exigirSessao(ctx, admin.uid, dados?.sessaoId);
  const papel = dados?.papel;
  if (![PAPEL.CLIENTE, PAPEL.PROPRIETARIO].includes(papel)) throw erro('permission-denied', 'Use a concessão de privilégios para Administradores.');
  const estado = Object.values(ESTADO_CONTA).includes(dados?.estado) ? dados.estado : ESTADO_CONTA.ACTIVA;
  const p = validarDadosPessoais(dados, { exigeFotoProprietario: false, papel });
  const senha = String(dados?.senhaInicial || '');
  if (senha.length < 6) throw erro('invalid-argument', 'A palavra-passe inicial deve ter no mínimo 6 caracteres.');
  let criado;
  try { criado = await ctx.auth.createUser({ email: p.email, password: senha, displayName: p.name, disabled: estado !== ESTADO_CONTA.ACTIVA }); }
  catch (e) { if (String(e?.code || '').includes('email-already-exists')) throw erro('already-exists', 'Já existe uma conta com este e-mail.'); throw e; }
  const agora = ctx.agora();
  try {
    await ctx.db.runTransaction(async (tx) => {
      tx.set(ctx.db.collection('users').doc(criado.uid), { id: criado.uid, role: papel, status: estado, ...p, photoPath: null, createdBy: admin.uid,
        createdAt: agora, updatedAt: agora, version: 1, policyAcceptance: null });
      auditoria(ctx, tx, { actor: admin, accao: 'CREATE_USER', entidade: 'USER', entidadeId: criado.uid, depois: { role: papel, status: estado } });
    });
    await publicarClaims(ctx, criado.uid, papel, estado);
  } catch (e) {
    await ctx.auth.deleteUser(criado.uid).catch(() => {});   // rollback (`rollbackManagedFirebaseAuthAccount`)
    throw e;
  }
  return { ok: true, uid: criado.uid };
}

/** Activar/inactivar/suspender/arquivar — qualquer conta (excepto a própria), dentro da competência. */
async function alterarEstado(ctx, auth, dados) {
  const admin = exigirAdmin(identidade(auth));
  await exigirSessao(ctx, admin.uid, dados?.sessaoId);
  const alvo = String(dados?.uid || '');
  const estado = dados?.estado;
  if (!Object.values(ESTADO_CONTA).includes(estado)) throw erro('invalid-argument', 'Estado inválido.');
  if (alvo === admin.uid) throw erro('permission-denied', 'Não pode alterar o estado da sua própria conta.');
  const ref = ctx.db.collection('users').doc(alvo);
  const agora = ctx.agora();
  const r = await ctx.db.runTransaction(async (tx) => {
    const s = await tx.get(ref);
    if (!s.exists) throw erro('not-found', 'Utilizador inexistente.');
    const antes = s.get('status');
    tx.update(ref, { status: estado, updatedAt: agora, statusChangedBy: admin.uid, statusReason: texto(dados?.motivo, 300, 'Motivo') });
    auditoria(ctx, tx, { actor: admin, accao: 'SET_USER_STATUS', entidade: 'USER', entidadeId: alvo, antes: { status: antes }, depois: { status: estado }, motivo: dados?.motivo || '' });
    return { papel: s.get('role') };
  });
  await publicarClaims(ctx, alvo, r.papel, estado);
  await ctx.auth.updateUser(alvo, { disabled: estado !== ESTADO_CONTA.ACTIVA });
  if (estado !== ESTADO_CONTA.ACTIVA) await ctx.auth.revokeRefreshTokens(alvo);
  return { ok: true };
}

/** Edição de dados pessoais: só contas criadas pelo Administrador actual (regra da origem). */
async function editarDadosPorAdmin(ctx, auth, dados) {
  const admin = exigirAdmin(identidade(auth));
  await exigirSessao(ctx, admin.uid, dados?.sessaoId);
  const ref = ctx.db.collection('users').doc(String(dados?.uid || ''));
  const agora = ctx.agora();
  return ctx.db.runTransaction(async (tx) => {
    const s = await tx.get(ref);
    if (!s.exists) throw erro('not-found', 'Utilizador inexistente.');
    if (s.get('createdBy') !== admin.uid) throw erro('permission-denied', 'Esta conta foi criada publicamente ou por outro Administrador: só o estado pode ser alterado.');
    if (Number(dados?.versao) !== Number(s.get('version') || 1)) throw erro('aborted', 'A ficha foi alterada entretanto. Reabra-a.', { motivo: 'CONFLITO_VERSAO' });
    const p = validarDadosPessoais(dados, { exigeFotoProprietario: false, papel: s.get('role') });
    if (p.email !== s.get('email')) throw erro('failed-precondition', 'O e-mail de acesso não é alterado por esta via.');
    tx.update(ref, { ...p, updatedAt: agora, version: (s.get('version') || 1) + 1 });
    auditoria(ctx, tx, { actor: admin, accao: 'EDIT_USER_DATA', entidade: 'USER', entidadeId: ref.id });
    return { ok: true };
  });
}

/** Concessão de privilégio de Administrador (fora do cadastro público). */
async function concederAdministrador(ctx, auth, dados) {
  const admin = exigirAdmin(identidade(auth));
  await exigirSessao(ctx, admin.uid, dados?.sessaoId);
  const alvo = String(dados?.uid || '');
  const motivo = texto(dados?.motivo, 300, 'Motivo');
  if (motivo.length < 5) throw erro('invalid-argument', 'Indique o motivo da concessão.');
  const ref = ctx.db.collection('users').doc(alvo);
  await ctx.db.runTransaction(async (tx) => {
    const s = await tx.get(ref);
    if (!s.exists) throw erro('not-found', 'Utilizador inexistente.');
    tx.update(ref, { role: PAPEL.ADMIN, updatedAt: ctx.agora(), roleGrantedBy: admin.uid });
    auditoria(ctx, tx, { actor: admin, accao: 'GRANT_ADMIN', entidade: 'USER', entidadeId: alvo, antes: { role: s.get('role') }, depois: { role: PAPEL.ADMIN }, motivo });
  });
  const s = await ref.get();
  await publicarClaims(ctx, alvo, PAPEL.ADMIN, s.get('status'));
  await ctx.auth.revokeRefreshTokens(alvo);
  return { ok: true };
}

module.exports = { registar, meuPerfil, actualizarMeuPerfil, criarPorAdmin, alterarEstado, editarDadosPorAdmin, concederAdministrador, POLITICA_VIGENTE };
