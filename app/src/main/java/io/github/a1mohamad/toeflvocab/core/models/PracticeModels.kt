package io.github.a1mohamad.toeflvocab.core.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

// MARK: - Mode

/**
 * Which counter an answer lands in.
 *
 * [Main] is the study path through books and sections. [Extra] is the drill
 * launched from Reports; it keeps a completely separate set of counters so a
 * heavy drilling session can never flatter (or wreck) main progress.
 */
@Serializable
enum class PracticeMode {
    @SerialName("main")
    Main,

    @SerialName("extra")
    Extra;

    val rawValue: String
        get() = when (this) {
            Main -> "main"
            Extra -> "extra"
        }

    val countsTowardMainProgress: Boolean get() = this == Main

    companion object {
        val allCases: List<PracticeMode> = listOf(Main, Extra)
    }
}

// MARK: - Checklist rendering

/** What the five boxes under a word should show right now. */
data class ChecklistDisplay(
    val marks: List<Boolean>,
    /**
     * True when these five boxes are a *finished* cycle being shown one last
     * time before the row resets. Lets the UI label it "last 5".
     */
    val isRecap: Boolean,
) {
    val filled: Int get() = marks.size
    val correctCount: Int get() = marks.count { it }
    val capacity: Int get() = WordStats.CYCLE_LENGTH
}

// MARK: - Instant

/**
 * ISO-8601 with no fractional seconds, which is exactly what Swift's
 * `JSONEncoder.dateEncodingStrategy = .iso8601` writes. Keeping the two byte
 * compatible means a backup exported from the iOS build restores here.
 */
object InstantIso8601Serializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value.truncatedTo(ChronoUnit.SECONDS)))
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

// MARK: - Per-word statistics

/**
 * Everything the app remembers about one word in one mode.
 *
 * The five-step rule lives in [record]: the checklist fills left to right, and
 * the instant the fifth box lands the cycle is banked into [lastCycle] and a
 * fresh empty checklist begins. The banked cycle stays visible until the user
 * moves on, so the fifth answer is actually seen instead of the row blanking out
 * underneath them.
 *
 * Immutable, unlike the Swift struct it was ported from. Swift got value
 * semantics for free; here a shared mutable record would both alias into the
 * progress map and fail to trigger recomposition, so [record] returns a new
 * value rather than mutating in place.
 */
@Serializable(with = WordStatsSerializer::class)
data class WordStats(
    // Lifetime totals — never reset, so Reports can show real history.
    val attempts: Int = 0,
    val correct: Int = 0,
    val incorrect: Int = 0,

    // Current cycle state.
    val currentCycle: List<Boolean> = emptyList(),
    val lastCycle: List<Boolean>? = null,

    /** Lifetime count of finished five-answer cycles. */
    val completedCycles: Int = 0,
    /**
     * Finished cycles since the last full restart. Drives "is this word done for
     * this run?", which is what the global completion check looks at.
     */
    val completedCyclesThisRun: Int = 0,

    val consecutiveCorrect: Int = 0,
    val lastAnsweredAt: Instant? = null,
) {

    // MARK: Derived

    val hasBeenSeen: Boolean get() = attempts > 0

    val accuracy: Double get() = if (attempts == 0) 0.0 else correct.toDouble() / attempts.toDouble()

    /** The most recent answer, looking through a just-completed cycle. */
    val lastAnswerWasCorrect: Boolean?
        get() = currentCycle.lastOrNull() ?: lastCycle?.lastOrNull()

    /** Finished at least one five-answer cycle in the current run. */
    val isCompletedThisRun: Boolean get() = completedCyclesThisRun >= 1

    /** Finished a cycle *and* is currently on a correct streak. */
    val isMastered: Boolean
        get() = completedCyclesThisRun >= 1 && consecutiveCorrect >= MASTERY_STREAK

    val checklist: ChecklistDisplay
        get() {
            val banked = lastCycle
            if (currentCycle.isEmpty() && banked != null && banked.isNotEmpty()) {
                return ChecklistDisplay(marks = banked, isRecap = true)
            }
            return ChecklistDisplay(marks = currentCycle, isRecap = false)
        }

    // MARK: Mutation

    /** The new record, plus whether this answer completed a cycle. */
    data class RecordResult(val stats: WordStats, val completedCycle: Boolean)

    /** Records one self-graded answer and applies the five-step reset rule. */
    fun record(correct: Boolean, at: Instant = Instant.now()): RecordResult {
        val nextCycle = currentCycle + correct
        val base = copy(
            attempts = attempts + 1,
            correct = if (correct) this.correct + 1 else this.correct,
            incorrect = if (correct) this.incorrect else this.incorrect + 1,
            consecutiveCorrect = if (correct) consecutiveCorrect + 1 else 0,
            lastAnsweredAt = at,
            currentCycle = nextCycle,
        )

        if (nextCycle.size < CYCLE_LENGTH) return RecordResult(base, false)

        return RecordResult(
            base.copy(
                lastCycle = nextCycle,
                currentCycle = emptyList(),
                completedCycles = completedCycles + 1,
                completedCyclesThisRun = completedCyclesThisRun + 1,
            ),
            true,
        )
    }

    /** [record] without the cycle flag, for the many call sites that ignore it. */
    fun recorded(correct: Boolean, at: Instant = Instant.now()): WordStats =
        record(correct, at).stats

    /**
     * Called on a full restart. Lifetime totals survive; per-run completion is
     * cleared so every word is "unfinished" again. The banked [lastCycle]
     * survives too — that recap is the whole point of the reset rule.
     */
    fun startNewRun(): WordStats = copy(completedCyclesThisRun = 0, currentCycle = emptyList())

    companion object {
        const val CYCLE_LENGTH = 5

        /** Consecutive correct answers after which a word counts as mastered. */
        const val MASTERY_STREAK = 3
    }
}

