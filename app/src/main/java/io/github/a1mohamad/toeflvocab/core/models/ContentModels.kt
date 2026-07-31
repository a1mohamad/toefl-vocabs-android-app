package io.github.a1mohamad.toeflvocab.core.models

import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// MARK: - Category

/**
 * The two word lists that live inside every section.
 *
 * The data file calls these `main` and `extras`; the app calls them
 * `Main` and `Extra`. [jsonKey] is the only place that mismatch matters.
 */
@Serializable
enum class VocabCategory {
    @SerialName("main")
    Main,

    @SerialName("extra")
    Extra;

    val rawValue: String
        get() = when (this) {
            Main -> "main"
            Extra -> "extra"
        }

    val id: String get() = rawValue

    val jsonKey: String
        get() = when (this) {
            Main -> "main"
            Extra -> "extras"
        }

    val symbol: AppSymbol
        get() = when (this) {
            Main -> AppSymbol.BookClosed
            Extra -> AppSymbol.Sparkles
        }

    val titleKey: StringKey
        get() = when (this) {
            Main -> StringKey.CategoryMain
            Extra -> StringKey.CategoryExtra
        }

    val subtitleKey: StringKey
        get() = when (this) {
            Main -> StringKey.CategoryMainSubtitle
            Extra -> StringKey.CategoryExtraSubtitle
        }

    companion object {
        val allCases: List<VocabCategory> = listOf(Main, Extra)

        fun fromRawValue(raw: String): VocabCategory? = when (raw) {
            "main" -> Main
            "extra" -> Extra
            else -> null
        }

        fun fromJSONKey(key: String): VocabCategory? = when (key) {
            "main" -> Main
            "extras", "extra" -> Extra
            else -> null
        }
    }
}

// MARK: - Word identity

/**
 * Stable identity for a single word.
 *
 * Scoped by book *and* section *and* category on purpose: four terms
 * (`abandon`, `circulate`, `feature`, `survive`) appear in more than one place
 * in the source data, and each occurrence deserves its own progress record.
 *
 * Serialised as one `book/section/category/term` string so the saved progress
 * file stays human-readable. Terms are validated to contain no `/`.
 */
@Serializable(with = VocabIDSerializer::class)
data class VocabID(
    val bookID: String,
    val sectionID: String,
    val category: VocabCategory,
    val term: String,
) {
    val rawValue: String get() = "$bookID/$sectionID/${category.rawValue}/$term"

    override fun toString(): String = rawValue

    companion object {
        /** Returns null rather than guessing at a malformed id. */
        fun fromRawValue(rawValue: String): VocabID? {
            // `limit = 4` mirrors Swift's `maxSplits: 3`: any `/` inside the
            // term itself would land in the fourth part rather than creating a
            // fifth. Terms are rejected at load time if they contain one, but
            // this keeps a hand-edited file from silently truncating a word.
            val parts = rawValue.split("/", limit = 4)
            if (parts.size != 4) return null
            val category = VocabCategory.fromRawValue(parts[2]) ?: return null
            if (parts[0].isEmpty() || parts[1].isEmpty() || parts[3].isEmpty()) return null
            return VocabID(
                bookID = parts[0],
                sectionID = parts[1],
                category = category,
                term = parts[3],
            )
        }
    }
}

/** Encodes a [VocabID] as its `book/section/category/term` string, not as an object. */
object VocabIDSerializer : KSerializer<VocabID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("VocabID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: VocabID) {
        encoder.encodeString(value.rawValue)
    }

    override fun deserialize(decoder: Decoder): VocabID {
        val raw = decoder.decodeString()
        return VocabID.fromRawValue(raw)
            ?: throw kotlinx.serialization.SerializationException("Malformed VocabID: $raw")
    }
}

// MARK: - Word

data class VocabItem(
    val id: VocabID,
    val term: String,
    val definition: String,
    /**
     * Usage note that the source data appends to the definition after ` --- `,
     * e.g. "followed by in". Not part of the meaning, so it is carried
     * separately and presented as a hint rather than as dictionary text.
     */
    val usageTip: String? = null,
    /**
     * Position within its category, straight from the source array. This is
     * what makes a never-practised section play back in book order.
     */
    val orderIndex: Int,
) {
    val bookID: String get() = id.bookID
    val sectionID: String get() = id.sectionID
    val category: VocabCategory get() = id.category
}

