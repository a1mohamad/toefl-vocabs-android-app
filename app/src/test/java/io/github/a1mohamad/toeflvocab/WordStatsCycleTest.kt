package io.github.a1mohamad.toeflvocab

import io.github.a1mohamad.toeflvocab.core.models.LastLocation
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.ProgressState
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import io.github.a1mohamad.toeflvocab.core.models.VocabID
import io.github.a1mohamad.toeflvocab.core.models.WordStats
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five-step rule, pinned down.
 *
 * This is the piece of behaviour a user would notice instantly if it broke and
 * the piece that is impossible to eyeball from an emulator screenshot, so it
 * gets the most thorough coverage in the suite.
 */
class WordStatsCycleTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `checklist fills left to right and banks on the fifth answer`() {
        var stats = WordStats()

        repeat(4) {
            val result = stats.record(correct = true)
            assertFalse("A cycle must not complete before the fifth answer", result.completedCycle)
            stats = result.stats
        }
        assertEquals(listOf(true, true, true, true), stats.currentCycle)
        assertEquals(0, stats.completedCycles)
        assertNull(stats.lastCycle)
        assertFalse(stats.isCompletedThisRun)

        val fifth = stats.record(correct = false)
        assertTrue("The fifth answer completes the cycle", fifth.completedCycle)
        stats = fifth.stats

        assertEquals("The checklist resets for the next cycle", emptyList<Boolean>(), stats.currentCycle)
        assertEquals(listOf(true, true, true, true, false), stats.lastCycle)
        assertEquals(1, stats.completedCycles)
        assertEquals(1, stats.completedCyclesThisRun)
        assertTrue(stats.isCompletedThisRun)
    }

    @Test
    fun `banked cycle stays on screen as a recap until the next answer`() {
        var stats = WordStats()
        repeat(5) { stats = stats.recorded(correct = true) }

        // The user must see their fifth answer land, not a row that blanks out.
        assertTrue(stats.checklist.isRecap)
        assertEquals(5, stats.checklist.filled)
        assertEquals(5, stats.checklist.correctCount)

        stats = stats.recorded(correct = false)
        assertFalse(stats.checklist.isRecap)
        assertEquals(listOf(false), stats.checklist.marks)
    }

    @Test
    fun `lifetime totals accumulate across cycles`() {
        var stats = WordStats()
        repeat(10) { stats = stats.recorded(correct = true) }
        repeat(5) { stats = stats.recorded(correct = false) }

        assertEquals(15, stats.attempts)
        assertEquals(10, stats.correct)
        assertEquals(5, stats.incorrect)
        assertEquals(3, stats.completedCycles)
        assertEquals(10.0 / 15.0, stats.accuracy, 0.0001)
    }

    @Test
    fun `consecutive correct resets on a wrong answer`() {
        var stats = WordStats()
        stats = stats.recorded(correct = true)
        stats = stats.recorded(correct = true)
        assertEquals(2, stats.consecutiveCorrect)

        stats = stats.recorded(correct = false)
        assertEquals(0, stats.consecutiveCorrect)
    }

    @Test
    fun `last answer is readable through a just-completed cycle`() {
        var stats = WordStats()
        repeat(4) { stats = stats.recorded(correct = true) }
        stats = stats.recorded(correct = false)

        // currentCycle is empty here, so this has to fall through to lastCycle
        // or the adaptive ordering loses the "just got it wrong" signal.
        assertEquals(false, stats.lastAnswerWasCorrect)
    }

    @Test
    fun `mastery needs both a finished cycle and a streak`() {
        var stats = WordStats()
        repeat(3) { stats = stats.recorded(correct = true) }
        assertFalse("A streak alone is not mastery", stats.isMastered)

        repeat(2) { stats = stats.recorded(correct = true) }
        assertTrue(stats.isMastered)

        stats = stats.recorded(correct = false)
        assertFalse("A wrong answer drops mastery immediately", stats.isMastered)
    }

    @Test
    fun `new run clears per-run completion but keeps history`() {
        var stats = WordStats()
        repeat(5) { stats = stats.recorded(correct = true) }
        assertTrue(stats.isCompletedThisRun)

        stats = stats.startNewRun()

        assertFalse(stats.isCompletedThisRun)
        assertEquals(0, stats.completedCyclesThisRun)
        assertEquals("Lifetime cycle count survives a restart", 1, stats.completedCycles)
        assertEquals("Lifetime attempts survive a restart", 5, stats.attempts)
        assertEquals(
            "The recap survives a restart",
            listOf(true, true, true, true, true),
            stats.lastCycle,
        )
    }

    // MARK: Persistence resilience

    @Test
    fun `decoding a file written before per-run tracking backfills it`() {
        val raw = """
        {
          "attempts": 5, "correct": 4, "incorrect": 1,
          "currentCycle": [], "completedCycles": 1, "consecutiveCorrect": 2
        }
        """.trimIndent()

        val stats = json.decodeFromString(WordStats.serializer(), raw)

        assertEquals(1, stats.completedCyclesThisRun)
        assertTrue(stats.isCompletedThisRun)
    }

    @Test
    fun `decoding an empty object yields a usable zeroed record`() {
        val stats = json.decodeFromString(WordStats.serializer(), "{}")

        assertEquals(0, stats.attempts)
        assertEquals(emptyList<Boolean>(), stats.currentCycle)
        assertFalse(stats.hasBeenSeen)
    }

    @Test
    fun `decoding repairs an overfull checklist`() {
        // A hand-edited or half-written file must not produce a checklist that
        // can never reset.
        val raw = """
        {"attempts": 7, "correct": 7, "incorrect": 0,
         "currentCycle": [true, true, true, true, true, true, true],
         "completedCycles": 0, "consecutiveCorrect": 7}
        """.trimIndent()

        val stats = json.decodeFromString(WordStats.serializer(), raw)

        assertTrue(stats.currentCycle.size < WordStats.CYCLE_LENGTH)
        assertEquals(WordStats.CYCLE_LENGTH - 1, stats.currentCycle.size)
    }

    @Test
    fun `progress state round-trips through JSON`() {
        val id = VocabID("504", "day_1", VocabCategory.Main, "abandon")
        var state = ProgressState()
        state = state.record(id, PracticeMode.Main, correct = true).state
        state = state.record(id, PracticeMode.Extra, correct = false).state
        state = state.copy(
            lastLocation = LastLocation("504", "day_1", VocabCategory.Main)
        )

        val encoded = json.encodeToString(ProgressState.serializer(), state)
        val restored = json.decodeFromString(ProgressState.serializer(), encoded)

        assertEquals(1, restored.stats(id, PracticeMode.Main)?.correct)
        assertEquals(1, restored.stats(id, PracticeMode.Extra)?.incorrect)
        assertEquals("day_1", restored.lastLocation?.sectionID)
    }

    @Test
    fun `word id survives a string round trip including multi-word terms`() {
        val id = VocabID(
            bookID = "400",
            sectionID = "day_3",
            category = VocabCategory.Extra,
            term = "sent chills up and down my spine",
        )
        val restored = VocabID.fromRawValue(id.rawValue)

        assertNotNull(restored)
        assertEquals(id, restored)
        assertEquals("sent chills up and down my spine", restored?.term)
    }

    @Test
    fun `malformed word id is rejected rather than guessed`() {
        assertNull(VocabID.fromRawValue("504/day_1/main"))
        assertNull(VocabID.fromRawValue("504/day_1/nonsense/abandon"))
        assertNull(VocabID.fromRawValue("504//main/abandon"))
    }
}
