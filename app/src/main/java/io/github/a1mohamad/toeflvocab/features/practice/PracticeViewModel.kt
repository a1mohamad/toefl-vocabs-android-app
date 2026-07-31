package io.github.a1mohamad.toeflvocab.features.practice

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.a1mohamad.toeflvocab.core.engine.AdaptiveOrdering
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import io.github.a1mohamad.toeflvocab.core.models.BookTheme
import io.github.a1mohamad.toeflvocab.core.models.ChecklistDisplay
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.SessionRecord
import io.github.a1mohamad.toeflvocab.core.models.VocabCatalog
import io.github.a1mohamad.toeflvocab.core.models.VocabItem
import io.github.a1mohamad.toeflvocab.core.models.WordStats
import io.github.a1mohamad.toeflvocab.core.persistence.ProgressStore
import io.github.a1mohamad.toeflvocab.navigation.PracticeConfiguration
import java.time.Instant

/**
 * The practice state machine.
 *
 * One epoch = one pass through the queue. The queue is built once when the
 * session starts and is *not* re-sorted mid-session: re-ranking after every
 * answer would make words jump around under the user and turn a 12-word section
 * into an unpredictable loop. Re-ordering happens on the next entry, which is
 * exactly the promise — "open a section, worst words first".
 *
 * A plain observable object rather than an `androidx.lifecycle.ViewModel`: it is
 * scoped to one modal presentation, not to the activity, and it is deliberately
 * torn down and rebuilt when the configuration changes so "next section" starts
 * clean.
 */
