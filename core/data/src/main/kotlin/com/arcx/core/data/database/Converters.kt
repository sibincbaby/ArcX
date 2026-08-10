package com.arcx.core.data.database

import androidx.room.TypeConverter
import com.arcx.core.model.InputSource
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.ProviderType
import com.arcx.core.model.RunStatus
import com.arcx.core.model.WorkflowCategory

/**
 * Enums are stored by name rather than ordinal so that reordering an enum — which happens every
 * time a new provider or output target is added — cannot silently rewrite existing rows.
 */
class Converters {
    @TypeConverter fun fromCategory(value: WorkflowCategory): String = value.name

    @TypeConverter fun toCategory(value: String): WorkflowCategory =
        enumValueOf<WorkflowCategory>(value)

    @TypeConverter fun fromInputSource(value: InputSource): String = value.name

    @TypeConverter fun toInputSource(value: String): InputSource = enumValueOf<InputSource>(value)

    @TypeConverter fun fromOutputTarget(value: OutputTarget): String = value.name

    @TypeConverter fun toOutputTarget(value: String): OutputTarget = enumValueOf<OutputTarget>(value)

    @TypeConverter fun fromProviderType(value: ProviderType): String = value.name

    @TypeConverter fun toProviderType(value: String): ProviderType = enumValueOf<ProviderType>(value)

    @TypeConverter fun fromRunStatus(value: RunStatus): String = value.name

    @TypeConverter fun toRunStatus(value: String): RunStatus = enumValueOf<RunStatus>(value)
}
