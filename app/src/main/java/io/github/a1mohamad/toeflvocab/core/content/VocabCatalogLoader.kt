package io.github.a1mohamad.toeflvocab.core.content

import android.content.Context
import android.util.Log
import io.github.a1mohamad.toeflvocab.BuildConfig
import io.github.a1mohamad.toeflvocab.core.models.Book
import io.github.a1mohamad.toeflvocab.core.models.BookTheme
import io.github.a1mohamad.toeflvocab.core.models.SectionKind
import io.github.a1mohamad.toeflvocab.core.models.VocabCatalog
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import io.github.a1mohamad.toeflvocab.core.models.VocabID
import io.github.a1mohamad.toeflvocab.core.models.VocabItem
import io.github.a1mohamad.toeflvocab.core.models.VocabSection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// MARK: - Errors

sealed class ContentError(message: String) : Exception(message) {

    data class ResourceMissing(val name: String) : ContentError(
        "$name is not inside the app package. Check that it is under app/src/main/assets/VocabData/."
    )

    data class DecodingFailed(val name: String, val reason: String) : ContentError(
        "$name could not be read: $reason"
    )

    data object Empty : ContentError("The content files loaded but contained no usable words.")

    val localizedDescription: String get() = message ?: toString()
}

// MARK: - Loader

/**
 * Turns the two bundled JSON files into an ordered, indexed [VocabCatalog].
 *
 * Ordering, in full, because it is the subtle part:
 *
 *  * **Books and sections** are ordered by `catalog.json`, because they live in
 *    JSON *objects* in `vocabs.json` and objects have no guaranteed key order.
 *    No sort function would ever place `504/review_1` between `day_6` and
 *    `day_7` where it belongs, so the order is stated explicitly instead.
 *  * **Words** are ordered by their position in the `[{term, definition}]`
 *    array. Legacy `{term: definition}` objects still load, sorted
 *    alphabetically, with a log warning.
 *
 * Definitions are also split here: everything after a `---` marker is a usage
 * note rather than part of the meaning, and is lifted into `usageTip`.
 *
 * Anything present in `vocabs.json` but missing from `catalog.json` is still
 * shown — appended at the end with a generated title — so adding a day and
 * forgetting the catalog entry degrades instead of hiding words.
 */
object VocabCatalogLoader {

    private const val TAG = "Content"

    /** Where the two files sit inside `app/src/main/assets`. */
    private const val ASSET_DIRECTORY = "VocabData"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    // MARK: Entry points

    fun load(context: Context): VocabCatalog {
        val vocabs = readAsset(context, "vocabs")
        // A missing catalog is survivable: everything falls back to generated
        // titles and natural ordering.
        val catalog = runCatching { readAsset(context, "catalog") }.getOrNull()
        return build(vocabsJson = vocabs, catalogJson = catalog)
    }

    /** Testable seam — feeds JSON straight in, no `AssetManager` involved. */
    fun build(vocabsJson: String, catalogJson: String?): VocabCatalog {
        val raw: JsonObject = try {
            json.parseToJsonElement(vocabsJson).jsonObject
        } catch (error: Exception) {
            throw ContentError.DecodingFailed("vocabs.json", error.toString())
        }

        var catalogFile: CatalogFile? = null
        if (catalogJson != null) {
            try {
                catalogFile = json.decodeFromString(CatalogFile.serializer(), catalogJson)
            } catch (error: Exception) {
                // Not fatal — order and titles degrade, words survive.
                log("catalog.json could not be read ($error). Falling back to generated titles.")
            }
        }

        val books = mutableListOf<Book>()
        val handledBookIDs = mutableSetOf<String>()

        for (meta in catalogFile?.books.orEmpty()) {
            val rawBook = raw[meta.id]
            if (rawBook == null) {
                log("catalog.json lists book '${meta.id}', which is not in vocabs.json. Skipping.")
                continue
            }
            if (meta.id in handledBookIDs) continue
            makeBook(
                id = meta.id,
                meta = meta,
                rawBook = rawBook.asObjectOrEmpty(),
                order = books.size,
            )?.let { books.add(it) }
            handledBookIDs.add(meta.id)
        }

        for (bookID in raw.keys.sortedWith(NATURAL_ORDER)) {
            if (bookID in handledBookIDs) continue
            log("Book '$bookID' is not in catalog.json — appending with a generated title.")
            makeBook(
                id = bookID,
                meta = null,
                rawBook = raw[bookID].asObjectOrEmpty(),
                order = books.size,
            )?.let { books.add(it) }
        }

        val catalog = VocabCatalog(books)
        if (catalog.isEmpty) throw ContentError.Empty
        return catalog
    }

