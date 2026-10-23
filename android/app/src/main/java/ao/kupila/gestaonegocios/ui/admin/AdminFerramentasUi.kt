package ao.kupila.gestaonegocios.ui.admin

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dados.eventos.Registo
import ao.kupila.gestaonegocios.dominio.anuncio.EstadoAnuncio
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.dominio.indicadores.Presenca
import ao.kupila.gestaonegocios.dominio.perfis.EstadoConta
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.entrada.formatarData
import ao.kupila.gestaonegocios.ui.entrada.iniciais
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.KpCabecalhoModal
import ao.kupila.gestaonegocios.ui.kp.KpEntrada
import ao.kupila.gestaonegocios.ui.kp.KpRotulo
import ao.kupila.gestaonegocios.ui.kp.KpSelect
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.linhaInferior
import ao.kupila.gestaonegocios.ui.kp.linhaSuperior
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque

private fun aoa(v: Double) = FormatoAoa.formatar(Dinheiro.de(java.math.BigDecimal.valueOf(v)))
private fun auto(u: Registo) = u.s("creationSource").uppercase() !in setOf("ADMIN", "ADMIN_CREATED")

/** GESTÃO DE CLIENTES E PROPRIETÁRIOS (`#modal-user-management`, HTML 8023-8064). */
@Composable
fun UtilizadoresEcra(vm: AdminViewModel, aoFechar: () -> Unit, aoFicha: (String) -> Unit) {
    val marca = LocalMarca.current
    val us by vm.users.collectAsStateWithLifecycle()
    val pr by vm.presenca.collectAsStateWithLifecycle()
    val produtos by vm.produtos.collectAsStateWithLifecycle()
    val reservas by vm.reservas.collectAsStateWithLifecycle()
    val msg by vm.mensagem.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    var criar by remember { mutableStateOf(false) }
    val agora = System.currentTimeMillis()
    val online = pr.filter { (it.ms("lastSeenAt") ?: 0) > agora - Presenca.JANELA_ONLINE_MS }.map { it.s("userId") }.toSet()
    val contas = us.filter { Papel.canonico(it.s("role")) != Papel.ADMINISTRADOR }.sortedBy { it.s("name").lowercase() }
    Column {
        KpCabecalhoModal("", "Gestão de Utilizadores", aoFechar, icone = Fa.USERS_GEAR, corIcone = Kp.yellow400, subtitulo = "Consulta, estado e privacidade das contas", fundo = Kp.slate900)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            msg?.let { T(it, 8f, marca.c800, 700, Modifier.fillMaxWidth().sup(15.dp, fundo = marca.c50, borda = marca.c100).toque { vm.mensagem.value = null }.padding(10.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Estat("Clientes", us.count { Papel.canonico(it.s("role")) == Papel.CLIENTE }, Modifier.weight(1f))
                Estat("Proprietários", us.count { Papel.canonico(it.s("role")) == Papel.PROPRIETARIO }, Modifier.weight(1f))
                Estat("Activos", us.count { it.s("status") == "ACTIVE" }, Modifier.weight(1f))
            }
            KpBotao("Novo Cliente / Proprietário", { criar = true }, icone = Fa.USER_PLUS, altura = 33.dp, raio = 15.dp, tamanho = 9.5f)
            Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Color(0xFFF0F9FF), borda = Color(0xFFE0F2FE)).padding(10.dp)) {
                FaIcone(Fa.USER_SHIELD, 8f, Color(0xFF0C4A6E), Modifier.padding(top = 2.dp, end = 4.dp))
                androidx.compose.material3.Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0C4A6E))) { append("Privacidade:") }
                    append(" contas criadas pelo próprio utilizador são apenas consultáveis pelo Administrador. Só é permitido alterar o estado da conta (activar, desactivar, suspender ou arquivar). Dados pessoais só podem ser editados quando a conta tiver sido criada pelo Administrador actual.")
                }, color = Kp.slate600, fontSize = 7.5.sp, lineHeight = 13.sp)
            }
            if (criar) EditorUtilizador(vm, ocupado) { criar = false }
            Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.slate950, borda = Kp.slate700).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).sup(12.dp, fundo = Kp.slate800), contentAlignment = Alignment.Center) { FaIcone(Fa.USER_TIE, 13f, Kp.yellow400) }
                Spacer(Modifier.width(8.dp))
                Column { T(us.firstOrNull { it.id == vm.meuUid }?.s("name") ?: "Administrador", 10f, Color.White, 700)
                    T(us.firstOrNull { it.id == vm.meuUid }?.s("email").orEmpty(), 7.5f, Kp.slate400) }
            }
            T("Administradores do sistema", 8f, Kp.slate400, 800, espacamento = 1.2f, maiusculas = true)
            contas.forEach { u ->
                val papel = Papel.canonico(u.s("role"))
                val auto = auto(u)
                val stats = if (papel == Papel.PROPRIETARIO) "${produtos.count { it.donoId == u.id }} anúncio(s)" else "${reservas.count { it.s("userId") == u.id }} reserva(s)"
                Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200, sombra = Kp.slate900.copy(.06f), desfoque = 8.dp).padding(9.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(48.dp).sup(16.dp, fundo = if (papel == Papel.CLIENTE) Color(0xFFE0F2FE) else marca.c100), contentAlignment = Alignment.Center) {
                            T(iniciais(u.s("name")), 14f, if (papel == Papel.CLIENTE) Color(0xFF0369A1) else marca.c800, 800)
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T(u.s("name").ifBlank { u.s("email") }, 9.5f, Kp.slate900, 700, max = 1)
                                Spacer(Modifier.width(6.dp)); Box(Modifier.size(8.dp).sup(8.dp, fundo = estadoMeta(u.s("status")).ponto, forma = CircleShape))
                            }
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                T(if (auto) "AUTO-CADASTRADA · SOMENTE LEITURA" else "CRIADA POR SI · EDITÁVEL", 6.5f, if (auto) Color(0xFF0369A1) else marca.c700, 800, Modifier.sup(999.dp, fundo = if (auto) Color(0xFFE0F2FE) else marca.c50).padding(horizontal = 6.dp, vertical = 2.dp))
                                val em = estadoMeta(u.s("status"))
                                T(em.rotulo, 6.5f, em.cor, 800, Modifier.sup(999.dp, fundo = em.fundo).padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            T("${papel?.rotulo ?: "Utilizador"} • ${u.id.take(12)}", 8f, Kp.slate400, Modifier.padding(top = 4.dp))
                            T("Dados pessoais protegidos · abra a Ficha para consulta autorizada", 8f, Color(0xFF0369A1), Modifier.padding(top = 2.dp))
                            T("$stats • Última actividade: ${u.ms("updatedAt")?.let { formatarData(it) } ?: "Sem actividade"}", 7f, Kp.slate400, Modifier.padding(top = 2.dp))
                        }
                    }
                    Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Kp.slate900))
                    val st = u.s("status").uppercase()
                    val botoes = buildList<@Composable (Modifier) -> Unit> {
                        add { m -> Accao("Ficha", Fa.ID_CARD, Kp.slate900, Color.White, m, regular = true) { aoFicha(u.id) } }
                        if (st != "ACTIVE") add { m -> Accao("Activar", Fa.USER_CHECK, Kp.emerald50, Kp.emerald700, m, ocupado = ocupado) { vm.estado(u.id, EstadoConta.ACTIVA, "") } }
                        else add { m -> Accao("Desactivar", Fa.USER_SLASH, Kp.slate50, Kp.slate700, m, ocupado = ocupado) { vm.estado(u.id, EstadoConta.INACTIVA, "") } }
                        if (st != "SUSPENDED") add { m -> Accao("Suspender", Fa.PAUSE, Kp.amber50, Kp.amber700, m, ocupado = ocupado) { vm.estado(u.id, EstadoConta.SUSPENSA, "") } }
                        if (st != "ARCHIVED") add { m -> Accao("Arquivar", Fa.BOX_ARCHIVE, Kp.violet50, Kp.violet700, m, ocupado = ocupado) { vm.estado(u.id, EstadoConta.ARQUIVADA, "") } }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        botoes.chunked(2).forEach { par -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { par.forEach { it(Modifier.weight(1f)) }; if (par.size == 1) Spacer(Modifier.weight(1f)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Estat(rotulo: String, v: Int, modifier: Modifier) {
    val marca = LocalMarca.current
    Column(modifier.sup(16.dp, fundo = marca.c50).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(rotulo, 8f, Kp.slate400); T(v.toString(), 10.5f, marca.c700, 800, Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Accao(texto: String, icone: String, fundo: Color, cor: Color, modifier: Modifier, ocupado: Boolean = false, regular: Boolean = false, aoTocar: () -> Unit) {
    Row(modifier.height(28.dp).sup(12.dp, fundo = fundo).toque(!ocupado, aoTocar), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        FaIcone(icone, 9f, cor, regular = regular); Spacer(Modifier.width(6.dp)); T(texto, 9.5f, cor, 700)
    }
}

private class EstadoMeta(val rotulo: String, val ponto: Color, val fundo: Color, val cor: Color)

/** `managedUserStatusMeta` (HTML 14740). */
private fun estadoMeta(s: String) = when (s.uppercase()) {
    "ACTIVE" -> EstadoMeta("ACTIVO", Color(0xFF10B981), Kp.emerald50, Kp.emerald700)
    "SUSPENDED" -> EstadoMeta("SUSPENSO", Color(0xFFF59E0B), Kp.amber50, Kp.amber800)
    "ARCHIVED" -> EstadoMeta("ARQUIVADO", Color(0xFF8B5CF6), Kp.violet50, Color(0xFF5B21B6))
    "INACTIVE", "" -> EstadoMeta("INACTIVO", Kp.slate300, Kp.slate100, Kp.slate700)
    else -> EstadoMeta(s.uppercase(), Kp.slate300, Kp.slate100, Kp.slate700)
}

/** FICHA PROFISSIONAL DO UTILIZADOR (`#modal-user-record`, HTML 8116-8132; renderUserRecord 14824). */
@Composable
fun FichaUtilizadorEcra(vm: AdminViewModel, uid: String, aoFechar: () -> Unit, aoHistorico: () -> Unit) {
    val marca = LocalMarca.current
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val us by vm.users.collectAsStateWithLifecycle()
    val aud by vm.auditoria.collectAsStateWithLifecycle()
    val produtos by vm.produtos.collectAsStateWithLifecycle()
    val reservas by vm.reservas.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val u = us.firstOrNull { it.id == uid }
    Column(Modifier.background(Kp.slate50)) {
        KpCabecalhoModal("Ficha do Utilizador", u?.s("name")?.ifBlank { null } ?: "Utilizador", aoFechar, fundo = Kp.slate950, corKicker = Kp.slate400)
        if (u == null) { T("O utilizador selecionado já não está disponível. Actualize a lista e tente novamente.", 10f, Kp.gray400, Modifier.padding(24.dp), centro = true); return@Column }
        val papel = Papel.canonico(u.s("role"))
        val anuncios = produtos.filter { it.donoId == uid }
        val feitas = reservas.filter { it.s("userId") == uid }
        val recebidas = reservas.filter { it.s("ownerId") == uid }
        val ops = aud.filter { it.s("actorId") == uid }.sortedByDescending { it.ms("createdAt") ?: 0 }
        val ultima = (listOfNotNull(u.ms("updatedAt"), u.ms("createdAt"), ops.firstOrNull()?.ms("createdAt")) + anuncios.map { it.criadoEm.toEpochMilli() } +
            (feitas + recebidas).mapNotNull { it.ms("updatedAt") ?: it.ms("createdAt") }).maxOrNull()
        val activo = u.s("status") == "ACTIVE"
        val meta = estadoMeta(u.s("status"))
        val morada = listOf(u.s("neighborhood"), u.s("municipality"), u.s("province")).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Não informado" }
        val editavel = papel != Papel.ADMINISTRADOR && !auto(u) && u.s("createdByAdminId") == vm.meuUid
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().sup(24.dp, pincel = ao.kupila.gestaonegocios.ui.kp.gradiente(135f, 0f to Kp.slate950, 1f to Kp.slate800), sombra = Kp.slate900.copy(.2f), desfoque = 12.dp).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(80.dp).sup(24.dp, fundo = Color.White.copy(.1f), borda = Color.White.copy(.1f)), contentAlignment = Alignment.Center) { FaIcone(Fa.USER, 24f, Kp.slate400) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val dono = papel == Papel.PROPRIETARIO
                            T(papel?.rotulo ?: u.s("role"), 9f, if (dono) Kp.amber300 else Kp.emerald300, 700, Modifier.sup(999.dp, fundo = (if (dono) Kp.amber400 else Color(0xFF34D399)).copy(.15f)).padding(horizontal = 8.dp, vertical = 4.dp))
                            T(meta.rotulo, 9f, if (activo) Kp.emerald300 else Kp.slate300, 700, Modifier.sup(999.dp, fundo = if (activo) Color(0xFF34D399).copy(.15f) else Kp.slate500.copy(.2f)).padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        T(u.s("name").ifBlank { "Sem nome" }, 18f, Color.White, 700, Modifier.padding(top = 8.dp), linha = 22f)
                        T("ID ${u.id}", 9f, Kp.slate400, Modifier.padding(top = 4.dp))
                    }
                }
                val primario = when (papel) { Papel.PROPRIETARIO -> anuncios.size to "Anúncios"; Papel.ADMINISTRADOR -> ops.size to "Operações"; else -> feitas.size to "Reservas" }
                val segundo = if (papel == Papel.PROPRIETARIO) recebidas.size to "Reservas recebidas" else ops.size to "Operações"
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(primario, segundo, ops.size to "Eventos").forEach { (n, r) ->
                        Column(Modifier.weight(1f).sup(16.dp, fundo = Color.White.copy(.05f)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { T(n.toString(), 18f, Color.White, 700); T(r, 8f, Kp.slate400, centro = true) }
                    }
                }
            }
            if (papel != Papel.ADMINISTRADOR) Row(Modifier.fillMaxWidth().sup(16.dp, fundo = if (editavel) marca.c50 else Color(0xFFF0F9FF), borda = if (editavel) marca.c100 else Color(0xFFE0F2FE)).padding(12.dp)) {
                val cor = if (editavel) marca.c800 else Color(0xFF0C4A6E)
                FaIcone(Fa.USER_SHIELD, 9f, cor, Modifier.padding(top = 2.dp, end = 4.dp))
                androidx.compose.material3.Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(if (editavel) "Conta criada por este Administrador." else "Conta protegida.") }
                    append(if (editavel) " Os dados podem ser editados por quem a criou." else " O Administrador pode consultar a ficha e gerir o estado, mas não alterar os dados pessoais porque esta conta não foi criada por ele.")
                }, color = cor, fontSize = 9.sp, lineHeight = 14.sp)
            }
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.gray200)) {
                Row(Modifier.fillMaxWidth().background(Kp.gray50).linhaInferior(Kp.gray200).padding(14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.ADDRESS_CARD, 10f, marca.c600, regular = true); Spacer(Modifier.width(6.dp)); T("Dados de identificação", 10f, Kp.gray500, 700, espacamento = .5f, maiusculas = true)
                }
                listOf("Nome" to u.s("name").ifBlank { "—" }, "E-mail" to u.s("email").ifBlank { "—" },
                    "Telefone principal" to u.s("phonePrimary").let { if (it.isBlank()) "—" else "+244 $it" },
                    "Alternativo" to u.s("phoneAlt").let { if (it.isBlank()) "—" else "+244 $it" }, "Morada" to morada).forEachIndexed { i, (k, v) ->
                    Row(Modifier.fillMaxWidth().then(if (i < 4) Modifier.linhaInferior(Kp.gray100) else Modifier).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        T(k, 11f, Kp.gray400); Spacer(Modifier.weight(1f)); T(v, 11f, Kp.gray800, 600, Modifier.fillMaxWidth(.62f), fim = true)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cadastro" to (u.ms("createdAt")?.let { formatarData(it) } ?: "Não informado"), "Última actividade" to (ultima?.let { formatarData(it) } ?: "Sem registo")).forEach { (k, v) ->
                    Column(Modifier.weight(1f).sup(16.dp, fundo = Color.White, borda = Kp.gray200).padding(12.dp)) { T(k, 8f, Kp.gray400, 700, maiusculas = true); T(v, 10f, Kp.gray800, 600, Modifier.padding(top = 4.dp)) }
                }
            }
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.gray200).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.CLOCK_ROTATE_LEFT, 10f, marca.c600); Spacer(Modifier.width(6.dp)); T("Actividade recente", 10f, Kp.gray500, 700, Modifier.weight(1f), espacamento = .5f, maiusculas = true); T("Últimas 5", 8f, Kp.gray400)
                }
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ops.isEmpty()) T("Sem actividade registada.", 10f, Kp.gray400, Modifier.fillMaxWidth().padding(vertical = 16.dp), centro = true)
                    ops.take(5).forEach { op ->
                        Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.gray50).padding(10.dp)) {
                            Row { T(op.s("action").ifBlank { "Operação" }, 10f, Kp.gray700, 700, Modifier.weight(1f)); T(op.ms("createdAt")?.let { formatarData(it) } ?: "", 8f, Kp.gray400) }
                            T(op.s("details").ifBlank { "Sem detalhe adicional." }, 9f, Kp.gray500, Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        val linhas = listOf(ao.kupila.gestaonegocios.dados.media.LinhaDoc("Identificação"), ao.kupila.gestaonegocios.dados.media.LinhaDoc("Nome", u.s("name").ifBlank { "—" }),
            ao.kupila.gestaonegocios.dados.media.LinhaDoc("Perfil", papel?.rotulo ?: u.s("role")), ao.kupila.gestaonegocios.dados.media.LinhaDoc("Estado", meta.rotulo),
            ao.kupila.gestaonegocios.dados.media.LinhaDoc("E-mail", u.s("email").ifBlank { "—" }), ao.kupila.gestaonegocios.dados.media.LinhaDoc("Telefone", u.s("phonePrimary").ifBlank { "—" }),
            ao.kupila.gestaonegocios.dados.media.LinhaDoc("Morada", morada), ao.kupila.gestaonegocios.dados.media.LinhaDoc("Actividade"),
            ao.kupila.gestaonegocios.dados.media.LinhaDoc("Anúncios", anuncios.size.toString()), ao.kupila.gestaonegocios.dados.media.LinhaDoc("Reservas feitas", feitas.size.toString()),
            ao.kupila.gestaonegocios.dados.media.LinhaDoc("Reservas recebidas", recebidas.size.toString()), ao.kupila.gestaonegocios.dados.media.LinhaDoc("Operações", ops.size.toString()),
            ao.kupila.gestaonegocios.dados.media.LinhaDoc("Cadastro", u.ms("createdAt")?.let { formatarData(it) } ?: "—"), ao.kupila.gestaonegocios.dados.media.LinhaDoc("Última actividade", ultima?.let { formatarData(it) } ?: "—"))
        Row(Modifier.fillMaxWidth().background(Color.White).linhaSuperior(Kp.gray100).padding(12.dp, 10.dp, 12.dp, 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Accao("Baixar JPG", Fa.IMAGE, marca.c50, marca.c800, Modifier.weight(1f), regular = true) { vm.exportarFicha(u, linhas, pdf = false) { contexto.startActivity(it) } }
            Accao("Baixar PDF", Fa.FILE_PDF, Kp.slate900, Color.White, Modifier.weight(1f), regular = true) { vm.exportarFicha(u, linhas, pdf = true) { contexto.startActivity(it) } }
        }
        Column(Modifier.fillMaxWidth().background(Color.White).linhaSuperior().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val botoes = buildList<@Composable (Modifier) -> Unit> {
                if (papel == Papel.ADMINISTRADOR) {
                    add { m -> Accao("Voltar", Fa.ARROW_LEFT, Kp.slate900, Color.White, m, aoTocar = aoFechar) }
                    add { m -> Accao("Auditoria", Fa.CLOCK_ROTATE_LEFT, marca.c50, marca.c700, m, aoTocar = aoHistorico) }
                } else {
                    if (activo) add { m -> Accao("Desactivar", Fa.USER_SLASH, Kp.slate50, Kp.slate800, m, ocupado) { vm.estado(uid, EstadoConta.INACTIVA, "") } }
                    else add { m -> Accao("Activar", Fa.USER_CHECK, Kp.emerald50, Color(0xFF065F46), m, ocupado) { vm.estado(uid, EstadoConta.ACTIVA, "") } }
                    if (u.s("status") != "SUSPENDED") add { m -> Accao("Suspender", Fa.PAUSE, Kp.amber50, Kp.amber800, m, ocupado) { vm.estado(uid, EstadoConta.SUSPENSA, "") } }
                    if (u.s("status") != "ARCHIVED") add { m -> Accao("Arquivar", Fa.BOX_ARCHIVE, Kp.violet50, Color(0xFF5B21B6), m, ocupado) { vm.estado(uid, EstadoConta.ARQUIVADA, "") } }
                }
            }
            botoes.chunked(2).forEach { par -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { par.forEach { it(Modifier.weight(1f)) }; if (par.size == 1) Spacer(Modifier.weight(1f)) } }
        }
    }
}

/** Editor inline `#user-editor` (HTML 8038-8057): Perfil/Estado, Nome, E-mail, Palavra-passe, Telefones, morada do Proprietário e foto. */
@Composable
private fun EditorUtilizador(vm: AdminViewModel, ocupado: Boolean, aoFechar: () -> Unit) {
    val marca = LocalMarca.current
    var papel by rememberSaveable { mutableStateOf(Papel.CLIENTE) }
    var estado by rememberSaveable { mutableStateOf(EstadoConta.ACTIVA) }
    var nome by rememberSaveable { mutableStateOf("") }; var email by rememberSaveable { mutableStateOf("") }; var senha by remember { mutableStateOf("") }
    var tel by rememberSaveable { mutableStateOf("") }; var alt by rememberSaveable { mutableStateOf("") }
    var prov by rememberSaveable { mutableStateOf("Luanda") }; var mun by rememberSaveable { mutableStateOf("") }; var bairro by rememberSaveable { mutableStateOf("") }
    var foto by remember { mutableStateOf<android.net.Uri?>(null) }
    val escolher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { foto = it }
    val dono = papel == Papel.PROPRIETARIO
    Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.gray50, borda = Kp.gray200).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { T("Novo Cliente / Proprietário", 12f, Kp.gray800, 700, Modifier.weight(1f)); Box(Modifier.toque(aoTocar = aoFechar).padding(4.dp)) { FaIcone(Fa.XMARK, 12f, Kp.gray400) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).sup(12.dp, fundo = marca.c100), contentAlignment = Alignment.Center) {
                if (foto != null) coil3.compose.AsyncImage(foto, null, Modifier.size(56.dp).sup(12.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else FaIcone(Fa.USER, 14f, marca.c700)
            }
            Spacer(Modifier.width(12.dp))
            Row(Modifier.sup(12.dp, fundo = Color.White, borda = Kp.gray300).toque { escolher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(12.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.CAMERA, 10f, Kp.gray700); Spacer(Modifier.width(4.dp)); T("Foto", 10f, Kp.gray700, 700) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { KpRotulo("Perfil", 10f, obrigatorio = true); KpSelect(listOf(Papel.CLIENTE to "Cliente", Papel.PROPRIETARIO to "Proprietário"), papel, { papel = it }, altura = 33.dp, tamanho = 12f) }
            Column(Modifier.weight(1f)) { KpRotulo("Estado", 10f, obrigatorio = true)
                KpSelect(listOf(EstadoConta.ACTIVA to "Activo", EstadoConta.INACTIVA to "Inactivo", EstadoConta.SUSPENSA to "Suspenso", EstadoConta.ARQUIVADA to "Arquivado"), estado, { estado = it }, altura = 33.dp, tamanho = 12f) }
        }
        Column { KpRotulo("Nome", 10f, obrigatorio = true); KpEntrada(nome, { nome = it.take(120) }, altura = 33.dp, tamanhoTexto = 12f) }
        Column { KpRotulo("E-mail *", 10f); KpEntrada(email, { email = it.trim().take(160) }, dica = "nome@dominio.com", altura = 33.dp, tamanhoTexto = 12f, teclado = KeyboardType.Email) }
        Column { KpRotulo("Palavra-passe", 10f, obrigatorio = true); KpEntrada(senha, { senha = it }, dica = "Mínimo 6 caracteres", altura = 33.dp, tamanhoTexto = 12f, senha = true)
            T("Na edição, deixe vazio para manter a palavra-passe actual.", 8f, Kp.gray400, Modifier.padding(top = 4.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { KpRotulo("Telefone Principal", 10f, obrigatorio = true); KpEntrada(tel, { tel = it.filter(Char::isDigit).take(9) }, altura = 33.dp, tamanhoTexto = 12f, teclado = KeyboardType.Phone) }
            Column(Modifier.weight(1f)) { KpRotulo("Alternativo", 10f); KpEntrada(alt, { alt = it.filter(Char::isDigit).take(9) }, altura = 33.dp, tamanhoTexto = 12f, teclado = KeyboardType.Phone) }
        }
        if (dono) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { KpRotulo("Província", 10f, obrigatorio = true); KpSelect(vm.geografia.provincias.map { it to it }, prov, { prov = it; mun = "" }, altura = 33.dp, tamanho = 12f) }
                Column(Modifier.weight(1f)) { KpRotulo("Município", 10f, obrigatorio = true); KpSelect(vm.geografia.municipios(prov).map { it to it }, mun.ifBlank { vm.geografia.municipios(prov).firstOrNull() }, { mun = it }, altura = 33.dp, tamanho = 12f) }
            }
            Column { KpRotulo("Bairro", 10f, obrigatorio = true); KpEntrada(bairro, { bairro = it }, altura = 33.dp, tamanhoTexto = 12f) }
        }
        KpBotao("Guardar Utilizador", {
            vm.criar(papel, nome, email, senha, tel, prov, if (dono) mun.ifBlank { vm.geografia.municipios(prov).firstOrNull().orEmpty() } else "", if (dono) bairro else "", estado, alt, foto, aoCriado = aoFechar)
        }, icone = Fa.FLOPPY_DISK, fundo = Kp.slate900, altura = 36.dp, raio = 12.dp, tamanho = 12f, ocupado = ocupado)
    }
}

/** "Fichas dos Administradores" (`#modal-admin-directory`, HTML 8065-8074; render 14716). Toque abre a ficha. */
@Composable
fun DirectorioEcra(vm: AdminViewModel, aoFechar: () -> Unit, aoFicha: (String) -> Unit) {
    val marca = LocalMarca.current
    val us by vm.users.collectAsStateWithLifecycle()
    val admins = us.filter { Papel.canonico(it.s("role")) == Papel.ADMINISTRADOR }
    Column(Modifier.background(Kp.slate50)) {
        KpCabecalhoModal("Administração", "Fichas dos Administradores", aoFechar, icone = Fa.USER_TIE, corIcone = Color.White,
            subtitulo = "${admins.size} Administrador(es) cadastrado(s)", fundo = Kp.slate950, corKicker = Kp.yellow300)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (admins.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FaIcone(Fa.USER_TIE, 24f, Kp.gray400); T("Nenhum Administrador disponível.", 10f, Kp.gray400, Modifier.padding(top = 8.dp))
            }
            admins.forEach { u ->
                Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.gray200, sombra = Kp.slate900.copy(.05f), desfoque = 3.dp).toque { aoFicha(u.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).sup(16.dp, fundo = Kp.slate100), contentAlignment = Alignment.Center) { FaIcone(Fa.USER_TIE, 14f, Kp.slate500) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T(u.s("name").ifBlank { "Administrador" }, 11f, Kp.gray900, 700, max = 1)
                            if (u.id == vm.meuUid) { Spacer(Modifier.width(6.dp)); T("SESSÃO ACTUAL", 7f, marca.c700, 700, Modifier.sup(999.dp, fundo = marca.c50).padding(horizontal = 6.dp, vertical = 2.dp)) }
                        }
                        T("${u.s("email").ifBlank { "Sem e-mail" }} • ${if (u.s("status") == "ACTIVE") "Activo" else "Inactivo"}", 8f, Kp.gray400, Modifier.padding(top = 2.dp), max = 1)
                        val local = listOf(u.s("municipality"), u.s("province")).filter { it.isNotBlank() }.joinToString(", ")
                        T((if (u.s("phonePrimary").isNotBlank()) "+244 ${u.s("phonePrimary")}" else "Sem telefone") + (if (local.isNotBlank()) " • $local" else ""), 8f, Kp.gray500, Modifier.padding(top = 2.dp), max = 1)
                        T("Última actividade: ${u.ms("updatedAt")?.let { formatarData(it) } ?: "Sem registo"}", 8f, Kp.gray400, Modifier.padding(top = 4.dp))
                    }
                    FaIcone(Fa.ID_CARD, 12f, marca.c600, regular = true)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).linhaSuperior().padding(16.dp, 10.dp)) {
            FaIcone(Fa.CIRCLE_INFO, 8f, Kp.gray400, Modifier.padding(end = 4.dp)); T("Toque numa ficha para consultar identificação, estado e actividade do Administrador.", 8f, Kp.gray400)
        }
    }
}

