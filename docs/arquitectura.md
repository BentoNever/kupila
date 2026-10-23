# Arquitectura — Kupila-Gestão de negócios (Android)

Fundamentação da stack: `docs/auditoria-stack.md`. Decisões: `docs/decisoes.md`.

## Repositório

```
android/                 App Android nativa (Gradle; applicationId ao.kupila.gestaonegocios, debug .dev)
  app/src/main/java/ao/kupila/gestaonegocios/
    dominio/             Kotlin puro, sem Android: regras, estados, motor financeiro (testado em JVM)
      financeiro/ anuncio/ catalogo/ perfis/ transaccoes/ ajustes/ duplicados/ rascunhos/
      indicadores/ autorizacoes/ notificacoes/ testemunhos/ aparencia/
    dados/               Repositórios: Room (local, por conta), Firebase (remoto), ficheiros, sessão, fila
      remoto/ local/ sessao/ media/ documentos/ sync/
    ui/                  Compose + Material 3; ViewModels (StateFlow); Navigation Compose (um único NavHost)
    di/                  Módulos Hilt
backend/                 Central: Firebase (regras + Cloud Functions Node 22), testado no Firebase Emulator
  firestore.rules storage.rules firestore.indexes.json firebase.json
  functions/src/         financeiro/motor.js (equivalente exacto do motor Kotlin) + comandos
contratos/               Ficheiros partilhados APK ↔ Central (casos financeiros, esquemas)
docs/                    Matriz de paridade, decisões, progresso, auditorias, índices da origem
tools/                   Consulta/indexação do HTML; build Gradle via unidade virtual
```

## Fluxo (secção 4.4 / 5)

```
Compose (ecrã) ──observa── StateFlow (ViewModel) ──chama── caso de uso (dominio)
                                                             │
                                  Repositório ── Room (fonte imediata do ecrã; por conta; cache público ≠ privado)
                                       │      └─ Fila persistente (Room) ── WorkManager (idempotente, backoff, por conta)
                                       └────── Firebase: leituras autorizadas (listeners ligados ao ciclo de vida)
                                               Comandos ao Central (Callable Functions) ── validação ── Firestore/Storage
                                                                                     └─ ledger + auditoria + notificações
```

- **Autoridade**: o Central (Functions + regras) decide identidade/papel (custom claims `papel`, `estado`), sessão exclusiva,
  disponibilidade, pagamentos, reservas, OTP, reembolsos, liberação, ajustes e auditoria. O APK nunca escreve estados
  financeiros nem montantes oficiais; mostra prévias do mesmo motor e os valores oficiais devolvidos.
- **Escrita directa permitida** ao cliente (regras): rascunhos próprios (`productWorkDrafts/{uid}__{id}`, com versão
  crescente e sessão vigente), presença (`appPresence`), marcar notificação como lida, e envio para `uploads/{uid}/…`.
- **Projecções**: `products` (pública) e `productPrivate` (titular/Administrador) separadas no servidor; os participantes de
  reserva confirmada recebem só os dados de encontro por comando. Caches Room públicos e privados têm tabelas/chaves próprias.
- **Dinheiro**: `Dinheiro`/`Percentagem` em `BigDecimal` (APK) e `BigInt` em cêntimos (Central); os dois validados contra
  `contratos/casos-financeiros.json`.
- **Tempo**: timestamps oficiais do servidor (UTC); apresentação em Africa/Luanda; contadores no APK são apresentação.

## Nomes de campos no Firestore (decisão D-20)

Os documentos mantêm os nomes de campos da origem (`status`, `ownerId`, `userId`, `title`, `price`, `category`, `intent`,
`province`, `location`, `categoryDetails`, `rentalTerms`, …) para não forçar migração de dados existentes; o domínio Kotlin
usa nomes portugueses e o mapeamento fica nos DTOs de `dados/remoto`. Campos novos (snapshots, versões, hashes) são
acrescentados sem remover os antigos; campos sensíveis legados (`otp`, `doc`, `ownerSnapshot` no público) são retirados
da projecção pública pela migração (ver `docs/migracao.md`).

## Comandos do Central (Callable, região configurável)

| Comando | Quem | Efeito |
|---|---|---|
| `contas.registar` | público autenticado (após criar conta Auth) | cria perfil CLIENTE/PROPRIETARIO, aceite versionado, claims |
| `contas.criarPorAdmin` / `contas.alterarEstado` / `contas.editarDados` | Administrador | Admin SDK; autoria verificada; não troca sessão |
| `admin.concederPrivilegio` | Administrador | papel GESTOR fora do cadastro público, auditado |
| `sessoes.reivindicar` / `sessoes.terminar` | titular | sessão exclusiva atómica com revogação da anterior |
| `anuncios.guardar` | Proprietário/Admin | valida versão, separa público/privado, move ficheiros de `uploads/` |
| `duplicados.reivindicar` / `duplicados.libertar` | Proprietário/Admin | claims concorrentes |
| `anuncios.prepararPublicacao` | Proprietário | rascunho privado + resumo oficial + intenção idempotente |
| `anuncios.publicarIsento` | Administrador | publicação sem cobrança, com motivo/snapshot |
| `pagamentos.criarIntencao` / `pagamentos.consultar` / `pagamentos.submeterComprovativo` | pagador | idempotente; referência do provedor (D-02) |
| `pagamentos.webhook` (HTTP) | provedor | autenticidade, replay, idempotência, reconciliação |
| `reservas.validarOtp` | titular/Admin | hash, tentativas, validade, uso único → decisão + pedido de liberação |
| `reservas.expirar` (agendado) | sistema | expira sem estorno automático |
| `reembolsos.solicitar` / `reembolsos.rever` / `reembolsos.decidir` | Cliente / Proprietário / Admin | transições de `TransicoesReembolso` |
| `autorizacoes.pedir` / `autorizacoes.gerarCodigo` / `autorizacoes.usar` | Proprietário / Admin / Proprietário | código hash, 30 min, uso único |
| `ajustes.guardar` / `ajustes.restaurarPadroes` | Administrador | versão, autor, data; não recalcula histórico |
| `interesse.registar` | Cliente | dedup por cliente/produto/dia; alerta ao Proprietário por limiar/janela/cooldown |
| `testemunhos.submeter` / `testemunhos.moderar` | participante / Admin | vínculo a reserva concluída |
| `campanhas.*` | Administrador | criar/activar/desactivar/excluir; entrega por público e vigência |
| `detalhes.encontro` | participante de reserva confirmada | dados mínimos de encontro |
