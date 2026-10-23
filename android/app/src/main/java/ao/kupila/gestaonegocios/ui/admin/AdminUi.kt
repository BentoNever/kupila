package ao.kupila.gestaonegocios.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.admin.AjustesAdminRepositorio
import ao.kupila.gestaonegocios.dados.admin.TestemunhosRepositorio
import ao.kupila.gestaonegocios.dados.admin.UtilizadoresRepositorio
import ao.kupila.gestaonegocios.dados.ajustes.AjustesRepositorio
import ao.kupila.gestaonegocios.dados.eventos.Conjunto
import ao.kupila.gestaonegocios.dados.eventos.Registo
import ao.kupila.gestaonegocios.dados.local.Geografia
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dominio.ajustes.AjustesPlataforma
import ao.kupila.gestaonegocios.dominio.ajustes.ValidacaoAjustes
import ao.kupila.gestaonegocios.dominio.duplicados.IdentidadeBem
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import ao.kupila.gestaonegocios.dominio.indicadores.Presenca
import ao.kupila.gestaonegocios.dominio.perfis.EstadoConta
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.ui.ajuda.EcraComTopo
import ao.kupila.gestaonegocios.ui.comum.Aviso
import ao.kupila.gestaonegocios.ui.comum.BotaoPrincipal
import ao.kupila.gestaonegocios.ui.comum.BotaoSecundario
import ao.kupila.gestaonegocios.ui.comum.Campo
import ao.kupila.gestaonegocios.ui.comum.CampoSenha
import ao.kupila.gestaonegocios.ui.comum.EstadoVazio
import ao.kupila.gestaonegocios.ui.comum.LinhaInfo
import ao.kupila.gestaonegocios.ui.comum.Seccao
import ao.kupila.gestaonegocios.ui.comum.Seletor
import ao.kupila.gestaonegocios.ui.comum.TipoAviso
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val conta: ContaActiva,
    val ajustes: AjustesRepositorio,
    private val ajustesAdmin: AjustesAdminRepositorio,
    private val utilizadores: UtilizadoresRepositorio,
    private val testemunhos: TestemunhosRepositorio,
    val geografia: Geografia,
) : ViewModel() {
    private fun lista(c: Conjunto) = conta.recursos.flatMapLatest { it?.eventos?.observar(c) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val users = lista(Conjunto.UTILIZADORES)
    val presenca = lista(Conjunto.PRESENCA)
    val testemunhosPendentes = lista(Conjunto.TESTEMUNHOS_PENDENTES)
    val mensagem = MutableStateFlow<String?>(null)
    val ocupado = MutableStateFlow(false)
    private val admin get() = conta.recursos.value!!.perfil
    val meuUid get() = conta.recursos.value?.perfil?.uid

    init { actualizar() }
    /** Utilizadores por delta (`updatedAt`): só o que mudou desde a última abertura. */
    fun actualizar() = viewModelScope.launch {
        conta.recursos.value?.eventos?.actualizar(listOf(Conjunto.UTILIZADORES, Conjunto.PRESENCA, Conjunto.TESTEMUNHOS_PENDENTES))
    }

    private fun accao(bloco: suspend () -> String) = viewModelScope.launch {
        if (ocupado.value) return@launch
        ocupado.value = true
        mensagem.value = runCatching { bloco() }.getOrElse { MensagensErro.de(it).takeIf { m -> m != MensagensErro.GENERICA } ?: (it.message ?: MensagensErro.GENERICA) }
        ocupado.value = false
        actualizar()
    }

    fun estado(uid: String, e: EstadoConta, motivo: String) = accao { utilizadores.alterarEstado(admin, uid, e.codigo, motivo.ifBlank { "Alteração administrativa." }); "Estado: ${e.rotulo}." }
    fun promover(uid: String, motivo: String) = accao { utilizadores.concederAdministrador(admin, uid, motivo); "Privilégios de Administrador concedidos." }
    fun criar(papel: Papel, nome: String, email: String, senha: String, tel: String, prov: String, mun: String, bairro: String) = accao {
        require(nome.isNotBlank() && email.contains('@') && senha.length >= 6) { "Indique nome, e-mail válido e palavra-passe com 6+ caracteres." }
        utilizadores.criar(admin, papel, EstadoConta.ACTIVA.codigo, nome, email, senha, tel, "", prov, mun, bairro, null); "Conta criada."
    }
    fun moderar(id: String, aprovado: Boolean) = accao { testemunhos.moderar(id, aprovado, admin); if (aprovado) "Testemunho publicado." else "Testemunho rejeitado." }
    fun guardar(a: AjustesPlataforma) = accao {
        val e = ValidacaoAjustes.validar(a)
        require(e.isEmpty()) { e.joinToString("\n") }
        ajustesAdmin.guardar(a, admin); "Ajustes guardados e aplicados a novos cálculos."
    }
}

private enum class Aba(val rotulo: String) { MONITOR("Monitor"), UTILIZADORES("Utilizadores"), AJUSTES("Ajustes"), TESTEMUNHOS("Testemunhos") }

@Composable
fun AdminEcra(vm: AdminViewModel, aoVoltar: () -> Unit, aoNegocios: () -> Unit, aoAnuncios: () -> Unit) {
    var aba by rememberSaveable { mutableStateOf(Aba.MONITOR) }
    val msg by vm.mensagem.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.mensagem.value = null } }
    EcraComTopo("Administração", aoVoltar) { pad ->
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Aba.entries) { a -> FilterChip(aba == a, { aba = a }, { Text(a.rotulo) }) }
                }
                when (aba) {
                    Aba.MONITOR -> Monitor(vm, aoNegocios, aoAnuncios)
                    Aba.UTILIZADORES -> Utilizadores(vm)
                    Aba.AJUSTES -> AjustesForm(vm)
                    Aba.TESTEMUNHOS -> Testemunhos(vm)
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Monitor(vm: AdminViewModel, aoNegocios: () -> Unit, aoAnuncios: () -> Unit) {
    val us by vm.users.collectAsStateWithLifecycle()
    val pr by vm.presenca.collectAsStateWithLifecycle()
    val agora = System.currentTimeMillis()
    val online = pr.count { (it.ms("lastSeenAt") ?: it.ms("updatedAt") ?: 0) > agora - Presenca.JANELA_ONLINE_MS }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Seccao("Agora") {
            LinhaInfo("Utilizadores online", online.toString(), destaque = true)
            LinhaInfo("Contas", us.size.toString())
            Papel.entries.forEach { p -> LinhaInfo(p.rotulo, us.count { Papel.canonico(it.s("role")) == p }.toString()) }
            LinhaInfo("Suspensas", us.count { it.s("status") == "SUSPENDED" }.toString())
        }
        BotaoPrincipal("Pendências financeiras", aoNegocios)
        BotaoSecundario("Anúncios e autorizações", aoAnuncios)
        BotaoSecundario("Actualizar dados", { vm.actualizar() })
    }
}

