// Abre o modal de pagamento da origem com uma intenção fictícia (só para captura de referência visual).
window.__checkout = function () {
  const q = (i, v) => { const e = document.getElementById(i); if (e) e.innerText = v; };
  q('checkout-title', 'Pagamento do sinal da reserva'); q('checkout-amount-label', 'Sinal completo da reserva');
  q('checkout-amount', '9 250 000 AOA'); q('ref-amount', '9 250 000 AOA'); q('ref-code', '122 346 623'); q('payment-entity', '00123');
  const ex = document.getElementById('checkout-deposit-explanation');
  if (ex) ex.innerHTML = '<b>Reserva em 2 etapas:</b> pague 9 250 000 AOA e anexe o comprovativo. Só depois da referência e do valor serem confirmados pelo backend o produto será reservado. Base do negócio: 185 000 000 AOA. Comissão Kupila: <b>1 387 500 AOA</b>. Líquido em custódia: <b>7 862 500 AOA</b>.';
  q('checkout-reservation-validity', 'A reserva só nasce depois do pagamento integral do sinal. Após confirmação, será válida por 24 horas; a expiração não cria estorno automático.');
  const m = document.getElementById('modal-checkout'); m.classList.remove('hidden'); m.style.display = 'flex';
  return 'ok';
};
window.__passo2 = function () {
  document.getElementById('payment-step-1').classList.add('hidden');
  document.getElementById('payment-step-2').classList.remove('hidden');
  return 'ok';
};
window.__mostrar = function (id) { const m = document.getElementById(id); m.classList.remove('hidden'); m.style.display = 'flex'; return 'ok'; };
