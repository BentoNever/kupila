package ao.kupila.gestaonegocios.ui.checkout

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dados.remoto.Origem
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.entrada.formatarData
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.KpCabecalhoModal
import ao.kupila.gestaonegocios.ui.kp.KpEntrada
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.gradiente
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque

private fun aoa(v: Any?): String = (v?.toString()?.toBigDecimalOrNull())?.let { FormatoAoa.formatar(Dinheiro.de(it)) } ?: "0 AOA"

/** MODAL 2: PAGAMENTO EM DUAS ETAPAS + COMPROVATIVO (`#modal-checkout`, HTML 7464-7508; `renderPaymentIntent`, 15914). */
@Composable
fun CheckoutEcra(vm: CheckoutViewModel, aoVoltar: () -> Unit, aoConcluido: (tipo: String, reservaId: String?, listingId: String) -> Unit, aoRegras: () -> Unit = {}) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val marca = LocalMarca.current
    val ctx = LocalContext.current
    var passo by rememberSaveable { mutableStateOf(1) }
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::comprovativo) }
    val i = e.intent
    val reserva = e.tipo != "LISTING"
    Column {
        Box(Modifier.fillMaxWidth().background(gradiente(135f, marca.c900, marca.c800))) {
            KpCabecalhoModal("Pagamento seguro", if (reserva) "Pagamento do sinal da reserva" else "Pagamento para publicar anúncio", aoVoltar, fundo = Color.Transparent, corKicker = Color.White)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Montante e estado
            Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.slate50, borda = Kp.slate900).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(if (reserva) "Sinal completo da reserva" else "Taxa + destaques seleccionados", 8.5f, Kp.slate500, 700, maiusculas = true)
                    T(aoa(i["amountText"] ?: i["amount"]), 18f, marca.c700, 900, Modifier.padding(top = 6.dp))
                }
                val (rot, f, c) = when (e.fase) {
                    FaseCheckout.VERIFICACAO -> Triple("Comprovativo em verificação", Kp.amber100, Kp.amber800)
                    FaseCheckout.CONCLUIDO -> Triple("Pagamento confirmado", marca.c100, marca.c800)
                    FaseCheckout.REJEITADO -> Triple("Comprovativo não aceite", Kp.red50, Kp.red700)
                    FaseCheckout.EXPIRADO -> Triple("Referência expirada", Kp.slate100, Kp.slate600)
                    else -> Triple("Aguardando pagamento", Kp.blue50, Kp.blue700)
                }
                T(rot, 8.5f, c, 700, Modifier.sup(8.dp, fundo = f).padding(horizontal = 8.dp, vertical = 5.dp))
            }
            // Explicação
            Box(Modifier.fillMaxWidth().sup(16.dp, fundo = marca.c50, borda = marca.c100).padding(9.dp)) {
                androidx.compose.material3.Text(buildAnnotatedString {
                    val b = SpanStyle(fontWeight = FontWeight.Bold, color = marca.c900)
                    if (reserva) {
                        withStyle(b) { append("Reserva em 2 etapas:") }
                        append(" pague ${aoa(i["amount"])} e anexe o comprovativo. Só depois da referência e do valor serem confirmados pelo backend o produto será reservado.")
                        if ((i["dealBaseSnapshot"] as? Number)?.toDouble() ?: 0.0 > 0) append(" Base do negócio: ${aoa(i["dealBaseSnapshot"])}.")
                        append(" Comissão Kupila: "); withStyle(b) { append(aoa(i["commissionAmount"])) }
                        append(". Líquido em custódia: "); withStyle(b) { append(aoa(i["ownerNetAmount"])) }; append(".")
                    } else {
                        withStyle(b) { append("Publicação condicionada ao pagamento.") }
                        append(" Taxa de anúncio: ${aoa(i["listingFeeAmount"])}. Serviços promocionais: ${aoa(i["promotionFeeAmount"])}. Total: ${aoa(i["amount"])}. O produto não entra no catálogo antes da confirmação.")
                    }
                }, color = marca.c900, fontSize = 9.sp, lineHeight = 16.sp)
            }
            Row(Modifier.fillMaxWidth().height(29.dp).sup(15.dp, fundo = Color.White, borda = marca.c100).toque(aoTocar = aoRegras),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                FaIcone(Fa.SCALE_BALANCED, 8.5f, marca.c700); Spacer(Modifier.width(6.dp)); T("Regras de intermediação e comissões", 8.5f, marca.c700, 700)
            }
            when (e.fase) {
                FaseCheckout.CARREGAR -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = marca.c600); Spacer(Modifier.width(8.dp)); T("A abrir o pagamento…", 9f, Kp.slate500) }
                FaseCheckout.INEXISTENTE -> Aviso("Pagamento não encontrado. Volte ao produto e tente de novo.", Kp.red50, Kp.red700)
                FaseCheckout.EXPIRADO -> Aviso("Por segurança cada referência vale 30 minutos. Volte e gere uma nova; nada foi cobrado em duplicado.", Kp.amber50, Kp.amber800)
                FaseCheckout.CONCLUIDO -> {
                    Passo(1, if (reserva) "Reserva confirmada" else "Pagamento confirmado", if (reserva) "O código OTP está nas suas reservas." else "O anúncio entra no catálogo.")
                    KpBotao(if (reserva) "Ver a reserva e o código OTP" else "Ver o anúncio", { aoConcluido(e.tipo, i["reservationId"] as? String, i["listingId"] as? String ?: "") },
                        icone = Fa.CIRCLE_CHECK, altura = 37.dp, raio = 12.dp, tamanho = 9f)
                }
                FaseCheckout.VERIFICACAO -> {
                    Passo(2, "Comprovativo recebido", if (i["status"] == "RECEIPT_MATCHED") "A referência coincide. Falta só a confirmação do recebimento; esta página actualiza-se sozinha."
                        else "A equipa Kupila está a validar o comprovativo. Pode sair: avisamos quando estiver confirmado.")
                    KpBotao("Continuar a explorar", aoVoltar, fundo = Color.White, cor = Kp.slate700, borda = Kp.slate200, altura = 33.dp, raio = 12.dp, tamanho = 9f, sombra = false)
                }
                FaseCheckout.PAGAR, FaseCheckout.REJEITADO -> {
                    if (e.fase == FaseCheckout.REJEITADO) Aviso((i["rejectionReason"] as? String).orEmpty().ifBlank { "O comprovativo não foi aceite." } +
                        " Pode enviar outro comprovativo; não precisa de pagar de novo se já pagou.", Kp.red50, Kp.red700)
                    if (passo == 1) {
                        Passo(1, if (reserva) "Pague o sinal completo" else "Pague a publicação", "Use exactamente a entidade, referência e montante gerados pelo Kupila.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metodo("Referência", "ATM / Homebanking", Kp.slate900, e.metodo == "REF", Modifier.weight(1f)) { vm.metodo("REF") }
                            Metodo("MC Express", "Quando suportado pelo provedor", Color(0xFFE11D48), e.metodo == "EXPRESS", Modifier.weight(1f)) { vm.metodo("EXPRESS") }
                        }
                        Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.slate950).padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            LinhaMono("Entidade", (i["entity"] as? String).orEmpty().ifBlank { "NÃO CONFIGURADA" }, Color.White)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinhaMono("Referência", e.referencia.chunked(3).joinToString(" "), Kp.yellow400, Modifier.weight(1f))
                                Spacer(Modifier.width(6.dp)); FaIcone(Fa.COPY, 9f, Kp.slate400, Modifier.toque { copiar(ctx, e.referencia) }.padding(2.dp), regular = true)
                            }
                            LinhaMono("Montante", aoa(i["amount"]), Color.White)
                        }
                        if ((i["entity"] as? String).isNullOrBlank()) Aviso("Configuração financeira indisponível neste momento. Nenhuma cobrança será iniciada até a entidade ser sincronizada.", Kp.amber50, Kp.amber800)
                        if (e.metodo == "EXPRESS") KpEntrada(e.telefoneExpress, vm::telefone, dica = "Número Multicaixa Express usado", altura = 33.dp, tamanhoTexto = 9f, prefixo = "+244", teclado = KeyboardType.Phone)
                        Origem.ms(i["expiresAt"])?.let { T("Referência válida até ${formatarData(it)}", 8f, Kp.slate500) }
                        KpBotao("Já paguei — submeter comprovativo", { passo = 2 }, icone = Fa.CHECK, altura = 37.dp, raio = 12.dp, tamanho = 9f)
                    } else {
                        Passo(2, "Submeta o comprovativo", "O app lê a referência; o backend confirma referência e valor integral antes de reservar.")
                        Row(Modifier.fillMaxWidth().tracejado(Kp.slate300).toque { escolher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).sup(12.dp, fundo = Color.White, borda = marca.c600), contentAlignment = Alignment.Center) {
                                if (e.aLer) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = marca.c600) else FaIcone(Fa.RECEIPT, 14f, marca.c700)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column { T(if (e.comprovativo != null) "Comprovativo seleccionado" else "Adicionar comprovativo", 10f, Kp.slate900, 700); T("JPG, PNG ou WebP · máximo 4 MB", 8f, Kp.slate500, Modifier.padding(top = 4.dp)) }
                        }
                        val (txt, cor) = when {
                            e.aLer -> "A ler a referência do comprovativo…" to Kp.slate600
                            e.aEnviar -> "A submeter…" to Kp.slate600
                            e.comprovativo != null && e.confere -> "Referência ${e.referenciaLida} confirmada: a submeter automaticamente." to marca.c700
                            e.comprovativo != null && e.referenciaLida.isNotBlank() -> "Lemos a referência ${e.referenciaLida}, diferente da esperada (${e.referencia}). Pode submeter para validação manual." to Kp.amber800
                            e.comprovativo != null -> "Não encontrámos a referência na imagem. Pode submeter para validação manual." to Kp.amber800
                            else -> "Seleccione o comprovativo para validação automática." to Kp.slate600
                        }
                        Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.slate50, borda = Kp.slate600).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            FaIcone(Fa.WAND_MAGIC_SPARKLES, 8.5f, Kp.slate600); Spacer(Modifier.width(6.dp)); T(txt, 8.5f, cor, linha = 13f)
                        }
                        e.erro?.let { Aviso(it, Kp.red50, Kp.red700) }
                        KpBotao("Submeter e validar automaticamente", { vm.enviar(manual = !e.confere) }, ocupado = e.aEnviar, activo = e.comprovativo != null, altura = 37.dp, raio = 12.dp, tamanho = 9f)
                        Row(Modifier.fillMaxWidth().toque { passo = 1 }, horizontalArrangement = Arrangement.Center) { T("← Voltar aos dados de pagamento", 8.5f, Kp.slate500, 700) }
                    }
                }
            }
            Aviso(if (reserva) "A reserva só nasce depois do pagamento integral do sinal. Após confirmação, será válida pelo prazo configurado; a expiração não cria estorno automático."
                else "Taxas de publicação e promoção são processadas antes do anúncio entrar no catálogo.", Color(0xFFFFFBEB), Kp.amber900)
        }
    }
}

