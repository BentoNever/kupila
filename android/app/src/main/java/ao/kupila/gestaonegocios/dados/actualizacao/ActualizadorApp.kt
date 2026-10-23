package ao.kupila.gestaonegocios.dados.actualizacao

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import ao.kupila.gestaonegocios.dominio.actualizacao.DistribuicaoApp
import ao.kupila.gestaonegocios.dominio.actualizacao.PoliticaVersao
import ao.kupila.gestaonegocios.dominio.actualizacao.PublicacaoGithub
import ao.kupila.gestaonegocios.dominio.actualizacao.ValidacaoActualizacao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

data class ProgressoActualizacao(val etapa: String = "Escolha o APK oficial", val percentagem: Int = 0,
    val ocupado: Boolean = false, val terminou: Boolean = false, val erro: Boolean = false, val retomar: Boolean = false,
    /** "download" | "instalar" | "". */
    val fase: String = "", val bytes: Long = 0, val total: Long = 0, val bytesPorSeg: Long = 0, val etaSeg: Long = -1,
    /** A falha foi no download e há bytes guardados: "Continuar" retoma de onde parou. */
    val podeRetomarDownload: Boolean = false)

/**
 * Actualizador do app, portado do PDV Digital (`ActualizadorApp`): consulta os releases públicos no GitHub (zero quota do Firestore,
 * no máximo de hora a hora), valida o manifesto, descarrega com retoma, confere o SHA-256, o pacote e a assinatura e instala pela
 * sessão do Android (sem confirmação quando o Android o permite). Depois reabre a app no mesmo ponto.
 */