@Composable
private fun Utilizadores(vm: AdminViewModel) {
    val us by vm.users.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    var seleccionado by remember { mutableStateOf<Registo?>(null) }
    var criar by remember { mutableStateOf(false) }
    val filtrados = remember(us, q) {
        val t = IdentidadeBem.normalizar(q)
        us.filter { t.isBlank() || IdentidadeBem.normalizar(it.s("name") + " " + it.s("email") + " " + it.s("phonePrimary")).contains(t) }.sortedBy { it.s("name").lowercase() }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Campo(q, { q = it }, "Pesquisar nome, e-mail ou telefone") }
        item { BotaoSecundario("Criar conta", { criar = true }) }
        if (filtrados.isEmpty()) item { EstadoVazio("Sem resultados", "Ajuste a pesquisa.") }
        items(filtrados, key = { it.id }) { u ->
            Card(onClick = { seleccionado = u }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(u.s("name").ifBlank { u.s("email") }, fontWeight = FontWeight.SemiBold)
                    Text("${Papel.canonico(u.s("role"))?.rotulo ?: u.s("role")} · ${EstadoConta.entries.firstOrNull { it.codigo == u.s("status") }?.rotulo ?: u.s("status")} · ${u.s("email")}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    seleccionado?.let { u ->
        var motivo by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { seleccionado = null }, title = { Text(u.s("name").ifBlank { u.s("email") }) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinhaInfo("Telefone", u.s("phonePrimary"))
                LinhaInfo("Residência", listOf(u.s("neighborhood"), u.s("municipality"), u.s("province")).filter { it.isNotBlank() }.joinToString(", "))
                if (u.id == vm.meuUid) Aviso("Esta é a sua conta.", TipoAviso.INFO)
                else {
                    Campo(motivo, { motivo = it }, "Motivo (fica na auditoria)")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        EstadoConta.entries.filter { it.codigo != u.s("status") && it != EstadoConta.ARQUIVADA }.forEach { e ->
                            TextButton(enabled = !ocupado, onClick = { vm.estado(u.id, e, motivo); seleccionado = null }) { Text(e.rotulo) }
                        }
                    }
                    if (Papel.canonico(u.s("role")) != Papel.ADMINISTRADOR) TextButton(enabled = motivo.trim().length >= 5 && !ocupado, onClick = { vm.promover(u.id, motivo); seleccionado = null }) {
                        Text("Conceder Administrador")
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { seleccionado = null }) { Text("Fechar") } })
    }
    if (criar) {
        var papel by remember { mutableStateOf(Papel.CLIENTE) }
        var nome by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var senha by remember { mutableStateOf("") }
        var tel by remember { mutableStateOf("") }; var prov by remember { mutableStateOf("Luanda") }; var mun by remember { mutableStateOf("") }; var bairro by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { criar = false }, title = { Text("Nova conta") }, text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Row { listOf(Papel.CLIENTE, Papel.PROPRIETARIO).forEach { p -> FilterChip(papel == p, { papel = p }, { Text(p.rotulo) }, Modifier.padding(end = 6.dp)) } } }
                item { Campo(nome, { nome = it }, "Nome completo") }
                item { Campo(email, { email = it.trim() }, "E-mail", teclado = KeyboardType.Email) }
                item { CampoSenha(senha, { senha = it }, "Palavra-passe inicial") }
                item { Campo(tel, { tel = it.filter(Char::isDigit).take(9) }, "Telefone", teclado = KeyboardType.Phone) }
                item { Seletor("Província", vm.geografia.provincias.map { it to it }, prov, { prov = it; mun = "" }) }
                item { Seletor("Município", vm.geografia.municipios(prov).map { it to it }, mun.ifBlank { null }, { mun = it }) }
                item { Campo(bairro, { bairro = it }, "Bairro") }
            }
        }, confirmButton = { TextButton(onClick = { vm.criar(papel, nome, email, senha, tel, prov, mun, bairro); criar = false }) { Text("Criar") } },
            dismissButton = { TextButton(onClick = { criar = false }) { Text("Cancelar") } })
    }
}

