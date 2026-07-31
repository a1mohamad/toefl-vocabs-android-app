package io.github.a1mohamad.toeflvocab

import io.github.a1mohamad.toeflvocab.core.content.VocabCatalogLoader
import io.github.a1mohamad.toeflvocab.core.engine.MetricSummary
import io.github.a1mohamad.toeflvocab.core.engine.StatsAggregator
import io.github.a1mohamad.toeflvocab.core.localization.AppLanguage
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import io.github.a1mohamad.toeflvocab.core.localization.Strings
import io.github.a1mohamad.toeflvocab.core.models.AppSettings
import io.github.a1mohamad.toeflvocab.core.models.AppTheme
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.ProgressState
import io.github.a1mohamad.toeflvocab.core.models.SessionRecord
import io.github.a1mohamad.toeflvocab.core.models.SpeechAccent
import io.github.a1mohamad.toeflvocab.core.models.VocabCatalog
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import io.github.a1mohamad.toeflvocab.core.models.VocabID
import io.github.a1mohamad.toeflvocab.core.models.WordStats
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReportingTest {

    private val vocabs = """
    {
      "504": {
        "day_1": {
          "main": [
            { "term": "alpha", "definition": "first" },
            { "term": "bravo", "definition": "second" }
          ],
          "extras": [
            { "term": "charlie", "definition": "third" }
          ]
        }
      }
    }
    """.trimIndent()

    private fun makeCatalog(): VocabCatalog = VocabCatalogLoader.build(vocabs, null)

    private fun id(term: String, category: VocabCategory = VocabCategory.Main) =
        VocabID(bookID = "504", sectionID = "day_1", category = category, term = term)

    // MARK: Global completion

    @Test
    fun `nothing practised means not complete`() {
        val catalog = makeCatalog()

        assertFalse(StatsAggregator.allWordsCompleted(catalog, ProgressState()))
    }

    @Test
    fun `completion requires every single word to finish a cycle`() {
        val catalog = makeCatalog()
        var state = ProgressState()

        // Two of the three words finish a full cycle.
        for (term in listOf("alpha", "bravo")) {
            repeat(WordStats.CYCLE_LENGTH) {
                state = state.record(id(term), PracticeMode.Main, correct = true).state
            }
        }
        assertFalse(
            "The extras word has not been touched, so the run is not finished",
            StatsAggregator.allWordsCompleted(catalog, state),
        )

        repeat(WordStats.CYCLE_LENGTH) {
            state = state.record(
                id("charlie", VocabCategory.Extra),
                PracticeMode.Main,
                correct = false,
            ).state
        }
        assertTrue(StatsAggregator.allWordsCompleted(catalog, state))
    }

    @Test
    fun `drill answers do not count toward main completion`() {
        val catalog = makeCatalog()
        var state = ProgressState()

        for (item in catalog.allItems) {
            repeat(WordStats.CYCLE_LENGTH) {
                state = state.record(item.id, PracticeMode.Extra, correct = true).state
            }
        }

        assertFalse(
            "Extra practice is explicitly separate from main progress",
            StatsAggregator.allWordsCompleted(catalog, state),
        )
    }

    @Test
    fun `starting a new run reopens completion`() {
        val catalog = makeCatalog()
        var state = ProgressState()
        for (item in catalog.allItems) {
            repeat(WordStats.CYCLE_LENGTH) {
                state = state.record(item.id, PracticeMode.Main, correct = true).state
            }
        }
        assertTrue(StatsAggregator.allWordsCompleted(catalog, state))

        state = state.beginNewRun()

        assertFalse(StatsAggregator.allWordsCompleted(catalog, state))
        assertEquals(2, state.runNumber)
        assertEquals(
            "Lifetime history survives the restart",
            WordStats.CYCLE_LENGTH,
            state.main[id("alpha").rawValue]?.attempts,
        )
    }

    // MARK: Summaries

    @Test
    fun `summary counts seen attempts and accuracy`() {
        val catalog = makeCatalog()
        var state = ProgressState()
        state = state.record(id("alpha"), PracticeMode.Main, correct = true).state
        state = state.record(id("alpha"), PracticeMode.Main, correct = false).state
        state = state.record(id("bravo"), PracticeMode.Main, correct = true).state

        val summary = MetricSummary.make(catalog.allItems) { state.stats(it, PracticeMode.Main) }

        assertEquals(3, summary.total)
        assertEquals(2, summary.seen)
        assertEquals(3, summary.attempts)
        assertEquals(2, summary.correct)
        assertEquals(2.0 / 3.0, summary.accuracy, 0.0001)
        assertEquals("Only alpha has been answered wrong", 1, summary.needsWork)
    }

    @Test
    fun `untouched content reports zero without dividing by zero`() {
        val catalog = makeCatalog()
        val summary = MetricSummary.make(catalog.allItems) { null }

        assertEquals(0.0, summary.accuracy, 0.0)
        assertEquals(0.0, summary.completedFraction, 0.0)
        assertTrue(summary.isUntouched)
    }

    @Test
    fun `weakest list skips unseen and mastered words`() {
        val catalog = makeCatalog()
        var state = ProgressState()

        // alpha: struggling. bravo: mastered. charlie: never seen.
        repeat(3) { state = state.record(id("alpha"), PracticeMode.Main, correct = false).state }
        repeat(WordStats.CYCLE_LENGTH) {
            state = state.record(id("bravo"), PracticeMode.Main, correct = true).state
        }

        val weakest = StatsAggregator.weakestWords(
            catalog = catalog,
            mainStats = { state.stats(it, PracticeMode.Main) },
            extraStats = { state.stats(it, PracticeMode.Extra) },
            limit = 10,
        )

        assertEquals(listOf("alpha"), weakest.map { it.item.term })
    }

    @Test
    fun `report build produces one entry per book and section`() {
        val catalog = makeCatalog()
        var state = ProgressState()
        state = state.record(id("alpha"), PracticeMode.Main, correct = true).state

        val report = StatsAggregator.build(catalog, state)

        assertTrue(report.hasData)
        assertEquals(1, report.books.size)
        assertEquals(1, report.books.first().sections.size)
        assertEquals("Two main words", 2, report.mainSummary.total)
        assertEquals("One extra word", 1, report.extraSummary.total)
    }

    @Test
    fun `session history is capped so the file cannot grow forever`() {
        var state = ProgressState()
        repeat(ProgressState.MAX_STORED_SESSIONS + 40) {
            state = state.append(
                SessionRecord(
                    mode = PracticeMode.Main,
                    bookID = "504",
                    sectionID = "day_1",
                    category = VocabCategory.Main,
                    startedAt = Instant.now(),
                    finishedAt = Instant.now(),
                    answered = 1,
                    correct = 1,
                    completed = true,
                )
            )
        }

        assertEquals(ProgressState.MAX_STORED_SESSIONS, state.sessions.size)
    }
}

