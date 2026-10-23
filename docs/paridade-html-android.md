# Matriz de paridade — Kupila HTML 1.0.239 → Kupila-Gestão de negócios (Android)

Origem: `Kupila%201.0.239.html` (SHA-256 `dde34687…a4830bf7c`, 32 552 linhas, numeração por `\n`).
Índices de apoio gerados por `tools/html_index.py` e `tools/html_ui_map.py` em `docs/origem-html/`
(`blocos.tsv`, `funcoes.tsv`, `redefinicoes.tsv`, `ids.tsv`, `colecoes.tsv`, `ui-map.txt`).

**Classe**: P = funcionalidade a preservar · A = adaptação de navegador → equivalente Android · D = defeito corrigido ·
I = integração por verificar (serviço externo/backend) · C = decisão comercial pendente.
**Perfil**: Cl = Cliente · Pr = Proprietário · Ad = Administrador · Td = todos.
**Estado**: `MAP` mapeado · `DOM` domínio implementado · `UI` ecrã implementado · `BE` backend implementado · `TST` testado (com evidência) · `BLQ` bloqueado por dependência.

Destinos Android referem-se aos pacotes de `app/src/main/java/ao/kupila/gestaonegocios/` (ver `docs/arquitectura.md`).
Destinos "Central" referem-se às Cloud Functions em `backend/functions/` (decisão D-01).

## 1. Arranque, identidade visual e tema

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| ID-01 | blocos 0,1,12,62,63 (`kupila-1-0-161-first-paint-theme`, `…229-login-first-frame-critical`) | Td | Tema/paleta confirmados aplicados no 1.º frame, incluindo login e offline, sem flash | `kupila-settings` local | `ui/tema` lê DataStore síncrono antes de `setContent`; splash API | UI: arranque frio claro/escuro | MAP | A |
| ID-02 | `appSettings.visualTheme` 9106; presets 9 (`EMERALD/OCEAN/VIOLET/SUNSET`) | Ad define, Td vê | Temas Kupila #16A34A/#FACC15, Oceano #0284C7/#38BDF8, Violeta #7C3AED/#C4B5FD, Pôr-do-sol #EA580C/#FBBF24, cor personalizada | `settings/platform` | `dominio/aparencia/Paletas` + `ui/tema/TemaKupila` | Unit: contraste AA calculado | DOM+TST | P |
| ID-03 | 24667-24722 (1.0.197) | Ad define | Paletas Pesquisa/Vitrine Aurora, Safira, Luxo, Coral (`sp/sa/hp/ha`) | `homeVisualPreset`, `homeSearch*`, `homeShowcase*` | idem | Unit | DOM+TST | P |
| ID-04 | 7909-7925, 23259 (1.0.159) | Ad/Td | Claro, escuro, automático | `visualAppearance` | DataStore por dispositivo + padrão da plataforma | UI | MAP | P |
| ID-05 | 7921-7925 `setting-app-logo-file` | Ad | Logo da plataforma configurável (PNG/JPG/WebP) | `appLogoDataUrl` → Storage | Storage `platform/logo` + cache local | Integração | MAP | P/A (dataURL → ficheiro) |
| ID-06 | 23840-23894 (1.0.181 FACTORY) | Ad | Restaurar padrões visuais/ajustes sem apagar operações | versão de ajustes | Central `settings.restoreDefaults` | Unit + regra | MAP | P |
| ID-07 | 9083 `updateKupilaAboutMetadata` | Td | Sobre: nome oficial, versão/build, crédito "Sousa B.Morais", ano actual | — | `ui/ajuda/Sobre` | UI | MAP | P (nome → Kupila-Gestão de negócios) |
| ID-08 | 23781 (1.0.177 háptico), 7948 | Td | Feedback háptico nos botões (configurável) | `hapticButtonFeedback` | `ui/comum/Haptica` | UI | MAP | P |
| ID-09 | blocos 3,9-62 (CSS), 22669 fullscreen, 23713, 25114 | — | Layout responsivo, fullscreen, teclado, safe areas | — | Compose `WindowInsets`, `imePadding` | UI A54/pequeno | MAP | A (substituído) |

## 2. Conta, login, recuperação, sessão

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| AUTH-01 | 6503-6537, `handleLogin` 10669 | Td | Login e-mail + palavra-passe; mostrar/ocultar senha; erro compreensível preservando e-mail | Firebase Auth | `dados/sessao/AutenticacaoRepositorio`, `ui/entrada/Login` | UI + unit de mensagens | MAP | P |
| AUTH-02 | `scheduleAutomaticCredentialLogin` 10495-10516, `loginCredentialSignature` 10483 | Td | Entrada automática ao completar credenciais válidas: debounce, 1 tentativa em curso, sem repetir mesma assinatura após falha | — | `LoginViewModel` (debounce 700 ms, assinatura hash em memória) | Unit (coroutines-test) | MAP | P |
| AUTH-03 | `loadKnownDeviceAccounts` 10175-10431 | Td | Cartões de contas reconhecidas: nome, foto compacta, último acesso; esquecer conta; **sem** guardar palavra-passe | `kupila-known-accounts` | DataStore cifrado (Cofre) só identidade mínima | Unit | MAP | P/D (origem tinha `saveSecureQuickLoginCredential` 10367 — não guardar senha) |
| AUTH-04 | 10362-10376 quick login nativo | Td | Desbloqueio rápido opcional | — | BiometricPrompt reabre sessão Firebase ainda válida; não substitui login | UI | MAP | A |
| AUTH-05 | 6537-6571 `openRegisterScreen` 11062, `handleRegister` 11199 | Cl/Pr | Cadastro público: perfil Cliente/Proprietário, nome, e-mail, telefone (9 díg.), província/município, bairro, telefone alt., canal de alertas, senha+confirmar, aceite de políticas versionado; foto obrigatória Proprietário (≤2 MB, comprimida) | `users/{uid}` | `ui/entrada/Cadastro` + Central `contas.registar` (cria perfil, define papel ≠ ADMIN) | Caso 01 | BE+TST(emulador) | P |
| AUTH-06 | 11097 `toggleRegisterOwnerFields` | Cl/Pr | Campos condicionais por perfil (foto e residência do Proprietário) | — | idem | UI | MAP | P (confirmar campos exactos do Cliente na implementação) |
| AUTH-07 | 11032-11061 rascunho de cadastro | Cl/Pr | Rascunho do cadastro preservado (sem senha) | local | `SavedStateHandle` + Room `rascunho_formulario` | Unit | MAP | P |
| AUTH-08 | 6584-6595, 10521-10628 | Td | Recuperação por link oficial de redefinição; cooldown; não confirma existência do e-mail; contas reconhecidas como atalho | Firebase `sendPasswordResetEmail` | `RecuperacaoViewModel` | Caso 01 | MAP | P |
| AUTH-09 | `loadAuthenticatedProfileResilient` 10629, 26819 (1.0.206), 27194 (1.0.209) | Td | Restaurar sessão válida e contexto seguro; não abrir área autenticada só por cache | perfil `users` | `SessaoRepositorio.restaurar()` valida token + perfil + sessão exclusiva | Unit | MAP | P/D |
| AUTH-10 | 6479-6492, 10209-10356 (`claimExclusiveSession`, heartbeat, listener) | Td | Sessão única: conflito mostra dispositivo/hora; "Encerrar sessão anterior e continuar"; sessão substituída faz logout | `activeSessions/{uid}` | Central `sessoes.reivindicar` (transacção atómica, auditada) + listener; regras negam mutação com `sessionId` antigo | Caso 03 | BE+TST(emulador) | P/D (origem: escrita cliente) |
| AUTH-11 | 6450-6459, 10732-10870 | Td | Inactividade configurável (padrão 10 min), aviso com contagem, continuar/sair; verificação ao retomar | `sessionInactivityMinutes` | `ui/sessao/Inactividade` com `elapsedRealtime` + `ProcessLifecycleOwner` | Caso 04 | MAP | P |
| AUTH-12 | 10871 `logout`, 27036 (1.0.208) | Td | Logout/troca de conta remove listeners, contexto privado e fila da conta anterior fica isolada | — | Base Room por conta; cancelamento de `Job`s; WorkManager por tag de conta | Caso 03/31 | MAP | P |
| AUTH-13 | 10432-10460 boas-vindas | Td | Saudação por período do dia, uma vez por conta/dispositivo | `welcomeOnce` | DataStore | Unit | MAP | P |
| AUTH-14 | 23764 (1.0.174), 25168 (1.0.173) | Cl/Pr | Correcções de cadastro → login | — | fluxo nativo | UI | MAP | A |
| AUTH-15 | 26948 (1.0.207 auth network intelligence), 29292 (1.0.218 histerese) | Td | Distinguir sem rede/serviço lento/credencial errada; histerese de estado da rede | — | `dados/rede/EstadoRede` (ConnectivityManager + prova de resposta, padrão PDV `ProvaDeRede`) | Unit | MAP | A |

