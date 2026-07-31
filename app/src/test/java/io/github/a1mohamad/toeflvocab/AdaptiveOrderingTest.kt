package io.github.a1mohamad.toeflvocab

import io.github.a1mohamad.toeflvocab.core.engine.AdaptiveOrdering
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import io.github.a1mohamad.toeflvocab.core.models.VocabID
import io.github.a1mohamad.toeflvocab.core.models.VocabItem
import io.github.a1mohamad.toeflvocab.core.models.WordStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ordering promise: open a section and your worst words are first, but a
 * section you have never touched plays in book order.
 */
class AdaptiveOrderingTest {

    // MARK: Helpers

    private fun item(
        term: String,
        index: Int,
        book: String = "504",
        section: String = "day_1",
        category: VocabCategory = VocabCategory.Main,
    ) = VocabItem(
        id = VocabID(bookID = book, sectionID = section, category = category, term = term),
        term = term,
        definition = "definition of $term",
        orderIndex = index,
    )

    private val sample: List<VocabItem>
        get() = listOf("alpha", "bravo", "charlie", "delta")
            .mapIndexed { index, term -> item(term, index) }

    private fun stats(correct: Int, incorrect: Int, endingWrong: Boolean = false): WordStats {
        var value = WordStats()
        repeat(correct) { value = value.recorded(correct = true) }
        repeat(incorrect) { value = value.recorded(correct = false) }
        if (endingWrong) value = value.recorded(correct = false)
        return value
    }

    // MARK: Tests

    @Test
    fun `untouched section plays in book order`() {
        val ordered = AdaptiveOrdering.order(sample) { null }

        assertEquals(listOf("alpha", "bravo", "charlie", "delta"), ordered.map { it.term })
    }

    @Test
    fun `words answered wrong move to the front`() {
        val wrong = WordStats().recorded(correct = false)

        val ordered = AdaptiveOrdering.order(sample) { id ->
            if (id.term == "charlie") wrong else null
        }

        assertEquals("charlie", ordered.first().term)
    }

    @Test
    fun `words answered right sink below unseen words`() {
        var good = WordStats()
        repeat(3) { good = good.recorded(correct = true) }

        val ordered = AdaptiveOrdering.order(sample) { id ->
            if (id.term == "alpha") good else null
        }

        assertEquals(
            "A word you keep getting right should not lead the queue",
            "alpha",
            ordered.last().term,
        )
    }

    @Test
    fun `the worst of several wrong words leads`() {
        val terrible = stats(correct = 0, incorrect = 4)
        val shaky = stats(correct = 3, incorrect = 1)

        val ordered = AdaptiveOrdering.order(sample) { id ->
            when (id.term) {
                "delta" -> terrible
                "bravo" -> shaky
                else -> null
            }
        }
        val terms = ordered.map { it.term }

        assertEquals("delta", terms.first())
        assertTrue(terms.indexOf("delta") < terms.indexOf("bravo"))
    }

    @Test
    fun `a recent mistake outweighs a clean record`() {
        val slipped = stats(correct = 9, incorrect = 0, endingWrong = true)
        val spotless = stats(correct = 10, incorrect = 0)

        val ordered = AdaptiveOrdering.order(sample) { id ->
            when (id.term) {
                "delta" -> slipped
                "alpha" -> spotless
                else -> null
            }
        }
        val terms = ordered.map { it.term }

        assertTrue(
            "A word missed on the last attempt should come before one never missed",
            terms.indexOf("delta") < terms.indexOf("alpha"),
        )
    }

    @Test
    fun `an unseen word outranks a word you nearly always get right`() {
        // Deliberate consequence of the Laplace prior: "never tested" scores a
        // flat 0.5, so a word answered right 9 times out of 10 does not jump
        // ahead of words that have never come up at all, even right after a slip
        // (0.41 vs 0.50). Showing brand-new words first is the more useful
        // behaviour; the recency bonus still lifts it above better-known words.
        val slipped = stats(correct = 9, incorrect = 0, endingWrong = true)

        val ordered = AdaptiveOrdering.order(sample) { id ->
            if (id.term == "delta") slipped else null
        }

        assertEquals("delta", ordered.last().term)
        assertTrue(AdaptiveOrdering.weakness(slipped) < AdaptiveOrdering.UNSEEN_SCORE)
    }