/** Histórico de Operações (`#modal-operation-history`, HTML 8135-8143; renderOperationHistory). */
@Composable
fun HistoricoEcra(vm: AdminViewModel, aoFechar: () -> Unit) {
    val marca = LocalMarca.current
    val a by vm.auditoria.collectAsStateWithLifecycle()
    val l = a.sortedByDescending { it.ms("createdAt") ?: 0 }
    Column(Modifier.background(Color.White)) {
        KpCabecalhoModal("", "Histórico de Operações", aoFechar, icone = Fa.SHIELD_HALVED, corIcone = Kp.yellow400,
            subtitulo = "Auditoria de utilizadores, produtos e reservas", fundo = Kp.slate900)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (l.isEmpty()) T("Sem operações registadas.", 12f, Kp.gray400, Modifier.fillMaxWidth().padding(vertical = 32.dp), centro = true)
            l.take(300).forEach { op ->
                Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.gray200).padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            T(op.s("action"), 12f, Kp.gray800, 700)
                            T("${op.ms("createdAt")?.let { formatarData(it, comHora = true) } ?: "—"} • ${op.id}", 9f, Kp.gray400, max = 1)
                        }
                        T(Papel.canonico(op.s("actorRole"))?.rotulo ?: op.s("actorRole"), 9f, marca.c700, 700, Modifier.sup(999.dp, fundo = marca.c50).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Column(Modifier.padding(top = 8.dp).fillMaxWidth().sup(8.dp, fundo = Kp.gray50).padding(8.dp)) {
                        listOf("Actor:" to "${op.s("actorName")} (${op.s("actorId")})", "Objecto:" to "${op.s("entity")} ${op.s("entityId")}", "Detalhe:" to op.s("details").ifBlank { "—" }).forEach { (k, v) ->
                            androidx.compose.material3.Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(k) }; append(" $v") }, color = Kp.gray600, fontSize = 10.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

private class Transaccao(val id: String, val tipo: String, val valor: Double, val em: Long, val origem: String, val produto: String, val produtoId: String, val dono: String, val descricao: String)

/** Histórico da Monetização (`#modal-monetization-history`; renderFinancialHistory, HTML 12543): só registos confirmados no servidor. */
@Composable
fun MonetizacaoEcra(vm: AdminViewModel, aoFechar: () -> Unit) {
    val marca = LocalMarca.current
    val intencoes by vm.intencoes.collectAsStateWithLifecycle()
    val reservas by vm.reservas.collectAsStateWithLifecycle()
    val tx = remember(intencoes, reservas) {
        val confirmadas = setOf("PUBLISHED", "COMPLETED", "PAID", "CONFIRMED")
        val taxas = intencoes.filter { it.s("status") in confirmadas && it.s("type") in setOf("LISTING", "PROMOTION") }.map {
            Transaccao(it.id, if (it.s("type") == "PROMOTION") "Serviço de Promoção" else "Taxa de Anúncio", it.n("amount"), it.ms("confirmedAt") ?: it.ms("updatedAt") ?: 0,
                "Pagamento confirmado pelo Administrador", it.s("listingTitle").ifBlank { it.s("title") }, it.s("listingId"), it.s("ownerName").ifBlank { it.s("userName") }, it.s("reference").let { r -> if (r.isBlank()) "" else "Referência $r" })
        }
        val comissoes = reservas.filter { it.n("commissionAmount") > 0 && it.s("status") in setOf("ACTIVE", "COMPLETED", "REFUND_PENDING", "REFUNDED") }.flatMap {
            val base = Transaccao(it.id + "-C", "Comissão de Reserva", it.n("commissionAmount"), it.ms("createdAt") ?: 0, "Sinal confirmado", it.s("listingTitle"), it.s("listingId"), it.s("ownerName"), "Comissão retida do sinal da reserva.")
            if (it.s("status") == "REFUNDED") listOf(base, Transaccao(it.id + "-E", "Estorno de Comissão", -it.n("commissionAmount"), it.ms("updatedAt") ?: 0, "Reembolso aprovado", it.s("listingTitle"), it.s("listingId"), it.s("ownerName"), "Comissão devolvida com o reembolso."))
            else listOf(base)
        }
        (taxas + comissoes).sortedByDescending { it.em }
    }
    val taxas = tx.filter { it.tipo != "Comissão de Reserva" && it.tipo != "Estorno de Comissão" }.sumOf { it.valor }
    val comissoes = tx.filter { it.tipo == "Comissão de Reserva" || it.tipo == "Estorno de Comissão" }.sumOf { it.valor }
    Column(Modifier.background(Color.White)) {
        Row(Modifier.fillMaxWidth().background(Kp.slate900).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FaIcone(Fa.RECEIPT, 14f, Kp.yellow400); Spacer(Modifier.width(8.dp)); T("Histórico da Monetização", 14f, Color.White, 700, Modifier.weight(1f))
            ao.kupila.gestaonegocios.ui.kp.KpFechar(aoFechar)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().sup(12.dp, fundo = marca.c50, borda = marca.c100).padding(12.dp)) {
                listOf(Triple("Taxas de anúncio", taxas, marca.c700), Triple("Comissões líquidas", comissoes, marca.c700), Triple("Saldo monetização", taxas + comissoes, Kp.slate900)).forEach { (r, v, c) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { T(r, 9f, Kp.gray500, centro = true); T(aoa(v), 12f, c, 700) }
                }
            }
            if (tx.isEmpty()) T("Ainda não existem cobranças ou transacções registadas.", 12f, Kp.gray400, Modifier.fillMaxWidth().padding(vertical = 24.dp), centro = true)
            tx.take(300).forEach { x ->
                Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.gray200).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) { T(x.tipo, 12f, Kp.gray800, 700); T("${if (x.em > 0) formatarData(x.em) else "—"} • ${x.id}", 9f, Kp.gray400, max = 1) }
                        T((if (x.valor >= 0) "+" else "") + aoa(x.valor), 12f, if (x.valor >= 0) Kp.emerald600 else Kp.red600, 700)
                    }
                    Column(Modifier.fillMaxWidth().sup(8.dp, fundo = Kp.gray50).padding(8.dp)) {
                        listOf("Origem:" to x.origem.ifBlank { "—" }, "Produto:" to (x.produto.ifBlank { "—" } + if (x.produtoId.isNotBlank()) " (${x.produtoId})" else ""),
                            "Proprietário:" to x.dono.ifBlank { "—" }, "Descrição:" to x.descricao.ifBlank { "—" }).forEach { (k, v) ->
                            androidx.compose.material3.Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(k) }; append(" $v") }, color = Kp.gray600, fontSize = 10.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/** CENTRO DE MONITORAMENTO (`#modal-platform-monitor`, HTML 7713-7765). */
@Composable
fun MonitorEcra(vm: AdminViewModel, aoFechar: () -> Unit, aoDetalhe: (tipo: String, categoria: String, modalidade: String) -> Unit) {
    val marca = LocalMarca.current
    val us by vm.users.collectAsStateWithLifecycle()
    val pr by vm.presenca.collectAsStateWithLifecycle()
    val aud by vm.auditoria.collectAsStateWithLifecycle()
    val produtos by vm.produtos.collectAsStateWithLifecycle()
    val reservas by vm.reservas.collectAsStateWithLifecycle()
    val intencoes by vm.intencoes.collectAsStateWithLifecycle()
    val em by vm.actualizadoEm.collectAsStateWithLifecycle()
    var periodo by rememberSaveable { mutableIntStateOf(14) }
    val agora = System.currentTimeMillis()
    val hoje0 = agora - agora % 86_400_000L
    val online = pr.filter { (it.ms("lastSeenAt") ?: 0) > agora - Presenca.JANELA_ONLINE_MS }
    val logins = aud.filter { it.s("action") == "LOGIN" }
    val loginsHoje = logins.filter { (it.ms("createdAt") ?: 0) >= hoje0 }
    Column {
        Row(Modifier.fillMaxWidth().background(Kp.slate950).padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).sup(9.dp, fundo = Color(0xFF22C55E), forma = CircleShape)); Spacer(Modifier.width(8.dp)); T("Tempo real", 9f, Kp.emerald300, 800, espacamento = 1.4f, maiusculas = true) }
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.CHART_LINE, 11f, Kp.yellow400); Spacer(Modifier.width(6.dp)); T("Centro de Monitoramento", 11f, Color.White, 700) }
                T("Actualizado ${formatarData(em)}", 8f, Kp.slate400)
            }
            Box(Modifier.size(36.dp).sup(36.dp, fundo = Color.White.copy(.08f), forma = CircleShape).toque { vm.actualizar() }, contentAlignment = Alignment.Center) { FaIcone(Fa.ARROWS_ROTATE, 10f, Color.White) }
            Spacer(Modifier.width(6.dp)); ao.kupila.gestaonegocios.ui.kp.KpFechar(aoFechar)
        }
        Column(Modifier.weight(1f, fill = false).background(Kp.slate50).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { T("Acesso ao app", 10f, Kp.slate600, 800, espacamento = .8f, maiusculas = true); T("Presença por dispositivo e acessos autenticados.", 8.5f, Kp.slate400) }
                T("LIVE", 8f, marca.c700, 800, Modifier.sup(999.dp, fundo = marca.c50).padding(horizontal = 8.dp, vertical = 4.dp))
            }
            val metricas = listOf(Metrica(Fa.WIFI, marca.c600, "Presença", online.size.toString(), "Online agora", "ONLINE"),
                Metrica(Fa.MOBILE_SCREEN_BUTTON, Kp.blue600, "Em primeiro plano", online.count { it.d["foreground"] == true }.toString(), "Usando o app", "USING"),
                Metrica(Fa.DOOR_OPEN, Kp.violet600, "Sessões", loginsHoje.size.toString(), "Visitas hoje", "VISITS_TODAY"),
                Metrica(Fa.USERS, Color(0xFFD97706), "Hoje", loginsHoje.map { it.s("actorId") }.distinct().size.toString(), "Visitantes únicos", "UNIQUE_TODAY"),
                Metrica(Fa.CLOCK_ROTATE_LEFT, Kp.slate700, "Histórico disponível", logins.size.toString(), "Acessos registados", "ACCESS_HISTORY"),
                Metrica(Fa.ADDRESS_BOOK, marca.c600, "Cadastrados", us.size.toString(), "Utilizadores", "USERS"))
            metricas.chunked(2).forEach { par -> Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { par.forEach { CartaoMetrica(it, Modifier.weight(1f)) { aoDetalhe(it.tipo, "", "") } } } }
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.SIGNAL, 9f, marca.c600); Spacer(Modifier.width(6.dp)); T("Sessões activas", 9.5f, Kp.slate900, 700, Modifier.weight(1f))
                    T(online.size.toString(), 7f, marca.c700, 800, Modifier.sup(999.dp, fundo = marca.c50).padding(horizontal = 6.dp, vertical = 3.dp))
                }
                if (online.isEmpty()) Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.SATELLITE_DISH, 12f, Kp.slate400); Spacer(Modifier.width(4.dp)); T("Nenhuma sessão online detectada agora.", 8.5f, Kp.slate400)
                }
                online.sortedByDescending { it.ms("lastSeenAt") ?: 0 }.forEach { p ->
                    val emUso = p.d["foreground"] != false
                    Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.gray50).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).sup(12.dp, fundo = Color.White, borda = Kp.gray200), contentAlignment = Alignment.Center) {
                            T(p.s("name").trim().take(1).uppercase().ifBlank { "U" }, 12f, marca.c700, 700)
                            Box(Modifier.align(Alignment.BottomEnd).size(10.dp).sup(10.dp, fundo = if (emUso) Color(0xFF10B981) else Kp.amber400, borda = Color.White, bordaLargura = 2.dp, forma = CircleShape))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            T(p.s("name").ifBlank { "Utilizador" }, 10f, Kp.gray800, 700, max = 1)
                            T("${Papel.canonico(p.s("role"))?.rotulo ?: "Utilizador"} • ${p.s("deviceLabel").ifBlank { "Dispositivo" }} • ${p.s("currentTab").ifBlank { "explore" }}", 8f, Kp.gray400, max = 1)
                        }
                        T(if (emUso) "EM USO" else "AUSENTE", 8f, if (emUso) Kp.emerald600 else Color(0xFFD97706), 700)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.USERS_VIEWFINDER, 9f, marca.c600); Spacer(Modifier.width(6.dp)); T("Acessos por perfil · hoje", 9.5f, Kp.slate900, 700) }
                val porPapel = loginsHoje.groupingBy { l -> Papel.canonico(us.firstOrNull { it.id == l.s("actorId") }?.s("role")) }.eachCount()
                val max = (porPapel.values.maxOrNull() ?: 1).coerceAtLeast(1)
                Papel.entries.forEach { p ->
                    val n = porPapel[p] ?: 0
                    Row { T(p.rotulo, 8.5f, Kp.slate700, 700, Modifier.weight(1f)); T(n.toString(), 8.5f, Kp.slate900, 800) }
                    Box(Modifier.fillMaxWidth().height(5.dp).sup(5.dp, fundo = Kp.slate100)) {
                        Box(Modifier.fillMaxWidth(n.toFloat() / max).height(5.dp).sup(5.dp, fundo = marca.c600))
                    }
                }
            }
            // Crescimento da utilização
            val ini = agora - periodo * 86_400_000L
            val ant = ini - periodo * 86_400_000L
            fun variacao(act: Int, prev: Int) = if (prev == 0) (if (act > 0) 100.0 else 0.0) else (act - prev) * 100.0 / prev
            val acessos = logins.count { (it.ms("createdAt") ?: 0) >= ini }; val acessosAnt = logins.count { (it.ms("createdAt") ?: 0) in ant until ini }
            val novos = produtos.count { it.criadoEm.toEpochMilli() >= ini }; val novosAnt = produtos.count { it.criadoEm.toEpochMilli() in ant until ini }
            val res = reservas.count { (it.ms("createdAt") ?: 0) >= ini }; val resAnt = reservas.count { (it.ms("createdAt") ?: 0) in ant until ini }
            val receita = reservas.filter { (it.ms("createdAt") ?: 0) >= ini }.sumOf { it.n("commissionAmount") } + intencoes.filter { it.s("type") == "LISTING" && it.s("status") == "PUBLISHED" && (it.ms("confirmedAt") ?: 0) >= ini }.sumOf { it.n("amount") }
            val varAcessos = variacao(acessos, acessosAnt); val varNovos = variacao(novos, novosAnt); val varRes = variacao(res, resAnt)
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("Crescimento da utilização", 10f, Kp.slate600, 800, espacamento = .8f, maiusculas = true)
                T("Período actual comparado ao período imediatamente anterior.", 8.5f, Kp.slate400)
                Row(Modifier.sup(12.dp, fundo = Kp.slate100).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(7, 14, 30).forEach { d -> T("${d}d", 8.5f, if (periodo == d) marca.c700 else Kp.slate400, 800,
                        Modifier.sup(9.dp, fundo = if (periodo == d) Color.White else null).toque { periodo = d }.padding(horizontal = 10.dp, vertical = 5.dp)) }
                }
                listOf(Triple(Fa.ARROW_RIGHT_TO_BRACKET, acessos.toString() to "Acessos", varAcessos), Triple(Fa.RECTANGLE_AD, novos.toString() to "Novos anúncios", varNovos),
                    Triple(Fa.CALENDAR_CHECK, res.toString() to "Reservas", varRes), Triple(Fa.COINS, aoa(receita) to "Receita", 0.0)).chunked(2).forEach { par ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        par.forEach { (ic, vr, va) ->
                            Column(Modifier.weight(1f).sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(ic, 9f, Kp.slate400); Spacer(Modifier.weight(1f)); Variacao(va) }
                                T(vr.first, 10f, Kp.slate900, 800, Modifier.padding(top = 4.dp)); T(vr.second, 8f, Kp.slate400)
                            }
                        }
                    }
                }
                val score = (varAcessos + varNovos + varRes) / 4
                Column(Modifier.fillMaxWidth(.5f).sup(16.dp, fundo = Kp.slate950).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.GAUGE_HIGH, 9f, Kp.yellow400); Spacer(Modifier.weight(1f)); Variacao(score, escuro = true) }
                    T("%+.1f%%".format(score), 11f, Color.White, 800, Modifier.padding(top = 4.dp)); T("Score geral", 8f, Kp.slate300)
                }
                // Evolução de acessos
                val diasSerie = (0 until periodo).map { d -> val i0 = hoje0 - (periodo - 1 - d) * 86_400_000L; logins.count { (it.ms("createdAt") ?: 0) in i0 until i0 + 86_400_000L } }
                Column(Modifier.fillMaxWidth().sup(20.dp, fundo = Kp.slate950).padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { T("Evolução de acessos", 9.5f, Color.White, 800); T("Sessões autenticadas por dia", 8f, Kp.slate400) }
                        T("${diasSerie.sum()} acesso(s) · ${periodo}d", 8f, Kp.emerald300, 700)
                    }
                    val maxS = (diasSerie.maxOrNull() ?: 1).coerceAtLeast(1)
                    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(16.dp)) {
                        val passo = size.width / (diasSerie.size - 1).coerceAtLeast(1)
                        val pts = diasSerie.mapIndexed { i, v -> Offset(i * passo, size.height - v.toFloat() / maxS * size.height) }
                        pts.zipWithNext().forEach { (p1, p2) -> drawLine(Color(0xFF34D399), p1, p2, 3f) }
                        pts.forEach { drawCircle(Color(0xFF34D399), 4f, it) }
                    }
                }
            }
            // Produtos cadastrados por grupo
            T("Produtos cadastrados para anúncio", 10f, Kp.slate600, 800, espacamento = .8f, maiusculas = true)
            T("Grupo = tipo do produto; categoria/modalidade = finalidade comercial.", 8.5f, Kp.slate400)
            produtos.groupBy { it.categoria to it.modalidade }.toSortedMap(compareBy<Pair<Categoria, Modalidade>> { it.first.rotuloPlural }.thenBy { it.second.name }).forEach { (k, l) ->
                Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).toque { aoDetalhe("PRODUCTS", k.first.name, k.second.name) }.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            T("${if (k.first == Categoria.OUTRO) "Outros Produtos" else k.first.rotuloPlural} · ${if (k.second == Modalidade.VENDA) "Venda" else "Arrendamento"}", 10f, Kp.slate900, 700)
                            T("${l.size} produto(s) • ${FormatoAoa.formatar(Dinheiro.soma(l.map { it.preco }))} anunciados", 8f, Kp.slate400)
                        }
                        T(l.size.toString(), 10f, marca.c700, 800)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Triple("Activos", l.count { it.estado == EstadoAnuncio.ACTIVO }, marca.c700 to marca.c50), Triple("Reservados", l.count { it.estado == EstadoAnuncio.RESERVADO }, Kp.amber700 to Color(0xFFFFFBEB)),
                            Triple("Concluídos", l.count { it.estado == EstadoAnuncio.CONCLUIDO }, Kp.slate700 to Kp.slate50)).forEach { (r, n, c) ->
                            Column(Modifier.weight(1f).sup(12.dp, fundo = c.second).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                T(n.toString(), 10f, c.first, 700); T(r, 7f, c.first)
                            }
                        }
                    }
                    T("Toque para ver os produtos →", 7f, marca.c600, 700)
                }
            }
            if (produtos.isEmpty()) T("Sem produtos cadastrados.", 9f, Kp.gray400, Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.gray200).padding(16.dp), centro = true)
            // Produtos reservados por grupo (renderMonitoringReservations)
            val classe = produtos.associate { it.id to (it.categoria to it.modalidade) }
            T("Produtos reservados", 10f, Kp.slate600, 800, espacamento = .8f, maiusculas = true)
            T("Reservas agrupadas pelo grupo e modalidade do produto.", 8.5f, Kp.slate400)
            val porGrupo = reservas.groupBy { classe[it.s("listingId")] }
            if (reservas.isEmpty()) T("Sem reservas registadas.", 9f, Kp.gray400, Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.gray200).padding(16.dp), centro = true)
            porGrupo.forEach { (k, l) ->
                Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.gray200).toque { aoDetalhe("RESERVATIONS", k?.first?.name.orEmpty(), k?.second?.name.orEmpty()) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            T(rotuloGrupo(k?.first, k?.second), 10f, Kp.gray900, 700)
                            T("${l.size} reserva(s) • ${aoa(l.sumOf { it.n("amount") })} em sinais históricos", 8f, Kp.gray400)
                        }
                        T(l.size.toString(), 14f, Kp.amber700, 900)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Triple("Activas", l.count { it.s("status") == "ACTIVE" }, Kp.amber700 to Kp.amber50), Triple("Expiradas", l.count { it.s("status") == "EXPIRED" }, Kp.red700 to Kp.red50),
                            Triple("Concluídas", l.count { it.s("status") == "COMPLETED" }, Kp.emerald700 to Kp.emerald50)).forEach { (r, n, c) ->
                            Column(Modifier.weight(1f).sup(8.dp, fundo = c.second).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) { T(n.toString(), 10f, c.first, 700); T(r, 7f, Kp.gray700) }
                        }
                    }
                    T("Toque para ver as reservas →", 7f, Kp.amber700, 700)
                }
            }
            // Resumo dos saldos
            T("Resumo dos saldos", 10f, Kp.slate600, 800, espacamento = .8f, maiusculas = true)
            T("Sinais confirmados, líquido em custódia/liberado, taxas/comissões e exposição por grupo/modalidade.", 8.5f, Kp.slate400, linha = 13f)
            val activas = reservas.filter { it.s("status") == "ACTIVE" }
            val anunciado = produtos.sumOf { it.preco.valor.toDouble() }
            val sinais = activas.sumOf { it.n("amount") }
            val liquido = activas.sumOf { (it.n("amount") - it.n("commissionAmount")).coerceAtLeast(0.0) }
            val receitaTotal = reservas.filter { it.s("status") in setOf("ACTIVE", "COMPLETED", "REFUND_PENDING") }.sumOf { it.n("commissionAmount") } +
                intencoes.filter { it.s("type") == "LISTING" && it.s("status") in setOf("PUBLISHED", "COMPLETED", "PAID") }.sumOf { it.n("amount") }
            listOf(Metrica(Fa.TAGS, Kp.blue600, "consolidado", aoa(anunciado), "Valor anunciado", "BALANCE_ADVERTISED"), Metrica(Fa.VAULT, Color(0xFFD97706), "reservas activas", aoa(sinais), "Sinais confirmados", "BALANCE_CUSTODY"),
                Metrica(Fa.WALLET, Kp.emerald600, "líquido calculado", aoa(liquido), "Líquido proprietários", "BALANCE_OWNER_NET"), Metrica(Fa.COINS, Kp.violet600, "taxas + comissões", aoa(receitaTotal), "Receita plataforma", "BALANCE_REVENUE"))
                .chunked(2).forEach { par -> Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { par.forEach { CartaoMetrica(it, Modifier.weight(1f), valorPequeno = true) { aoDetalhe(it.tipo, "", "") } } } }
            produtos.groupBy { it.categoria to it.modalidade }.forEach { (k, l) ->
                val ids = l.map { it.id }.toSet()
                val act = activas.filter { it.s("listingId") in ids }
                val rec = reservas.filter { it.s("listingId") in ids && it.s("status") in setOf("ACTIVE", "COMPLETED", "REFUND_PENDING") }.sumOf { it.n("commissionAmount") } +
                    intencoes.filter { it.s("listingId") in ids && it.s("type") == "LISTING" && it.s("status") in setOf("PUBLISHED", "COMPLETED", "PAID") }.sumOf { it.n("amount") }
                Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.gray200).toque { aoDetalhe("BALANCES", k.first.name, k.second.name) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row { T(rotuloGrupo(k.first, k.second), 10f, Kp.gray900, 700, Modifier.weight(1f)); T("Receita ${aoa(rec)}", 8f, Kp.gray400) }
                    listOf(listOf(Triple("Anunciado", l.sumOf { it.preco.valor.toDouble() }, Kp.gray50 to Kp.gray800), Triple("Sinais", act.sumOf { it.n("amount") }, Kp.amber50 to Kp.amber800)),
                        listOf(Triple("Líquido proprietário", act.sumOf { (it.n("amount") - it.n("commissionAmount")).coerceAtLeast(0.0) }, Kp.emerald50 to Color(0xFF065F46)), Triple("Taxas + comissões", rec, Kp.violet50 to Color(0xFF5B21B6)))).forEach { linha ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            linha.forEach { (r, v, c) -> Column(Modifier.weight(1f).sup(8.dp, fundo = c.first).padding(8.dp)) { T(r, 8f, c.second.copy(.7f)); T(aoa(v), 8f, c.second, 700) } }
                        }
                    }
                    T("Toque para ver a composição →", 7f, Kp.violet600, 700)
                }
            }
        }
    }
}