    // MARK: Building

    private fun makeBook(
        id: String,
        meta: CatalogBook?,
        rawBook: JsonObject,
        order: Int,
    ): Book? {
        val sections = mutableListOf<VocabSection>()
        val handledSectionIDs = mutableSetOf<String>()

        for (sectionMeta in meta?.sections.orEmpty()) {
            val rawSection = rawBook[sectionMeta.id]
            if (rawSection == null) {
                log(
                    "catalog.json lists section '$id/${sectionMeta.id}', " +
                        "which is not in vocabs.json. Skipping."
                )
                continue
            }
            if (sectionMeta.id in handledSectionIDs) continue
            makeSection(
                bookID = id,
                sectionID = sectionMeta.id,
                meta = sectionMeta,
                rawSection = rawSection.asObjectOrEmpty(),
                order = sections.size,
            )?.let { sections.add(it) }
            handledSectionIDs.add(sectionMeta.id)
        }

        for (sectionID in rawBook.keys.sortedWith(NATURAL_ORDER)) {
            if (sectionID in handledSectionIDs) continue
            log("Section '$id/$sectionID' is not in catalog.json — appending with a generated title.")
            makeSection(
                bookID = id,
                sectionID = sectionID,
                meta = null,
                rawSection = rawBook[sectionID].asObjectOrEmpty(),
                order = sections.size,
            )?.let { sections.add(it) }
        }

        if (sections.isEmpty()) return null

        val title = meta?.title ?: humanReadableTitle(id)
        return Book(
            id = id,
            title = title,
            shortTitle = meta?.shortTitle ?: title,
            author = meta?.author ?: "",
            intro = meta?.intro ?: "",
            theme = BookTheme.named(meta?.theme),
            order = order,
            sections = sections,
        )
    }

    private fun makeSection(
        bookID: String,
        sectionID: String,
        meta: CatalogSection?,
        rawSection: JsonObject,
        order: Int,
    ): VocabSection? {
        val itemsByCategory = mutableMapOf<VocabCategory, List<VocabItem>>()

        for ((categoryKey, rawList) in rawSection) {
            val category = VocabCategory.fromJSONKey(categoryKey)
            if (category == null) {
                log("Unknown category '$categoryKey' in $bookID/$sectionID. Skipping.")
                continue
            }

            val list = parseWordList(rawList) ?: continue
            if (list.wasUnordered) {
                log(
                    "$bookID/$sectionID/$categoryKey uses the legacy object form — " +
                        "words fall back to alphabetical order. Run Scripts/migrate_vocabs.py."
                )
            }

            val items = mutableListOf<VocabItem>()
            val seenTerms = mutableSetOf<String>()

            for (word in list.words) {
                val term = word.term.trim()
                val (definition, usageTip) = splitUsageTip(word.definition)

                // Skip rather than throw: one bad row should never cost the user
                // the whole library.
                if (term.isEmpty() || definition.isEmpty()) continue
                if (term.contains("/")) {
                    log("Skipping '$term' in $bookID/$sectionID: '/' is reserved in word ids.")
                    continue
                }
                if (!seenTerms.add(term.lowercase())) {
                    log("Skipping duplicate '$term' in $bookID/$sectionID/$categoryKey.")
                    continue
                }

                items.add(
                    VocabItem(
                        id = VocabID(
                            bookID = bookID,
                            sectionID = sectionID,
                            category = category,
                            term = term,
                        ),
                        term = term,
                        definition = definition,
                        usageTip = usageTip,
                        orderIndex = items.size,
                    )
                )
            }

            if (items.isNotEmpty()) {
                itemsByCategory[category] = items
            }
        }

        if (itemsByCategory.isEmpty()) return null

        return VocabSection(
            id = sectionID,
            bookID = bookID,
            title = meta?.title ?: humanReadableTitle(sectionID),
            intro = meta?.intro ?: "",
            kind = SectionKind.fromRawValue(meta?.kind ?: "lesson") ?: SectionKind.Lesson,
            order = order,
            itemsByCategory = itemsByCategory,
        )
    }

    // MARK: Helpers

    /** The marker the source data uses to append a usage note to a definition. */
    const val USAGE_TIP_SEPARATOR = "---"

    /** The definition and, if present, the usage note that followed the marker. */
    data class UsageTipSplit(val definition: String, val usageTip: String?)

