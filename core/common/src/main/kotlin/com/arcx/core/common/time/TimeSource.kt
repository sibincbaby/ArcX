package com.arcx.core.common.time

import javax.inject.Inject
import javax.inject.Singleton

/** Wall-clock reads go through this so tests can pin time instead of racing it. */
fun interface TimeSource {
    fun nowMillis(): Long
}

@Singleton
class SystemTimeSource @Inject constructor() : TimeSource {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
