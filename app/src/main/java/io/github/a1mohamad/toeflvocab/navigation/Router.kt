package io.github.a1mohamad.toeflvocab.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.a1mohamad.toeflvocab.core.models.ExtraPracticeScope
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// MARK: - Practice configuration

/**
 * Everything needed to start a practice session, in one value so it can travel
 * through navigation and modal presentation.
 */
data class PracticeConfiguration(
    val mode: PracticeMode,
    val bookID: String?,
    val sectionID: String?,
    val category: VocabCategory?,
    val scope: ExtraPracticeScope?,
) {
    val id: String
        get() = listOf(
            mode.rawValue,
            bookID ?: "-",
            sectionID ?: "-",
            category?.rawValue ?: "-",
            scope?.rawValue ?: "-",
        ).joinToString("|")

    companion object {
        fun section(
            bookID: String,
            sectionID: String,
            category: VocabCategory,
        ): PracticeConfiguration = PracticeConfiguration(
            mode = PracticeMode.Main,
            bookID = bookID,
            sectionID = sectionID,
            category = category,
            scope = null,
        )

        fun drill(scope: ExtraPracticeScope): PracticeConfiguration = PracticeConfiguration(
            mode = PracticeMode.Extra,
            bookID = null,
            sectionID = null,
            category = null,
            scope = scope,
        )
    }
}

// MARK: - Routes

sealed interface Route {
    data class BookRoute(val bookID: String) : Route
    data class SectionRoute(val bookID: String, val sectionID: String) : Route
    data object About : Route
}

// MARK: - Router

/**
 * Navigation state, kept out of the screens so a session can be started from
 * either tab and torn down from one place.
 *
 * The two stacks are plain lists rather than a `NavHost`. The iOS original
 * models navigation as `[Route]` arrays it can rewrite wholesale — "back to
 * menu" empties one, the screenshot harness assigns a two-deep path directly —
 * and a route graph would have made those into multi-step animations rather than
 * the single state change they are.
 *
 * Practice is presented modally rather than pushed: it is a self-contained task
 * with its own quit affordance, and modality is what stops a half-finished
 * session from being left behind in a navigation stack.
 */
@Stable
class Router(private val scope: CoroutineScope = MainScope()) {

    enum class Tab { Study, Reports, Settings }

    var tab: Tab by mutableStateOf(Tab.Study)

    var studyPath: List<Route> by mutableStateOf(emptyList())

    /**
     * Separate stack: Settings pushes About, and the two tabs must not share a
     * path or navigating one would move the other.
     */
    var settingsPath: List<Route> by mutableStateOf(emptyList())

    var activePractice: PracticeConfiguration? by mutableStateOf(null)

    private var replaceJob: Job? = null

    fun open(route: Route) {
        studyPath = studyPath + route
    }

    fun openInSettings(route: Route) {
        settingsPath = settingsPath + route
    }

    /** True when the system back gesture had somewhere to go. */
    fun popCurrentStack(): Boolean = when (tab) {
        Tab.Study -> {
            if (studyPath.isEmpty()) false
            else {
                studyPath = studyPath.dropLast(1)
                true
            }
        }

        Tab.Reports -> false

        Tab.Settings -> {
            if (settingsPath.isEmpty()) false
            else {
                settingsPath = settingsPath.dropLast(1)
                true
            }
        }
    }

    fun startPractice(configuration: PracticeConfiguration) {
        replaceJob?.cancel()
        activePractice = configuration
    }

    fun endPractice() {
        replaceJob?.cancel()
        activePractice = null
    }

    /**
     * Used by "Back to menu" on the summary screen — closes the session and
     * unwinds to the book list in one step.
     */
    fun returnToLibrary() {
        replaceJob?.cancel()
        activePractice = null
        studyPath = emptyList()
        tab = Tab.Study
    }

    /**
     * Used by "Next section".
     *
     * Dismisses first and re-presents on a later frame rather than swapping the
     * value in place. The practice screen keys its view model off the
     * configuration id, and tearing the old one down before the new one appears
     * is what guarantees the finished session's summary is gone rather than
     * cross-fading into the next word.
     */
    fun replacePractice(configuration: PracticeConfiguration) {
        activePractice = null
        replaceJob?.cancel()
        replaceJob = scope.launch {
            delay(REPLACE_DELAY_MILLIS)
            activePractice = configuration
        }
    }

    companion object {
        private const val REPLACE_DELAY_MILLIS = 350L
    }
}
