package io.github.a1mohamad.toeflvocab

import io.github.a1mohamad.toeflvocab.core.content.ContentError
import io.github.a1mohamad.toeflvocab.core.content.VocabCatalogLoader
import io.github.a1mohamad.toeflvocab.core.models.BookTheme
import io.github.a1mohamad.toeflvocab.core.models.SectionKind
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Loading rules, especially the ordering ones — the source data is two nested
 * JSON objects, and objects have no key order, so every ordering guarantee the
 * app makes has to come from somewhere else and be verified here.
 */
class VocabCatalogLoaderTest {

    // MARK: Fixtures

    /**
     * Deliberately written with `review_1` between the two days, and with word
     * arrays whose order is not alphabetical.
     */
    private val vocabs = """
    {
      "504": {
        "day_1": {
          "main": [
            { "term": "zebra", "definition": "striped animal" },
            { "term": "abandon", "definition": "leave behind" }
          ],
          "extras": [
            { "term": "cloak", "definition": "a kind of coat" }
          ]
        },
        "review_1": {
          "main": [
            { "term": "recap", "definition": "a summary" }
          ]
        },
        "day_2": {
          "main": [
            { "term": "corpse", "definition": "a dead body" }
          ]
        }
      }
    }
    """.trimIndent()

    /**
     * Section order here is day_1 -> review_1 -> day_2, which no sort function
     * would produce from the ids.
     */
    private val catalog = """
    {
      "books": [
        {
          "id": "504",
          "title": "504 Absolutely Essential Words",
          "shortTitle": "504",
          "author": "Barron's",
          "theme": "indigo",
          "intro": "Book intro.",
          "sections": [
            { "id": "day_1", "title": "Day 1", "kind": "lesson", "intro": "First." },
            { "id": "review_1", "title": "Review 1", "kind": "review", "intro": "Recap." },
            { "id": "day_2", "title": "Day 2", "kind": "lesson", "intro": "Second." }
          ]
        }
      ]
    }
    """.trimIndent()

    // MARK: Ordering

    @Test
    fun `section order comes from the catalog not from sorting`() {
        val result = VocabCatalogLoader.build(vocabs, catalog)
        val book = result.book("504")
        assertNotNull(book)

        assertEquals(
            "Sorting the ids would put review_1 last; the catalog says otherwise",
            listOf("day_1", "review_1", "day_2"),
            book!!.sections.map { it.id },
        )
    }

    @Test
    fun `word order comes from the array not alphabetically`() {
        val result = VocabCatalogLoader.build(vocabs, catalog)
        val section = result.section("504", "day_1")!!

        assertEquals(listOf("zebra", "abandon"), section.items(VocabCategory.Main).map { it.term })
        assertEquals(listOf(0, 1), section.items(VocabCategory.Main).map { it.orderIndex })
    }

    @Test
    fun `legacy object form still loads sorted alphabetically`() {
        val legacy = """
        {
          "504": {
            "day_1": {
              "main": { "zebra": "striped animal", "abandon": "leave behind" }
            }
          }
        }
        """.trimIndent()

        val result = VocabCatalogLoader.build(legacy, null)
        val section = result.section("504", "day_1")!!

        assertEquals(listOf("abandon", "zebra"), section.items(VocabCategory.Main).map { it.term })
    }

    // MARK: Metadata

    @Test
    fun `catalog supplies titles and intros`() {
        val result = VocabCatalogLoader.build(vocabs, catalog)
        val book = result.book("504")!!

        assertEquals("504 Absolutely Essential Words", book.title)
        assertEquals("504", book.shortTitle)
        assertEquals(BookTheme.Indigo, book.theme)
        assertEquals("Day 1", book.sections.first().title)
        assertEquals("First.", book.sections.first().intro)
    }

    @Test
    fun `review section is marked and exposes only the lists it has`() {
        val result = VocabCatalogLoader.build(vocabs, catalog)
        val review = result.section("504", "review_1")!!

        assertEquals(SectionKind.Review, review.kind)
        assertEquals(
            "review_1 has no extras and must not offer an empty list",
            listOf(VocabCategory.Main),
            review.availableCategories,
        )
        assertTrue(review.items(VocabCategory.Extra).isEmpty())
    }

    // MARK: Degradation

    @Test
    fun `section missing from the catalog is appended with a generated title`() {
        val extended = """
        {
          "504": {
            "day_1": { "main": [ { "term": "abandon", "definition": "leave behind" } ] },
            "day_9": { "main": [ { "term": "newword", "definition": "recently added" } ] }
          }
        }
        """.trimIndent()

        val result = VocabCatalogLoader.build(extended, catalog)
        val book = result.book("504")!!

        assertEquals(
            "Unlisted sections go last, never missing",
            listOf("day_1", "day_9"),
            book.sections.map { it.id },
        )
        assertEquals("Day 9", book.sections.last().title)
    }

    @Test
    fun `catalog entry without data is skipped rather than shown empty`() {
        val sparse = """
        { "504": { "day_1": { "main": [ { "term": "abandon", "definition": "leave behind" } ] } } }
        """.trimIndent()

        val result = VocabCatalogLoader.build(sparse, catalog)
        val book = result.book("504")!!

        assertEquals(listOf("day_1"), book.sections.map { it.id })
    }

