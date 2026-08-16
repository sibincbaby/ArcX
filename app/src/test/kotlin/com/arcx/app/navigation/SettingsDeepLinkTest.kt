package com.arcx.app.navigation

import com.arcx.feature.home.SurfaceSubject
import com.arcx.feature.settings.SettingsDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Home's surface strip is a control, not a signpost, and this is the join that decides whether it
 * still is. Both halves have already drifted apart once: the accessibility grant moved off Entry
 * points onto Permissions and the "Screen text off" chip went on landing where the grant used to
 * be, two taps from the switch it was reporting on.
 *
 * The half these tests cannot reach is [SettingsRoute]'s restore behaviour — that the argument is
 * read once and never re-applied — because it is `rememberSaveable` semantics and there is no
 * Compose test harness in this project. See the report note; it is verified by inspection only.
 */
class SettingsDeepLinkTest {

    @Test
    fun `the screen text chip reaches the screen that owns the grant`() {
        assertEquals(
            SettingsDestination.PERMISSIONS,
            settingsDestinationFor(SurfaceSubject.SCREEN_TEXT),
        )
    }

    @Test
    fun `the sidebar chip reaches the screen that owns the switch`() {
        assertEquals(
            SettingsDestination.ENTRY_POINTS,
            settingsDestinationFor(SurfaceSubject.SIDEBAR),
        )
    }

    @Test
    fun `the surfaces the manifest guarantees reach the list they are described on`() {
        assertEquals(SettingsDestination.ENTRY_POINTS, settingsDestinationFor(SurfaceSubject.SHARE))
        assertEquals(
            SettingsDestination.ENTRY_POINTS,
            settingsDestinationFor(SurfaceSubject.SELECTION),
        )
    }

    @Test
    fun `every destination survives the trip through a route string`() {
        SettingsDestination.entries.forEach { destination ->
            val argument = settingsRoute(destination).substringAfter('=')
            assertEquals(destination, settingsDestinationFrom(argument))
        }
    }

    @Test
    fun `no destination is the settings index, with no argument on the route`() {
        assertEquals("settings", settingsRoute(null))
        assertNull(settingsDestinationFrom(null))
    }

    @Test
    fun `an argument naming nothing opens the index rather than throwing`() {
        // What a renamed or removed enum entry looks like on a back stack entry restored from a
        // previous version of the app.
        assertNull(settingsDestinationFrom("ENTRY_POINTS_OLD"))
    }
}