// MARK: - Localisation

class StringsTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `every key has english copy`() {
        val missing = StringKey.allCases.filter { Strings.english[it] == null }

        assertTrue(
            "Missing English copy for: ${missing.map { it.rawValue }}",
            missing.isEmpty(),
        )
    }

    @Test
    fun `no key falls back to its own raw value`() {
        val strings = Strings(AppLanguage.English)
        val leaked = StringKey.allCases.filter { strings[it] == it.rawValue }

        assertTrue(
            "These keys would render as raw identifiers: ${leaked.map { it.rawValue }}",
            leaked.isEmpty(),
        )
    }

    @Test
    fun `a translation gap falls back to english rather than the key`() {
        val persian = Strings(AppLanguage.Persian)

        for (key in StringKey.allCases) {
            val value = persian[key]
            assertFalse(value.isEmpty())
            assertNotEquals("${key.rawValue} rendered as a raw key", key.rawValue, value)
        }
    }

    @Test
    fun `formatted keys substitute their placeholders`() {
        val strings = Strings(AppLanguage.English)

        assertEquals("3 of 12", strings.format(StringKey.PracticeProgress, 3, 12))
        assertTrue(strings.format(StringKey.BookWordsCount, 42).contains("42"))
    }

    @Test
    fun `persian is right to left and english is not`() {
        assertTrue(AppLanguage.Persian.isRightToLeft)
        assertFalse(AppLanguage.English.isRightToLeft)
    }

    @Test
    fun `speech rate is clamped to the usable band`() {
        assertEquals(
            AppSettings.MAXIMUM_SPEECH_RATE,
            AppSettings(speechRate = 5.0).clampedSpeechRate,
            0.0,
        )
        assertEquals(
            AppSettings.MINIMUM_SPEECH_RATE,
            AppSettings(speechRate = -1.0).clampedSpeechRate,
            0.0,
        )
    }

    @Test
    fun `settings decode from an empty object`() {
        val settings = json.decodeFromString(AppSettings.serializer(), "{}")

        assertEquals(AppTheme.System, settings.theme)
        assertEquals(SpeechAccent.American, settings.accent)
        assertEquals(AppSettings.DEFAULT_SPEECH_RATE, settings.speechRate, 0.0)
    }
}
