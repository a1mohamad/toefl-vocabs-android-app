package io.github.a1mohamad.toeflvocab.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.a1mohamad.toeflvocab.core.models.BookTheme

// MARK: - Appearance

/**
 * Whether to draw the dark palette right now.
 *
 * Provided once at the root from the user's theme setting, so [Palette] can stay
 * a flat list of colour pairs instead of every screen threading a `ColorScheme`
 * around. `staticCompositionLocalOf` because it changes at most once per app
 * launch in practice, and a static local skips the invalidation bookkeeping.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

// MARK: - Colour

/**
 * Colours are declared in code rather than in `res/values/colors.xml` plus a
 * `values-night` twin. Two resource files that have to stay in lockstep is one
 * more thing that can only be verified by a full CI round trip; a pair of
 * literals on one line cannot drift, and reacts to the in-app theme setting
 * rather than only to the system one.
 */
object Palette {

    @Composable
    @ReadOnlyComposable
    fun dynamic(light: Long, dark: Long): Color =
        Color(0xFF000000L or if (LocalIsDarkTheme.current) dark else light)

    // Surfaces
    val background: Color @Composable @ReadOnlyComposable get() = dynamic(0xF5F4FA, 0x0B0A11)
    val surface: Color @Composable @ReadOnlyComposable get() = dynamic(0xFFFFFF, 0x17161F)
    val surfaceRaised: Color @Composable @ReadOnlyComposable get() = dynamic(0xFFFFFF, 0x21202C)
    val surfaceSunken: Color @Composable @ReadOnlyComposable get() = dynamic(0xECEAF4, 0x121118)

    // Text
    val textPrimary: Color @Composable @ReadOnlyComposable get() = dynamic(0x14121C, 0xF5F4F9)
    val textSecondary: Color @Composable @ReadOnlyComposable get() = dynamic(0x676480, 0x9B98AC)
    val textTertiary: Color @Composable @ReadOnlyComposable get() = dynamic(0x8F8CA3, 0x6E6B7F)

    // Lines
    val separator: Color @Composable @ReadOnlyComposable get() = dynamic(0xE4E1EE, 0x2B2937)

    // Semantics. Every use is paired with an icon or text — colour is never the
    // only thing carrying the meaning.
    val success: Color @Composable @ReadOnlyComposable get() = dynamic(0x14855A, 0x34D399)
    val danger: Color @Composable @ReadOnlyComposable get() = dynamic(0xC7374A, 0xFB7185)
    val warning: Color @Composable @ReadOnlyComposable get() = dynamic(0xB45309, 0xFBBF24)
    val accent: Color @Composable @ReadOnlyComposable get() = dynamic(0x4F46E5, 0x818CF8)

    val successSoft: Color @Composable @ReadOnlyComposable get() = dynamic(0xE3F5EC, 0x14312A)
    val dangerSoft: Color @Composable @ReadOnlyComposable get() = dynamic(0xFCE9EC, 0x36181F)
    val warningSoft: Color @Composable @ReadOnlyComposable get() = dynamic(0xFDF2E0, 0x342612)
}

// MARK: - Book accents

/**
 * Start/end colours of the book's signature gradient.
 *
 * The light-mode pair is deliberately deep: white text and glyphs sit on these
 * gradients, and the lighter mid-tones that look nice on a dark background fail
 * contrast on a white one.
 */
val BookTheme.gradientColors: List<Color>
    @Composable @ReadOnlyComposable get() = when (this) {
        BookTheme.Indigo -> listOf(
            Palette.dynamic(0x4F46E5, 0x6366F1),
            Palette.dynamic(0x7C3AED, 0xA855F7),
        )

        BookTheme.Teal -> listOf(
            Palette.dynamic(0x0F766E, 0x14B8A6),
            Palette.dynamic(0x0E7490, 0x22D3EE),
        )

        BookTheme.Amber -> listOf(
            Palette.dynamic(0xB45309, 0xF59E0B),
            Palette.dynamic(0xC2410C, 0xFB923C),
        )

        BookTheme.Rose -> listOf(
            Palette.dynamic(0xBE123C, 0xF43F5E),
            Palette.dynamic(0xA21CAF, 0xE879F9),
        )
    }

/**
 * Top-leading to bottom-trailing, which is what Compose's default
 * `linearGradient` start/end pair already means.
 */
val BookTheme.gradient: Brush
    @Composable @ReadOnlyComposable get() = Brush.linearGradient(gradientColors)

/** Flat colour for small text and icons, where the gradient would hurt legibility. */
val BookTheme.solid: Color
    @Composable @ReadOnlyComposable get() = gradientColors[0]

/** Very low-opacity wash for card backgrounds. */
val BookTheme.wash: Color
    @Composable @ReadOnlyComposable get() = gradientColors[0].copy(alpha = 0.12f)

/** A one-colour brush, for the places that need a `Brush` but not a gradient. */
fun solidBrush(color: Color): Brush = Brush.linearGradient(listOf(color, color))

// MARK: - Typography

/**
 * Sizes are given in `sp`, which scales with the system font-size setting the
 * same way SwiftUI's text styles scale with Dynamic Type. No fixed `dp` text
 * anywhere — that is the single most common reason an otherwise well-built app
 * is unusable at large accessibility sizes.
 *
 * The one thing that could not carry across is SwiftUI's `.rounded` design.
 * Android has no rounded system face, so those styles fall back to the platform
 * default and keep their weights, which is what actually carried the hierarchy.
 */
object AppFont {
    /**
     * The word under test. Serif, because it reads as "dictionary" and gives the
     * practice screen its one moment of personality.
     */
    val word = TextStyle(fontFamily = FontFamily.Serif, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    val screenTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold)
    val title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
    val cardTitle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val sectionHeader = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 17.sp)
    val definition = TextStyle(fontFamily = FontFamily.Serif, fontSize = 20.sp)
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)

    /** `tnum` keeps a counter from jittering as the digits change width. */
    val metricValue = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )
    val metricValueSmall = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )
    val badge = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val badgeMono = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )
}

// MARK: - Metrics

object Metrics {
    val cardRadius: Dp = 22.dp
    val controlRadius: Dp = 16.dp
    val chipRadius: Dp = 10.dp

    val screenPadding: Dp = 20.dp
    val cardPadding: Dp = 18.dp
    val stackSpacing: Dp = 16.dp
    val tightSpacing: Dp = 8.dp

    /** Android's minimum comfortable hit target. */
    val minimumTapTarget: Dp = 48.dp
}

// MARK: - Shared modifiers

@Composable
fun Modifier.card(
    padding: Dp = Metrics.cardPadding,
    background: Color = Palette.surface,
): Modifier {
    val shape = RoundedCornerShape(Metrics.cardRadius)
    return this
        .clip(shape)
        .background(background, shape)
        .border(1.dp, Palette.separator, shape)
        .padding(padding)
}

/** Standard screen background, applied behind scrollable content. */
@Composable
fun Modifier.screenBackground(): Modifier = this.background(Palette.background)
