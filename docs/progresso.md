# Progresso — Kupila-Gestão de negócios (Android)

Leia isto primeiro ao retomar. Depois `docs/decisoes.md` e a matriz `docs/paridade-html-android.md`.
Ferramentas de consulta do HTML (não carregar o ficheiro inteiro):
- `python -I tools/html_query.py show <ini> <fim> [largura]` · `grep <regex>` · `fn <nome>` · `ids <regex>` · `cut <linha> <regex>`
- `python -I tools/html_ui_map.py <ini> <fim>` (campos/botões por modal)
- `python -I tools/html_index.py "Kupila%201.0.239.html" docs/origem-html` (regenera índices)

## Etapas (secção 39)

| Etapa | Âmbito | Estado |
|---|---|---|
| 1 | Auditoria HTML + PDV Digital + Finanças; matriz; stack | **Concluída a 1.ª passagem** (2026-10-23). Refinar MED (bloco 108) e OFF (bloco 89) ao implementar |
| 2 | Domínio, contratos, estados, segurança, motor financeiro; infra Android | **Base concluída**: domínio + motor (APK e Central coincidem em `contratos/casos-financeiros.json`), regras Firestore/Storage, Central (sessões, contas) testado no emulador |
| 3 | Autenticação/perfis/sessões, catálogo/detalhes, cadastro/edição, anexos, rascunhos, personalização | Em curso: login/cadastro/recuperação/conflito/inactividade, Explorar, Detalhe, Conta, FAQ, Políticas, Sobre, Simulador **compilam**. Falta: formulário Anunciar/Editar + rascunhos + anexos (Central `anuncios.guardar`), perfil do Proprietário, Room de rascunhos ligado, persistir última actividade para inactividade após morte do processo |
| 4 | Publicação paga, reservas, comprovativos/OCR, disponibilidade, custódia, OTP, liberação, reembolsos | Por iniciar (financeiro real BLQ D-01/D-02) |
| 5 | Administração, autorizações, pendências/lixeira, monitoramento, monetização, alertas, campanhas, testemunhos | Por iniciar |
| 6 | Simulador, documentos, manual/políticas, sync/recuperação, migração ensaiada, validação, APK | Por iniciar |

## Registo

- 2026-10-23 02:40 — Build: `powershell -File tools\gradle.ps1 <log> assembleDebug testDebugUnitTest` (28 testes JVM OK). Central: `powershell -File tools\central-testes.ps1` (3 unitários + 9 emulador OK). APK dev: `android/app/build/outputs/apk/debug/app-debug.apk` (`ao.kupila.gestaonegocios.dev`). Instalação no A54 falhou: dispositivo `offline` no adb (exige desbloquear/autorizar). Publicação GitHub: não existe repositório do Kupila; aguarda nome/visibilidade do utilizador.
- 2026-10-23 00:00 — Hash do HTML confirmado. Índices em `docs/origem-html/`. Auditoria de stack em `docs/auditoria-stack.md`. Kupila-Finanças = só especificação. Backend Kupila inexistente localmente → D-01..D-03.
