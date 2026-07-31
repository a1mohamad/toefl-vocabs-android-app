package io.github.a1mohamad.toeflvocab.core.persistence

import android.content.Context
import android.net.Uri
import io.github.a1mohamad.toeflvocab.BuildConfig
import io.github.a1mohamad.toeflvocab.core.models.InstantIso8601Serializer
import io.github.a1mohamad.toeflvocab.core.models.ProgressState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// MARK: - Backup envelope

/**
 * A progress file plus enough metadata to recognise it later.
 *
 * The raw [ProgressState] would round-trip on its own, but a bare JSON blob
 * gives the import side nothing to check: any `.json` the user picks would
 * decode into *something*, and a wrong file would silently replace real study
 * history. The `app` marker makes a mistaken pick fail loudly instead.
 */
@Serializable
data class ProgressBackup(
    /**
     * Bumped only if the envelope itself changes shape. [ProgressState] has its
     * own `schemaVersion` and decodes field-by-field with defaults, so adding a
     * field to progress does not require a new format here.
     */
    val format: Int = CURRENT_FORMAT,
    val app: String = MARKER,
    val appVersion: String = BuildConfig.VERSION_NAME,
    @Serializable(with = InstantIso8601Serializer::class)
    val exportedAt: Instant = Instant.now(),
    val progress: ProgressState = ProgressState(),
) {

    /**
     * Summary shown in the confirmation prompt, so the user can see what they
     * are about to restore before it replaces anything.
     */
    fun summary(locale: Locale = Locale.getDefault()): String {
        val formatter = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
        val words = (progress.main.keys + progress.extra.keys).size
        return "$words words · ${progress.sessions.size} sessions · ${formatter.format(exportedAt)}"
    }

    companion object {
        const val CURRENT_FORMAT = 1
        const val MARKER = "TOEFLVocab"

        val encoder: Json = Json {
            // Readable on purpose: a backup the user can open and eyeball is a
            // backup they can trust, and the file is a few hundred KB at most.
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
        }

        val decoder: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        /**
         * Decodes and validates. Throws rather than returning a partial result,
         * so a caller can never half-apply a bad file.
         */
        fun decode(json: String): ProgressBackup {
            val backup = try {
                decoder.decodeFromString(serializer(), json)
            } catch (error: Exception) {
                throw BackupError.NotABackupFile
            }

            if (backup.app != MARKER) throw BackupError.NotABackupFile
            if (backup.format > CURRENT_FORMAT) throw BackupError.NewerFormat(backup.format)
            return backup
        }
    }
}

// MARK: - Errors

sealed class BackupError(message: String) : Exception(message) {

    data object NotABackupFile :
        BackupError("That file is not a TOEFL Vocab progress backup.")

    data class NewerFormat(val format: Int) :
        BackupError("This backup was made by a newer version of the app (format $format).")

    data object Unreadable : BackupError("The file could not be read.")

    val localizedDescription: String get() = message ?: toString()
}

// MARK: - Store integration

/** Current progress as a backup file's contents. */
fun ProgressStore.exportBackup(appVersion: String = BuildConfig.VERSION_NAME): String {
    val backup = ProgressBackup(progress = state, appVersion = appVersion)
    return ProgressBackup.encoder.encodeToString(ProgressBackup.serializer(), backup)
}

/**
 * Validates [json] and, only if it is a genuine backup, replaces all progress
 * with it and writes through to disk immediately.
 *
 * Restoring is destructive by nature — the point is to overwrite whatever the
 * current install has — so callers confirm with the user first. The decode
 * happens before anything is touched, so an invalid file leaves the existing
 * history intact.
 */
fun ProgressStore.importBackup(json: String): ProgressBackup {
    val backup = ProgressBackup.decode(json)
    replaceState(backup.progress)
    return backup
}

/**
 * Reads a document returned by the Storage Access Framework.
 *
 * The `content://` uri handed back by `OpenDocument` carries a one-shot read
 * grant for this process, which is why the read has to happen now rather than
 * being deferred behind the confirmation dialog.
 */
object BackupFile {

    fun read(context: Context, uri: Uri): String = try {
        context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw BackupError.Unreadable
    } catch (error: BackupError) {
        throw error
    } catch (error: Exception) {
        throw BackupError.Unreadable
    }

    fun write(context: Context, uri: Uri, contents: String) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(contents.toByteArray(Charsets.UTF_8))
            } ?: throw BackupError.Unreadable
        } catch (error: BackupError) {
            throw error
        } catch (error: Exception) {
            throw BackupError.Unreadable
        }
    }

    /** Dated so successive backups do not overwrite each other in Files. */
    fun defaultFileName(now: Instant = Instant.now()): String {
        val formatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd", Locale.US)
            .withZone(ZoneId.systemDefault())
        return "toefl-vocab-progress-${formatter.format(now)}.json"
    }
}