/**
 * Decoded key by key with defaults rather than by the generated decoder: a saved
 * file written by an older build is missing whatever fields were added since,
 * and a hard throw there would wipe real progress.
 *
 * Two rules cannot be expressed as plain `= default` parameters, which is why
 * this is hand-written rather than annotated:
 *
 *  * `completedCyclesThisRun` falls back to `completedCycles`, not to zero, so a
 *    file written before per-run tracking existed does not read as "nothing has
 *    ever been finished".
 *  * An over-full `currentCycle` — a hand-edited or half-written file — is
 *    trimmed, because a checklist of six can never reach the reset rule and
 *    would be stuck forever.
 */
object WordStatsSerializer : KSerializer<WordStats> {

    private val boolListSerializer = ListSerializer(Boolean.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("WordStats") {
        element<Int>("attempts")
        element<Int>("correct")
        element<Int>("incorrect")
        element("currentCycle", boolListSerializer.descriptor)
        element("lastCycle", boolListSerializer.descriptor, isOptional = true)
        element<Int>("completedCycles")
        element<Int>("completedCyclesThisRun")
        element<Int>("consecutiveCorrect")
        element("lastAnsweredAt", InstantIso8601Serializer.descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: WordStats) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.attempts)
            encodeIntElement(descriptor, 1, value.correct)
            encodeIntElement(descriptor, 2, value.incorrect)
            encodeSerializableElement(descriptor, 3, boolListSerializer, value.currentCycle)
            encodeNullableSerializableElement(descriptor, 4, boolListSerializer, value.lastCycle)
            encodeIntElement(descriptor, 5, value.completedCycles)
            encodeIntElement(descriptor, 6, value.completedCyclesThisRun)
            encodeIntElement(descriptor, 7, value.consecutiveCorrect)
            encodeNullableSerializableElement(
                descriptor, 8, InstantIso8601Serializer, value.lastAnsweredAt
            )
        }
    }

    override fun deserialize(decoder: Decoder): WordStats = decoder.decodeStructure(descriptor) {
        var attempts = 0
        var correct = 0
        var incorrect = 0
        var currentCycle: List<Boolean> = emptyList()
        var lastCycle: List<Boolean>? = null
        var completedCycles = 0
        var completedCyclesThisRun: Int? = null
        var consecutiveCorrect = 0
        var lastAnsweredAt: Instant? = null

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break
                0 -> attempts = decodeIntElement(descriptor, 0)
                1 -> correct = decodeIntElement(descriptor, 1)
                2 -> incorrect = decodeIntElement(descriptor, 2)
                3 -> currentCycle =
                    decodeSerializableElement(descriptor, 3, boolListSerializer)
                4 -> lastCycle =
                    decodeNullableSerializableElement(descriptor, 4, boolListSerializer)
                5 -> completedCycles = decodeIntElement(descriptor, 5)
                6 -> completedCyclesThisRun = decodeIntElement(descriptor, 6)
                7 -> consecutiveCorrect = decodeIntElement(descriptor, 7)
                8 -> lastAnsweredAt =
                    decodeNullableSerializableElement(descriptor, 8, InstantIso8601Serializer)
                else -> throw kotlinx.serialization.SerializationException("Unexpected index $index")
            }
        }

        // Defend against a hand-edited or truncated file.
        val repairedCycle =
            if (currentCycle.size >= WordStats.CYCLE_LENGTH) {
                currentCycle.takeLast(WordStats.CYCLE_LENGTH - 1)
            } else {
                currentCycle
            }

        WordStats(
            attempts = attempts,
            correct = correct,
            incorrect = incorrect,
            currentCycle = repairedCycle,
            lastCycle = lastCycle,
            completedCycles = completedCycles,
            completedCyclesThisRun = completedCyclesThisRun ?: completedCycles,
            consecutiveCorrect = consecutiveCorrect,
            lastAnsweredAt = lastAnsweredAt,
        )
    }
}

