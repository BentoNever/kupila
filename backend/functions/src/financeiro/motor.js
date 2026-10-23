'use strict';
/**
 * Motor financeiro do Central — equivalente exacto de
 * android/app/src/main/java/ao/kupila/gestaonegocios/dominio/financeiro/MotorFinanceiro.kt.
 * Dinheiro em cêntimos (BigInt); percentagens em pontos percentuais com 6 casas (BigInt × 10^6).
 * Pontos de arredondamento iguais ao Kotlin (D-10): cada montante fixado uma vez, HALF_UP.
 * Os casos de contratos/casos-financeiros.json são verificados nos dois lados.
 */

const P6 = 1000000n;

/** HALF_UP para divisão inteira com sinal. */
function divHalfUp(n, d) {
  if (d < 0n) { n = -n; d = -d; }
  const neg = n < 0n;
  const a = neg ? -n : n;
  const q = a / d, r = a % d;
  const res = r * 2n >= d ? q + 1n : q;
  return neg ? -res : res;
}

function decimal(texto, casas) {
  const s = String(texto).trim().replace(',', '.');
  if (!/^-?\d+(\.\d+)?$/.test(s)) throw new Error(`Número inválido: ${texto}`);
  const neg = s.startsWith('-');
  const [i, f = ''] = s.replace('-', '').split('.');
  const escala = 10n ** BigInt(casas);
  let v;
  if (f.length <= casas) v = BigInt(i) * escala + BigInt((f + '0'.repeat(casas)).slice(0, casas) || '0');
  else v = divHalfUp(BigInt(i + f), 10n ** BigInt(f.length - casas));
  return neg ? -v : v;
}

const cents = (t) => decimal(t, 2);
const pct = (t) => decimal(t, 6);

function textoCents(c) {
  const neg = c < 0n, a = neg ? -c : c;
  return `${neg ? '-' : ''}${a / 100n}.${String(a % 100n).padStart(2, '0')}`;
}

/** valor × p% fixado em cêntimos (o intermédio Kotlin a 10 casas é exacto para p ≤ 6 casas). */
function aplicarPercentagem(c, p6) { return divHalfUp(c * p6, 100n * P6); }

const DIAS_PRECO = { DAILY: 1, WEEKLY: 7, BIWEEKLY: 15, MONTHLY: 30, ANNUAL: 360, OTHER: null };
const DIAS_DURACAO = { DAY: 1, WEEK: 7, BIWEEK: 15, MONTH: 30, YEAR: 360, CUSTOM_UNIT: null };

function prazoNormalizado(prazo, qtd, unidade) {
  if (prazo === '3_MONTHS') return { qtd: 3, unidade: 'MONTH', dias: 90 };
  if (prazo === '6_MONTHS') return { qtd: 6, unidade: 'MONTH', dias: 180 };
  if (prazo === '12_MONTHS') return { qtd: 12, unidade: 'MONTH', dias: 360 };
  const d = DIAS_DURACAO[unidade];
  return { qtd: Number(qtd || 0), unidade, dias: d == null ? null : d * Number(qtd || 0) };
}

/** `RegrasAluguer.termos` — mesmas validações e mensagens. */
function termosAluguer(c) {
  const precoC = cents(c.preco);
  const n = prazoNormalizado(c.prazo, c.prazoQuantidade, c.prazoUnidade);
  const personalizado = !['3_MONTHS', '6_MONTHS', '12_MONTHS'].includes(c.prazo);
  const outra = c.unidade === 'OTHER';
  let erro = null;
  if (!(precoC > 0n)) erro = 'Informe um preço por unidade superior a zero.';
  else if (!(c.unidade in DIAS_PRECO)) erro = 'Unidade de preço desconhecida.';
  else if (outra && !String(c.nomeUnidade || '').trim()) erro = 'Informe o nome da unidade personalizada do preço.';
  else if (outra && (!personalizado || n.unidade !== 'CUSTOM_UNIT')) erro = 'Para outra unidade de preço, use “Outro período específico” e “Mesma unidade personalizada”.';
  else if (!outra && personalizado && n.unidade === 'CUSTOM_UNIT') erro = 'A unidade personalizada do período só pode ser usada com outra unidade de preço.';
  else if (personalizado && !(Number.isInteger(n.qtd) && n.qtd >= 1)) erro = 'Informe uma quantidade mínima inteira e superior a zero.';
  else if (!outra && (n.dias == null || n.dias < DIAS_PRECO[c.unidade])) erro = 'O tempo mínimo não pode ser inferior à unidade usada para cobrar o preço.';
  else if (outra && c.unidadesPorMes != null && !(pct(c.unidadesPorMes) > 0n)) erro = 'A equivalência mensal da unidade personalizada deve ser positiva.';
  if (erro) return { erro };
  const dPreco = DIAS_PRECO[c.unidade];
  const unidades = outra ? n.qtd : Math.ceil(n.dias / dPreco);
  const base = precoC * BigInt(unidades);
  let mensal = null;
  if (dPreco == null) {
    if (c.unidadesPorMes != null) mensal = divHalfUp(precoC * pct(c.unidadesPorMes), P6);
  } else {
    // Kotlin: preço × 30 / dias a 10 casas (HALF_UP) e depois a 2 casas (HALF_UP).
    const dez = divHalfUp(precoC * 100000000n * 30n, BigInt(dPreco));   // unidades de 1e-10 AOA
    mensal = divHalfUp(dez, 100000000n);
  }
  return { unidades, base, mensal, dias: n.dias };
}