## 3. Perfis e permissões

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| PERM-01 | `canonicalRole` 9170, `ROLE`, `isManager` 10149 | Td | Papéis CLIENTE, PROPRIETARIO, ADMINISTRADOR (MANAGER) | `users.role` | `dominio/perfis/Papel` + custom claims/`users.role` lido só do servidor | Regras + unit | DOM+TST | P/D (papel nunca vindo do cliente) |
| PERM-02 | `canPublish` 10152, `canManageListing` 10153, `canValidateListing` 10154 | Td | Cl não anuncia; Pr só gere próprios; Ad gere todos | — | `dominio/perfis/Permissoes` + regras Firestore + Central | Caso 11 | DOM+TST | P |
| PERM-03 | `applyRolePermissions` 14677 | Td | Navegação por perfil (Home, Anunciar, Proprietário, Admin) | — | `ui/Navegacao` | UI | MAP | P |
| PERM-04 | 25238 (1.0.188 authz alignment), 25383 (1.0.189), 22496 (1.0.148) | Td | Alinhamento das permissões cloud/privadas | — | Regras novas (`backend/firestore.rules`) | Emulador | BE+TST(emulador) | D/I |

## 4. Catálogo, pesquisa e descoberta

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| CAT-01 | 13631-13637 `PRODUCT_SUBCATEGORIES` | Td | Categorias IMOVEL, TERRENO, VIATURA, MOTORIZADA, OUTRO e subcategorias exactas | — | `dominio/catalogo/Categorias` (versionado) | Unit | DOM+TST | P |
| CAT-02 | 8501-8870 `administrativeDivisions` (Lei 14/24: 21 províncias, 326 municípios) | Td | Filtros e formulários por província/município | — | `assets/geografia-ao-v1.json` extraído por script + versão | Unit (21/326) | MAP | P |
| CAT-03 | 6701-6715, `applyFilters` 13150, `setCategory/setIntent` | Td | Pesquisa texto (produto, município, marca) + categoria + modalidade (Tudo/Comprar/Alugar) + província, combináveis; limpar | `products` públicos | `ui/explorar` + Room FTS/LIKE sobre projecção pública | Caso 05 | DOM | P |
| CAT-04 | `rankFeaturedItems` 13118 → 15853 (payments-v2) | Td | Vitrines Destaques (score), Recentes, Em Alta (interesse 24h), Mais Procurados (reservas×10+visitas), Patrocinados (promoção paga activa, por boost) | `engagementStats`, `promotion*` | `dominio/catalogo/Vitrines` (puro) | Unit | DOM+TST | P (métricas do servidor) |
| CAT-05 | `listingFeaturedScore` 13117 | Td | Score = frescura(28−2,2×dias) + min(30, int24h×8) + min(22, visitas×1,8) + min(28, reservas×9) + 8 se interesse <24h | — | idem | Unit | DOM+TST | P |
| CAT-06 | `listingHighlightMeta` 13119 → 15854 | Td | Selos: Top Patrocinado/Patrocinado (pago), Novo (≤3 dias), Em Alta (≥3), Mais Procurado (≥2 reservas ou ≥8 visitas), Destaque (score≥32) | — | idem | Unit | DOM+TST | P |
| CAT-07 | 6768-6779 `changeCatalogPage`, carrosséis 13170-13400, 30757/31026 | Td | Catálogo paginado + carrossel tocável; gesto não abre por acidente | — | `LazyColumn` paginado (Paging 3 não presente no PDV → paginação manual por cursor) + `HorizontalPager` | UI | MAP | A |
| CAT-08 | 31374-31481 (1.0.231) `DEMO_IDS` KPL-001/002/003 | — | Demonstração nunca entra no catálogo real | — | Sem dados demo em produção; flavor `demo` isolado opcional | Caso 05 | MAP | D |
| CAT-09 | `trackQualifiedProductInterest` 14132 | Cl | Interesse qualificado: só Cliente, não titular, anúncio activo, 1 por cliente/produto/dia; respeita preferências | `productInterest/INT_{p}_{u}_{dia}` | Central `interesse.registar` (dedup servidor) | Caso 27 | MAP | P/D |
| CAT-10 | 6756 "Reservas e Reembolsos" na Home, `renderMyReservations` 14927 | Td | Atalho para reservas e reembolsos | — | `ui/reservas` | UI | MAP | P |
| CAT-11 | 6667 `province-filter` + `persistUiSessionNow` | Td | Restaurar pesquisa/filtros/posição | — | `SavedStateHandle` + DataStore por conta | Caso 05 | MAP | P |
| CAT-12 | 15211 pull-to-refresh, 15176 `smartRefreshApp` | Td | Actualizar por gesto | — | `PullToRefreshBox` | UI | MAP | A |

