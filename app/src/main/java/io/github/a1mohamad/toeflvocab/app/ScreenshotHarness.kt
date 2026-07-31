package io.github.a1mohamad.toeflvocab.app

import android.content.Intent
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.VocabCatalog
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import io.github.a1mohamad.toeflvocab.core.persistence.ProgressStore
import io.github.a1mohamad.toeflvocab.features.practice.PracticeViewModel
import io.github.a1mohamad.toeflvocab.navigation.PracticeConfiguration
import io.github.a1mohamad.toeflvocab.navigation.Route
import io.github.a1mohamad.toeflvocab.navigation.Router
import kotlinx.coroutines.delay

/**
 * Lets CI open the app directly on any screen, with realistic data already in
 * place, so every page can be photographed without UI automation.
 *
 * Launched as:
 *
 *     adb shell am start -n io.github.a1mohamad.toeflvocab/.app.MainActivity \
 *       -e screenshot reports
 *
 * An intent extra rather than the iOS build's launch argument, because that is
 * what `am start` can pass. The value is read once in `MainActivity.onCreate`
 * and stashed here, so the rest of the harness stays free of Android types.
 *
 * Every entry point is guarded by `BuildConfig.DEBUG` at its call site, which R8
 * folds to a constant and strips from the release APK — the same effect as the
 * `#if DEBUG` this was ported from.
 */
object ScreenshotHarness {

    const val INTENT_EXTRA = "screenshot"

    var requestedScreen: String? = null
        private set

    val isActive: Boolean get() = requestedScreen != null

    /** Called from `MainActivity.onCreate` before the first composition. */
    fun readIntent(intent: Intent?) {
        requestedScreen = intent?.getStringExtra(INTENT_EXTRA)?.takeIf { it.isNotBlank() }
    }

    // MARK: Entry point

    suspend fun prepare(progress: ProgressStore, catalog: VocabCatalog, router: Router) {
        val screen = requestedScreen ?: return
        if (catalog.isEmpty) return

        seed(progress, catalog)

        // Select the tab immediately so its content gets built.
        router.tab = when (screen) {
            "reports" -> Router.Tab.Reports
            "settings", "about" -> Router.Tab.Settings
            else -> Router.Tab.Study
        }

        // Then defer the push by a tick, so the tab's first composition has
        // settled before the stack is rewritten underneath it. This only affects
        // launch-time navigation — by the time a user can tap anything, every
        // screen is long since built.
        delay(500)
        navigate(screen, catalog, router)
    }

    // MARK: Data

    /**
     * Deterministic fake history, so Reports has numbers to show and the
     * checklists have marks in them.
     *
     * Seeded from a fixed constant and applied in catalog order, so every run
     * produces byte-identical screenshots — otherwise a visual diff between two
     * CI runs would be pure noise. The store is wiped first because the emulator
     * keeps the app's data between the launches in one capture loop, and seeding
     * nine times over would drift.
     */
    private fun seed(progress: ProgressStore, catalog: VocabCatalog) {
        progress.eraseAll()

        var state = 0x9E3779B97F4A7C15uL
        fun nextRandom(): ULong {
            state = state xor (state shl 13)
            state = state xor (state shr 7)
            state = state xor (state shl 17)
            return state
        }

        catalog.allItems.forEachIndexed { index, item ->
            // Leave every third word untouched so "not started" states are
            // visible in the screenshots too.
            if (index % 3 == 2) return@forEachIndexed

            val attempts = (nextRandom() % 7uL).toInt()
            repeat(attempts) {
                progress.record(item.id, PracticeMode.Main, nextRandom() % 10uL >= 4uL)
            }
        }
    }

    // MARK: Navigation

    private fun navigate(screen: String, catalog: VocabCatalog, router: Router) {
        val book = catalog.books.firstOrNull() ?: return
        val section = book.sections.firstOrNull() ?: return

        when (screen) {
            "book" -> router.studyPath = listOf(Route.BookRoute(book.id))

            "section" -> router.studyPath = listOf(
                Route.BookRoute(book.id),
                Route.SectionRoute(bookID = book.id, sectionID = section.id),
            )

            // The practice states differ only in how far the session is driven,
            // which the practice screen handles as it appears.
            "practice", "practice-revealed", "summary" -> router.startPractice(
                PracticeConfiguration.section(
                    bookID = book.id,
                    sectionID = section.id,
                    category = VocabCategory.Main,
                )
            )

            "about" -> router.settingsPath = listOf(Route.About)

            // "library" and anything unrecognised land on the root.
            else -> Unit
        }
    }

    /**
     * Drives a live session far enough to photograph the revealed and summary
     * states. Called from the practice screen, which owns the view model.
     */
    fun advance(viewModel: PracticeViewModel) {
        when (requestedScreen) {
            "practice-revealed" -> viewModel.answer(correct = false)

            "summary" -> {
                // Bounded rather than `while (true)`: a logic slip here would
                // hang the app on the main thread and the capture step would
                // time out with nothing useful to show.
                var steps = 0
                while (viewModel.phase != PracticeViewModel.Phase.Finished && steps < 500) {
                    steps += 1
                    if (viewModel.revealedAnswer == null) {
                        viewModel.answer(correct = steps % 3 != 0)
                    } else {
                        viewModel.advance()
                    }
                }
            }

            else -> Unit
        }
    }
}