private fun rotuloGrupo(c: Categoria?, m: Modalidade?) =
    "${when (c) { null -> "Sem grupo"; Categoria.OUTRO -> "Outros Produtos"; else -> c.rotuloPlural }} · ${when (m) { null -> "Sem modalidade"; Modalidade.VENDA -> "Venda"; else -> "Arrendamento" }}"

private data class Metrica(val icone: String, val cor: Color, val etiqueta: String, val valor: String, val rotulo: String, val tipo: String)

@Composable
private fun CartaoMetrica(m: Metrica, modifier: Modifier, valorPequeno: Boolean = false, aoDetalhes: () -> Unit) {
    val marca = LocalMarca.current
    Column(modifier.sup(16.dp, fundo = Color.White, borda = Kp.slate200).toque(aoTocar = aoDetalhes).padding(9.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(32.dp).sup(12.dp, fundo = Kp.slate50), contentAlignment = Alignment.Center) { FaIcone(m.icone, 13f, m.cor) }
            Spacer(Modifier.weight(1f)); T(m.etiqueta, 8f, Kp.slate400, 700, maiusculas = true, fim = true, modifier = Modifier.padding(top = 10.dp))
        }
        T(m.valor, if (valorPequeno) 11f else 16f, Kp.slate900, 900, Modifier.padding(top = 10.dp), max = 1); T(m.rotulo, 8.5f, Kp.slate500)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) { T("Ver detalhes", 7f, marca.c700, 800); Spacer(Modifier.width(3.dp)); T("→", 7f, marca.c700, 800) }
    }
}