## 5. Detalhes do anúncio e privacidade

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| DET-01 | 7313-7457; `openDetails` final 31907 (19 redefinições; ver `redefinicoes.tsv`) | Td | ID, título, categoria/subcategoria, modalidade, disponibilidade, preço/unidade, município/província, fotos, características, diferenciais, resumo financeiro | `products/{id}` | `ui/detalhe` + `DetalheViewModel` | Caso 12 | MAP | P |
| DET-02 | 7317-7341, 13987-14047 | Td | Galeria: swipe, miniaturas, contador, visor em ecrã inteiro com zoom | — | `HorizontalPager` + visor com zoom | UI | MAP | P |
| DET-03 | 7358-7389 resumo; 16593 (comissão info) | Td | Preço, sinal, comissão ("sobre o sinal" ou "sobre o contrato mínimo"), info expandível; card do Proprietário (líquido do sinal, saldo de fecho, total líquido) | — | `dominio/financeiro/Motor` | Caso 13 | MAP | P |
| DET-04 | 7389 "Fluxo completo deste negócio", 18669 (1.0.67), 18744 (1.0.69) | Td | Explicação passo a passo do fluxo financeiro do negócio | — | idem | UI | MAP | P |
| DET-05 | 7401-7410, 16488 (CRUD controller) | Pr/Ad | CRUD contextual Novo/Ver/Editar/Excluir por titularidade/estado; leitura quando sem permissão | — | `ui/detalhe/AccoesCrud` | Caso 11 | MAP | P |
| DET-06 | 7425-7433, `canViewPrivateProductDetails` 14069, `hydratePrivateLocationForDetails` (5×) | Td | Bairro, ponto de referência, nome/contacto do Proprietário só para titular, Ad, e Cliente com reserva financeiramente confirmada | `productPrivate/{id}` | Central `detalhes.privados` (projecção mínima) | Caso 11 | MAP | P/D |
| DET-07 | 7438-7447 características + titularidade | Td | Características por categoria; referência de titularidade mostrada só a quem tem permissão | — | idem | Caso 06 | MAP | P |
| DET-08 | 31613-31760 (1.0.233 SENSITIVE), 27738 (1.0.213 isolamento), 28587 (1.0.216 idempotente) | Td | Cache público sem campos sensíveis; respostas atrasadas não preenchem produto seguinte; falha privada não apaga públicas | IndexedDB → Room | Room `produto_publico` e `produto_privado` (chaves/âmbitos próprios) + token de pedido no ViewModel | Caso 12 | MAP | D/A |
| DET-09 | 7457 "Gerar referência do sinal" → `proceedToCheckout` (4×, final 27622) | Cl | Iniciar reserva | — | ver RES-* | — | MAP | P |
| DET-10 | 32231/32332/32450 (1.0.237-239 alturas) | — | Ecrã de detalhes com cabeçalho/acção fixos e conteúdo rolável sem cobertura por barras | — | `Scaffold` + insets | UI A54 | MAP | A |

## 6. Anunciar e editar

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| PUB-01 | 6791-6814, `togglePublishFields` 15226 | Pr/Ad | Categoria (rádio), subcategoria, modalidade Venda/Arrendamento | — | `ui/anunciar` passo 1 | Caso 06 | DOM | P |
| PUB-02 | 6859-6898 | Pr/Ad | Imóvel: compartimentos*, estado/reparos*, mosaicada/pintada/climatizada/mobilada, observações (600) | `categoryDetails` | `dominio/anuncio/DetalhesImovel` | Caso 06 | DOM | P |
| PUB-03 | 6907-6911 | Pr/Ad | Terreno: área m²*, finalidade*, topografia, acesso, infraestruturas (600) | idem | `DetalhesTerreno` | Caso 06 | DOM | P |
| PUB-04 | 6917-6920 | Pr/Ad | Viatura: marca*, modelo*, ano* (1950-2100), km, combustível, transmissão, estado (500) | idem | `DetalhesViatura` | Caso 06 | DOM | P |
| PUB-05 | 6926-6928 | Pr/Ad | Motorizada: marca*, modelo*, ano*, cilindrada, km, estado (500) | idem | `DetalhesMotorizada` | Caso 06 | DOM | P |
| PUB-06 | 6934-6936 | Pr/Ad | Outros: estado*, quantidade, marca, modelo/ref., descrição técnica* (700) | idem | `DetalhesOutro` | Caso 06 | DOM | P |
| PUB-07 | 6943-6946, `draftCategoryDetails` (wrap 16727) | Pr/Ad | Diferenciais: perfil da localização, condomínio/ref. comercial, pontos fortes (500) | idem | `Diferenciais` | Caso 06 | DOM | P |
| PUB-08 | 6952-6993 | Pr/Ad | Província*, município*, bairro* (privado), ponto de referência* (privado) | público/privado | separação no contrato | Caso 11 | DOM | P |
| PUB-09 | 6830-6841, `collectRentalTerms` 12455 (+wrap 16723) | Pr/Ad | Aluguer: unidade de preço, tempo mínimo (3/6/12 meses/personalizado), quantidade+unidade, outra unidade nomeada, meses de sinal (só leitura) | `rentalTerms` | `dominio/financeiro/CondicoesAluguer` | Caso 13 | DOM | P/D (ver FIN-05) |
| PUB-10 | 7004-7011 | Pr/Ad | Preço AOA*; % de sinal global (só leitura p/ Pr) | — | idem | Caso 13 | DOM | P |
| PUB-11 | 7038-7047, `previewImages` 13527 | Pr/Ad | Fotos: até 5, ≤1,20 MB cada, optimização, capa, ordem, remover, contador | Storage | ver MED-* | Caso 07 | MAP | P |
| PUB-12 | 7058-7070, `previewOwnershipDocuments` 13587 | Pr/Ad | Nº/ref. do documento de titularidade*; até 10 comprovativos JPG/PDF ≤2 MB | privado | Storage privado + `productFiles` | Caso 07 | MAP | P |
| PUB-13 | 7084-7096, `togglePayoutFields` 15221 | Pr/Ad | Recebimento: IBAN ou nº Multicaixa Express (9 díg.) | privado | `productPrivate.payout` (nunca público) | Caso 11 | DOM | P/I |
| PUB-14 | 7104-7113 | Pr/Ad | Alertas do produto: reserva, interesse, canal (perfil/e-mail/SMS/ambos) | `ownerAlertPreferences` | idem | Caso 27 | DOM | P |
| PUB-15 | 7121-7122, gate 8879-8970 | Pr/Ad | Aceite das políticas do anúncio (versão, data, leitura) | `policyAcceptance` | `dominio/politicas/Aceite` | Caso 01 | MAP | P |
| PUB-16 | 7127-7131 | Pr | Promoções: Destaque Premium, Top Patrocinados, Prioridade na Vitrine (% do preço, duração) | `promotionServices` | FIN-07 | Caso 13 | DOM | P |
| PUB-17 | 7136-7171 resumo "Quem paga, quem recebe e quanto"; 22232-22467 (1.0.145-147) | Pr/Ad | Resumo financeiro do anúncio, chips globais, explicação; cancelar trabalho/edição | — | Motor | Caso 13 | MAP | P |
| PUB-18 | `handlePublish` 13723 → 15939 (payments-v2) | Pr | Fluxo pago: validar → verificar duplicado → rascunho privado → resumo taxa+promo → intenção de pagamento | `listingDrafts`, `paymentIntents` | PAG-* | Caso 15 | MAP | P/D |
| PUB-19 | 15943, 17728 (1.0.52), 9129 | Ad | Administrador publica sem taxa nem promoções cobradas (`managerPublicationIsAlwaysFree`) | — | Central `anuncios.publicarIsento` com motivo/snapshot | Caso 15 | MAP | P/C (D-05) |
| PUB-20 | `editListing` 13880 (8×, final 25691), 24185-24613 (1.0.190-196), 25535-25757 (1.0.192-193) | Pr/Ad | Edição hidrata todos os campos/fotos/docs; regressa ao chamador; campos obrigatórios legados; estabilidade de categoria | — | `ui/anunciar` modo edição + verificação de versão | Caso 06 | MAP | P/D |
| PUB-21 | `deleteListing` 13937, `toggleListingPublication` 13960 | Pr/Ad | Ad exclui/desanuncia; Pr via autorização (AUTZ-*) | — | Central | Caso 24 | MAP | P |
| PUB-22 | 18124-18362 (1.0.57 persistência do formulário) | Pr/Ad | Formulário sobrevive a recriação | — | `SavedStateHandle` + rascunho Room | Caso 31 | MAP | A |

