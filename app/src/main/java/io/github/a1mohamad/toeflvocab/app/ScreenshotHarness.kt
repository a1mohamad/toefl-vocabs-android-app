package io.github.a1mohamad.toeflvocab.app

import android.content.Intent
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.VocabCatalog
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import io.github.a1mohamad.toeflvocab.core.models.VocabItem
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

        // The tip card is only worth photographing on a word that actually has
        // tips, which the first section of the first book does not.
        if (screen == "practice-tip") {
            val exemplar = tipExemplar(catalog) ?: return
            router.startPractice(
                PracticeConfiguration.section(
                    bookID = exemplar.bookID,
                    sectionID = exemplar.sectionID,
                    category = exemplar.category,
                )
            )
            return
        }

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
     * The first word in the library carrying more than one usage tip, falling
     * back to any word with one. Found by searching rather than hardcoded, so
     * reordering the content cannot leave this pointing at a word with nothing
     * to show.
     */
    private fun tipExemplar(catalog: VocabCatalog): VocabItem? =
        catalog.allItems.firstOrNull { it.usageTips.size > 1 }
            ?: catalog.allItems.firstOrNull { it.usageTips.isNotEmpty() }

    /**
     * Steps the session forward until [matches] is true of the current word, then
     * reveals it. Bounded rather than `while (true)`: a logic slip here would
     * hang the app on the main thread and the capture step would time out with
     * nothing useful to show.
     *
     * Falls back to revealing whatever is on screen, because a screenshot of the
     * wrong word is still a screenshot; an empty one is not.
     */
    private fun driveTo(
        viewModel: PracticeViewModel,
        revealAs: Boolean = true,
        matches: (VocabItem) -> Boolean,
    ) {
        var steps = 0
        while (viewModel.phase != PracticeViewModel.Phase.Finished && steps < 500) {
            steps += 1
            val item = viewModel.currentItem
            if (item != null && matches(item)) {
                if (viewModel.revealedAnswer == null) viewModel.answer(correct = revealAs)
                return
            }
            if (viewModel.revealedAnswer == null) {
                viewModel.answer(correct = true)
            } else {
                viewModel.advance()
            }
        }
    }

    /**
     * Drives a live session far enough to photograph the revealed, tip and
     * summary states. Called from the practice screen, which owns the view model.
     */
    fun advance(viewModel: PracticeViewModel) {
        when (requestedScreen) {
            // Drive to a word the book gives several meanings, so the screen
            // that exists to show the numbered list actually shows one. The
            // adaptive queue decides what comes first, and that is often a
            // single-meaning word.
            "practice-revealed" -> driveTo(viewModel, revealAs = false) { it.meanings.size > 1 }

            "practice-tip" -> {
                // The session opens on whichever word the adaptive queue ranks
                // first, which is not necessarily the one with the tips, so step
                // forward until it comes up and then reveal it.
                val target = (
                    viewModel.queue.firstOrNull { it.usageTips.size > 1 }
                        ?: viewModel.queue.firstOrNull { it.usageTips.isNotEmpty() }
                    )?.term
                if (target == null) {
                    viewModel.answer(correct = true)
                    return
                }
                driveTo(viewModel) { it.term == target }
            }

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
