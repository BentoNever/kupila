# Kupila-Gestão de negócios

Marketplace angolano de anúncio, descoberta, compra, aluguer, reserva e intermediação de bens — reconstrução **nativa
Android** do Kupila (HTML 1.0.239), com reserva protegida por sinal validado, vistoria com OTP e reembolso conforme as regras.

> Estado: **em desenvolvimento** (Etapas 1–2 concluídas, Etapa 3 em curso). Ver `docs/progresso.md`.
> Os APK publicados como *pré-release* são builds de desenvolvimento e ligam-se ao Firebase Emulator local; não são uma versão de produção.

## Estrutura
- `android/` — app Kotlin + Jetpack Compose + Material 3, Hilt, Room, WorkManager, Navigation Compose (stack herdada do PDV Digital).
- `backend/` — regras Firestore/Storage (autoridade no plano **Spark**) e Cloud Functions Node 22 (modo **Blaze**, futuro).
- `contratos/` — casos financeiros partilhados pelo APK e pelo Central.
- `docs/` — matriz de paridade, decisões, arquitectura, auditoria da stack, progresso; `docs/origem-html/` índices da origem.
- `tools/` — consulta/extracção do HTML de origem, build e testes.

## Documentos principais
`docs/paridade-html-android.md` · `docs/decisoes.md` · `docs/arquitectura.md` · `docs/auditoria-stack.md` · `docs/build.md`

Desenvolvido por Sousa B.Morais.
