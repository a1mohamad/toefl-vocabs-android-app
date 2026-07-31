package io.github.a1mohamad.toeflvocab.core.models

import io.github.a1mohamad.toeflvocab.core.localization.AppLanguage
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - Theme

@Serializable
enum class AppTheme {
    @SerialName("system")
    System,

    @SerialName("light")
    Light,

    @SerialName("dark")
    Dark;

    val rawValue: String
        get() = when (this) {
            System -> "system"
            Light -> "light"
            Dark -> "dark"
        }

    val id: String get() = rawValue

    /**
     * Whether to draw the dark palette. [System] means "follow the device", so
     * it defers to [systemIsDark] — the direct equivalent of SwiftUI's
     * `preferredColorScheme(nil)`.
     */
    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        System -> systemIsDark
        Light -> false
        Dark -> true
    }

    val titleKey: StringKey
        get() = when (this) {
            System -> StringKey.ThemeSystem
            Light -> StringKey.ThemeLight
            Dark -> StringKey.ThemeDark
        }

    val symbol: AppSymbol
        get() = when (this) {
            System -> AppSymbol.ThemeSystem
            Light -> AppSymbol.ThemeLight
            Dark -> AppSymbol.ThemeDark
        }

    companion object {
        val allCases: List<AppTheme> = listOf(System, Light, Dark)
    }
}

// MARK: - Pronunciation

/**
 * Accent is chosen by picking a different locale for the system TextToSpeech
 * engine, so no audio files are bundled and nothing is downloaded — the engine
 * that ships with Android already carries all three.
 */
@Serializable
enum class SpeechAccent {
    @SerialName("american")
    American,

    @SerialName("british")
    British,

    @SerialName("australian")
    Australian;

    val rawValue: String
        get() = when (this) {
            American -> "american"
            British -> "british"
            Australian -> "australian"
        }

    val id: String get() = rawValue

    /** BCP-47 tag handed to `TextToSpeech.setLanguage`. */
    val localeIdentifier: String
        get() = when (this) {
            American -> "en-US"
            British -> "en-GB"
            Australian -> "en-AU"
        }

    /** Two-letter badge shown on the pronunciation control. */
    val badge: String
        get() = when (this) {
            American -> "US"
            British -> "UK"
            Australian -> "AU"
        }

    val titleKey: StringKey
        get() = when (this) {
            American -> StringKey.AccentAmerican
            British -> StringKey.AccentBritish
            Australian -> StringKey.AccentAustralian
        }

    companion object {
        val allCases: List<SpeechAccent> = listOf(American, British, Australian)
    }
}

// MARK: - Extra practice scope

/** How wide the Reports drill casts its net. */
@Serializable
enum class ExtraPracticeScope {
    @SerialName("weakest25")
    Weakest25,

    @SerialName("weakest50")
    Weakest50,

    @SerialName("everything")
    Everything;

    val rawValue: String
        get() = when (this) {
            Weakest25 -> "weakest25"
            Weakest50 -> "weakest50"
            Everything -> "everything"
        }

    val id: String get() = rawValue

    /** null means no cap — every word in the library. */
    val limit: Int?
        get() = when (this) {
            Weakest25 -> 25
            Weakest50 -> 50
            Everything -> null
        }

    val titleKey: StringKey
        get() = when (this) {
            Weakest25 -> StringKey.ScopeWeakest25
            Weakest50 -> StringKey.ScopeWeakest50
            Everything -> StringKey.ScopeEverything
        }

    companion object {
        val allCases: List<ExtraPracticeScope> = listOf(Weakest25, Weakest50, Everything)
    }
}

// MARK: - Settings

@Serializable
data class AppSettings(
    val theme: AppTheme = AppTheme.System,
    val language: AppLanguage = AppLanguage.System,
    val accent: SpeechAccent = SpeechAccent.American,
    val speechRate: Double = DEFAULT_SPEECH_RATE,
    val autoSpeak: Boolean = false,
    val haptics: Boolean = true,
    val extraPracticeScope: ExtraPracticeScope = ExtraPracticeScope.Weakest25,
) {

    /**
     * The stored rate keeps AVFoundation's scale, where 0.5 is normal speech, so
     * a settings blob is interchangeable with the iOS build's and the
     * Slow/Normal/Fast thresholds below did not have to be re-tuned.
     * `PronunciationService` converts to Android's 1.0-is-normal scale at the
     * point it talks to the engine.
     */
    val clampedSpeechRate: Double
        get() = speechRate.coerceIn(MINIMUM_SPEECH_RATE, MAXIMUM_SPEECH_RATE)

    /** Slow / Normal / Fast label for the current slider position. */
    val speedLabelKey: StringKey
        get() = when {
            clampedSpeechRate < 0.40 -> StringKey.SpeedSlow
            clampedSpeechRate > 0.53 -> StringKey.SpeedFast
            else -> StringKey.SpeedNormal
        }

    companion object {
        /**
         * `AVSpeechUtteranceDefaultSpeechRate` is 0.5. Below ~0.3 a synthesiser
         * starts to sound slurred rather than slow, above ~0.62 it is hard to
         * follow for a learner, so the slider is clamped to a usable band.
         */
        const val MINIMUM_SPEECH_RATE: Double = 0.30
        const val MAXIMUM_SPEECH_RATE: Double = 0.62
        const val DEFAULT_SPEECH_RATE: Double = 0.46

        /** Android's TextToSpeech treats 1.0 as normal; AVFoundation uses 0.5. */
        const val ANDROID_RATE_SCALE: Double = 2.0
    }
}
