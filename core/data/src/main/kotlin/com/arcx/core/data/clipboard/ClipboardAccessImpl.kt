package com.arcx.core.data.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.arcx.core.domain.capture.ClipboardAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardAccessImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ClipboardAccess {

    private val manager: ClipboardManager?
        get() = context.getSystemService(ClipboardManager::class.java)

    /**
     * Android 10+ returns nothing to an app that does not hold window focus, and some OEM
     * clipboards throw instead of returning empty. Neither is a failure worth surfacing: the
     * caller's job is to fall back to another input source, so an empty clipboard reads as null.
     */
    override fun read(): String? = try {
        manager?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.takeIf { it.isNotEmpty() }
    } catch (e: RuntimeException) {
        null
    }

    override fun write(label: String, text: String) {
        manager?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
