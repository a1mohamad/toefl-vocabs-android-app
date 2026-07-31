package io.github.a1mohamad.toeflvocab.app

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.a1mohamad.toeflvocab.core.audio.PronunciationService
import io.github.a1mohamad.toeflvocab.core.persistence.ProgressStore
import io.github.a1mohamad.toeflvocab.core.persistence.SettingsStore
import io.github.a1mohamad.toeflvocab.navigation.Router

/**
 * The five stores the iOS build injects once at the root as `@EnvironmentObject`.
 *
 * `staticCompositionLocalOf` rather than `compositionLocalOf`: none of these
 * five instances is ever swapped after launch, only their contents change, so
 * there is nothing for Compose to invalidate on the local itself.
 *
 * The default values throw rather than returning a placeholder. A screen that
 * renders outside `TOEFLVocabApp` is a wiring mistake, and a silent empty store
 * would show up as a blank library rather than as an error.
 */

val LocalContentProvider = staticCompositionLocalOf<ContentProvider> {
    error("ContentProvider was not provided. Wrap the tree in TOEFLVocabApp.")
}

val LocalProgressStore = staticCompositionLocalOf<ProgressStore> {
    error("ProgressStore was not provided. Wrap the tree in TOEFLVocabApp.")
}

val LocalSettingsStore = staticCompositionLocalOf<SettingsStore> {
    error("SettingsStore was not provided. Wrap the tree in TOEFLVocabApp.")
}

val LocalPronunciationService = staticCompositionLocalOf<PronunciationService> {
    error("PronunciationService was not provided. Wrap the tree in TOEFLVocabApp.")
}

val LocalRouter = staticCompositionLocalOf<Router> {
    error("Router was not provided. Wrap the tree in TOEFLVocabApp.")
}

/**
 * Mirrors SwiftUI's `\.accessibilityReduceMotion`. Read once from the platform
 * animation scale so the two animated affordances — the word card transition and
 * the speaker pulse — can opt out.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
