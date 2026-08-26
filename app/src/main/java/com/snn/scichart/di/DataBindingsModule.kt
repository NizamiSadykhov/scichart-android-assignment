package com.snn.scichart.di

import com.snn.scichart.data.repository.PointSourceRepository
import com.snn.scichart.data.repository.InMemoryPointSourceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Связывает публичные контракты data-слоя с их рабочими реализациями. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    /** Скрывает in-memory реализацию за контрактом репозитория. */
    @Binds
    abstract fun bindPointSourceRepository(
        repository: InMemoryPointSourceRepository,
    ): PointSourceRepository
}
