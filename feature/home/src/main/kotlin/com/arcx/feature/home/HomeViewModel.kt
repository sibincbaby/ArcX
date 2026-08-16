package com.arcx.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.common.di.DefaultDispatcher
import com.arcx.core.common.time.TimeSource
import com.arcx.core.common.time.startOfDay
import com.arcx.core.common.time.timeTicker
import com.arcx.core.domain.capture.SystemSurfaces
import com.arcx.core.domain.repository.HistoryRepository
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.model.RunSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * Enough to answer "what did I just do", not enough to become a second Activity screen.
 *
 * Five rather than the three the tile grid used to leave room for. With the grid gone this is the
 * only list on the screen, and three rows under a heading read as a leftover rather than a section.
 * Activity is still the place that groups by day and goes back a thousand runs.
 */
private const val MAX_RECENT_RUNS = 5

/**
 * Said instead of showing an empty screen. Room and DataStore can both fail outright — a
 * database Android refused to open, a preferences file that did not survive a restore — and a
 * flow that throws simply stops, leaving Home holding whatever it had, which on launch is
 * nothing. A blank status screen then reads as "everything is off".
 *
 * Deliberately not the exception's own text: a `SQLiteDatabaseCorruptException` is not something
 * the person reading it can act on.
 */
private const val LOAD_FAILED =
    "ArcX couldn't read your workflows or your run history from this device. " +
        "Reopening the app usually fixes it."

/**
 * What a way into ArcX is currently doing.
 *
 * Four states, not the three the surface chips had, and the split that was missing is the one this
 * screen exists for. "Off" used to mean both *the user has not switched this on* and *the user
 * switched it on and Android is not honouring it* — a sidebar whose overlay permission was revoked
 * in system Settings, or an accessibility service Xiaomi killed without clearing the setting.
 * Those are opposite situations: the first is a choice and deserves no colour at all, the second is
 * the thing Home is here to shout about. Reported as one state, either the choices nag or the
 * breakages whisper.
 */
enum class SurfaceState {
    /** Switched on, and the platform is honouring it. */
    LIVE,

    /** Declared in the manifest. Nobody can switch it off, so it has nothing to report. */
    ALWAYS_ON,

    /** Not switched on. A decision the user made, drawn as calmly as one. */
    OFF,

    /**
     * Asked for, and not working. A grant taken away in system Settings, or a service an OEM
     * stopped without clearing the setting that says it is enabled — see [SystemSurfaces].
     */
    BROKEN,
}

/**
 * Which surface a row is reporting on — the *subject*, not a destination.
 *
 * Home names what the row is about and :app decides which Settings screen owns that subject today,
 * the same way [HomeRoute]'s other callbacks say "see activity" rather than naming a tab route.
 * Home cannot see :feature:settings anyway — features do not depend on each other — but even if it
 * could, a row pointing at a screen would have to be edited every time a control moved between
 * screens, which is exactly what went wrong when the accessibility grant left Entry points and the
 * "Screen text off" chip kept landing there.
 */
enum class SurfaceSubject { SIDEBAR, SHARE, SELECTION, SCREEN_TEXT }

/** One way into ArcX, as the "Ways in" list draws it. */
data class SurfaceStatus(
    val subject: SurfaceSubject,
    val label: String,
    /** What it is for, in the words of someone who has never opened Settings. */
    val detail: String,
    val state: SurfaceState,
    /**
     * What to do about it, for the card Home raises above the list. Set only when the state is
     * [SurfaceState.BROKEN] — an "off" surface needs no remedy, it needs switching on, and the row
     * itself is already the way to that.
     */
    val remedy: String? = null,
)

/**
 * Home answers the two questions no other tab does: **is ArcX actually reachable right now**, and
 * **what did it just do**.
 *
 * It used to lead with a grid of workflows to tap, which was the wrong bet twice over. Nobody opens
 * this app to run something — the whole product is firing a workflow from inside another app — and
 * a run started from here is the worst possible one, because ArcX's own UI is what is on screen, so
 * there is no selection and nothing to read and the input silently falls back to the clipboard. The
 * grid also duplicated Library and the run list duplicated Activity.
 *
 * What is left is the part nothing else showed: a bubble Android stopped, or screen reading an OEM
 * revoked while ArcX was in the background, used to be invisible until the user went hunting in
 * Settings for why a workflow had gone quiet. That is also why [onResume] re-reads the permissions
 * rather than observing them — none of them emit anything when they change.
 */
