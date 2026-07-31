package io.github.a1mohamad.toeflvocab.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.a1mohamad.toeflvocab.BuildConfig
import io.github.a1mohamad.toeflvocab.core.models.AppSettings
import io.github.a1mohamad.toeflvocab.core.models.SpeechAccent
import java.util.Locale

/**
 * Speaks words with the platform [TextToSpeech] engine.
 *
 * No audio files are bundled and nothing is ever downloaded: US, UK and AU
 * accents are three locales the on-device engine already knows. That keeps the
 * app fully offline, keeps the APK small, and means a new word added to
 * `vocabs.json` is instantly pronounceable with no extra work.
 *
 * The one place Android differs materially from the iOS original is voice
 * availability. `AVSpeechSynthesizer` ships every English locale; Android's
 * engine may report a locale as `LANG_MISSING_DATA` on a device that has never
 * downloaded it, so [speak] falls back through the accents rather than going
 * silent.
 */
@Stable
class PronunciationService(context: Context) {

    var isSpeaking: Boolean by mutableStateOf(false)
        private set

    /** The word currently being spoken, so a list can highlight the right row. */
    var speakingText: String? by mutableStateOf(null)
        private set

    private var engine: TextToSpeech? = null
    private var isReady = false
    /** Queued while the engine was still starting up. Spoken once it is ready. */
    private var pendingUtterance: Triple<String, SpeechAccent, Double>? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (!isReady) {
                if (BuildConfig.DEBUG) Log.e(TAG, "TextToSpeech init failed (status $status)")
                return@TextToSpeech
            }
            engine?.setAudioAttributes(
                AudioAttributes.Builder()
                    // `USAGE_ASSISTANCE_ACCESSIBILITY` plays through the media
                    // route even with the ringer silenced, which is the closest
                    // equivalent to the iOS build's `.playback` category — a
                    // muted vocabulary app is a bug report.
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            engine?.setOnUtteranceProgressListener(listener)
            pendingUtterance?.let { (text, accent, rate) ->
                pendingUtterance = null
                speak(text, accent, rate)
            }
        }
    }

    /**
     * Speaks [text]. A second tap while speaking restarts it rather than
     * queueing — repeated taps on a pronunciation button mean "say it again".
     */
    fun speak(text: String, accent: SpeechAccent, rate: Double) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val tts = engine ?: return
        if (!isReady) {
            pendingUtterance = Triple(trimmed, accent, rate)
            return
        }

        applyAccent(tts, accent)

        // AVFoundation's 0.5-is-normal scale, converted to Android's 1.0.
        val clamped = rate.coerceIn(
            AppSettings.MINIMUM_SPEECH_RATE,
            AppSettings.MAXIMUM_SPEECH_RATE,
        )
        tts.setSpeechRate((clamped * AppSettings.ANDROID_RATE_SCALE).toFloat())
        tts.setPitch(1.0f)

        speakingText = trimmed
        // QUEUE_FLUSH, not QUEUE_ADD: tapping twice means "say it again", not
        // "say it twice".
        tts.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        engine?.stop()
        isSpeaking = false
        speakingText = null
    }

    /** Called when the owning activity goes away for good. */
    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        isReady = false
        isSpeaking = false
        speakingText = null
    }

    // MARK: Voice selection

    private fun applyAccent(tts: TextToSpeech, accent: SpeechAccent) {
        val requested = Locale.forLanguageTag(accent.localeIdentifier)
        val result = tts.setLanguage(requested)
        if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
            return
        }

        if (BuildConfig.DEBUG) {
            Log.w(TAG, "${accent.localeIdentifier} unavailable on this device; falling back.")
        }

        // Try the other two accents, then plain English, rather than saying
        // nothing at all.
        for (fallback in SpeechAccent.allCases) {
            if (fallback == accent) continue
            val code = tts.setLanguage(Locale.forLanguageTag(fallback.localeIdentifier))
            if (code != TextToSpeech.LANG_MISSING_DATA && code != TextToSpeech.LANG_NOT_SUPPORTED) {
                return
            }
        }
        tts.setLanguage(Locale.ENGLISH)
    }

    // MARK: Engine callbacks

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            post { isSpeaking = true }
        }

        override fun onDone(utteranceId: String?) {
            post {
                isSpeaking = false
                speakingText = null
            }
        }

        // Abstract on the base class, and deprecated in favour of the
        // errorCode overload, so it has to be implemented either way.
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) {
            post {
                isSpeaking = false
                speakingText = null
            }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            post {
                isSpeaking = false
                speakingText = null
            }
        }
    }

    /**
     * The engine calls back on a binder thread. Compose state must only be
     * written from the main thread, so every callback hops.
     */
    private fun post(block: () -> Unit) {
        mainHandler.post(block)
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    companion object {
        private const val TAG = "Speech"
        private const val UTTERANCE_ID = "toefl-vocab-word"
    }
}
