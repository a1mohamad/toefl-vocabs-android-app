package io.github.a1mohamad.toeflvocab.core.engine

import io.github.a1mohamad.toeflvocab.core.models.BookTheme
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.ProgressState
import io.github.a1mohamad.toeflvocab.core.models.SectionKind
import io.github.a1mohamad.toeflvocab.core.models.SessionRecord
import io.github.a1mohamad.toeflvocab.core.models.VocabCatalog
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import io.github.a1mohamad.toeflvocab.core.models.VocabID
import io.github.a1mohamad.toeflvocab.core.models.VocabItem
import io.github.a1mohamad.toeflvocab.core.models.WordStats
import kotlin.math.max

// MARK: - Summary

/**
 * One rolled-up set of numbers, reused at every level: whole library, one book,
 * one section, one category.
 */
data class MetricSummary(
    val total: Int,
    val seen: Int,
    val completed: Int,
    val mastered: Int,
    val needsWork: Int,
    val attempts: Int,
    val correct: Int,
) {
    val incorrect: Int get() = max(0, attempts - correct)
    val accuracy: Double get() = if (attempts == 0) 0.0 else correct.toDouble() / attempts.toDouble()
    val seenFraction: Double get() = if (total == 0) 0.0 else seen.toDouble() / total.toDouble()
    val completedFraction: Double
        get() = if (total == 0) 0.0 else completed.toDouble() / total.toDouble()
    val masteredFraction: Double
        get() = if (total == 0) 0.0 else mastered.toDouble() / total.toDouble()
    val isUntouched: Boolean get() = attempts == 0

    companion object {
        val zero = MetricSummary(
            total = 0, seen = 0, completed = 0, mastered = 0, needsWork = 0,
            attempts = 0, correct = 0,
        )

        fun make(items: List<VocabItem>, stats: (VocabID) -> WordStats?): MetricSummary {
            var seen = 0
            var completed = 0
            var mastered = 0
            var needsWork = 0
            var attempts = 0
            var correct = 0

            for (item in items) {
                val wordStats = stats(item.id) ?: continue
                if (wordStats.attempts == 0) continue
                seen += 1
                attempts += wordStats.attempts
                correct += wordStats.correct
                if (wordStats.isCompletedThisRun) completed += 1
                if (wordStats.isMastered) mastered += 1
                // "Needs work" = has actually got it wrong and is not currently
                // on a mastery streak. Deliberately not "score above a
                // threshold" so the number matches what the user would count by
                // hand.
                if (wordStats.incorrect > 0 && !wordStats.isMastered) needsWork += 1
            }

            return MetricSummary(
                total = items.size,
                seen = seen,
                completed = completed,
                mastered = mastered,
                needsWork = needsWork,
                attempts = attempts,
                correct = correct,
            )
        }
    }
}

// MARK: - Report shapes

data class SectionReport(
    val id: String,
    val bookID: String,
    val sectionID: String,
    val title: String,
    val kind: SectionKind,
    val summary: MetricSummary,
)

data class BookReport(
    val id: String,
    val title: String,
    val shortTitle: String,
    val theme: BookTheme,
    val summary: MetricSummary,
    val sections: List<SectionReport>,
    val mainSummary: MetricSummary,
    val extraSummary: MetricSummary,
)

/**
 * A word surfaced in "needs the most work", carrying enough context to be
 * rendered without another catalog lookup.
 */
data class WeakWord(
    val item: VocabItem,
    val bookShortTitle: String,
    val sectionTitle: String,
    val mainStats: WordStats?,
    val extraStats: WordStats?,
    val score: Double,
) {
    val id: String get() = item.id.rawValue

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WeakWord) return false
        return id == other.id && score == other.score
    }

    override fun hashCode(): Int = 31 * id.hashCode() + score.hashCode()
}

data class ReportData(
    val overall: MetricSummary,
    val mainSummary: MetricSummary,
    val extraSummary: MetricSummary,
    val books: List<BookReport>,
    val weakest: List<WeakWord>,
    val recentSessions: List<SessionRecord>,
    val extraAttempts: Int,
    val runNumber: Int,
    /** Every word in the library has finished a five-answer cycle this run. */
    val allWordsCompleted: Boolean,
) {
    val hasData: Boolean get() = overall.attempts > 0

    companion object {
        val empty = ReportData(
            overall = MetricSummary.zero,
            mainSummary = MetricSummary.zero,
            extraSummary = MetricSummary.zero,
            books = emptyList(),
            weakest = emptyList(),
            recentSessions = emptyList(),
            extraAttempts = 0,
            runNumber = 1,
            allWordsCompleted = false,
        )
    }
}

