package ao.kupila.gestaonegocios.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Os serviços singleton (Central, SessaoRepositorio, Dispositivo, Cofre, GestorBases, AjustesRepositorio, Geografia,
 * ContaActiva) são injectados por construtor (@Inject). Os repositórios por conta são criados por [ao.kupila.gestaonegocios.dados.ContaActiva].
 */
@Module
@InstallIn(SingletonComponent::class)
object Modulos