data class HomeUiState(
    val greeting: String = "",
    val workflowCount: Int = 0,
    val runsToday: Int = 0,
    val surfaces: List<SurfaceStatus> = emptyList(),
    val recentRuns: List<RunSummary> = emptyList(),
    /**
     * Pinned at emission so every row's "9m ago" is measured against the same instant, and
     * refreshed on a timer rather than only when something upstream moves — see the ticker stage
     * on [HomeViewModel.uiState].
     */
    val nowMillis: Long = 0L,
    /**
     * Whether any connected provider could actually answer — configured *and* holding a key, or
     * local and needing none. It gates the whole screen: without one, every way in below leads to
     * the same failure, so listing four of them as "on" would be four true statements adding up to
     * a lie.
     *
     * Defaults to true so the first frame does not accuse a working install of being unconfigured;
     * `loading` is what the screen actually reads until the first emission lands.
     */
    val providerReady: Boolean = true,
    val loading: Boolean = true,
    /** Set when the reads behind this screen failed, so an empty Home can say which kind it is. */
    val error: String? = null,
)

/** What history contributes to Home: the rows one list draws, and one number. */
private data class Activity(
    val recent: List<RunSummary>,
    val today: Int,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    workflows: WorkflowRepository,
    history: HistoryRepository,
    settings: SettingsRepository,
    private val providers: ProviderRepository,
    private val surfaces: SystemSurfaces,
    private val time: TimeSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val permissions = MutableStateFlow(Permissions())

    // Fixed for the lifetime of the screen; re-deriving it on every emission would let the
    // greeting flip mid-scroll as the clock crosses noon.
    private val greeting = greetingFor(time.nowMillis())

    // Likewise fixed: the query is bounded by this instant, and a screen open across midnight
    // showing yesterday's count until it is reopened is better than re-querying on every tick.
    private val startOfToday = startOfDay(time.nowMillis())

    /**
     * A number, not the library.
     *
     * Home used to observe four slices of it — all, pinned, favourites and recently run — to sort
     * eight of them into a grid. Nothing on this screen draws a workflow any more, so the only
     * thing left to know is how many there are, and `distinctUntilChanged` on an `Int` means
     * renaming a workflow no longer re-runs everything below.
     *
     * Still `observeAll().map { it.size }` rather than a `count()` on the repository: that would be
     * a new DAO query and a new port method across two modules for a number a list of tens already
     * carries. The rule that makes counts worth buying is [HistoryRepository]'s, and it is about the
     * one table that grows without limit — which this is not.
     */
    private val workflowCount = workflows.observeAll()
        .map { it.size }
        .distinctUntilChanged()

    /**
     * Two bounded reads. The list asks for exactly the rows it draws, and "today" is a count of a
     * day's worth of rows rather than a list of them — see [HistoryRepository], which has no
     * "give me everything" method on purpose.
     *
     * The mean-duration aggregate that used to be here went with the grid: it was a `GROUP BY` over
     * the whole runs table, computed on every emission, to print "1.2s avg" on eight tiles.
     */
    private val activity = combine(
        history.observeRecent(MAX_RECENT_RUNS),
        history.observeSince(startOfToday).map { it.size }.distinctUntilChanged(),
        ::Activity,
    )

    /**
     * Whether anything could answer a prompt at all — the same test `ExecuteWorkflowUseCase` makes
     * before it calls out, so Home cannot claim a readiness the runner would refuse.
     *
     * The key is asked for rather than assumed, because a provider row with no key behind it is the
     * shape a half-finished setup leaves: it exists, it is the default, and every run fails on it.
     */
    private val providerReady = providers.observeAll()
        .map { configs -> configs.any { it.type.isLocal || providers.hasKey(it.id) } }
        .distinctUntilChanged()

    /**
     * One flag out of the settings, not the settings.
     *
     * `UserSettings` carries every slider on the Appearance screen; folding the whole object in
     * meant that dragging the sidebar's opacity re-derived Home's entire state. This is the only
     * field on it Home has ever read.
     */
    private val sidebarEnabled = settings.settings
        .map { it.bubbleEnabled }
        .distinctUntilChanged()

    val uiState: StateFlow<HomeUiState> = combine(
        workflowCount,
        activity,
        sidebarEnabled,
        permissions,
        providerReady,
    ) { count, runs, sidebarOn, granted, hasProvider ->
        HomeUiState(
            greeting = greeting,
            workflowCount = count,
            runsToday = runs.today,
            surfaces = surfaceStatuses(sidebarOn, granted),
            recentRuns = runs.recent,
            providerReady = hasProvider,
            loading = false,
        )
    }
        // The clock is combined in rather than read inside the fold above, for two reasons. The
        // fold is already at combine's five-flow typed arity — which is why `activity` is grouped
        // in the first place — and re-running it every minute would rebuild the whole surface list
        // to move one word in a timestamp. `now` is the only part of this state that goes stale on
        // its own, so it is the only part a tick touches. Placed above `catch` so a throwing
        // upstream still ends the flow here.
        .combine(timeTicker(time)) { state, now -> state.copy(nowMillis = now) }
        // Above flowOn so the replacement state is built off the main thread with everything
        // else. A throwing flow is a finished flow, so this is the last thing Home will show
        // until the screen is reopened — which is exactly what the copy tells the user to do.
        .catch { emit(HomeUiState(greeting = greeting, loading = false, error = LOAD_FAILED)) }
        // Off the main thread. `stateIn(viewModelScope)` collects on Main.immediate, so before
        // this every fold above ran on the UI thread — and the vault read behind `providerReady`
        // is real IO, not just a fold.
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(greeting = greeting),
        )

    /**
     * Called on every resume. Overlay and accessibility can both be taken away in system
     * Settings while ArcX is in the background, and a screen that still says "Edge sidebar, on"
     * after that is worse than no screen at all.
     */
    fun onResume() {
        permissions.value = Permissions(
            overlayGranted = surfaces.isOverlayGranted(),
            screenReadingEnabled = surfaces.isScreenReadingEnabled(),
            screenReadingRunning = surfaces.isScreenReadingRunning(),
        )
    }

    /**
     * Enabled and running are held apart, not folded into one "live" boolean, because the gap
     * between them is a whole state: Android goes on reporting the accessibility service as
     * enabled after an OEM has killed the process behind it. Collapsed, that reads as "off" and
     * the user is sent to turn on something the system insists is already on.
     */
    private data class Permissions(
        val overlayGranted: Boolean = false,
        val screenReadingEnabled: Boolean = false,
        val screenReadingRunning: Boolean = false,
    )

    private fun surfaceStatuses(sidebarEnabled: Boolean, granted: Permissions) = listOf(
        SurfaceStatus(
            subject = SurfaceSubject.SIDEBAR,
            // "Edge sidebar", not "Bubble": the surface became an edge strip and Settings calls it
            // that everywhere. The stored flag keeps its old name; only the wording is user-facing.
            label = "Edge sidebar",
            detail = "A thin strip down one edge of any app",
            state = when {
                !sidebarEnabled -> SurfaceState.OFF
                granted.overlayGranted -> SurfaceState.LIVE
                else -> SurfaceState.BROKEN
            },
            remedy = if (sidebarEnabled && !granted.overlayGranted) {
                "The sidebar is switched on, but drawing over other apps is not allowed — so it " +
                    "has nowhere to appear."
            } else {
                null
            },
        ),
        // Both are declared in the manifest and cannot be switched off, so neither can ever break.
        SurfaceStatus(
            subject = SurfaceSubject.SHARE,
            label = "Share sheet",
            detail = "ArcX in any app's share menu",
            state = SurfaceState.ALWAYS_ON,
        ),
        SurfaceStatus(
            subject = SurfaceSubject.SELECTION,
            label = "Selection menu",
            detail = "ArcX in the text-highlight popup",
            state = SurfaceState.ALWAYS_ON,
        ),
        SurfaceStatus(
            subject = SurfaceSubject.SCREEN_TEXT,
            label = "Screen reading",
            detail = "Reads and photographs what is on screen",
            state = when {
                !granted.screenReadingEnabled -> SurfaceState.OFF
                granted.screenReadingRunning -> SurfaceState.LIVE
                else -> SurfaceState.BROKEN
            },
            remedy = if (granted.screenReadingEnabled && !granted.screenReadingRunning) {
                "Android still lists the permission as granted, but nothing is behind it — your " +
                    "phone stopped the service. Turning it off and on again brings it back."
            } else {
                null
            },
        ),
    )
}

private fun greetingFor(nowMillis: Long): String =
    when (Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..21 -> "Good evening"
        else -> "Hello"
    }