## 7. Rascunhos, continuidade e lixeira

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| RAS-01 | 9386 `KUPILA_PRODUCT_DRAFT_LIMIT=5`, `pruneProductDrafts` 9576 | Pr/Ad | Até 5 cadastros inacabados; ao exceder remove o mais antigo, com aviso | IndexedDB + `productWorkDrafts` | Room `rascunho_anuncio` + Central sync | Caso 09 | DOM+TST | P (avisar antes) |
| RAS-02 | 8235-8246, `renderProductDraftPicker` 9793, `resumeProductDraft` 9800 | Pr/Ad | Escolher continuar, remover, iniciar novo | — | `ui/rascunhos` | Caso 09 | MAP | P |
| RAS-03 | 25977-26469 (1.0.199-203) | Pr/Ad | Selecção em lote/seleccionar tudo e exclusão em lote de rascunhos | — | idem | Caso 09 | MAP | P |
| RAS-04 | `saveProductDraftNow` 9704, `scheduleProductDraftSave` 9780 | Pr/Ad | Autosave imediato; campos dinâmicos, aceites, fotos/docs | — | Room + ficheiros privados | Caso 31 | MAP | P |
| RAS-05 | `startProductDraftCloudRealtime` 9855, 21227 (1.0.110) | Pr/Ad | Sincronização entre dispositivos | `productWorkDrafts/{uid_draft}` | WorkManager + listener | Caso 09 | MAP | P |
| RAS-06 | `productDraftHasContent` 9567, `productDraftCompletion` 9572 | Pr/Ad | Ficha vazia ≠ rascunho útil; % de conclusão | — | domínio | Unit | DOM+TST | P |
| RAS-07 | 9975-10133 persistência universal de modais/UI | Td | Restaurar contexto de formulários e scroll | `kupila-ui-*` | `SavedStateHandle`/DataStore | Caso 31 | MAP | A |
| RAS-08 | 21768-22174 (1.0.132-142), 21844 `modal-admin-trash` | Ad | Lixeira administrativa: restaurar tudo, esvaziar, lista; selecção em lote | tombstones | Central + `ui/admin/Lixeira` | Caso 25 | MAP | P |
| RAS-09 | 26472 (1.0.204 resume durable listings) | Pr | Retomar anúncios duráveis/pendentes | — | Central de pendências | Caso 25 | MAP | P |

## 8. Fotos, documentos e assistente IA

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| MED-01 | 19123-20802 `kupila-source-picker` (1.0.77), 21065 (1.0.93) | Pr/Ad | Escolher fonte: câmara, galeria/ficheiros, IA | — | `ui/media/EscolherFonte` (PickVisualMedia, câmara `TakePicture`, `OpenDocument` p/ PDF) | Caso 07 | MAP | A |
| MED-02 | `compressListingImageForFirestore` 11790, `canvasJpegDataUrlAsync` 11772 | Pr/Ad | Optimização em segundo plano sem perder selecção | — | `dados/media/Compressor` (Dispatchers.Default) | Caso 07 | MAP | A |
| MED-03 | 21102-21128 (1.0.97-99) | Pr/Ad | Confirmação da selecção, guarda de duplicados, persistência | — | idem | Caso 07 | MAP | P |
| MED-04 | 20059 `ksp-ai-assistant`, `kupilaAiBuildShotPlan` 19389, 21257-21300 (1.0.114-118) | Pr/Ad | Assistente IA: contexto por categoria/subcategoria/características, vistas interior/exterior, perspectivas, grelha até 8 propostas, "Novas", selecção sem regenerar | — | `dominio/ia/PlanoImagens` + `dados/ia/GeradorImagens` (adaptador) | Caso 08 | MAP | P/I (D-06) |
| MED-05 | 21302 (1.0.119 minimizar tarefa), 21130 (1.0.100 recuperação) | Pr/Ad | Minimizar tarefa, progresso, voltar a editar, recuperar resultado | — | WorkManager + Room `tarefa_ia` | Caso 08 | MAP | P |
| MED-06 | 21174-21195 (1.0.104-106 Puter / AI Horde), 21149 (1.0.102 placeholdr) | — | Fornecedores históricos (anónimos) | — | Adaptador configurável; sem rota anónima presumida | — | BLQ | I |
| MED-07 | — (requisito 14) | Td | Ilustração gerada sempre identificada; não serve de prova | `images[].synthetic=true` | domínio + selo na UI | Caso 08 | DOM | D |
| MED-08 | 12176-12212 docs em chunks no Firestore | Pr/Ad | Comprovativos de titularidade com integridade | `productFiles` (dataURL em chunks) | Storage privado + hash SHA-256 | Caso 07 | MAP | D (dataURL→Storage) |

## 9. Titularidade e duplicados

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| DUP-01 | 11839-11954 (`normalizeDuplicateText`, `duplicateSha256`, `duplicateImagePerceptualHash`, Hamming, Jaccard, `buildDuplicateIdentity`, `evaluateLocalDuplicate`) | Pr/Ad | Sinais: ref. documental, hash docs, hash exacto/perceptual de imagens, descritores; prova vs semelhança | — | `dominio/duplicados/Identidade` (puro, testado) | Caso 10 | DOM+TST | P |
| DUP-02 | `claimDuplicateIdentity` 12051, `checkDuplicateClaims` 12008, `releaseDuplicateClaims` 12089 | Pr/Ad | Claims centrais concorrentes; libertar em rascunho fechado | `productIdentityClaims` | Central `duplicados.reivindicar` (transacção) | Caso 10 | MAP | P/D |
| DUP-03 | `scheduleDuplicatePrecheck` 12117, `runDuplicatePrecheck` 12113 | Pr/Ad | Pré-verificação local com feedback | — | ViewModel | UI | MAP | P |
| DUP-04 | 20805 (1.0.88), `disableGlobalDuplicateClaims` 12000 | — | Permissão negada não apaga nem gera retry infinito; indicar verificação pendente | — | estado `VERIFICACAO_PENDENTE` | Caso 10 | MAP | D |

