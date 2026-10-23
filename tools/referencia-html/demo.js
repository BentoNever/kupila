// Estado de demonstração injectado na página headless (nunca em produção): utilizador local e anúncios fictícios com imagens SVG.
(function () {
  const cores = ['#0f766e', '#1d4ed8', '#b45309', '#7c3aed', '#be123c', '#15803d', '#0369a1', '#a16207'];
  const img = (t, c) => 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="640" height="480"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${c}"/><stop offset="1" stop-color="#0f172a"/></linearGradient></defs><rect width="100%" height="100%" fill="url(#g)"/><text x="50%" y="52%" fill="#fff" font-size="44" font-family="sans-serif" text-anchor="middle">${t}</text></svg>`);
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
  window.__demoListings = base.map((b, i) => ({
    id: 'REF-' + (i + 1), title: b[0], category: b[1], subcategory: b[2], intent: b[3], price: b[4], province: b[5], location: b[6],
    neighborhood: 'Centro', image: img(b[1], cores[i]), images: [img(b[1], cores[i]), img('Foto 2', cores[(i + 3) % 8])],
    deposit: Math.round(b[4] * 0.05), status: i === 2 ? 'RESERVED' : 'ACTIVE', ownerId: i % 2 ? 'OWN-001' : 'MGR-001', ownerName: 'Proprietário Demo',
    featured: i < 3, isFeatured: i < 3, promotionServices: i === 0 ? [{ code: 'TOP_SPONSORED' }] : i === 1 ? [{ code: 'FEATURED' }] : [],
    createdAt: new Date(agora - i * 86400000).toISOString(), updatedAt: new Date(agora - i * 3600000).toISOString(),
    reservationExpiresAt: i === 2 ? new Date(agora + 5 * 3600000).toISOString() : '',
    views: 40 - i * 3, details: {}, rentalPriceUnit: b[3] === 'ARRENDAMENTO' ? 'MONTHLY' : '',
  }));

  // Entra como utilizador demo (MGR-001 / OWN-001 / CLI-001) sem tocar no Firebase.
  window.__entrar = function (uid) {
    if (window.KupilaAccessGate208) KupilaAccessGate208.explicit = true;
    document.documentElement.classList.remove('kupila-login-first-208');
    const t = new Date().toISOString();
    const demo = [['MGR-001', 'Administrador Kupila', 'admin@kupila.ao', 'GESTOR'], ['OWN-001', 'Proprietário Demo', 'dono@kupila.ao', 'PROPRIETARIO'], ['CLI-001', 'Cliente Demo', 'cliente@kupila.ao', 'CLIENTE']];
    try {
      for (const [id, name, email, role] of demo) if (!appUsers.some((u) => u.id === id)) appUsers.push({ id, name, email, role, status: 'ACTIVE', phonePrimary: '923000000', province: 'Luanda', municipality: 'Talatona', createdAt: t, updatedAt: t });
      if (!auditLog.length) auditLog.push({ id: 'AUD-1', action: 'LOGIN', actorId: 'MGR-001', actorName: 'Administrador Kupila', actorRole: 'GESTOR', entity: 'USER', entityId: 'MGR-001', details: 'Sessão iniciada.', createdAt: t },
        { id: 'AUD-2', action: 'CREATE_LISTING', actorId: 'OWN-001', actorName: 'Proprietário Demo', actorRole: 'PROPRIETARIO', entity: 'LISTING', entityId: 'REF-2', details: 'Anúncio publicado.', createdAt: t });
    } catch (_) { }
    activeUserId = uid; mockListings = window.__demoListings;
    try { showAppScreen(); } catch (_) { }
    activeUserId = uid;
    try { hideKupilaLoading(true); } catch (_) { }
    document.getElementById('login-screen').style.display = 'none';
    try { applyFilters(true); } catch (_) { }
    try { applyRolePermissions(); } catch (_) { }
    try { updateNav && updateNav('explore'); } catch (_) { }
    return { app: getComputedStyle(document.getElementById('app-container')).display };
  };
  // Fecha tudo o que estiver aberto (modais estáticos e folhas criadas por JS).
  window.__fechar = function () {
    document.querySelectorAll('[id^="modal-"]').forEach((m) => { if (/fixed/.test(m.className)) m.classList.add('hidden'); });
    document.querySelectorAll('.fixed.inset-0').forEach((m) => { if (!m.id || !/^modal-/.test(m.id)) { if (m.dataset.refOriginal === undefined) m.dataset.refOriginal = m.style.display; m.style.display = 'none'; } });
    document.body.style.overflow = '';
  };
  // Estilos computados (valores reais depois de todas as camadas de CSS) para reproduzir no Compose.
  window.__estilos = function (seletores) {
    const P = ['display', 'width', 'height', 'padding', 'margin', 'gap', 'backgroundColor', 'backgroundImage', 'color', 'fontSize', 'fontWeight', 'lineHeight',
      'letterSpacing', 'textTransform', 'borderTop', 'borderRadius', 'boxShadow', 'gridTemplateColumns', 'opacity'];
    const out = {};
    for (const s of seletores) {
      const e = document.querySelector(s); if (!e) { out[s] = null; continue; }
      const c = getComputedStyle(e), r = e.getBoundingClientRect(), o = { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) };
      for (const p of P) { const v = c[p]; if (v && v !== 'none' && v !== 'normal' && v !== '0px' && v !== 'rgba(0, 0, 0, 0)' && v !== 'auto') o[p] = v; }
      out[s] = o;
    }
    return out;
  };
  // Rola o contentor de scroll do modal visível (ou o principal).
  window.__rolar = function (y) {
    const visiveis = [...document.querySelectorAll('*')].filter((e) => ['auto', 'scroll'].includes(getComputedStyle(e).overflowY)).filter((e) => e.checkVisibility() && e.getBoundingClientRect().height > 80 && e.scrollHeight > e.clientHeight + 10 && e.id !== 'main-content');
    const alvo = visiveis.sort((a, b) => b.getBoundingClientRect().width * b.clientHeight - a.getBoundingClientRect().width * a.clientHeight)[0] || document.getElementById('main-content');
    alvo.scrollTop = y; return (alvo.id || alvo.className.toString().slice(0, 40)) + ' ' + alvo.scrollTop + '/' + alvo.scrollHeight;
  };
})();
