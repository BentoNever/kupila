# Auditoria da stack — Kupila-Gestão de negócios (Android)

Execução: 2026-10-23 00:00 (+01:00, Africa/Luanda / W. Central Africa Standard Time).
Prompt mestre v1.1 (22/10/2026), secção 4. Leitura apenas; nenhum projecto de referência foi alterado.

## 1. Cópias auditadas

| Projecto | Caminho | Repositório / commit | Versão | Estado |
|---|---|---|---|---|
| PDV Digital (APK) | `C:\Users\sousa\Desktop\PDV Pro-Sistema de Gestão ( actualizada)\android-pdv-digital` | Sem `.git` acessível e `git` ausente do PATH → **estado actual da cópia acessível** | versionCode 81 / versionName 1.0.80; APK release `app/build/outputs/apk/release/pdv-digital-1.0.80.apk` (22/10/2026 22:05) | `app-android/versao.json` declara 1.0.80 publicada em 2026-10-22T21:13:40Z (GitHub `BentoNever/pdv-digital-versoes`); não verificada remotamente |
| PDV Pro (web) | mesma raiz, ficheiros `*.html/*.js`, `functions/`, `firestore.rules` | idem | — | Frontend web e "Central" do PDV; **não** é o APK |
| Kupila-Finanças pessoais | `C:\Users\sousa\Desktop\Kupila-Finanças pessoais` | — | — | **Só especificação** (`Prompt_Mestre_Kupila_Financas_Pessoais_Android.txt`, v3.0). Sem código. Regra 4.7 aplicada |
| Kupila HTML (origem funcional) | `Kupila%201.0.239.html` | SHA-256 `dde34687…a4830bf7c` (confere com o prompt) | strings até 1.0.239 | 32 552 linhas, 196 blocos `<script>`, 131 `<style>` |

Instruções lidas: `AGENTS.md` do PDV (publicação GitHub automática do PDV, JDK 27 obrigatório, AGP 9.4.0/Gradle 9.6.1/Kotlin 2.3.21, bytecode Java 17). Essas regras de publicação referem-se ao PDV Digital; **não** autorizam publicar o Kupila.

## 2. Componentes