// MARK: - Section

@Serializable
enum class SectionKind {
    @SerialName("lesson")
    Lesson,

    @SerialName("review")
    Review;

    val rawValue: String
        get() = when (this) {
            Lesson -> "lesson"
            Review -> "review"
        }

    val symbol: AppSymbol
        get() = when (this) {
            Lesson -> AppSymbol.Calendar
            Review -> AppSymbol.Review
        }

    companion object {
        fun fromRawValue(raw: String): SectionKind? = when (raw) {
            "lesson" -> Lesson
            "review" -> Review
            else -> null
        }
    }
}

data class VocabSection(
    val id: String,
    val bookID: String,
    val title: String,
    val intro: String,
    val kind: SectionKind,
    /** Display order inside the book, assigned by the loader from catalog.json. */
    val order: Int,
    val itemsByCategory: Map<VocabCategory, List<VocabItem>>,
) {
    fun items(category: VocabCategory): List<VocabItem> = itemsByCategory[category] ?: emptyList()

    /**
     * Only the categories that actually have words. `504/review_1` has no
     * extras, so the section screen must not offer an empty Extra list.
     */
    val availableCategories: List<VocabCategory>
        get() = VocabCategory.allCases.filter { items(it).isNotEmpty() }

    val allItems: List<VocabItem>
        get() = VocabCategory.allCases.flatMap { items(it) }

    val wordCount: Int get() = allItems.size

    fun wordCount(category: VocabCategory): Int = items(category).size
}

// MARK: - Book

/** Accent identity for a book, resolved to real colours in the design system. */
@Serializable
enum class BookTheme {
    @SerialName("indigo")
    Indigo,

    @SerialName("teal")
    Teal,

    @SerialName("amber")
    Amber,

    @SerialName("rose")
    Rose;

    val rawValue: String
        get() = when (this) {
            Indigo -> "indigo"
            Teal -> "teal"
            Amber -> "amber"
            Rose -> "rose"
        }

    companion object {
        fun named(raw: String?): BookTheme {
            if (raw == null) return Indigo
            return when (raw.lowercase()) {
                "indigo" -> Indigo
                "teal" -> Teal
                "amber" -> Amber
                "rose" -> Rose
                else -> Indigo
            }
        }
    }
}

data class Book(
    val id: String,
    val title: String,
    val shortTitle: String,
    val author: String,
    val intro: String,
    val theme: BookTheme,
    val order: Int,
    val sections: List<VocabSection>,
) {
    val allItems: List<VocabItem> get() = sections.flatMap { it.allItems }

    val wordCount: Int get() = sections.sumOf { it.wordCount }

    fun section(sectionID: String): VocabSection? = sections.firstOrNull { it.id == sectionID }

    /** The section that follows [sectionID], or null at the end of the book. */
    fun sectionAfter(sectionID: String): VocabSection? {
        val index = sections.indexOfFirst { it.id == sectionID }
        if (index < 0 || index + 1 >= sections.size) return null
        return sections[index + 1]
    }
}

// MARK: - Catalog

/**
 * The whole content library, already ordered and indexed. Built once at launch
 * and treated as immutable afterwards.
 */
class VocabCatalog(val books: List<Book>) {

    val allItems: List<VocabItem>
    private val itemIndex: Map<String, VocabItem>

    init {
        val items = mutableListOf<VocabItem>()
        val index = mutableMapOf<String, VocabItem>()
        for (book in books) {
            for (section in book.sections) {
                for (item in section.allItems) {
                    items.add(item)
                    index[item.id.rawValue] = item
                }
            }
        }
        allItems = items
        itemIndex = index
    }

    val isEmpty: Boolean get() = allItems.isEmpty()
    val totalWordCount: Int get() = allItems.size

    fun book(bookID: String): Book? = books.firstOrNull { it.id == bookID }

    fun section(bookID: String, sectionID: String): VocabSection? =
        book(bookID)?.section(sectionID)

    fun item(id: VocabID): VocabItem? = itemIndex[id.rawValue]

    fun items(bookID: String, sectionID: String, category: VocabCategory): List<VocabItem> =
        section(bookID, sectionID)?.items(category) ?: emptyList()

    companion object {
        val empty = VocabCatalog(emptyList())
    }
}