## 10. Motor financeiro

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| FIN-01 | `getDepositAmount` 12429, `getCommissionAmount` 12436, `getNetAmount` 12440 | Td | Venda: D=P·s/100; C=D·c/100; N=D−C; R=P−D | — | `dominio/financeiro/MotorFinanceiro` (BigDecimal, escala 2, HALF_UP no fim) | Caso 13 (venda 1.000.000) | DOM+TST | P |
| FIN-02 | `getListingFeeAmount` 12444 | Pr | T=P·t/100 (sobre preço/unidade no aluguer) | — | idem | Caso 13 | DOM+TST | P |
| FIN-03 | `RENTAL_PRICE_UNIT_DAYS` 12448, `rentalDurationPreset` 12451, `collectRentalTerms` 12455-12467 | Pr | B = preço unidade × ceil(dias_mín / dias_unidade), convenção 1/7/15/30/360; validações de coerência | — | `CondicoesAluguer` | Unit | DOM+TST | P (convenção comercial documentada) |
| FIN-04 | `rentalDepositMonths` 16702, `monthlyEquivalentPrice` 16707 | Pr/Cl | Aluguer: D = equivalente mensal × m (m global 1-12, snapshot) | — | idem | Caso 13 (aluguer 100.000) | DOM+TST | P |
| FIN-05 | `monthlyEquivalentPrice` 16707 (`OTHER:price`, `DAILY×30`, `WEEKLY×30/7`) | Pr | Unidade personalizada sem equivalência mensal **não** presume 1 unidade = mês | — | exigir `equivalenciaMensal` explícita; senão bloquear e encaminhar | Unit | DOM+TST | D (incons. B) |
| FIN-06 | `getListingCommissionAmount` 12470, calculateCommission 16735 | Td | Aluguer: C = B·c/100 ("sobre o contrato mínimo"); venda: sobre o sinal | — | Motor | Caso 13 | DOM+TST | P |
| FIN-07 | `getPromotionCatalog` 15831, `promotionFeeTotal` 15833 | Pr | Promoção j = P·%j/100; Q = T + Σ promoções | — | Motor | Caso 13 (11.000 + 10.000) | DOM+TST | P |
| FIN-08 | 16739-16740 (vermelho se C>D ou D>B) | Td | Validar D>0, D≤B, 0≤C≤D; inválido bloqueia operação e encaminha Ad | — | `ResultadoCalculo.Invalido(motivo)` | Caso 13 (c=40%) | DOM+TST | D (origem usa `max(0,…)`) |
| FIN-09 | `getEffectiveListingCommissionPercent` 12469 | Ad | Isenção de comissão para anúncio publicado por Ad / `commissionExempt` | snapshot | Motor + snapshot | Caso 14 | DOM | C (D-05) |
| FIN-10 | `calculateIntermediationSimulation` 9065 | Td | Simulador usa fórmula antiga (% sobre B no aluguer) | — | Simulador usa **o mesmo motor** (meses) | Caso 30 | DOM | D (incons. B) |
| FIN-11 | `formatMoney` 12479 (`pt-AO`) | Td | Formato AOA português de Angola | — | `dominio/financeiro/FormatoAoa` | Unit | DOM+TST | P |
| FIN-12 | `recordFinancialTransaction` 12491, `getFinancialTotals` 12519, `saveFinancialState` 12483 | Ad | Livro financeiro (local na origem) | `financialTransactions` | Ledger central imutável (Central) | Caso 26 | MAP | D |
| FIN-13 | snapshots: `depositPercentSnapshot`, `commissionPercentSnapshot`, `dealBaseSnapshot`, `rentalDepositMonthsSnapshot` | Td | Snapshot do anúncio e da transacção; ajuste novo não recalcula histórico | — | `SnapshotFinanceiro` versionado | Caso 14 | DOM+TST | P |

## 11. Pagamento para publicar e checkout

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| PAG-01 | `saveListingDraftForPayment` 15857 | Pr | Rascunho privado `PENDING_PAYMENT` antes de pagar | `listingDrafts`, `listingDraftPrivate` | Central `anuncios.prepararPagamento` | Caso 15 | MAP | P |
| PAG-02 | `createPaymentIntent` 15897, `generatePaymentReference` 15829 | Pr/Cl | Intenção de pagamento com referência | `paymentIntents` | Central `pagamentos.criarIntencao` (idempotente, referência do **provedor**) | Caso 16/17 | BLQ | D/I (Math.random → provedor; D-02) |
| PAG-03 | `reconcileListingPaymentLifecycle` 15879 | Pr | Fechar intenções já publicadas/expiradas; consultar antes de nova cobrança | — | Central `pagamentos.reconciliar` + job agendado | Caso 15 | MAP | P/D |
| PAG-04 | 7464-7501, `selectPayMethod` 15926, `goToReceiptStep` 15927 | Pr/Cl | Duas etapas: Referência ATM/Homebanking ou MC Express (telefone) → "Já paguei" → comprovativo | — | `ui/checkout` | Caso 16 | MAP | P/I |
| PAG-05 | `analyzePaymentReceipt` 15932, `bestReferenceFromText` 15930 | Pr/Cl | Comprovativo JPG/PNG/WebP ≤4 MB; OCR extrai referência de 9 dígitos; campo manual só se falhar | `paymentSubmissions` | ML Kit + Central `pagamentos.submeterComprovativo` | Caso 16 | MAP | A (Tesseract→ML Kit) |
| PAG-06 | `submitPaymentReceipt` 15934, `attachPaymentIntentListener` 15919 | Pr/Cl | Estado só muda por confirmação do backend (listener) | `paymentIntents.status` | Central valida com provedor; app observa | Caso 16/17 | BLQ | I |
| PAG-07 | `KUPILA_PAYMENT_OPEN/TERMINAL_STATUSES` 15868-15869 | Td | Estados AWAITING_PAYMENT, VERIFYING, RECEIPT_MATCHED, AWAITING_PROVIDER_CONFIGURATION, PROCESSING / COMPLETED, PUBLISHED, PAYOUT_PENDING, CANCELLED, EXPIRED, VOIDED | — | `dominio/pagamentos/EstadoIntencao` | Unit | DOM+TST | P |
| PAG-08 | 15916 entidade ausente | Td | Sem entidade/provedor: indisponibilidade específica, trabalho conservado | — | estado `AGUARDA_CONFIGURACAO_PROVEDOR` | Caso 16 | MAP | P |
| PAG-09 | 27459 (1.0.211 timeout de referência), 27543 (1.0.212 ponte) | Td | Timeout = resultado desconhecido; consultar pelo id idempotente | — | `idempotencyKey` | Caso 17 | MAP | P/D |
| PAG-10 | 15937 `confirmBooking`/`simulateWebhookRefPayment` neutralizados | — | Sem simulação de webhook no cliente | — | Webhook só no Central | Caso 17 | MAP | D |
| PAG-11 | `paymentCompleted` 15922 | Pr/Cl | Após confirmação: publicar (LISTING) ou abrir comprovativo da reserva | — | observa estado | Caso 15/16 | MAP | P |