@Stable
class PracticeViewModel(
    val configuration: PracticeConfiguration,
    private val catalog: VocabCatalog,
    private val progress: ProgressStore,
) {

    sealed interface Phase {
        data object Question : Phase
        data class Revealed(val correct: Boolean) : Phase
        data object Finished : Phase
    }

    data class Outcome(
        val answered: Int,
        val correct: Int,
        val cyclesCompleted: Int,
    ) {
        val accuracy: Double
            get() = if (answered == 0) 0.0 else correct.toDouble() / answered.toDouble()
    }

    // MARK: Published state

    var queue: List<VocabItem> by mutableStateOf(emptyList())
        private set

    var index: Int by mutableStateOf(0)
        private set

    var phase: Phase by mutableStateOf(Phase.Question)
        private set

    /**
     * Live stats for the current word, so the checklist updates the instant an
     * answer lands rather than after a store round trip.
     */
    var currentStats: WordStats? by mutableStateOf(null)
        private set

    var showQuitConfirmation: Boolean by mutableStateOf(false)

    /** Set when the user quits; the container watches this to dismiss. */
    var dismissRequested: Boolean by mutableStateOf(false)
        private set

    // MARK: Session identity

    val theme: BookTheme

    /** Content-derived heading (a section title). Not localizable — it is data. */
    val headerTitle: String?

    /** Used instead of [headerTitle] for the drill, which has no section. */
    val headerTitleKey: StringKey?
    val headerSubtitleKey: StringKey?

    // MARK: Private

    private var startedAt: Instant = Instant.now()
    private var answeredCount = 0
    private var correctCount = 0
    private var cyclesCompleted = 0
    private var didFinalize = false

    // MARK: Init

    init {
        val book = configuration.bookID?.let { catalog.book(it) }
        val section = book?.let { b -> configuration.sectionID?.let { b.section(it) } }

        theme = book?.theme ?: BookTheme.Indigo

        when (configuration.mode) {
            PracticeMode.Main -> {
                headerTitle = when {
                    section != null && book != null -> "${book.shortTitle} · ${section.title}"
                    section != null -> section.title
                    else -> null
                }
                headerTitleKey = null
                headerSubtitleKey = configuration.category?.titleKey
            }

            PracticeMode.Extra -> {
                headerTitle = null
                headerTitleKey = StringKey.ReportsExtraPractice
                headerSubtitleKey = configuration.scope?.titleKey
            }
        }

        queue = buildQueue(configuration, catalog, progress)
        currentStats = queue.firstOrNull()?.let { progress.stats(it.id, configuration.mode) }
        if (queue.isEmpty()) phase = Phase.Finished
    }

    // MARK: Derived

    val currentItem: VocabItem?
        get() = queue.getOrNull(index)

    val isOnLastItem: Boolean get() = index >= queue.size - 1

    /** Current position and total, as the "3 of 12" label needs them. */
    val positionText: Pair<Int, Int>
        get() = minOf(index + 1, queue.size) to queue.size

    val progressFraction: Double
        get() = if (queue.isEmpty()) 0.0 else index.toDouble() / queue.size.toDouble()

    val outcome: Outcome
        get() = Outcome(
            answered = answeredCount,
            correct = correctCount,
            cyclesCompleted = cyclesCompleted,
        )

    /** Checklist to draw. Falls back to an empty strip for a never-seen word. */
    val checklist: ChecklistDisplay
        get() = currentStats?.checklist ?: ChecklistDisplay(marks = emptyList(), isRecap = false)

    /**
     * Lifetime tallies for the current word. Shown during the drill, where "how
     * often have I got this one wrong?" is the whole point.
     */
    val currentTallies: Pair<Int, Int>
        get() {
            val item = currentItem
            val main = item?.let { progress.stats(it.id, PracticeMode.Main) }
            val extra = item?.let { progress.stats(it.id, PracticeMode.Extra) }
            return ((main?.correct ?: 0) + (extra?.correct ?: 0)) to
                ((main?.incorrect ?: 0) + (extra?.incorrect ?: 0))
        }

    val revealedAnswer: Boolean?
        get() = (phase as? Phase.Revealed)?.correct

    // MARK: Actions

    fun answer(correct: Boolean) {
        if (phase != Phase.Question) return
        val item = currentItem ?: return

        val before = progress.stats(item.id, configuration.mode)?.completedCyclesThisRun ?: 0
        val updated = progress.record(item.id, configuration.mode, correct)
        if (updated.completedCyclesThisRun > before) cyclesCompleted += 1

        answeredCount += 1
        if (correct) correctCount += 1

        currentStats = updated
        phase = Phase.Revealed(correct)
    }

    fun advance() {
        if (revealedAnswer == null) return

        if (index + 1 < queue.size) {
            index += 1
            phase = Phase.Question
            currentStats = progress.stats(queue[index].id, configuration.mode)
        } else {
            finalize(completed = true)
            phase = Phase.Finished
        }
    }

    /**
     * "Practise again" — rebuilds the queue so the words just answered wrong
     * come back to the front.
     */
    fun restart() {
        queue = buildQueue(configuration, catalog, progress)
        index = 0
        answeredCount = 0
        correctCount = 0
        cyclesCompleted = 0
        didFinalize = false
        startedAt = Instant.now()
        currentStats = queue.firstOrNull()?.let { progress.stats(it.id, configuration.mode) }
        phase = if (queue.isEmpty()) Phase.Finished else Phase.Question
    }

    fun requestQuit() {
        // Nothing is at risk mid-word, so skip the dialog when no answer has
        // been given yet — a confirmation that is always trivially safe to
        // dismiss trains people to tap through it.
        if (answeredCount == 0) {
            confirmQuit()
        } else {
            showQuitConfirmation = true
        }
    }

    fun confirmQuit() {
        finalize(completed = false)
        dismissRequested = true
    }

    // MARK: Finalisation

    /** Writes the session record exactly once, whether the user finished or bailed. */
    private fun finalize(completed: Boolean) {
        if (didFinalize) return
        didFinalize = true

        // Nothing answered means nothing worth recording in the timeline.
        if (answeredCount <= 0) return

        progress.appendSession(
            SessionRecord(
                mode = configuration.mode,
                bookID = configuration.bookID,
                sectionID = configuration.sectionID,
                category = configuration.category,
                startedAt = startedAt,
                finishedAt = Instant.now(),
                answered = answeredCount,
                correct = correctCount,
                completed = completed,
            )
        )
        progress.saveNow()
    }

    // MARK: Queue building

    companion object {
        private fun buildQueue(
            configuration: PracticeConfiguration,
            catalog: VocabCatalog,
            progress: ProgressStore,
        ): List<VocabItem> = when (configuration.mode) {
            PracticeMode.Main -> {
                val bookID = configuration.bookID
                val sectionID = configuration.sectionID
                val category = configuration.category
                if (bookID == null || sectionID == null || category == null) {
                    emptyList()
                } else {
                    AdaptiveOrdering.order(
                        catalog.items(bookID, sectionID, category)
                    ) { progress.stats(it, PracticeMode.Main) }
                }
            }

            PracticeMode.Extra -> AdaptiveOrdering.extraPracticeQueue(
                items = catalog.allItems,
                limit = (configuration.scope
                    ?: io.github.a1mohamad.toeflvocab.core.models.ExtraPracticeScope.Weakest25).limit,
                mainStats = { progress.stats(it, PracticeMode.Main) },
                extraStats = { progress.stats(it, PracticeMode.Extra) },
            )
        }
    }
}
