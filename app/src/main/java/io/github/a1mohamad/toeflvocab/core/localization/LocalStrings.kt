package io.github.a1mohamad.toeflvocab.core.localization

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The active string table, provided once at the root.
 *
 * Direct equivalent of the SwiftUI `\.strings` environment key: language is an
 * app-level choice, so it is applied in one place rather than threaded through
 * every screen's parameter list.
 */
val LocalStrings = staticCompositionLocalOf { Strings(AppLanguage.English) }
