package com.arcx.core.common.time

import java.time.Instant
import java.time.ZoneId

/**
 * Local calendar midnight, not "twenty-four hours ago" — "runs today" has to reset at midnight
 * or the number means nothing to the person reading it, and this is the same boundary the
 * Activity list groups by, so a run at 01:00 is counted under the heading it is filed under.
 *
 * Home and Activity both bound a query with this and each carried its own byte-identical copy.
 * Two definitions of when today starts is one edit away from the two screens disagreeing.
 */
fun startOfDay(nowMillis: Long): Long {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone)
        .toInstant().toEpochMilli()
}
