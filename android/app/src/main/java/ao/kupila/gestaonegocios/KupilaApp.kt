package ao.kupila.gestaonegocios

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.remoto.ArranqueFirebase
import ao.kupila.gestaonegocios.dados.sessao.PrimeiroPlano
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KupilaApp : Application(), Configuration.Provider, coil3.SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var conta: ContaActiva

    override fun onCreate() {
        super.onCreate()
        ao.kupila.gestaonegocios.dados.rede.Economia.inicializar(this)
        ArranqueFirebase.iniciar(this)
        ao.kupila.gestaonegocios.dados.actualizacao.ActualizadorApp.inicializar(this)
        // Verificação de versão no GitHub: zero quota do Firestore e no máximo de hora a hora (cache).
        ao.kupila.gestaonegocios.dados.actualizacao.ActualizadorApp.verificar(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                PrimeiroPlano.activo.value = true
                ao.kupila.gestaonegocios.dados.rede.RedeFirestore.primeiroPlano()
                // Abrir o app é um pedido explícito da pessoa: conta como toque para a sincronização delta do regresso.
                ao.kupila.gestaonegocios.dados.rede.Economia.tocou()
                conta.aoVoltar()
            }
            override fun onStop(owner: LifecycleOwner) { PrimeiroPlano.activo.value = false; ao.kupila.gestaonegocios.dados.rede.RedeFirestore.segundoPlano() }
        })
        conta.iniciar()
    }

    /**
     * Cache inteligente das fotografias: memória (20% da RAM da app) + disco PERSISTENTE na pasta da app (o sistema não a apaga
     * como apaga a cache) — o catálogo já visto abre com fotos mesmo sem internet e cada foto só é descarregada uma vez.
     */
    override fun newImageLoader(context: coil3.PlatformContext): coil3.ImageLoader = coil3.ImageLoader.Builder(context)
        .memoryCache { coil3.memory.MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
        .diskCache { coil3.disk.DiskCache.Builder().directory(okio.Path.Companion.run { filesDir.resolve("imagens").toOkioPath() }).maxSizeBytes(300L * 1024 * 1024).build() }
        .build()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