// MARK: - Session history

/** One finished (or abandoned) practice run, kept for the Reports timeline. */
@Serializable
data class SessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val mode: PracticeMode = PracticeMode.Main,
    val bookID: String? = null,
    val sectionID: String? = null,
    val category: VocabCategory? = null,
    @Serializable(with = InstantIso8601Serializer::class)
    val startedAt: Instant = Instant.now(),
    @Serializable(with = InstantIso8601Serializer::class)
    val finishedAt: Instant = Instant.now(),
    val answered: Int = 0,
    val correct: Int = 0,
    /** False when the user quit part-way. */
    val completed: Boolean = true,
) {
    val accuracy: Double
        get() = if (answered == 0) 0.0 else correct.toDouble() / answered.toDouble()
}

/** Where the user was last, so the library can offer "continue". */
@Serializable
data class LastLocation(
    val bookID: String,
    val sectionID: String,
    val category: VocabCategory,
)

// MARK: - Persisted root

/**
 * Immutable for the same reason [WordStats] is: `ProgressStore` publishes it
 * into Compose state, and an in-place edit would not recompose. Every mutating
 * name from the Swift original is kept, but each returns a new value.
 */
@Serializable
data class ProgressState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /**
     * Keyed by `VocabID.rawValue`. A `Map<VocabID, WordStats>` would encode as a
     * flat JSON *array* of alternating keys and values, because VocabID is not a
     * String key — this keeps the file readable.
     */
    val main: Map<String, WordStats> = emptyMap(),
    val extra: Map<String, WordStats> = emptyMap(),
    val sessions: List<SessionRecord> = emptyList(),
    /** Increments on every full restart, so Reports can say "run 2". */
    val runNumber: Int = 1,
    val lastLocation: LastLocation? = null,
) {

    // MARK: Access

    fun stats(id: VocabID, mode: PracticeMode): WordStats? = when (mode) {
        PracticeMode.Main -> main[id.rawValue]
        PracticeMode.Extra -> extra[id.rawValue]
    }

    fun setStats(stats: WordStats, id: VocabID, mode: PracticeMode): ProgressState = when (mode) {
        PracticeMode.Main -> copy(main = main + (id.rawValue to stats))
        PracticeMode.Extra -> copy(extra = extra + (id.rawValue to stats))
    }

    /** The new state, plus the word record that was just written into it. */
    data class RecordOutcome(val state: ProgressState, val stats: WordStats)

    fun record(
        id: VocabID,
        mode: PracticeMode,
        correct: Boolean,
        at: Instant = Instant.now(),
    ): RecordOutcome {
        val updated = (stats(id, mode) ?: WordStats()).recorded(correct, at)
        return RecordOutcome(setStats(updated, id, mode), updated)
    }

    fun append(session: SessionRecord): ProgressState {
        val appended = sessions + session
        val capped =
            if (appended.size > MAX_STORED_SESSIONS) appended.takeLast(MAX_STORED_SESSIONS)
            else appended
        return copy(sessions = capped)
    }

    /**
     * Full restart. Lifetime counters and session history survive so Reports
     * keeps its history; per-run completion is cleared everywhere.
     */
    fun beginNewRun(): ProgressState = copy(
        runNumber = runNumber + 1,
        main = main.mapValues { it.value.startNewRun() },
        extra = extra.mapValues { it.value.startNewRun() },
    )

    /** Wipes everything. Used by Settings → Reset all progress. */
    fun eraseAll(): ProgressState = ProgressState()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        /** Session history is capped so the file cannot grow without bound. */
        const val MAX_STORED_SESSIONS = 250
    }
}