## 12. Reserva, custódia, OTP, liberação

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| RES-01 | `proceedToCheckout` final 27622; 15936 | Cl | Conta activa, anúncio ACTIVE; intenção RESERVATION com snapshot (base, sinal, comissão, líquido, `holdOwnerPayout:true`, `payoutReleaseTrigger:'INSPECTION_DECISION'`) | `paymentIntents` | Central | Caso 16 | MAP | P |
| RES-02 | `firebaseAtomicReserve` 12361 | Cl | Aquisição atómica da disponibilidade após pagamento confirmado | `reservations`, `products.status=RESERVED` | Central transacção; excedente → `pagamentosExcedentes` | Caso 18 | MAP | P/D |
| RES-03 | `reservationDurationMinutes` 1440; `formatCountdown` 14914 | Cl/Pr | Prazo começa na confirmação oficial (timestamp servidor); contador é apresentação | `expiresAt` | Central + UI | Caso 19 | MAP | P |
| RES-04 | `expireReservation` 14916, `checkReservationExpirations` 14926 | Td | Expiração liberta o bem sem estorno automático; regista evento | — | Job agendado do Central | Caso 19 | MAP | P/D (origem: cliente gera novo OTP com Math.random) |
| RES-05 | 7509-7566 recibo; `populateReceipt` 14943; `downloadReceiptJPG/PDF` 14966-14971 | Cl/Pr/Ad | Comprovativo da reserva JPG/PDF: nº, OTP (só a quem deve), local, bairro, ref., contacto, contagem, estado | — | `dados/documentos/GeradorRecibo` + FileProvider | Caso 29 | MAP | P |
| RES-06 | `openReservationReceipt` 14945, `renderMyReservations` 14927 | Td | Consulta de reservas (ACTIVE/EXPIRED/REFUND_PENDING/REFUNDED) e reabertura do comprovativo | — | `ui/reservas` | UI | MAP | P |
| RES-07 | `validateOTP` 14473 → 15948 (payments-v2) | Pr/Ad | OTP 6 dígitos: titular/Ad confirma vistoria + negócio fechado → decisão + pedido de liberação; bloqueado se houver reembolso | `reservations`, `payoutReleaseRequests` | Central `reservas.validarOtp` (hash, tentativas, validade, uso único) | Caso 20 | DOM | D (incons. C) |
| RES-08 | `cancelReservation` 13948 | Ad | Cancelamento com mensagem e consequência próprias | — | Central | Caso 19 | MAP | P |
| RES-09 | `holdOwnerPayout`/estados de liberação | Pr/Ad | Estados: custódia → decisão positiva → liberação solicitada → processamento → liberado confirmado / falha | `payouts` | Central + provedor | Caso 21 | BLQ | I (incons. A, H) |
| RES-10 | 7374-7385 card do Proprietário | Pr | "Recebido" só sobe após confirmação financeira | — | painel derivado de eventos | Caso 21 | MAP | D |

## 13. Reembolso e contestação

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| REE-01 | 7573-7583, `openRefundRequest` 14946, `submitRefundRequest` 14954 | Cl | Pedido: OTP da vistoria, motivo, descrição (800), evidência imagem/PDF ≤600 KB, declaração | `refundRequests`, `refundEvidence` | Central `reembolsos.solicitar` + Storage privado com hash | Caso 22 | MAP | P |
| REE-02 | 7580 motivos | Cl | PRODUCT_MISMATCH, CONDITION_UNSATISFACTORY, DOCUMENTATION_ISSUE, OWNER_DID_NOT_ATTEND, OTHER | — | `MotivoReembolso` | Unit | DOM+TST | P |
| REE-03 | 7583 declaração única | Cl | Declaração adaptada ao motivo (ausência do Proprietário ≠ vistoria presencial) | — | `Declaracao.para(motivo)` | Caso 22 | DOM+TST | D (incons. E) |
| REE-04 | `reviewRefundByOwner` 14957 | Pr | Proprietário abre evidência (obrigatório) e confirma não conclusão ou contesta | `ownerEvidenceViewedAt` | Central | Caso 22 | MAP | P |
| REE-05 | `decideRefundRequest` 14958, 14940 | Ad | Aprovar só após confirmação do Proprietário; recusar; contestado vai a análise | — | Central; contestado → decisão administrativa com fundamento (ver D-07) | Caso 22 | DOM | P/C |
| REE-06 | `viewRefundEvidence` 14949, `refundEvidenceHash` 14948 | Td aut. | Verificar integridade (SHA-256) antes de decidir; guardar cópia autorizada | — | idem | Caso 23 | MAP | P |
| REE-07 | `refundAmounts` 14934 | Td | Reembolso elegível = sinal − comissão (snapshot) | — | Motor | Caso 13 | DOM+TST | P |
| REE-08 | `refundStatusLabel` 14933 | Td | Estados PENDING_OWNER_CONFIRMATION, OWNER_CONFIRMED, CONTESTED_PENDING_ADMIN, APPROVED_REFUND_PENDING, REFUNDED, REJECTED | — | `EstadoReembolso` | Unit | DOM+TST | P |
| REE-09 | `refundPayoutRequests` 14958 | Ad | Aprovação cria obrigação; "reembolsado" só com confirmação do provedor | — | Central + provedor | Caso 21/23 | BLQ | I |

## 14. Painel do Proprietário

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| PROP-01 | 7178-7303, `renderMyListings` 14346, `renderProductCarousel` 14416 | Pr/Ad | Painel: perfil, produtos cadastrados (carrossel), estados, OTP, pertences | — | `ui/proprietario` | UI | MAP | P |
| PROP-02 | 7644-7705, `saveOwnerRegistration` 12696, `isOwnerProfileComplete` 12598 | Pr | Cadastro do Proprietário: foto, nome, e-mail funcional, residência, telefones, canal; exigido antes de anunciar | `users` | `ui/proprietario/Perfil` | Caso 01 | MAP | P |
| PROP-03 | `renderFinancialSummary` 12527 | Pr | Sinal confirmado, comissão, custódia, liberação, recebido, reembolsos | — | Central agrega eventos | Caso 21/26 | MAP | D |