/**
 * @param e { modalidade, preco, unidade?, nomeUnidade?, prazo?, prazoQuantidade?, prazoUnidade?, unidadesPorMes?,
 *            sinalVenda, mesesSinal, comissao, taxaAnuncio, percentagensPromocao, promocoes: [códigos],
 *            isencoes?: { comissao, taxaAnuncio, promocoes, motivo } }
 */
function calcular(e) {
  const problemas = [];
  const preco = cents(e.preco);
  const isen = e.isencoes || {};
  const pc = pct(e.comissao), pt = pct(e.taxaAnuncio), ps = pct(e.sinalVenda);
  const dentro = (p) => p >= 0n && p <= 100n * P6;
  if (!(preco > 0n)) problemas.push({ tipo: 'PARAMETRO', mensagem: 'O preço deve ser superior a zero.' });
  if (!dentro(pc)) problemas.push({ tipo: 'CONFIGURACAO', mensagem: 'Comissão fora do intervalo 0–100%.' });
  if (!dentro(pt)) problemas.push({ tipo: 'CONFIGURACAO', mensagem: 'Taxa de anúncio fora do intervalo 0–100%.' });
  const pp = e.percentagensPromocao || { FEATURED: '0.25', TOP_SPONSORED: '0.50', PRIORITY: '0.35' };
  for (const [k, v] of Object.entries(pp)) if (!dentro(pct(v))) problemas.push({ tipo: 'CONFIGURACAO', mensagem: `Percentagem de ${k} fora de 0–100%.` });

  let base = 0n, sinal = 0n, baseComissao, termos = null;
  if (e.modalidade === 'VENDA') {
    if (!(ps > 0n) || !dentro(ps)) problemas.push({ tipo: 'CONFIGURACAO', mensagem: 'Sinal de venda deve estar entre 0% (exclusive) e 100%.' });
    base = preco; sinal = aplicarPercentagem(preco, ps); baseComissao = 'SINAL';
  } else if (e.modalidade === 'ARRENDAMENTO') {
    baseComissao = 'CONTRATO_MINIMO';
    const m = Number(e.mesesSinal);
    if (!(Number.isInteger(m) && m >= 1 && m <= 12)) problemas.push({ tipo: 'CONFIGURACAO', mensagem: 'Meses de sinal devem estar entre 1 e 12.' });
    const t = termosAluguer(e);
    if (t.erro) problemas.push({ tipo: 'ALUGUER', mensagem: t.erro });
    else {
      termos = t; base = t.base;
      if (t.mensal == null) problemas.push({ tipo: 'ALUGUER', mensagem: `A unidade “${e.nomeUnidade}” não tem equivalência mensal declarada; o sinal em meses não pode ser calculado.` });
      else sinal = t.mensal * BigInt(m);
    }
  } else {
    problemas.push({ tipo: 'PARAMETRO', mensagem: 'Modalidade desconhecida.' });
    baseComissao = 'SINAL';
  }

  const comissao = isen.comissao ? 0n : aplicarPercentagem(baseComissao === 'SINAL' ? sinal : base, pc);
  if ((isen.comissao || isen.taxaAnuncio || isen.promocoes) && !String(isen.motivo || '').trim())
    problemas.push({ tipo: 'PARAMETRO', mensagem: 'Isenção sem motivo registado.' });

  const estruturaOk = !problemas.some((p) => p.tipo === 'ALUGUER') && preco > 0n;
  if (estruturaOk) {
    if (!(sinal > 0n)) problemas.push({ tipo: 'CONFIGURACAO', mensagem: 'O sinal calculado deve ser superior a zero.' });
    if (sinal > base) problemas.push({ tipo: 'CONFIGURACAO', mensagem: 'O sinal excede a base do negócio.' });
    if (comissao < 0n || comissao > sinal) problemas.push({ tipo: 'CONFIGURACAO', mensagem: 'A comissão excede o sinal. A regra administrativa precisa de revisão.' });
  }

  const taxa = isen.taxaAnuncio ? 0n : aplicarPercentagem(preco, pt);
  const promocoes = {};
  for (const codigo of e.promocoes || []) promocoes[codigo] = isen.promocoes ? 0n : aplicarPercentagem(preco, pct(pp[codigo] || '0'));
  const totalPromo = Object.values(promocoes).reduce((a, b) => a + b, 0n);
  const publicacao = taxa + totalPromo;
  const liquido = sinal - comissao, saldo = base - sinal;

  return {
    valido: problemas.length === 0,
    problemas,
    baseComissao,
    unidadesCobranca: termos ? termos.unidades : null,
    centimos: { base, sinal, comissao, liquido, saldo, taxa, promocoes, publicacao,
      liquidoPrevisto: liquido + saldo - publicacao, reembolso: liquido, receitaEstimada: comissao + publicacao },
  };
}

/** Versão serializável (texto com 2 casas) para snapshots e respostas. */
function resumo(e) {
  const r = calcular(e);
  const c = r.centimos;
  const promocoes = Object.fromEntries(Object.entries(c.promocoes).map(([k, v]) => [k, textoCents(v)]));
  return {
    valido: r.valido, problemas: r.problemas, baseComissao: r.baseComissao, unidadesCobranca: r.unidadesCobranca,
    base: textoCents(c.base), sinal: textoCents(c.sinal), comissao: textoCents(c.comissao), liquido: textoCents(c.liquido),
    saldo: textoCents(c.saldo), taxa: textoCents(c.taxa), promocoes, publicacao: textoCents(c.publicacao),
    liquidoPrevisto: textoCents(c.liquidoPrevisto), reembolso: textoCents(c.reembolso), receitaEstimada: textoCents(c.receitaEstimada),
  };
}

module.exports = { calcular, resumo, termosAluguer, cents, pct, textoCents, divHalfUp, aplicarPercentagem };