    /**
     * Splits `"meaning --- usage note"` into its two halves.
     *
     * The note is grammar advice ("followed by in", "usually comes before the
     * noun it describes"), not part of what the word means, so it must not be
     * read as the answer during practice.
     *
     * A row that is *only* a note, or only whitespace either side of the marker,
     * keeps its original text and reports no tip — losing the meaning would be a
     * far worse outcome than showing an unsplit line.
     */
    fun splitUsageTip(raw: String): UsageTipSplit {
        val whole = raw.trim()

        val parts = whole.split(USAGE_TIP_SEPARATOR)
        if (parts.size <= 1) return UsageTipSplit(whole, null)

        val definition = parts[0].trim()
        // Any further markers belong to the note, not to another field.
        val tip = parts.drop(1).joinToString(USAGE_TIP_SEPARATOR).trim()

        if (definition.isEmpty()) return UsageTipSplit(whole, null)
        return UsageTipSplit(definition, tip.ifEmpty { null })
    }

    /** `day_9` -> `Day 9`, `review_1` -> `Review 1`, `504` -> `504`. */
    fun humanReadableTitle(identifier: String): String {
        val parts = identifier.split('_', '-').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return identifier
        return parts.joinToString(" ") { part ->
            if (part.toIntOrNull() != null) part
            else part.take(1).uppercase() + part.drop(1)
        }
    }

    /** Orders `day_2` before `day_10` — plain string sorting would not. */
    fun naturallyPrecedes(lhs: String, rhs: String): Boolean {
        val left = splitTrailingNumber(lhs)
        val right = splitTrailingNumber(rhs)
        if (left.first != right.first) return left.first < right.first
        return (left.second ?: -1) < (right.second ?: -1)
    }

    /** [naturallyPrecedes] as a total comparator, for sorting key sets. */
    private val NATURAL_ORDER = Comparator<String> { lhs, rhs ->
        val left = splitTrailingNumber(lhs)
        val right = splitTrailingNumber(rhs)
        val byPrefix = left.first.compareTo(right.first)
        if (byPrefix != 0) byPrefix
        else (left.second ?: -1).compareTo(right.second ?: -1)
    }

    private fun splitTrailingNumber(value: String): Pair<String, Int?> {
        val digits = value.reversed().takeWhile { it.isDigit() }
        if (digits.isEmpty()) return value to null
        val numberText = digits.reversed()
        val prefix = value.dropLast(numberText.length)
        return prefix to numberText.toIntOrNull()
    }

    private fun readAsset(context: Context, name: String): String {
        val path = "$ASSET_DIRECTORY/$name.json"
        return try {
            context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (error: java.io.FileNotFoundException) {
            throw ContentError.ResourceMissing("$name.json")
        } catch (error: Exception) {
            throw ContentError.DecodingFailed("$name.json", error.message ?: error.toString())
        }
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    // MARK: Raw decoding shapes

    private fun kotlinx.serialization.json.JsonElement?.asObjectOrEmpty(): JsonObject =
        (this as? JsonObject) ?: JsonObject(emptyMap())

    private data class RawWord(val term: String, val definition: String)

    private data class RawWordList(val words: List<RawWord>, val wasUnordered: Boolean)

    /**
     * Accepts either shape:
     *
     *     [{ "term": "abandon", "definition": "..." }]   <- ordered, preferred
     *     { "abandon": "..." }                           <- legacy, alphabetised
     */
    private fun parseWordList(element: kotlinx.serialization.json.JsonElement): RawWordList? {
        if (element is JsonArray) {
            val words = element.mapNotNull { entry ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                val term = (obj["term"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: return@mapNotNull null
                val definition =
                    (obj["definition"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
                RawWord(term, definition)
            }
            return RawWordList(words, wasUnordered = false)
        }

        if (element is JsonObject) {
            val words = element
                .mapNotNull { (key, value) ->
                    val definition = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?: return@mapNotNull null
                    RawWord(key, definition)
                }
                .sortedBy { it.term.lowercase() }
            return RawWordList(words, wasUnordered = true)
        }

        log("Expected an array of {term, definition} objects or a term -> definition object.")
        return null
    }

    @Serializable
    private data class CatalogFile(val books: List<CatalogBook> = emptyList())

    @Serializable
    private data class CatalogBook(
        val id: String,
        val title: String,
        @SerialName("shortTitle") val shortTitle: String? = null,
        val author: String? = null,
        val intro: String? = null,
        val theme: String? = null,
        val sections: List<CatalogSection>? = null,
    )

    @Serializable
    private data class CatalogSection(
        val id: String,
        val title: String? = null,
        val kind: String? = null,
        val intro: String? = null,
    )
}