## 15. Administração

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| ADM-01 | 7206-7234 atalhos | Ad | Utilizadores, Catálogo, Monitoramento, Clientes & Proprietários, Administradores, Autorizações, Histórico | — | `ui/admin/Centro` | UI | MAP | P |
| ADM-02 | 8023-8056, `renderUserManagement` 14750, `saveManagedUser` 14880 | Ad | Pesquisa/filtros, ficha, criar Cl/Pr (foto, perfil, estado, dados, senha inicial), activar/inactivar/suspender/arquivar | `users` | Central `contas.criarPorAdmin` (Admin SDK, não troca sessão) | Caso 02 | BE+TST(emulador) | P/D |
| ADM-03 | `isUserCreatedByCurrentAdmin` 14737, `managedUserPrivacyBadge` 14747 | Ad | Dados pessoais editáveis só se criados pelo Ad actual; contas públicas só mudam estado | `createdBy` | Central valida autoria | Caso 02 | BE+TST(emulador) | P |
| ADM-04 | `createManagedFirebaseAuthAccount` 14866, `rollbackManagedFirebaseAuthAccount` 14877 | Ad | Criação com rollback | — | Central (Admin SDK) | Caso 02 | BE+TST(emulador) | D (app secundária no cliente → servidor) |
| ADM-05 | 8065, `renderAdministratorDirectory` 14715 | Ad | Directório da equipa administrativa | — | `ui/admin/Administradores` | UI | MAP | P |
| ADM-06 | — | Ad | Concessão de privilégio ADMIN fora do cadastro público | custom claims | Central `admin.conceder` (só Ad existente) | Caso 01 | BE | P |
| ADM-07 | 8116-8128, `renderUserRecord` 14824, 23457 (1.0.166 export) | Ad | Ficha do utilizador com estatísticas; exportar JPG/PDF | — | `GeradorFicha` | Caso 29 | MAP | P |
| ADM-08 | `openAdminCatalogCenter63` (18365, 1.0.63) | Ad | Catálogo administrativo com detalhes e gestão | — | `ui/admin/Catalogo` | UI | MAP | P |
| ADM-09 | 8135, `renderOperationHistory` 14911, `recordOperation` 12397, `cloudRecordAudit` 12368 | Ad | Histórico de operações/auditoria | `auditLog` | Central escreve auditoria; app lê | Caso 26 | MAP | P/D |
| ADM-10 | 7777, `openMonetizationHistory` 12537, `renderFinancialHistory` 12544 | Ad | Histórico da monetização | `financialTransactions` | Ledger | Caso 26 | MAP | P |
| ADM-11 | 8078-8110, 15603 fontes, 21387/21737 (1.0.128/131) | Ad | Processos pendentes (rascunhos, pagamentos, autorizações, reembolsos, liberações): continuar, tentar novamente, abrir, manter, descartar da central | várias | Central `pendencias.listar` | Caso 25 | MAP | P |
| ADM-12 | 21768 (1.0.132 admin bulk), 21894 (1.0.134) | Ad | Acções em lote com confirmação interna | — | idem | Caso 25 | MAP | P |

## 16. Autorizações de desanúncio/exclusão

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| AUTZ-01 | `requestListingAuthorization` 14613 | Pr | Pedir DELETE/UNPUBLISH (não se RESERVED/COMPLETED; UNPUBLISH só ACTIVE); um pedido em curso; e-mail funcional obrigatório; aviso "sem reembolso" | `authorizationRequests` | Central | Caso 24 | DOM+TST | P |
| AUTZ-02 | `generateAuthorizationCode` 14647 | Ad | Gerar código 6 díg., validade 30 min, envio por e-mail/SMS (origem: entrega **simulada**) | — | Central gera, guarda hash, envia por e-mail/SMS | Caso 24 | BLQ | D/I (D-08) |
| AUTZ-03 | `useAuthorizationCode` 14656, `executeAuthorizedListingAction` 14664 | Pr | Usar código: titular, não expirado, uso único, vinculado a produto/acção; executa e regista sem reembolso | — | Central | Caso 24 | DOM | P/D |
| AUTZ-04 | 8146-8173, `renderAuthorizationCenter` 14633 | Pr/Ad | Centro de autorizações (Ad vê todos; Pr os seus) | — | `ui/autorizacoes` | UI | MAP | P |

## 17. Monitoramento e indicadores

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| MON-01 | 7713-7766, 11456-11627 | Ad | Centro de monitoramento: presença por dispositivo/perfil, sessões activas, acessos, crescimento 7/14/30 dias vs período anterior, produtos por tipo/modalidade/estado, reservas, saldos; detalhe | `appPresence`, `platformMetrics` | Central agrega; `ui/admin/Monitoramento` | Caso 26 | MAP | P |
| MON-02 | `writeKupilaPresence` 11466, `startPresenceTracking` 11482 | Td | Heartbeat de primeiro plano com TTL | `appPresence` | `ProcessLifecycleOwner` + heartbeat 60 s | Caso 26 | DOM | P |
| MON-03 | `monitoringGrowth` 11510 | Ad | Base zero não produz percentagem falsa | — | `dominio/indicadores` | Caso 26 | DOM+TST | D (verificar fórmula na implementação) |
| MON-04 | `getMonitoringBalances` 11590 | Ad | Custódia ≠ receita | — | idem | Caso 26 | MAP | D |
| MON-05 | 18365 (1.0.63 background admin crossdevice), 17308 alertas em 2.º plano | Ad | Alertas administrativos em segundo plano | — | FCM | Caso 27 | MAP | A |

## 18. Notificações, interesse, campanhas

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| NOT-01 | 7603-7616, `renderNotifications` 14534, `notificationEventKey` 14504 | Td | Sino, contador não lidas, filtros Todas/Reservas/Monetização/Interesse/Administração/Novidades, marcar lidas, acção contextual | `ownerAlerts`/notificações | Room + Central (chave idempotente) | Caso 27 | MAP | P |
| NOT-02 | `notifyUser` 14510, `notifyManagers` 14517 | — | Eventos para utilizador e Ads | — | Central cria; FCM entrega | Caso 27 | MAP | P/D |
| NOT-03 | 7940-7949, `playKupilaDeviceAlert` 14565, `testKupilaDeviceAlert` 14572 | Td | Modos Automático/Som/Vibração/Som+Vibração/Silencioso; notificação do sistema; teste no dispositivo | `alertMode` | Canais Android + `Vibrator` | UI dispositivo | MAP | A |
| NOT-04 | 7863-7872 | Ad | Alertas ao Proprietário: reserva, interesse (limiar clientes distintos 3, janela 24 h, cooldown 12 h), e-mail/SMS | `settings` | Central agenda/agrega | Caso 27 | DOM+TST | P/I (e-mail/SMS: D-08) |
| NOT-05 | 7952-7960, 14578-14609 | Ad | Campanhas: título (70), mensagem (320), público, prioridade, início/fim, acção (Explorar/Reservas/Anúncios), CTA; criar/listar/activar/desactivar/excluir; entrega uma vez por utilizador | `settings.marketingMessages` | Colecção `campanhas` + Central | Caso 27 | DOM+TST | P |

## 19. Testemunhos

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| TES-01 | 7623-7638, `renderTrustCenter` 13127, `renderTrustSummary` 13122 | Td | Negócios concluídos, nº de testemunhos, média; lista "NEGÓCIO VERIFICADO" | `testimonials`, `platformMetrics` | `ui/confianca` | Caso 28 | MAP | P |
| TES-02 | `eligibleTestimonialReservations` 13125, `openTestimonialComposer` 13129, `submitVerifiedTestimonial` 13130 | Cl/Pr | Só reserva COMPLETED em que participou, 1 por reserva; classificação 1-5, texto, nome (completo/abreviado/anónimo) | — | Central valida vínculo | Caso 28 | DOM+TST | P |
| TES-03 | `moderateTestimonial` 13131 | Ad | Aprovar/rejeitar, auditado | — | Central | Caso 28 | MAP | P |

