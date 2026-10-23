# Princípios do produto (requisitos permanentes do utilizador, 2026-10-23)

1. **Zero burocracia — o app é uma solução, não um formulário.** Cada fluxo é medido pelo número de toques; o objectivo é reduzir a
   intervenção humana ao mínimo possível (meta indicada pelo utilizador: ~99,98%). Regras práticas:
   - Preencher sozinho tudo o que já se sabe (perfil, residência, contactos, recebimento usado antes, preferências, categoria por
     título, subcategoria provável, município pela província, unidade/prazo mais comuns).
   - Guardar sozinho (rascunho contínuo), retomar sozinho, sincronizar sozinho, validar enquanto se escreve, nunca apagar o que foi feito.
   - Ler sozinho (OCR da referência e do valor; submeter automaticamente quando a referência coincide — `AUTO_REFERENCE_MATCH` da origem).
   - Decidir sozinho o que é determinístico (expirações, conflitos, estados, cálculos, alertas, duplicados exactos); pedir decisão humana
     **só** onde a lei/as regras a exigem: pagamento consciente, aceite, vistoria/OTP, decisão de reembolso contestado, confirmação de
     dinheiro recebido sem provedor. Mesmo aí: um toque, com tudo pré-preparado e explicado.
2. **Rápido e leve**: arranque e ecrãs instantâneos a partir do cache local; nada de trabalho pesado na UI; listas virtualizadas; imagens
   redimensionadas; sem serviços permanentes; WorkManager só quando há trabalho; nenhum polling.
3. **Economia de quotas do Firestore (meta do utilizador: ~99,99% em leituras e escritas)**:
   - cache-first (persistência local do Firestore + Room), **sincronização delta** por `updatedAt` em vez de reler colecções;
   - **sem escutas permanentes**: só enquanto o ecrã que precisa delas está visível, e nunca em segundo plano;
   - leituras automáticas só com interacção recente (janela de toque, como a poupança de direcção do PDV);
   - escritas agrupadas (batch), sem regravar o que não mudou (diff), heartbeats espaçados;
   - **Modo poupança** igual ao do PDV Digital: pausa total da ligação com fila local e "Enviar agora" (janela curta que volta sozinha à pausa),
     contadores de pedidos evitados.
4. **Actualizador** igual ao do PDV Digital (GitHub `BentoNever/kupila`, manifesto, SHA-256, download retomável, assinatura, instalação sem
   confirmação quando o Android o permite, reabertura no mesmo ponto), sem tocar no Firestore.
5. **Moderno, elegante, inteligente e diferenciado**, sem sacrificar os limites abaixo.

## Limites que estes princípios não podem violar (prompt mestre)
- Nunca automatizar sucesso financeiro inexistente, consentimento, vistoria ou decisão controversa.
- Não reduzir segurança/privacidade para poupar toques (dados privados, OTP, papéis continuam protegidos).
- Não perder dados existentes da app HTML em uso (D-29).
