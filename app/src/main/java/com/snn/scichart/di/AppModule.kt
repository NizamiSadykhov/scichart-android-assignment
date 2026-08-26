package com.snn.scichart.di

import com.snn.scichart.BuildConfig
import com.snn.scichart.core.time.MillisClock
import com.snn.scichart.core.time.SystemMillisClock
import com.snn.scichart.data.repository.InMemoryPointSourceRepository
import com.snn.scichart.data.source.RandomWalkSourceFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Обозначает область корутин, время жизни которой совпадает с singleton-компонентом Hilt. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationCoroutineScope

/** Обозначает диспетчер для фоновых вычислений, нагружающих процессор. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Обозначает частоту поступления точек, настраиваемую отдельно для production и benchmark. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PointIntervalMillis

/**
 * Описывает инфраструктуру и зависимости слоя данных уровня приложения.
 *
 * Hilt хранит один репозиторий и одну область корутин в течение жизни процесса приложения.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Предоставляет общий диспетчер для фоновой работы генераторов и репозитория. */
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /** Предоставляет контролируемую область, переживающую пересоздание Activity и экранов. */
    @Provides
    @Singleton
    @ApplicationCoroutineScope
    fun provideApplicationCoroutineScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Связывает абстракцию времени ядра с системными часами. */
    @Provides
    @Singleton
    fun provideMillisClock(): MillisClock = SystemMillisClock

    /** Предоставляет общий источник случайности для настройки генераторов. */
    @Provides
    @Singleton
    fun provideRandom(): Random = Random.Default

    /**
     * В обычной сборке возвращает требуемую одну секунду; benchmark-вариант ускоряет поток,
     * чтобы воспроизводимо накопить тысячи точек до начала измеряемого взаимодействия.
     */
    @Provides
    @PointIntervalMillis
    fun providePointIntervalMillis(): Long = BuildConfig.POINT_INTERVAL_MILLIS

    /** Создаёт единственный репозиторий и принадлежащий ему in-memory источник истины. */
    @Provides
    @Singleton
    fun provideInMemoryPointSourceRepository(
        sourceFactory: RandomWalkSourceFactory,
        @ApplicationCoroutineScope applicationScope: CoroutineScope,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): InMemoryPointSourceRepository = InMemoryPointSourceRepository(
        pointSources = sourceFactory.create(),
        scope = applicationScope,
        dispatcher = dispatcher,
    )
}