## 20. Simulador, ajuda, manual e políticas

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| SIM-01 | 8253-8309, 9044-9081 | Td | Modos completo/reserva/publicação; venda/aluguer; unidades/prazo; %; promoções; resultados e fórmulas | — | `ui/simulador` + mesmo Motor | Caso 30 | DOM | P/D (meses) |
| SIM-02 | 8278 `sim-persistence-bar`, 22381 (1.0.147) | Td | Cenário guardado automaticamente, limpar, "usar percentagens actuais" | local | DataStore | Caso 30 | MAP | P |
| FAQ-01 | 8212-8228, `renderKupilaFaq` 9032 | Td | FAQ com expansão individual e Ver mais/menos | conteúdo | `assets/conteudo/faq.json` (extraído) | UI | MAP | P |
| POL-01 | 8324-8391, 8879-8970 | Td | Manual por perfil, políticas, intermediação; "Li e compreendi"; leitura até ao fim; aceite versionado | — | `assets/conteudo/politicas-v1.md` (extraído) + `Aceite` | Caso 01 | MAP | P |
| POL-02 | 8184 `modal-reservation-protection` | Td | Explicação "Reserva Protegida" | — | idem | UI | MAP | P |
| AJU-01 | 17825 (1.0.55 account assets & help) | Td | Ajuda contextual e recursos da conta | — | `ui/ajuda` | UI | MAP | P |

## 21. Offline, sincronização e navegação

| ID | Origem | Perfil | Comportamento esperado | Dados/efeitos | Destino | Teste | Estado | Classe/limitação |
|---|---|---|---|---|---|---|---|---|
| OFF-01 | 17315-17726 (1.0.51 offline-first), 21338-21395 (1.0.123-128) | Td | Fila offline persistente com auto-recuperação, tipos Firestore, sync rápido, cabeçalho de sync manual | IndexedDB | Room `fila_operacoes` + WorkManager (idempotente, por conta) | Caso 31 | MAP | A |
| OFF-02 | 17296-17308 `modal-offline-sync` | Td | Centro de sincronização do dispositivo (contador no cabeçalho) | — | `ui/sincronizacao` | UI | MAP | P |
| OFF-03 | 27367 (1.0.210 offline continue), 31496 (1.0.232 resiliência), 32450 (1.0.239) | Td | Continuar offline com cache autorizado e idade dos dados | — | Room + rótulo "actualizado há…" | Caso 31 | MAP | P |
| OFF-04 | 14978-15010 histórico de navegação ◀ ▶ | Td | Voltar/avançar | — | Back do Android (predictive back); sem "avançar" | UI | MAP | A |
| OFF-05 | 15017-15130 pilha de modais, posições arrastáveis | Td | Folhas filhas regressam ao pai | — | Navigation Compose (rotas + `ModalBottomSheet`) | Caso 12 | MAP | A |
| OFF-06 | 16126 watchdog, 29877 (1.0.220) | — | Watchdog de arranque | — | Desnecessário (nativo) | — | MAP | A |
| OFF-07 | 26592 (1.0.205 session lifecycle) | Td | Revalidação ao voltar ao 1.º plano | — | `ProcessLifecycleOwner` | Caso 04 | MAP | A |

## 22. Colecções da origem → destino

| Colecção | Uso na origem | Destino |
|---|---|---|
| users | perfis, papéis, estado | `users` (papel só servidor) |
| activeSessions | sessão exclusiva | `activeSessions` (escrita só Central) |
| appPresence | heartbeat | `appPresence` (TTL) |
| auditLog | auditoria | `auditLog` (só Central escreve) |
| products / productPrivate / productFiles | público / privado / anexos em chunks | `products` / `productPrivate` / Storage privado + metadados |
| productIdentityClaims | duplicados | idem (só Central) |
| productInterest | interesse | idem (só Central) |
| productWorkDrafts / listingDrafts / listingDraftPrivate | rascunhos | `productWorkDrafts` (privado do autor) / `listingDrafts`+`listingDraftPrivate` (Central) |
| authorizationRequests | autorizações | idem (código só como hash) |
| reservations | reservas | idem (só Central muda estado) |
| paymentIntents / paymentSubmissions | intenções / comprovativos | idem (montantes/estado só Central) |
| financialTransactions | livro | `ledger` imutável (Central) |
| payoutReleaseRequests / payouts | liberação | idem (Central + provedor) |
| refundRequests / refundEvidence / refundPayoutRequests | reembolso | idem; evidência no Storage privado |
| ownerAlerts | alertas | `notifications/{uid}/items` |
| platformMetrics | métricas | idem (Central agrega) |
| settings | ajustes | `settings/platform` versionado (só Ad via Central) |
| testimonials | testemunhos | idem (moderação Central) |
| `__kupila_probe__` | probe técnico | **não transportado** |

## 23. Blocos de correcção finais (cobertura)

Todos os 196 blocos `<script>` estão listados em `docs/origem-html/blocos.tsv`. Classificação por intervalo:

| Blocos (ids) | Conteúdo | Classe | Destino |
|---|---|---|---|
| 64, 66-69, 71-78, 86, 110-111, 152, 168-171, 182-185, 207-209, 238, 284-326 (exc. 310/312) | viewport, teclado, navegação fluida, modais, scroll, fullscreen, carrossel, alturas dos detalhes | A | Compose/Navigation/insets |
| 70, 82, 101-104, 173-177, 283 | pagamentos v2, sinal em meses, resumos financeiros, ponte de pagamento | P/D | FIN-*, PAG-*, RES-* |
| 85, 271-278 | sessão exclusiva, restauro rápido, rede, relogin | P/A | AUTH-* |
| 89, 144-149, 281, 312 | offline-first, fila, resiliência | A | OFF-* |
| 90, 216 | isenção do Admin, restauro FACTORY | P/C | PUB-19, ID-06 |
| 95, 220-226, 248-252 | persistência e edição do formulário | P/D | PUB-20/22 |
| 96, 157-164, 257-265 | admin crossdevice, pendências, lixeira, rascunhos em lote | P | ADM-11/12, RAS-* |
| 108-143 | escolha de fonte, fotos, assistente IA | P/I | MED-* |
| 109, 112-113, 128, 179, 246-247 | permissões Firestore/duplicados | D/I | PERM-04, DUP-04 |
| 187-199, 229, 232, 299 | identidade visual e temas | P | ID-* |
| 205, 211-214 | export de ficha, háptico, sync de ajustes | P | ADM-07, ID-08 |
| 218-219, 252, 266, 285-289, 314-318 | detalhes privados, isolamento, pipeline único | D/A | DET-06/08 |
| 310 | catálogo de produção sem demo | D | CAT-08 |

Pendente nesta matriz (a refinar durante a implementação de cada módulo, sem perder destino): leitura detalhada dos blocos 108 (1680 linhas, assistente IA) e 89 (offline), e textos integrais de FAQ/manual/políticas para extracção.