object ActualizadorApp {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _estado = MutableStateFlow(ProgressoActualizacao())
    val estado = _estado.asStateFlow()
    val confirmacao = MutableStateFlow<Intent?>(null)
    private var inicializado = false
    private fun prefs(c: Context) = c.getSharedPreferences("kupila_actualizacao", Context.MODE_PRIVATE)
    private fun codigo(p: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()
    @Suppress("DEPRECATION") private fun certificados(p: PackageInfo): Set<String> =
        (if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures).orEmpty().map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }
        }.toSet()
    @Suppress("DEPRECATION") private fun flags() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Synchronized fun inicializar(contexto: Context) {
        if (inicializado) return
        inicializado = true
        val c = contexto.applicationContext
        val p = prefs(c)
        val alvo = p.getLong("versao", 0)
        val instalado = c.packageManager.getPackageInfo(c.packageName, 0)
        if (alvo == 0L && p.getBoolean("concluida", false)) _estado.value = ProgressoActualizacao("Actualização concluída", 100, terminou = true)
        if (alvo > 0 && codigo(instalado) >= alvo && instalado.lastUpdateTime >= p.getLong("inicio", Long.MAX_VALUE)) concluir(c)
        else {
            val id = p.getInt("sessao", -1)
            val info = if (id >= 0) c.packageManager.packageInstaller.getSessionInfo(id) else null
            if (info != null && info.isSealed) _estado.value = ProgressoActualizacao("Actualização em curso no Android", (60 + info.progress * 39).toInt(), ocupado = true, retomar = true)
            else if (info != null) { runCatching { c.packageManager.packageInstaller.abandonSession(id) }; falhar(c, "A preparação foi interrompida. Tente novamente; os seus dados foram preservados.") }
            else if (alvo > 0) falhar(c, "A actualização foi interrompida. Os dados foram preservados; pode tentar novamente.")
        }
        limparGuardados(c)
        c.packageManager.packageInstaller.registerSessionCallback(object : PackageInstaller.SessionCallback() {
            override fun onCreated(sessionId: Int) {}
            override fun onBadgingChanged(sessionId: Int) {}
            override fun onActiveChanged(sessionId: Int, active: Boolean) {}
            override fun onProgressChanged(sessionId: Int, progress: Float) {
                if (sessionId == p.getInt("sessao", -1)) _estado.update { a -> if (a.ocupado && a.percentagem >= 60) a.copy(percentagem = maxOf(a.percentagem, (60 + progress.coerceIn(0f, 1f) * 39).toInt())) else a }
            }
            override fun onFinished(sessionId: Int, success: Boolean) {
                if (sessionId == p.getInt("sessao", -1)) { if (success) concluir(c) else if (!_estado.value.erro) falhar(c, "A instalação não foi concluída. A versão anterior e os seus dados permanecem disponíveis.") }
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun reabrirDepoisDeActualizar(c: Context) {
        prefs(c).edit().remove("reabrir").apply()
        val abrir = c.packageManager.getLaunchIntentForPackage(c.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return
        runCatching { c.startActivity(abrir) }
        runCatching {
            val nm = c.getSystemService(android.app.NotificationManager::class.java)
            nm.createNotificationChannel(android.app.NotificationChannel("actualizacao", "Actualizações do app", android.app.NotificationManager.IMPORTANCE_HIGH))
            val versao = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull().orEmpty()
            val toque = PendingIntent.getActivity(c, 7001, abrir, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(7001, androidx.core.app.NotificationCompat.Builder(c, "actualizacao").setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Kupila-Gestão de negócios actualizado ($versao)").setContentText("Toque para continuar exactamente onde estava.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH).setContentIntent(toque).setAutoCancel(true).build())
        }
    }

    private fun progredir(etapa: String, percentagem: Int) { _estado.update { if (it.ocupado) it.copy(etapa = etapa, percentagem = maxOf(it.percentagem, percentagem.coerceIn(0, 99))) else it } }

    fun retomar(contexto: Context) {
        if (!_estado.value.retomar) return
        _estado.value = _estado.value.copy(retomar = false)
        val c = contexto.applicationContext
        escopo.launch {
            try {
                val id = prefs(c).getInt("sessao", -1)
                val retorno = PendingIntent.getBroadcast(c, id, Intent(c, ResultadoActualizacao::class.java).setAction("${c.packageName}.ACTUALIZACAO"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                c.packageManager.packageInstaller.openSession(id).use { it.commit(retorno.intentSender) }
            } catch (e: Exception) { falhar(c, "Não foi possível retomar. Tente novamente.") }
        }
    }

    /** APK manual (alternativa ao download): mesmas verificações de pacote, versão e assinatura. */
    @Synchronized fun instalar(contexto: Context, origem: Uri) {
        inicializar(contexto)
        if (_estado.value.ocupado) return
        val c = contexto.applicationContext
        _estado.value = ProgressoActualizacao("A preparar o ficheiro", ocupado = true)
        escopo.launch {
            val ficheiro = File(c.cacheDir, "actualizacao-manual.apk")
            var sessaoId = -1
            try {
                require(c.packageManager.canRequestPackageInstalls()) { "Autorize o Kupila-Gestão de negócios a instalar actualizações nas definições do Android." }
                val tamanho = c.contentResolver.openAssetFileDescriptor(origem, "r")?.use { it.length } ?: -1
                var copiado = 0L
                c.contentResolver.openInputStream(origem)?.use { entrada -> ficheiro.outputStream().use { saida ->
                    val b = ByteArray(64 * 1024)
                    while (true) { val n = entrada.read(b); if (n < 0) break; copiado += n; require(copiado <= 250L * 1024 * 1024) { "O ficheiro excede o limite de 250 MB." }
                        saida.write(b, 0, n); if (tamanho > 0) progredir("A preparar o ficheiro", ((copiado * 50 / tamanho).toInt()).coerceIn(0, 50)) }
                } } ?: error("Não foi possível ler o APK. Escolha novamente o ficheiro.")
                require(copiado > 0) { "O ficheiro está vazio." }
                sessaoId = instalarFicheiro(c, ficheiro)
            } catch (e: Exception) {
                if (sessaoId >= 0) runCatching { c.packageManager.packageInstaller.abandonSession(sessaoId) }
                falhar(c, e.message ?: "Não foi possível actualizar. Os dados existentes foram preservados.")
            } finally { ficheiro.delete() }
        }
    }

    // ── Verificação online (GitHub; zero quota do Firestore; no máximo de hora a hora) ──
    data class VersaoDisponivel(val codigo: Long, val nome: String, val novidades: List<String>, val apk: String, val tamanho: Long, val sha256: String?, val obrigatoria: Boolean)
    private val _disponivel = MutableStateFlow<VersaoDisponivel?>(null)
    val disponivel = _disponivel.asStateFlow()
    private val _verificacao = MutableStateFlow<String?>(null)
    val verificacao = _verificacao.asStateFlow()
    data class ResultadoVerificacao(val instalada: String, val publicada: VersaoDisponivel, val situacao: PoliticaVersao.Situacao, val em: Long)
    private val _resultadoVerificacao = MutableStateFlow<ResultadoVerificacao?>(null)
    val resultadoVerificacao = _resultadoVerificacao.asStateFlow()
    private val _aVerificar = MutableStateFlow(false)
    val aVerificar = _aVerificar.asStateFlow()
    private fun prefsRemotas(c: Context) = c.getSharedPreferences("kupila_actualizacao_remota", Context.MODE_PRIVATE)

    @Synchronized fun verificar(contexto: Context, forcar: Boolean = false) {
        if (_aVerificar.value) return
        val c = contexto.applicationContext
        val p = prefsRemotas(c)
        val instalado = runCatching { codigo(c.packageManager.getPackageInfo(c.packageName, 0)) }.getOrDefault(0L)
        val nomeInstalado = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull().orEmpty()
        _disponivel.value = _disponivel.value?.takeIf { it.codigo > instalado }
        if (_disponivel.value == null) p.getString("json", null)?.let { ler(it, c.packageName) }?.takeIf { it.codigo > instalado }?.let { _disponivel.value = it }
        _aVerificar.value = true
        _resultadoVerificacao.value = null
        escopo.launch {
            _verificacao.value = "A procurar actualizações…"
            try {
                val v = consultarPublicacao(c, forcar)
                _disponivel.value = v.takeIf { it.codigo > instalado }
                val situacao = PoliticaVersao.situacao(instalado, v.codigo)
                _resultadoVerificacao.value = ResultadoVerificacao(nomeInstalado, v, situacao, System.currentTimeMillis())
                _verificacao.value = when (situacao) {
                    PoliticaVersao.Situacao.NOVA_VERSAO -> "Nova versão ${v.nome} disponível"
                    PoliticaVersao.Situacao.ACTUALIZADA -> "O app já está atualizado. A versão $nomeInstalado é a última atualização publicada."
                    PoliticaVersao.Situacao.PUBLICACAO_ANTERIOR -> "A versão instalada é mais recente que a publicação consultada (${v.nome})."
                }
            } catch (e: CancellationException) { throw e
            } catch (e: java.io.IOException) { _verificacao.value = "Não foi possível confirmar a versão mais recente. Verifique a ligação e tente novamente."
            } catch (e: Exception) { _verificacao.value = "Não foi possível validar a publicação oficial. Tente novamente."
            } finally { _aVerificar.value = false }
        }
    }

    private val consulta = Mutex()
    private suspend fun consultarPublicacao(c: Context, forcar: Boolean): VersaoDisponivel = consulta.withLock {
        val p = prefsRemotas(c)
        val guardada = p.getString("json", null)?.let { ler(it, c.packageName) }
        val agora = System.currentTimeMillis()
        if (!forcar && guardada != null && agora - p.getLong("githubEm", 0) in 0..3_600_000L) return@withLock guardada
        val ligacao = (java.net.URL("${DistribuicaoApp.API_ULTIMA}?per_page=10").openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 6_000; readTimeout = 8_000; useCaches = false
            setRequestProperty("Accept", "application/vnd.github+json"); setRequestProperty("User-Agent", "Kupila-Gestao-de-negocios")
        }
        val texto = try {
            if (ligacao.responseCode != 200) throw java.io.IOException("Publicacao indisponivel")
            ligacao.inputStream.use { String(it.readBytes().take(512 * 1024).toByteArray(), Charsets.UTF_8) }
        } finally { ligacao.disconnect() }
        val linha = PublicacaoGithub.melhor(texto, c.packageName) ?: throw java.io.IOException("Sem publicacao oficial valida")
        val remoto = ler(linha, c.packageName) ?: throw java.io.IOException("Manifesto invalido")
        // Uma fonte antiga nunca apaga uma actualização superior já conhecida.
        val escolhido = if (guardada != null && guardada.codigo > remoto.codigo) guardada to p.getString("json", "")!! else remoto to linha
        p.edit().putString("json", escolhido.second).putLong("githubEm", agora).apply()
        escolhido.first
    }

    private fun ler(texto: String, pacote: String): VersaoDisponivel? = runCatching {
        val o = org.json.JSONObject(texto)
        val apk = o.getString("apk")
        PoliticaVersao.validar(o.getLong("versionCode"), o.getString("versionName"), apk, o.getLong("tamanho"), o.getString("sha256"), pacote)
        VersaoDisponivel(o.getLong("versionCode"), o.getString("versionName"), o.optJSONArray("novidades")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            apk, o.optLong("tamanho", -1), o.optString("sha256").takeIf { it.length == 64 }?.lowercase(), o.optBoolean("obrigatoria", false))
    }.getOrNull()

    /** Um toque: descarrega (com retoma), confere o SHA-256 e a assinatura, e instala. */
    @Synchronized fun descarregarEInstalar(contexto: Context) {
        inicializar(contexto)
        val v = _disponivel.value ?: return
        if (_estado.value.ocupado) return
        val c = contexto.applicationContext
        _estado.value = ProgressoActualizacao("A descarregar a versão ${v.nome}", ocupado = true)
        escopo.launch {
            val ficheiro = File(c.cacheDir, "actualizacao-${v.codigo}.apk")
            val parte = File(ficheiro.path + ".parte")
            var sessaoId = -1
            try {
                require(c.packageManager.canRequestPackageInstalls()) { "Autorize o Kupila-Gestão de negócios a instalar actualizações nas definições do Android." }
                val esperado = v.sha256
                if (esperado != null && ficheiro.exists() && ficheiro.length() > 0) {
                    progredir("A verificar o ficheiro já descarregado", 45)
                    if (sha256(ficheiro) == esperado) { progredir("Ficheiro já descarregado e verificado", 50); sessaoId = instalarFicheiro(c, ficheiro); return@launch }
                    ficheiro.delete()
                }
                var total = v.tamanho
                var tentativa = 0
                val medidor = Velocidade()
                while (true) {
                    val jaTenho = if (parte.exists()) parte.length() else 0L
                    if (total > 0 && jaTenho >= total) break
                    try {
                        val ligacao = (java.net.URL(v.apk).openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
                            if (jaTenho > 0) setRequestProperty("Range", "bytes=$jaTenho-")
                        }
                        val http = ligacao.responseCode
                        val continua = http == 206
                        if (!continua && jaTenho > 0) parte.delete()
                        if (http !in 200..299) throw java.io.IOException("HTTP $http")
                        val base = if (continua) jaTenho else 0L
                        val corpo = ligacao.contentLengthLong
                        if (corpo > 0) total = base + corpo
                        var lido = base
                        ligacao.inputStream.use { e -> java.io.FileOutputStream(parte, continua).use { s ->
                            val b = ByteArray(64 * 1024)
                            while (true) { val n = e.read(b); if (n < 0) break; lido += n; require(lido <= 250L * 1024 * 1024) { "O ficheiro excede o limite de 250 MB." }
                                s.write(b, 0, n); medidor.registar(n.toLong()); if (total > 0) progressoDownload(v.nome, lido, total, medidor.bytesPorSeg()) }
                        } }
                        if (total > 0 && lido < total) throw java.io.IOException("download incompleto")
                        break
                    } catch (e: java.io.IOException) {
                        tentativa++
                        if (tentativa >= 5) throw e
                        _estado.update { it.copy(etapa = "Rede instável · a retomar de ${(if (parte.exists()) parte.length() else 0L) / 1_048_576} MB (tentativa ${tentativa + 1} de 5)") }
                        delay(2_000L * tentativa)
                    }
                }
                require(parte.exists() && parte.length() > 0) { "O download veio vazio. Tente novamente." }
                if (total > 0) require(parte.length() == total) { "O download ficou incompleto. Toque em Continuar para retomar." }
                esperado?.let { progredir("A verificar o ficheiro", 50); if (sha256(parte) != it) { parte.delete(); error("O ficheiro descarregado não corresponde ao publicado. Nada foi instalado.") } }
                ficheiro.delete()
                check(parte.renameTo(ficheiro)) { "Não foi possível guardar o ficheiro descarregado." }
                sessaoId = instalarFicheiro(c, ficheiro)
                if (esperado == null) ficheiro.delete()
            } catch (e: java.io.IOException) {
                if (sessaoId >= 0) runCatching { c.packageManager.packageInstaller.abandonSession(sessaoId) }
                val mb = (if (parte.exists()) parte.length() else 0L) / 1_048_576
                falhar(c, "A ligação falhou aos $mb MB. Toque em Continuar: o download retoma de onde parou.")
                _estado.update { it.copy(podeRetomarDownload = parte.exists() && parte.length() > 0) }
            } catch (e: Exception) {
                if (sessaoId >= 0) runCatching { c.packageManager.packageInstaller.abandonSession(sessaoId) }
                falhar(c, e.message ?: "Não foi possível actualizar. Os dados existentes foram preservados.")
            }
        }
    }

    private fun progressoDownload(versao: String, lido: Long, total: Long, bps: Long) {
        val eta = if (bps > 0) (total - lido) / bps else -1
        _estado.update { if (!it.ocupado) it else it.copy(etapa = "A descarregar a versão $versao", fase = "download", bytes = lido, total = total, bytesPorSeg = bps, etaSeg = eta,
            percentagem = maxOf(it.percentagem, (lido * 50 / total).toInt().coerceIn(0, 50))) }
    }

    private class Velocidade {
        private var inicio = System.nanoTime(); private var acumulado = 0L; private var media = 0.0
        fun registar(n: Long) { acumulado += n; val dt = (System.nanoTime() - inicio) / 1e9
            if (dt >= 1.0) { val inst = acumulado / dt; media = if (media == 0.0) inst else media * 0.7 + inst * 0.3; acumulado = 0; inicio = System.nanoTime() } }
        fun bytesPorSeg() = media.toLong()
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { e -> val b = ByteArray(64 * 1024); while (true) { val n = e.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun limparGuardados(c: Context) {
        val instalado = runCatching { codigo(c.packageManager.getPackageInfo(c.packageName, 0)) }.getOrDefault(Long.MAX_VALUE)
        c.cacheDir.listFiles()?.forEach { f -> Regex("^actualizacao-(\\d+)\\.apk(\\.parte)?$").find(f.name)?.groupValues?.get(1)?.toLongOrNull()?.let { if (it <= instalado) f.delete() } }
    }

    private fun instalarFicheiro(c: Context, ficheiro: File): Int {
        var sessaoId = -1
        try {
            progredir("A verificar a versão e assinatura", 50)
            val novo = c.packageManager.getPackageArchiveInfo(ficheiro.absolutePath, flags()) ?: error("APK inválido ou incompleto.")
            val actual = c.packageManager.getPackageInfo(c.packageName, flags())
            ValidacaoActualizacao.verificar(novo.packageName, c.packageName, codigo(novo), codigo(actual), certificados(novo), certificados(actual))
            val instalador = c.packageManager.packageInstaller
            val parametros = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(c.packageName); setSize(ficheiro.length())
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                if (Build.VERSION.SDK_INT >= 34) setRequestUpdateOwnership(true)
            }
            sessaoId = instalador.createSession(parametros)
            check(prefs(c).edit().putInt("sessao", sessaoId).putLong("versao", codigo(novo)).putLong("inicio", System.currentTimeMillis()).commit()) { "Não foi possível guardar o estado da actualização." }
            instalador.openSession(sessaoId).use { s ->
                s.openWrite("base.apk", 0, ficheiro.length()).use { saida -> ficheiro.inputStream().use { e ->
                    val b = ByteArray(64 * 1024); var escrito = 0L
                    while (true) { val n = e.read(b); if (n < 0) break; saida.write(b, 0, n); escrito += n
                        val f = escrito.toFloat() / ficheiro.length(); s.setStagingProgress(f)
                        _estado.update { if (it.ocupado) it.copy(etapa = "A instalar a actualização", fase = "instalar", percentagem = maxOf(it.percentagem, 50 + (f * 10).toInt())) else it } }
                }; s.fsync(saida) }
                val retorno = PendingIntent.getBroadcast(c, sessaoId, Intent(c, ResultadoActualizacao::class.java).setAction("${c.packageName}.ACTUALIZACAO"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                prefs(c).edit().putBoolean("reabrir", true).apply()
                _estado.update { it.copy(etapa = "A instalar a actualização", fase = "instalar", percentagem = maxOf(it.percentagem, 60)) }
                s.commit(retorno.intentSender)
            }
            return sessaoId
        } catch (e: Exception) { if (sessaoId >= 0) runCatching { c.packageManager.packageInstaller.abandonSession(sessaoId) }; throw e }
    }

    fun receber(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_MY_PACKAGE_REPLACED) { val reabrir = prefs(c).getBoolean("reabrir", false); inicializar(c); if (reabrir) reabrirDepoisDeActualizar(c); return }
        if (i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != prefs(c).getInt("sessao", -2)) return
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val pedido = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmacao.value = pedido
                _estado.value = _estado.value.copy(etapa = "Confirme a actualização no Android", ocupado = true)
            }
            PackageInstaller.STATUS_SUCCESS -> concluir(c)
            else -> falhar(c, when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_FAILURE_ABORTED -> "Actualização cancelada. A versão instalada e os dados foram preservados."
                PackageInstaller.STATUS_FAILURE_STORAGE -> "Espaço insuficiente. Liberte espaço no dispositivo e tente novamente."
                else -> "O Android não concluiu a instalação. Verifique o APK oficial e tente novamente."
            })
        }
    }

    private fun concluir(c: Context) {
        prefs(c).edit().clear().putBoolean("concluida", true).apply()
        confirmacao.value = null; _disponivel.value = null; limparGuardados(c)
        _estado.value = ProgressoActualizacao("Actualização concluída", 100, terminou = true)
    }

    private fun falhar(c: Context, mensagem: String) {
        prefs(c).edit().clear().apply(); confirmacao.value = null
        _estado.value = ProgressoActualizacao(mensagem, _estado.value.percentagem.coerceAtMost(99), erro = true)
    }
}

class ResultadoActualizacao : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { ActualizadorApp.receber(context, intent) }
}
