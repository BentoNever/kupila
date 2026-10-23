# Riscos das regras Firestore de produção (Kupila 1.0.188)

Cópia lida (só leitura) em `backend/producao-actual/cloud.firestore__firestore.rules`, publicada a 2026-09-02.
Não foram alteradas (decisão D-29): a app HTML continua em uso e depende delas.

| Risco | Onde | Efeito possível | Mitigação no Android | Correcção definitiva |
|---|---|---|---|---|
| Participante altera campos de estado da reserva | `reservations` update permite ao Cliente/Proprietário mudar tudo excepto ids e montantes | Marcar `status`/`payoutStatus` sem decisão válida | O app só faz transições válidas; o Administrador revê antes de liberar/reembolsar | Regras com transições explícitas (proposta em `backend/proposta/`) ou Functions (Blaze) |
| Dono altera `paymentIntents` próprios (excepto montante/referência) | `paymentIntents` update | Escrever `status: COMPLETED` na própria intenção | Publicação/reserva só acontece quando o **Administrador** confirma e grava a reserva/produto; um `COMPLETED` sem reserva/produto publicado não tem efeito | Estado só pelo Administrador/backend |
| Proprietário escreve o próprio produto com qualquer `status` | `products` create/update pelo dono | Publicar sem pagar a taxa | O app do Proprietário nunca cria `products` directamente (usa `listingDrafts` + pagamento); o Administrador vê anúncios activos sem pagamento nas pendências | Regra: dono só cria com `status == 'PENDING_PAYMENT'` |
| OTP em claro na reserva (registos antigos) | `reservations.otp` legível pelo dono | Dono valida sem vistoria | Novas reservas: só `otpHash` PBKDF2 com sal | Segredo fora do documento legível |
| `activeSessions` sem validação de campos | create/update/delete pelo titular | Sem impacto em terceiros | — | Validação de campos e hora do servidor |
| Auditoria só para Administrador no HTML | `cloudRecordAudit` só grava se isManager | Acções de outros perfis não ficam no servidor | O Android grava auditoria para todos os perfis (permitido pelas regras: `actorId == uid`) | — |