@Composable
private fun Passo(n: Int, titulo: String, sub: String) {
    val marca = LocalMarca.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).sup(28.dp, fundo = marca.c700, forma = CircleShape), contentAlignment = Alignment.Center) { T("$n", 10f, Color.White, 900) }
        Column { T(titulo, 9.5f, Kp.slate900, 700); T(sub, 8.5f, Kp.slate500, Modifier.padding(top = 2.dp), linha = 13f) }
    }
}

@Composable
private fun Metodo(titulo: String, sub: String, cor: Color, on: Boolean, modifier: Modifier, aoTocar: () -> Unit) {
    val marca = LocalMarca.current
    Column(modifier.height(50.dp).sup(12.dp, fundo = if (on) marca.c50 else Color.White, borda = if (on) marca.c600 else Kp.slate200, bordaLargura = 1.5.dp).toque(aoTocar = aoTocar)
        .padding(horizontal = 9.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
        T(titulo, 10f, cor, 700); T(sub, 7.5f, Kp.slate500, Modifier.padding(top = 3.dp), max = 1)
    }
}

@Composable
private fun LinhaMono(k: String, v: String, cor: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        androidx.compose.material3.Text(k, Modifier.weight(1f), color = Kp.slate400, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
        androidx.compose.material3.Text(v, color = cor, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Aviso(texto: String, fundo: Color, cor: Color) {
    Box(Modifier.fillMaxWidth().sup(12.dp, fundo = fundo, borda = cor.copy(.18f)).padding(9.dp)) { T(texto, 8.5f, cor, linha = 13f) }
}

private fun Modifier.tracejado(cor: Color) = this.drawBehind {
    val r = 16.dp.toPx()
    drawRoundRect(cor, cornerRadius = CornerRadius(r, r), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

private fun copiar(ctx: Context, texto: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Referência Kupila", texto))
}