    @Test
    fun `ordering is deterministic`() {
        val history = mapOf(
            "alpha" to stats(correct = 2, incorrect = 2),
            "bravo" to stats(correct = 2, incorrect = 2),
            "charlie" to stats(correct = 2, incorrect = 2),
        )
        val provider: (VocabID) -> WordStats? = { history[it.term] }

        val first = AdaptiveOrdering.order(sample, stats = provider).map { it.term }
        repeat(25) {
            assertEquals(first, AdaptiveOrdering.order(sample, stats = provider).map { it.term })
        }
    }

    @Test
    fun `tied words from different sections still get a total ordering`() {
        // Source index collides across sections, so the id has to break the tie
        // or the drill queue could reshuffle between launches.
        val mixed = listOf(
            item("one", index = 0, section = "day_1"),
            item("two", index = 0, section = "day_2"),
            item("three", index = 0, book = "400", section = "day_1"),
        )

        val first = AdaptiveOrdering.order(mixed) { null }.map { it.term }
        repeat(25) {
            assertEquals(first, AdaptiveOrdering.order(mixed) { null }.map { it.term })
        }
    }

    @Test
    fun `unseen word scores exactly the documented baseline`() {
        assertEquals(AdaptiveOrdering.UNSEEN_SCORE, AdaptiveOrdering.weakness(null), 0.0001)
        assertEquals(AdaptiveOrdering.UNSEEN_SCORE, AdaptiveOrdering.weakness(WordStats()), 0.0001)
    }

    // MARK: Drill queue

    @Test
    fun `drill queue respects the scope limit`() {
        val many = (0 until 40).map { item("word$it", index = it) }

        val queue = AdaptiveOrdering.extraPracticeQueue(
            items = many,
            limit = 25,
            mainStats = { null },
            extraStats = { null },
        )

        assertEquals(25, queue.size)
    }

    @Test
    fun `drill queue with no limit covers everything`() {
        val many = (0 until 40).map { item("word$it", index = it) }

        val queue = AdaptiveOrdering.extraPracticeQueue(
            items = many,
            limit = null,
            mainStats = { null },
            extraStats = { null },
        )

        assertEquals(40, queue.size)
    }

    @Test
    fun `drill queue ranks by main history not drill history`() {
        val wrongInMain = stats(correct = 0, incorrect = 3)
        val wrongInDrill = stats(correct = 0, incorrect = 3)

        val queue = AdaptiveOrdering.extraPracticeQueue(
            items = sample,
            limit = null,
            mainStats = { if (it.term == "charlie") wrongInMain else null },
            extraStats = { if (it.term == "delta") wrongInDrill else null },
        )

        assertEquals(
            "The drill targets what the study path found weak",
            "charlie",
            queue.first().term,
        )
    }

    @Test
    fun `equally weak words prefer the one drilled least`() {
        val drilled = stats(correct = 2, incorrect = 0)

        val queue = AdaptiveOrdering.extraPracticeQueue(
            items = sample,
            limit = null,
            mainStats = { null },
            extraStats = { if (it.term == "alpha") drilled else null },
        )

        assertNotEquals(
            "A word already drilled should not lead a tie",
            "alpha",
            queue.first().term,
        )
    }

    @Test
    fun `drill queue runs wrong words before correct ones`() {
        val bad = stats(correct = 0, incorrect = 3)
        val good = stats(correct = 5, incorrect = 0)

        val queue = AdaptiveOrdering.extraPracticeQueue(
            items = sample,
            limit = null,
            mainStats = { id ->
                when (id.term) {
                    "delta" -> bad
                    "alpha" -> good
                    else -> null
                }
            },
            extraStats = { null },
        )

        val terms = queue.map { it.term }
        assertEquals("delta", terms.first())
        assertEquals("alpha", terms.last())
    }
}
