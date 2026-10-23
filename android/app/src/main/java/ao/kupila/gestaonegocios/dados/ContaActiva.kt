package ao.kupila.gestaonegocios.dados

import ao.kupila.gestaonegocios.dados.ajustes.AjustesRepositorio
import ao.kupila.gestaonegocios.dados.catalogo.CatalogoRepositorio
import ao.kupila.gestaonegocios.dados.local.BaseDadosConta
import ao.kupila.gestaonegocios.dados.local.GestorBases
import ao.kupila.gestaonegocios.dados.sessao.EstadoSessao
import ao.kupila.gestaonegocios.dados.sessao.SessaoRepositorio
import ao.kupila.gestaonegocios.dominio.perfis.Perfil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Recursos de UMA conta (base Room própria, catálogo, escutas). Recriados a cada troca de conta. */
class RecursosConta(val perfil: Perfil, val sessaoId: String, val base: BaseDadosConta, val catalogo: CatalogoRepositorio, val offline: Boolean,
                    val eventos: ao.kupila.gestaonegocios.dados.eventos.EventosConta)

/**
 * Liga o ciclo de vida da sessão aos dados (secção 9): ao entrar abre a base da conta e as escutas; ao sair ou trocar
 * de conta encerra-as, para que nenhum dado privado ou fila da conta anterior fique acessível ao novo utilizador.
 */
@Singleton
class ContaActiva @Inject constructor(
    private val sessao: SessaoRepositorio,
    private val bases: GestorBases,
    private val ajustes: AjustesRepositorio,
) {
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _recursos = MutableStateFlow<RecursosConta?>(null)
    val recursos: StateFlow<RecursosConta?> = _recursos.asStateFlow()

    /** Regresso ao app (1.º plano): sincroniza o essencial por delta, se o cache tiver mais de 5 min. */
    fun aoVoltar() {
        val r = _recursos.value ?: return
        if (r.offline) return
        ambito.launch { r.catalogo.sincronizar(automatica = true) }
        ambito.launch {
            val ultima = r.base.meta().obter("eventos")?.em ?: 0L
            if (System.currentTimeMillis() - ultima > 5 * 60_000L) r.eventos.actualizar(r.eventos.essenciais, automatica = true)
        }
    }

    fun iniciar() {
        ajustes.escutarIdentidade()
        ambito.launch {
            sessao.estado.collect { e ->
                val actual = _recursos.value
                when (e) {
                    is EstadoSessao.Activa -> {
                        if (actual != null && actual.perfil.uid == e.perfil.uid && actual.sessaoId == e.sessaoId) {
                            if (actual.perfil != e.perfil || actual.offline != e.offline) _recursos.value = RecursosConta(e.perfil, e.sessaoId, actual.base, actual.catalogo, e.offline, actual.eventos)
                            if (actual.offline && !e.offline) { actual.catalogo.iniciar(); ambito.launch { actual.eventos.actualizar(actual.eventos.essenciais, automatica = true) } }
                            return@collect
                        }
                        actual?.catalogo?.parar(); actual?.eventos?.parar()
                        val base = bases.para(e.perfil.uid)
                        val cat = CatalogoRepositorio(base, e.perfil.uid, e.perfil.papel)
                        val ev = ao.kupila.gestaonegocios.dados.eventos.EventosConta(base, e.perfil)
                        _recursos.value = RecursosConta(e.perfil, e.sessaoId, base, cat, e.offline, ev)
                        if (!e.offline) { cat.iniciar(); ambito.launch { ev.actualizar(ev.essenciais, automatica = true) }; ajustes.escutarAjustes() }
                    }
                    else -> if (actual != null) {
                        actual.catalogo.parar(); actual.eventos.parar()
                        ajustes.pararAjustes()
                        bases.fechar()
                        _recursos.value = null
                    }
                }
            }
        }
        sessao.restaurar()
    }
}
