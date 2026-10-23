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
class KupilaApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var conta: ContaActiva

    override fun onCreate() {
        super.onCreate()
        ArranqueFirebase.iniciar(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { PrimeiroPlano.activo.value = true }
            override fun onStop(owner: LifecycleOwner) { PrimeiroPlano.activo.value = false }
        })
        conta.iniciar()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
