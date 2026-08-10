package com.arcx.core.common.di

import com.arcx.core.common.time.SystemTimeSource
import com.arcx.core.common.time.TimeSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {

    @Binds
    abstract fun bindTimeSource(impl: SystemTimeSource): TimeSource
}