    @Test
    fun `content loads even with no catalog at all`() {
        val result = VocabCatalogLoader.build(vocabs, null)
        val book = result.book("504")!!

        assertEquals(3, book.sections.size)
        assertEquals("504", book.title)
        assertFalse(result.isEmpty)
    }

    @Test
    fun `broken rows are skipped instead of losing the whole library`() {
        val messy = """
        {
          "504": {
            "day_1": {
              "main": [
                { "term": "good", "definition": "fine" },
                { "term": "", "definition": "empty term" },
                { "term": "nodefinition", "definition": "" },
                { "term": "bad/slash", "definition": "reserved character" },
                { "term": "Good", "definition": "duplicate ignoring case" },
                { "term": "  spaced  ", "definition": "  padded  " }
              ]
            }
          }
        }
        """.trimIndent()

        val result = VocabCatalogLoader.build(messy, null)
        val section = result.section("504", "day_1")!!
        val terms = section.items(VocabCategory.Main).map { it.term }

        assertEquals(listOf("good", "spaced"), terms)
        assertEquals(
            "Whitespace is trimmed on load",
            "padded",
            section.items(VocabCategory.Main).last().definition,
        )
    }

    @Test
    fun `completely unusable content throws rather than showing a blank app`() {
        try {
            VocabCatalogLoader.build("{}", null)
            fail("Expected ContentError.Empty")
        } catch (error: ContentError) {
            assertEquals(ContentError.Empty, error)
        }
    }

    @Test
    fun `invalid JSON reports which file is at fault`() {
        try {
            VocabCatalogLoader.build("{ not json", null)
            fail("Expected a DecodingFailed error")
        } catch (error: ContentError) {
            val failure = error as? ContentError.DecodingFailed
                ?: return fail("Expected a decodingFailed error, got $error")
            assertEquals("vocabs.json", failure.name)
        }
    }

    // MARK: Usage tips

    @Test
    fun `usage note after the marker is lifted out of the definition`() {
        val tipped = """
        {
          "504": {
            "day_1": {
              "main": [
                { "term": "inherent", "definition": "naturally characteristic --- followed by in" },
                { "term": "corpse", "definition": "a dead body" }
              ]
            }
          }
        }
        """.trimIndent()

        val result = VocabCatalogLoader.build(tipped, null)
        val items = result.section("504", "day_1")!!.items(VocabCategory.Main)

        assertEquals(
            "The marker and everything after it is not the meaning",
            "naturally characteristic",
            items[0].definition,
        )
        assertEquals("followed by in", items[0].usageTip)
        assertNull("A plain definition must not grow an empty tip", items[1].usageTip)
    }

    @Test
    fun `splitting usage tips`() {
        assertEquals(
            "a strong influence",
            VocabCatalogLoader.splitUsageTip("a strong influence --- followed by on or of").definition,
        )
        assertEquals(
            "followed by on or of",
            VocabCatalogLoader.splitUsageTip("a strong influence --- followed by on or of").usageTip,
        )

        // No marker at all — the whole string is the meaning.
        val plain = VocabCatalogLoader.splitUsageTip("  a dead body  ")
        assertEquals("a dead body", plain.definition)
        assertNull(plain.usageTip)

        // A second marker belongs to the note, not to a third field.
        val doubled = VocabCatalogLoader.splitUsageTip("meaning --- first --- second")
        assertEquals("meaning", doubled.definition)
        assertEquals("first --- second", doubled.usageTip)

        // Nothing before the marker: keep the row readable rather than blanking
        // the meaning and getting the whole word skipped on load.
        val headless = VocabCatalogLoader.splitUsageTip("--- followed by to")
        assertEquals("--- followed by to", headless.definition)
        assertNull(headless.usageTip)

        // Nothing after it either.
        val empty = VocabCatalogLoader.splitUsageTip("a dead body ---")
        assertEquals("a dead body", empty.definition)
        assertNull(empty.usageTip)
    }

    // MARK: Identity

    @Test
    fun `the same term in two books gets two independent records`() {
        val shared = """
        {
          "504": { "day_1": { "main": [ { "term": "abandon", "definition": "leave" } ] } },
          "400": { "day_1": { "main": [ { "term": "abandon", "definition": "give up" } ] } }
        }
        """.trimIndent()

        val result = VocabCatalogLoader.build(shared, null)
        val ids = result.allItems.map { it.id.rawValue }

        assertEquals(
            "abandon appears in both books and must not share progress",
            2,
            ids.toSet().size,
        )
    }

    // MARK: Helpers

    @Test
    fun `generated titles`() {
        assertEquals("Day 9", VocabCatalogLoader.humanReadableTitle("day_9"))
        assertEquals("Review 1", VocabCatalogLoader.humanReadableTitle("review_1"))
        assertEquals("504", VocabCatalogLoader.humanReadableTitle("504"))
    }

    @Test
    fun `natural ordering puts day two before day ten`() {
        assertTrue(VocabCatalogLoader.naturallyPrecedes("day_2", "day_10"))
        assertFalse(VocabCatalogLoader.naturallyPrecedes("day_10", "day_2"))
    }
}