@Composable
private fun AjustesForm(vm: AdminViewModel) {
    val a by vm.ajustes.ajustes.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val f = a.financeiro
    var sinal by rememberSaveable(a.versao) { mutableStateOf(f.sinalVenda.valor.toPlainString()) }
    var meses by rememberSaveable(a.versao) { mutableStateOf(f.mesesSinalAluguer.toString()) }
    var comissao by rememberSaveable(a.versao) { mutableStateOf(f.comissao.valor.toPlainString()) }
    var taxa by rememberSaveable(a.versao) { mutableStateOf(f.taxaAnuncio.valor.toPlainString()) }
    var destaque by rememberSaveable(a.versao) { mutableStateOf(f.promocoes[Promocao.DESTAQUE]?.valor?.toPlainString().orEmpty()) }
    var top by rememberSaveable(a.versao) { mutableStateOf(f.promocoes[Promocao.TOP]?.valor?.toPlainString().orEmpty()) }
    var prioridade by rememberSaveable(a.versao) { mutableStateOf(f.promocoes[Promocao.PRIORIDADE]?.valor?.toPlainString().orEmpty()) }
    var dias by rememberSaveable(a.versao) { mutableStateOf(f.duracaoPromocaoDias.toString()) }
    var horas by rememberSaveable(a.versao) { mutableStateOf((a.prazoReservaMinutos / 60.0).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }) }
    var inact by rememberSaveable(a.versao) { mutableStateOf(a.inactividadeMinutos.toString()) }
    var entidade by rememberSaveable(a.versao) { mutableStateOf(a.entidadePagamento) }
    fun pct(s: String) = ValidacaoAjustes.percentagem(s)
    val novo = runCatching {
        a.copy(financeiro = f.copy(sinalVenda = pct(sinal)!!, mesesSinalAluguer = meses.toInt(), comissao = pct(comissao)!!, taxaAnuncio = pct(taxa)!!,
            promocoes = mapOf(Promocao.DESTAQUE to pct(destaque)!!, Promocao.TOP to pct(top)!!, Promocao.PRIORIDADE to pct(prioridade)!!), duracaoPromocaoDias = dias.toInt()),
            prazoReservaMinutos = (horas.replace(',', '.').toDouble() * 60).toLong(), inactividadeMinutos = inact.toInt(), entidadePagamento = entidade.trim())
    }.getOrNull()
    val erros = novo?.let(ValidacaoAjustes::validar) ?: listOf("Preencha todos os campos com números válidos.")
    val incompat = novo?.let { ValidacaoAjustes.prazosAluguerIncompativeis(it.financeiro) }.orEmpty()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Seccao("Intermediação") {
                Campo(sinal, { sinal = it }, "Sinal da venda (%)", teclado = KeyboardType.Decimal)
                Campo(meses, { meses = it.filter(Char::isDigit) }, "Meses de sinal no aluguer", teclado = KeyboardType.Number)
                Campo(comissao, { comissao = it }, "Comissão sobre o sinal (%)", teclado = KeyboardType.Decimal)
                Campo(taxa, { taxa = it }, "Taxa de anúncio (% do preço)", teclado = KeyboardType.Decimal)
            }
        }
        item {
            Seccao("Promoções") {
                Campo(destaque, { destaque = it }, "Destaque Premium (%)", teclado = KeyboardType.Decimal)
                Campo(top, { top = it }, "Top Patrocinados (%)", teclado = KeyboardType.Decimal)
                Campo(prioridade, { prioridade = it }, "Prioridade (%)", teclado = KeyboardType.Decimal)
                Campo(dias, { dias = it.filter(Char::isDigit) }, "Duração (dias)", teclado = KeyboardType.Number)
            }
        }
        item {
            Seccao("Operação") {
                Campo(horas, { horas = it }, "Prazo da reserva (horas)", teclado = KeyboardType.Decimal)
                Campo(inact, { inact = it.filter(Char::isDigit) }, "Bloqueio por inactividade (min)", teclado = KeyboardType.Number)
                Campo(entidade, { entidade = it.take(20) }, "Entidade de pagamento", ajuda = "Vazio = em configuração (os pagadores são avisados).")
            }
        }
        if (incompat.isNotEmpty()) item { Aviso("Com estes valores, contratos de aluguer de ${incompat.joinToString("/")} meses ficam com comissão superior ao sinal.", TipoAviso.AVISO) }
        if (erros.isNotEmpty()) item { Aviso(erros.joinToString("\n"), TipoAviso.ERRO) }
        item { BotaoPrincipal("Guardar ajustes", { novo?.let(vm::guardar) }, ocupado = ocupado, enabled = erros.isEmpty()) }
    }
}

@Composable
private fun Testemunhos(vm: AdminViewModel) {
    val l by vm.testemunhosPendentes.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (l.isEmpty()) item { EstadoVazio("Nada para moderar", "Testemunhos de negócios concluídos aparecem aqui.") }
        items(l, key = { it.id }) { t ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${"★".repeat(t.n("rating").toInt().coerceIn(0, 5))} · ${t.s("publicName")}", fontWeight = FontWeight.SemiBold)
                    Text(t.s("text"), style = MaterialTheme.typography.bodySmall)
                    Text(t.s("listingTitle"), style = MaterialTheme.typography.labelSmall)
                    Row {
                        TextButton(enabled = !ocupado, onClick = { vm.moderar(t.id, true) }) { Text("Publicar") }
                        TextButton(enabled = !ocupado, onClick = { vm.moderar(t.id, false) }) { Text("Rejeitar") }
                    }
                }
            }
        }
    }
}