@Composable
private fun Variacao(v: Double, escuro: Boolean = false) {
    val (cor, fundo, ic) = when {
        v > 0 -> Triple(if (escuro) Kp.emerald300 else Color(0xFF16A34A), if (escuro) Color.Transparent else Color(0xFFF0FDF4), Fa.ARROW_TREND_UP)
        v < 0 -> Triple(Kp.red600, Kp.red50, Fa.ARROW_TREND_DOWN)
        else -> Triple(Kp.slate500, Kp.slate100, Fa.MINUS)
    }
    Row(Modifier.sup(999.dp, fundo = fundo).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        FaIcone(ic, 7f, cor); Spacer(Modifier.width(3.dp)); T("%+.1f%%".format(v).replace("+0.0", "0.0"), 7.5f, cor, 800)
    }
}

/** DETALHE INTELIGENTE DO MONITORAMENTO (`#modal-monitor-detail`; openMonitoringDetail, HTML 11603). */
@Composable
fun MonitorDetalheEcra(vm: AdminViewModel, tipo: String, categoria: String, modalidade: String, aoFechar: () -> Unit, aoFicha: (String) -> Unit, aoProduto: (String) -> Unit) {
    val marca = LocalMarca.current
    val us by vm.users.collectAsStateWithLifecycle()
    val pr by vm.presenca.collectAsStateWithLifecycle()
    val aud by vm.auditoria.collectAsStateWithLifecycle()
    val produtos by vm.produtos.collectAsStateWithLifecycle()
    val reservas by vm.reservas.collectAsStateWithLifecycle()
    val intencoes by vm.intencoes.collectAsStateWithLifecycle()
    val agora = System.currentTimeMillis(); val hoje0 = agora - agora % 86_400_000L
    val online = pr.filter { (it.ms("lastSeenAt") ?: 0) > agora - Presenca.JANELA_ONLINE_MS }.sortedByDescending { it.ms("lastSeenAt") ?: 0 }
    val acessos = aud.filter { it.s("action") == "LOGIN" }.sortedByDescending { it.ms("createdAt") ?: 0 }
    val hoje = acessos.filter { (it.ms("createdAt") ?: 0) >= hoje0 }
    val cat = Categoria.entries.firstOrNull { it.name == categoria }; val mod = Modalidade.entries.firstOrNull { it.name == modalidade }
    val classe = produtos.associate { it.id to (it.categoria to it.modalidade) }
    fun noGrupo(id: String) = classe[id].let { (categoria.isBlank() || it?.first == cat) && (modalidade.isBlank() || it?.second == mod) }
    val grupo = if (categoria.isNotBlank()) " · " + rotuloGrupo(cat, mod) else ""
    val vazio: @Composable (String) -> Unit = { T(it, 10f, Kp.gray400, Modifier.fillMaxWidth().padding(vertical = 40.dp), centro = true) }
    val (titulo, subtitulo) = when (tipo) {
        "ONLINE" -> "Utilizadores online agora" to "${online.size} sessão(ões) detectada(s)."
        "USING" -> "Utilizadores usando o app" to "${online.count { it.d["foreground"] != false }} sessão(ões) detectada(s)."
        "VISITS_TODAY" -> "Visitas de hoje" to "${hoje.size} acesso(s) apresentado(s)."
        "ACCESS_HISTORY" -> "Histórico de acessos" to "${acessos.take(150).size} acesso(s) apresentado(s)."
        "UNIQUE_TODAY" -> "Visitantes únicos hoje" to "${hoje.distinctBy { it.s("actorId") }.size} utilizador(es) distinto(s)."
        "USERS" -> "Utilizadores cadastrados" to "${us.size} perfil(is) disponíveis."
        "PRODUCTS" -> (if (categoria.isNotBlank()) "Produtos$grupo" else "Produtos cadastrados") to "${produtos.count { noGrupo(it.id) }} produto(s)."
        "RESERVATIONS" -> (if (categoria.isNotBlank()) "Reservas$grupo" else "Reservas da plataforma") to "${reservas.count { noGrupo(it.s("listingId")) }} reserva(s)."
        else -> (if (tipo == "BALANCES" && categoria.isNotBlank()) "Saldos$grupo" else "Composição dos saldos") to "Valores derivados de anúncios, reservas activas e movimentos de monetização."
    }
    Column(Modifier.background(Kp.slate50)) {
        KpCabecalhoModal("Análise detalhada", titulo, aoFechar, subtitulo = subtitulo, fundo = Kp.slate950, corKicker = Kp.emerald300)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            @Composable fun Pessoa(nome: String, papel: String, dispositivo: String, email: String, presenca: Long?, criado: Long?, aoTocar: (() -> Unit)? = null) {
                Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.gray200).then(if (aoTocar != null) Modifier.toque(aoTocar = aoTocar) else Modifier).padding(12.dp)) {
                    Row { Column(Modifier.weight(1f)) { T(nome.ifBlank { "Utilizador" }, 10f, Kp.gray900, 700); T(Papel.canonico(papel)?.rotulo ?: papel.ifBlank { "Utilizador" }, 8f, Kp.gray400) }; T(dispositivo, 8f, Kp.gray400) }
                    if (email.isNotBlank()) T(email, 8f, Kp.gray500, Modifier.padding(top = 4.dp))
                    presenca?.let { T("Última presença: ${formatarData(it)}", 8f, Kp.emerald600, Modifier.padding(top = 4.dp)) }
                    criado?.let { T(formatarData(it), 8f, Kp.gray400, Modifier.padding(top = 4.dp)) }
                }
            }
            @Composable fun Linha(r: String, v: String, extra: String = "") {
                Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.gray200).padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { T(r, 9f, Kp.gray400, Modifier.weight(1f)); T(v, 10f, Kp.gray800, 700, fim = true) }
                    if (extra.isNotBlank()) T(extra, 8f, Kp.gray400, Modifier.padding(top = 6.dp), linha = 12f)
                }
            }
            when (tipo) {
                "ONLINE", "USING" -> { val l = if (tipo == "ONLINE") online else online.filter { it.d["foreground"] != false }
                    if (l.isEmpty()) vazio("Nenhuma sessão nesta condição.")
                    l.forEach { Pessoa(it.s("name"), it.s("role"), it.s("deviceLabel"), "", it.ms("lastSeenAt"), null) } }
                "VISITS_TODAY", "ACCESS_HISTORY" -> { val l = if (tipo == "VISITS_TODAY") hoje else acessos.take(150)
                    if (l.isEmpty()) vazio("Sem acessos registados.")
                    l.forEach { x -> Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.gray200).padding(12.dp)) {
                        Row { T(x.s("actorName").ifBlank { "Utilizador" }, 10f, Kp.gray800, 700, Modifier.weight(1f)); T(x.ms("createdAt")?.let { formatarData(it) } ?: "", 8f, Kp.gray400) }
                        T("${Papel.canonico(x.s("actorRole"))?.rotulo ?: x.s("actorRole").ifBlank { "Perfil" }} • ${x.s("details").ifBlank { "Login no Kupila" }}", 8f, Kp.gray500, Modifier.padding(top = 4.dp))
                    } } }
                "UNIQUE_TODAY" -> { val l = hoje.distinctBy { it.s("actorId") }; if (l.isEmpty()) vazio("Sem visitantes hoje.")
                    l.forEach { Pessoa(it.s("actorName"), it.s("actorRole"), "", "", null, it.ms("createdAt")) } }
                "USERS" -> us.forEach { u -> Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.gray200).toque { aoFicha(u.id) }.padding(12.dp)) {
                    Column(Modifier.weight(1f)) { T(u.s("name").ifBlank { "Utilizador" }, 10f, Kp.gray900, 700); T("${Papel.canonico(u.s("role"))?.rotulo ?: u.s("role")} • ${u.s("email").ifBlank { "Sem e-mail" }}", 8f, Kp.gray400, max = 1) }
                    val a = u.s("status") == "ACTIVE"; T(if (a) "ACTIVO" else "INACTIVO", 8f, if (a) Kp.emerald600 else Kp.gray400, 700)
                } }
                "PRODUCTS" -> { val l = produtos.filter { noGrupo(it.id) }; if (l.isEmpty()) vazio("Sem produtos neste grupo.")
                    l.forEach { x -> Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.gray200).toque { aoProduto(x.id) }.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) { T(x.titulo, 10f, Kp.gray900, 700, max = 1); T("${x.id} • ${x.municipio}", 8f, Kp.gray400, max = 1) }
                            T(FormatoAoa.formatar(x.preco) + if (x.modalidade != Modalidade.VENDA) " / mês" else "", 9f, marca.c700, 700)
                        }
                        Row(Modifier.padding(top = 8.dp)) {
                            T(rotuloGrupo(x.categoria, x.modalidade), 8f, Kp.gray500, Modifier.weight(1f))
                            T(x.estado.name, 8f, when (x.estado) { EstadoAnuncio.ACTIVO -> Kp.emerald600; EstadoAnuncio.RESERVADO -> Color(0xFFD97706); else -> Kp.gray500 }, 700)
                        }
                    } } }
                "RESERVATIONS" -> { val l = reservas.filter { noGrupo(it.s("listingId")) }; if (l.isEmpty()) vazio("Sem reservas neste grupo.")
                    l.forEach { r -> Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.gray200).padding(12.dp)) {
                        Row { Column(Modifier.weight(1f)) { T(r.s("listingTitle").ifBlank { r.s("listingId") }, 10f, Kp.gray900, 700); T("${r.id} • ${r.s("userName").ifBlank { "Cliente" }}", 8f, Kp.gray400) }; T(aoa(r.n("amount")), 9f, Kp.amber700, 700) }
                        Row(Modifier.padding(top = 8.dp)) { T("Estado: ${r.s("status").ifBlank { "—" }}", 8f, Kp.gray700, Modifier.weight(1f)); T(r.ms("createdAt")?.let { formatarData(it) } ?: "", 8f, Kp.gray500) }
                    } } }
                else -> {
                    val activas = reservas.filter { it.s("status") == "ACTIVE" && noGrupo(it.s("listingId")) }
                    when (tipo) {
                        "BALANCE_ADVERTISED" -> produtos.forEach { Linha(it.titulo, FormatoAoa.formatar(it.preco), rotuloGrupo(it.categoria, it.modalidade)) }
                        "BALANCE_CUSTODY", "BALANCE_OWNER_NET" -> activas.forEach { r -> Linha(r.s("listingTitle"), aoa(if (tipo == "BALANCE_CUSTODY") r.n("amount") else (r.n("amount") - r.n("commissionAmount")).coerceAtLeast(0.0)), "Reserva ${r.id} • ${r.s("userName").ifBlank { "Cliente" }}") }
                        "BALANCE_REVENUE" -> {
                            intencoes.filter { it.s("type") == "LISTING" && it.s("status") in setOf("PUBLISHED", "COMPLETED", "PAID") }.forEach { Linha(it.s("listingTitle").ifBlank { "Taxa de Anúncio" }, aoa(it.n("amount")), "Pagamento confirmado • ${it.ms("confirmedAt")?.let { m -> formatarData(m) } ?: ""}") }
                            reservas.filter { it.n("commissionAmount") > 0 && it.s("status") in setOf("ACTIVE", "COMPLETED", "REFUND_PENDING") }.forEach { Linha(it.s("listingTitle").ifBlank { "Comissão de Reserva" }, aoa(it.n("commissionAmount")), "Sinal confirmado • ${it.ms("createdAt")?.let { m -> formatarData(m) } ?: ""}") }
                        }
                        else -> produtos.filter { noGrupo(it.id) }.groupBy { it.categoria to it.modalidade }.forEach { (k, l) ->
                            val ids = l.map { it.id }.toSet(); val act = activas.filter { it.s("listingId") in ids }
                            Linha(rotuloGrupo(k.first, k.second), FormatoAoa.formatar(Dinheiro.soma(l.map { it.preco })),
                                "Custódia ${aoa(act.sumOf { it.n("amount") })} • Líquido proprietário ${aoa(act.sumOf { (it.n("amount") - it.n("commissionAmount")).coerceAtLeast(0.0) })} • Receita ${aoa(act.sumOf { it.n("commissionAmount") })}")
                        }
                    }
                }
            }
        }
    }
}