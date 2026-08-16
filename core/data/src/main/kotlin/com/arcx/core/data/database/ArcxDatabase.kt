package com.arcx.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [WorkflowEntity::class, ProviderEntity::class, RunEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ArcxDatabase : RoomDatabase() {
    abstract fun workflowDao(): WorkflowDao
    abstract fun providerDao(): ProviderDao
    abstract fun runDao(): RunDao

    companion object {
        const val NAME = "arcx.db"
    }
}
