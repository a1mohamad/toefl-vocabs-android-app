package io.github.a1mohamad.toeflvocab.core.persistence

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.a1mohamad.toeflvocab.BuildConfig
import io.github.a1mohamad.toeflvocab.core.models.LastLocation
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.ProgressState
import io.github.a1mohamad.toeflvocab.core.models.SessionRecord
import io.github.a1mohamad.toeflvocab.core.models.VocabID
import io.github.a1mohamad.toeflvocab.core.models.WordStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Owns all mutable user progress and its single JSON file on disk.
 *
 * Why a plain serialized file rather than Room or DataStore:
 *
 *  * There is no local emulator on this project, so a schema or migration bug
 *    costs a full CI round trip to see and another to confirm a fix. A file that
 *    is one atomic write has far less that can go wrong, and [ProgressState]
 *    decodes field-by-field with defaults so an old file never fails to load.
 *  * The whole dataset is a few hundred small records. There is no query load
 *    here that would justify a database.
 *  * It keeps the saved format byte-identical to the iOS build's, so a backup
 *    exported there restores here.
 *
 * The Room move stays open: [ProgressState] is the only shape that would need
 * porting, and this class is the only type the UI talks to.
 */
@Stable
class ProgressStore(
    private val file: File?,
    private val scope: CoroutineScope = MainScope(),
) {

    /**
     * Compose state rather than a `StateFlow`: every reader is a composable and
     * the value is a plain immutable snapshot, which is exactly the case
     * `mutableStateOf` is for. It also keeps the read sites identical in shape
     * to the SwiftUI `@Published` they were ported from.
     */
    var state: ProgressState by mutableStateOf(loadState(file))
        private set

    private var saveJob: Job? = null

    // MARK: Reading

    fun stats(id: VocabID, mode: PracticeMode): WordStats? = state.stats(id, mode)

    /** Convenience for handing a lookup to `AdaptiveOrdering`. */
    fun statsProvider(mode: PracticeMode): (VocabID) -> WordStats? = { state.stats(it, mode) }

    val lastLocation: LastLocation? get() = state.lastLocation
    val runNumber: Int get() = state.runNumber

    // MARK: Writing

    fun record(
        id: VocabID,
        mode: PracticeMode,
        correct: Boolean,
        at: Instant = Instant.now(),
    ): WordStats {
        val outcome = state.record(id, mode, correct, at)
        state = outcome.state
        scheduleSave()
        return outcome.stats
    }

    fun appendSession(record: SessionRecord) {
        state = state.append(record)
        scheduleSave()
    }

    fun rememberLocation(location: LastLocation) {
        if (state.lastLocation == location) return
        state = state.copy(lastLocation = location)
        scheduleSave()
    }

    /**
     * Full restart once every word has finished a cycle. Lifetime totals and
     * session history survive so Reports keeps its history.
     */
    fun beginNewRun() {
        state = state.beginNewRun()
        saveNow()
    }

    /** Settings → Reset all progress. */
    fun eraseAll() {
        state = state.eraseAll()
        saveNow()
    }

    /**
     * Wholesale replacement, used when restoring a backup. Writes through
     * immediately: a restore the user has just confirmed should survive the app
     * being killed a second later.
     */
    fun replaceState(newState: ProgressState) {
        state = newState
        saveNow()
    }

    // MARK: Persistence

    /**
     * Answers arrive one tap at a time; batching them keeps the app off the
     * filesystem during a fast session.
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            if (isActive) saveNow()
        }
    }

    /** Called on backgrounding and on any change that must not be lost. */
    fun saveNow() {
        saveJob?.cancel()
        saveJob = null

        val target = file ?: return
        val snapshot = state
        // The encode is cheap but the write is not, and this is called from the
        // main thread on every lifecycle stop.
        scope.launch(Dispatchers.IO) {
            try {
                val encoded = encoder.encodeToString(ProgressState.serializer(), snapshot)
                // Write to a sibling and rename: a kill mid-write then leaves the
                // previous good file in place instead of a truncated one.
                val temporary = File(target.parentFile, "${target.name}.tmp")
                temporary.writeText(encoded)
                if (!temporary.renameTo(target)) {
                    target.writeText(encoded)
                    temporary.delete()
                }
            } catch (error: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Save failed: $error")
            }
        }
    }

    companion object {
        private const val TAG = "Progress"
        private const val SAVE_DEBOUNCE_MILLIS = 600L
        const val FILE_NAME = "progress.json"

        /** In-memory only. Used by tests and Compose previews. */
        fun inMemory(state: ProgressState = ProgressState()): ProgressStore {
            val store = ProgressStore(file = null)
            store.state = state
            return store
        }

        internal val encoder: Json = Json {
            prettyPrint = false
            encodeDefaults = true
            explicitNulls = false
        }

        internal val decoder: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        private fun loadState(file: File?): ProgressState {
            if (file == null || !file.exists()) return ProgressState()
            return try {
                decoder.decodeFromString(ProgressState.serializer(), file.readText())
            } catch (error: Exception) {
                // Never crash on a bad file. Move it aside so the user gets a
                // working app and the original is still there to inspect.
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Load failed: $error. Quarantining the file.")
                }
                val quarantine = File(
                    file.parentFile,
                    "progress-corrupt-${System.currentTimeMillis() / 1000}.json"
                )
                runCatching { file.renameTo(quarantine) }
                ProgressState()
            }
        }
    }
}