| Componente | PDV Digital actual e evidência | Kupila-Finanças pessoais | Convergência | Escolha para Kupila-Gestão de negócios | Reutilização | Motivo |
|---|---|---|---|---|---|---|
| Linguagem | Kotlin 2.3.21 (`gradle/libs.versions.toml`) | Prescreve Kotlin (spec) | Convergem | Kotlin 2.3.21 | Mesmo catálogo | Stack comprovada |
| Build | AGP 9.4.0, Gradle 9.6.1 wrapper, JDK 27 (`org.gradle.java.home`), bytecode 17, compile/target SDK 37, minSdk 26, R8 + shrink no release | Spec: seguir PDV | — | Igual; `android.overridePathCheck=true` (caminho com "ã") | Copiar estrutura de build, não o `applicationId` | Toolchain instalado e validado (JDK 27 e SDK presentes) |
| UI | Jetpack Compose BOM 2026.08.00, Material 3, material-icons-extended | Compose/M3 | Convergem | Compose BOM 2026.08.00 + M3 | Padrões de `ui/comum` (Tema, Carregamento, Haptica) adaptados | — |
| Navegação | Navigation Compose 2.10.2, rotas string, `NavHost` único (`ui/Navegacao.kt:475`) | Navigation Compose | Convergem | Navigation Compose 2.10.2 (rotas tipadas por serialização onde útil) | Padrão de grafo único | Um só motor de navegação |
| Arquitectura | MVVM: `@HiltViewModel` + `StateFlow`; repositórios em `dados/`, domínio puro em `dominio/` | MVVM/Clean | Convergem | ui → ViewModel → casos de uso (domínio) → repositórios → Room / remoto | Organização por camadas | — |
| DI | Hilt 2.60.1 + KSP 2.3.12; `HiltWorkerFactory` | Hilt | Convergem | Hilt + KSP | `di/Modulos.kt` como modelo | — |
| Persistência local | Room 2.8.5, `exportSchema` (`app/schemas/1..7.json`), migrações explícitas `MIGRACAO_1_2…6_7`; tabelas de cache cifrável (`CacheLocal.cifrado`) | Room | Convergem | Room 2.8.5, schemas exportados, migrações versionadas, base **por conta** (isolamento 9) | Padrão `cache` + `Cofre` | — |
| Preferências | DataStore Preferences 1.2.1 (`SessaoRepositorio.kt:41`, `Preferencias.kt`) | DataStore | Convergem | DataStore (tema/aparência lidos antes do 1.º frame) | Padrão `Aparencia` | Sem flash de tema (secção 6) |
| Segundo plano | WorkManager 2.12.0, `enqueueUniqueWork`, periódicos 15 min com rede (`sync/Sincronizador.kt`) | WorkManager | Convergem | WorkManager com fila Room idempotente | Padrão `SyncWorker` | Sem `setInterval`/serviço permanente |
| Rede | Retrofit 3.0.0 + OkHttp 5.5.0 + kotlinx.serialization 1.11.0; Firestore **REST** sobre OkHttp (`dados/rede/FirestoreRest.kt`) | Retrofit/OkHttp | Convergem | OkHttp/Retrofit para Cloud Functions; SDK Firestore para leituras com listener | — | Ver "Backend" |
| Firebase | BoM 34.4.0: auth, firestore (listeners em `SessoesVivas.kt:57`), messaging; app Firebase secundária nomeada para Auth/Firestore | — | — | BoM 34.4.0: auth, firestore, storage, messaging, functions | Padrão de inicialização sem `google-services.json` em código | Projecto Kupila é **outro** (ver §3) |
| Autenticação | Firebase Auth (identitytoolkit/securetoken REST + SDK); login por identificador do PDV | E-mail/senha (spec) | Divergem no identificador | Firebase Auth **e-mail + palavra-passe** (secção 8); reset oficial | Não copiar formulário NIP do PDV | Origem Kupila usa e-mail |
| Segurança local | `Cofre`: AES-256-GCM com chave no Android Keystore (`dados/seguranca/Cofre.kt`); biometria 1.1.0 opcional | Keystore | Convergem | Mesmo padrão `Cofre`, alias próprio | Adaptar | — |
| OCR | ML Kit text-recognition 16.0.1 + document scanner 16.0.0; `PickVisualMedia` | OCR (spec) | — | ML Kit on-device (substitui Tesseract.js da origem) | Padrão `LeitorFaturas` | OCR só prepara evidência |
| Push | FCM (`sync/ServicoPush.kt`, canal `pdvd_imediatos`) | Push (spec) | — | FCM com canais próprios Kupila | Adaptar | — |
| Documentos | `dados/factura/EmissorFacturas.kt` (facturas) | — | — | `PdfDocument` + Bitmap JPG + `FileProvider` | Ideia, não código | Recibos/fichas (secção 24) |
| Testes | JUnit 4.13.2, coroutines-test, MockWebServer; ~120 ficheiros de teste; logs `BUILD SUCCESSFUL` | — | — | Igual + testes de domínio financeiro | — | — |
| Backend PDV | Firebase `posto-de-vendas`; `BuildConfig.MODO="spark"`: app escreve na caixa de entrada e o **Central (navegador da direcção)** processa; `functions/` Node 22 + firebase-functions 4.5 preparado para Blaze (`MODO="api"`) | Spec | — | **Não reutilizável tal como está**: delegar autoridade a um navegador viola 1.3 e 5 | Contrato `Central` (interface com implementação substituível) | Ver §4 |

## 3. Backend Kupila (origem)

- Firebase projecto `pdv-pro-313f5` (`Kupila%201.0.239.html:2915-2923`), Web compat (Auth, Firestore com IndexedDB, Storage). Projecto **distinto** do PDV Digital (`posto-de-vendas`).
- **Não foi encontrado** em nenhuma pasta acessível: `firestore.rules`/`storage.rules` do `pdv-pro-313f5`, Cloud Functions, integração de provedor de pagamento, configuração de e-mail/SMS. A existência de colecções no cliente não comprova serviço.
- Pagamentos na origem (`kupila-payments-v2`, 15814-15970): referência gerada no cliente (`generatePaymentReference`, `Math.random`, 15829), `paymentIntents` escrito pelo cliente (15903), estados esperados de um processador inexistente (`AWAITING_PROVIDER_CONFIGURATION`, `VERIFYING`, `RECEIPT_MATCHED`…, 15868). OTP local conclui reserva e cria `payoutReleaseRequests` por batch do cliente (15948-15965). → Inconsistências C e H do prompt confirmadas.

## 4. Conflito a decidir (bloqueia apenas a activação financeira real)

O servidor tem de ser autoridade de pagamentos, reservas, OTP, reembolsos e liberação (secção 5). O padrão "Spark + Central no navegador" do PDV não serve (admin teria de manter um navegador aberto; 1.3). A tecnologia coerente com a infraestrutura verificada é **Cloud Functions for Firebase (Node 22)** no projecto `pdv-pro-313f5`, o que exige **plano Blaze** nesse projecto e um **provedor de pagamentos contratado** (referência/Multicaixa Express). Ver `docs/decisoes.md` D-01..D-04.

Enquanto não houver decisão: o app usa a interface `ServicoCentral` com implementação real (Functions) e implementação **de ensaio isolada e identificada** (emulador/fixtures), nunca apresentada como integração.

## 5. Por verificar
- Estado remoto do PDV (sem git). Regras reais publicadas do `pdv-pro-313f5`. Dados reais existentes (contagens) — requer acesso autorizado. Contrato com provedor de pagamentos.