// MARK: - Aggregator

/**
 * Turns raw per-word records into everything the Reports screen shows.
 *
 * Pure and synchronous: given the same catalog and progress it always returns
 * the same numbers, which keeps it trivially testable. The whole library is only
 * a few hundred words, so a full recompute per render is cheaper than any
 * caching scheme would be to maintain.
 */
object StatsAggregator {

    fun build(
        catalog: VocabCatalog,
        progress: ProgressState,
        weakestLimit: Int = 8,
    ): ReportData {
        if (catalog.isEmpty) return ReportData.empty

        val mainStats: (VocabID) -> WordStats? = { progress.stats(it, PracticeMode.Main) }
        val extraStats: (VocabID) -> WordStats? = { progress.stats(it, PracticeMode.Extra) }

        val bookReports = catalog.books.map { book ->
            val sectionReports = book.sections.map { section ->
                SectionReport(
                    id = "${book.id}/${section.id}",
                    bookID = book.id,
                    sectionID = section.id,
                    title = section.title,
                    kind = section.kind,
                    summary = MetricSummary.make(section.allItems, mainStats),
                )
            }

            val items = book.allItems
            BookReport(
                id = book.id,
                title = book.title,
                shortTitle = book.shortTitle,
                theme = book.theme,
                summary = MetricSummary.make(items, mainStats),
                sections = sectionReports,
                mainSummary = MetricSummary.make(
                    items.filter { it.category == VocabCategory.Main }, mainStats
                ),
                extraSummary = MetricSummary.make(
                    items.filter { it.category == VocabCategory.Extra }, mainStats
                ),
            )
        }

        val allItems = catalog.allItems
        val overall = MetricSummary.make(allItems, mainStats)

        val weakest = weakestWords(
            catalog = catalog,
            mainStats = mainStats,
            extraStats = extraStats,
            limit = weakestLimit,
        )

        val extraOverall = MetricSummary.make(allItems, extraStats)

        return ReportData(
            overall = overall,
            mainSummary = MetricSummary.make(
                allItems.filter { it.category == VocabCategory.Main }, mainStats
            ),
            extraSummary = MetricSummary.make(
                allItems.filter { it.category == VocabCategory.Extra }, mainStats
            ),
            books = bookReports,
            weakest = weakest,
            recentSessions = progress.sessions.takeLast(12).reversed(),
            extraAttempts = extraOverall.attempts,
            runNumber = progress.runNumber,
            allWordsCompleted = allWordsCompleted(catalog, progress),
        )
    }

    /**
     * The words to put in front of the user, highest weakness first. Only words
     * that have actually been answered wrong at least once qualify — an unseen
     * word is not "weak", it is just new, and mixing the two makes the list
     * useless right after a fresh install.
     */
    fun weakestWords(
        catalog: VocabCatalog,
        mainStats: (VocabID) -> WordStats?,
        extraStats: (VocabID) -> WordStats?,
        limit: Int,
    ): List<WeakWord> {
        data class BookTitles(val book: String, val sections: Map<String, String>)

        val titles = catalog.books.associate { book ->
            book.id to BookTitles(
                book = book.shortTitle,
                sections = book.sections.associate { it.id to it.title },
            )
        }

        return catalog.allItems
            .mapNotNull { item ->
                val stats = mainStats(item.id) ?: return@mapNotNull null
                if (stats.incorrect <= 0 || stats.isMastered) return@mapNotNull null
                val bookInfo = titles[item.bookID]
                WeakWord(
                    item = item,
                    bookShortTitle = bookInfo?.book ?: item.bookID,
                    sectionTitle = bookInfo?.sections?.get(item.sectionID) ?: item.sectionID,
                    mainStats = stats,
                    extraStats = extraStats(item.id),
                    score = AdaptiveOrdering.weakness(stats),
                )
            }
            .sortedWith(compareByDescending<WeakWord> { it.score }.thenBy { it.id })
            .take(limit)
    }

    /**
     * Drives the "you've been through everything" notice. True only when every
     * single word has banked a full five-answer cycle in the current run.
     */
    fun allWordsCompleted(catalog: VocabCatalog, progress: ProgressState): Boolean {
        if (catalog.isEmpty) return false
        for (item in catalog.allItems) {
            val stats = progress.stats(item.id, PracticeMode.Main)
            if (stats == null || !stats.isCompletedThisRun) return false
        }
        return true
    }
}
