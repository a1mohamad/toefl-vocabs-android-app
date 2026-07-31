package io.github.a1mohamad.toeflvocab.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.a1mohamad.toeflvocab.core.localization.LocalStrings
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import io.github.a1mohamad.toeflvocab.core.models.AppSymbol
import io.github.a1mohamad.toeflvocab.core.models.BookTheme
import io.github.a1mohamad.toeflvocab.core.models.ChecklistDisplay
import io.github.a1mohamad.toeflvocab.core.models.SpeechAccent
import io.github.a1mohamad.toeflvocab.core.models.WordStats
import kotlin.math.roundToInt

// MARK: - Checklist

/**
 * The five-box accuracy strip under a word.
 *
 * Correct/incorrect is carried by an icon as well as a colour, so it still reads
 * for a red-green colourblind user, and the whole strip collapses to one
 * accessibility element with a spoken summary instead of five anonymous shapes.
 */
@Composable
fun ChecklistView(
    display: ChecklistDisplay,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val strings = LocalStrings.current
    val boxSize: Dp = if (compact) 13.dp else 30.dp
    val spacing: Dp = if (compact) 4.dp else 9.dp
    val radius: Dp = if (compact) 4.dp else 9.dp

    val label = strings.format(
        StringKey.PracticeChecklistLabel,
        display.filled,
        display.capacity,
        display.correctCount,
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
    ) {
        for (index in 0 until WordStats.CYCLE_LENGTH) {
            val mark: Boolean? = display.marks.getOrNull(index)
            ChecklistBox(
                mark = mark,
                size = boxSize,
                radius = radius,
                compact = compact,
                dimmed = display.isRecap,
            )
        }
    }
}

@Composable
private fun ChecklistBox(
    mark: Boolean?,
    size: Dp,
    radius: Dp,
    compact: Boolean,
    dimmed: Boolean,
) {
    val shape = RoundedCornerShape(radius)
    val fill = when (mark) {
        null -> Palette.surfaceSunken
        true -> Palette.success
        false -> Palette.danger
    }
    val alpha = if (dimmed) 0.55f else 1f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(fill.copy(alpha = fill.alpha * alpha), shape)
            .then(
                if (mark == null) {
                    Modifier.border(1.5.dp, Palette.separator.copy(alpha = alpha), shape)
                } else {
                    Modifier
                }
            ),
    ) {
        if (mark != null && !compact) {
            Icon(
                imageVector = (if (mark) AppSymbol.Checkmark else AppSymbol.Xmark).vector,
                contentDescription = null,
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

// MARK: - Progress ring

@Composable
fun ProgressRing(
    progress: Double,
    modifier: Modifier = Modifier,
    lineWidth: Dp = 10.dp,
    gradient: Brush? = null,
    trackColor: Color = Palette.surfaceSunken,
) {
    val clamped = progress.coerceIn(0.0, 1.0).toFloat()
    val brush = gradient ?: BookTheme.Indigo.gradient

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round)
        val inset = lineWidth.toPx() / 2f
        val diameter = size.minDimension - lineWidth.toPx()
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = lineWidth.toPx()),
        )
        drawArc(
            brush = brush,
            // -90 so the ring starts at twelve o'clock, matching the SwiftUI
            // `rotationEffect(.degrees(-90))`.
            startAngle = -90f,
            sweepAngle = 360f * maxOf(0.0001f, clamped),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
    }
}

/** Ring with a percentage in the middle. */
@Composable
fun ProgressRingLabelled(
    progress: Double,
    caption: String,
    modifier: Modifier = Modifier,
    gradient: Brush? = null,
    diameter: Dp = 108.dp,
    labelColor: Color = Palette.textPrimary,
    captionColor: Color = Palette.textSecondary,
) {
    val percent = (progress.coerceIn(0.0, 1.0) * 100).roundToInt()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(diameter)
            .clearAndSetSemantics { contentDescription = "$caption: $percent percent" },
    ) {
        ProgressRing(
            progress = progress,
            lineWidth = 11.dp,
            gradient = gradient,
            modifier = Modifier.size(diameter),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", style = AppFont.metricValueSmall, color = labelColor)
            Text(caption, style = AppFont.badge, color = captionColor)
        }
    }
}

// MARK: - Meter

/** Horizontal progress bar used in book and section rows. */
@Composable
fun MeterBar(
    progress: Double,
    modifier: Modifier = Modifier,
    gradient: Brush? = null,
    height: Dp = 8.dp,
) {
    val brush = gradient ?: BookTheme.Indigo.gradient
    val fraction = progress.coerceIn(0.0, 1.0).toFloat()
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Palette.surfaceSunken, shape)
            .clearAndSetSemantics { },
    ) {
        // `fillMaxWidth(0f)` collapses to nothing, which is the right look for
        // an untouched section, so no special case is needed here.
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(height)
                .background(brush, shape)
        )
    }
}

// MARK: - Tiles and chips

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    symbol: AppSymbol? = null,
    tint: Color = Palette.accent,
) {
    val shape = RoundedCornerShape(Metrics.controlRadius)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(shape)
            .background(Palette.surfaceRaised, shape)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (symbol != null) {
                Icon(
                    imageVector = symbol.vector,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(label, style = AppFont.badge, color = Palette.textSecondary)
        }
        Text(value, style = AppFont.metricValueSmall, color = Palette.textPrimary)
    }
}

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    symbol: AppSymbol? = null,
    tint: Color = Palette.textSecondary,
    background: Color = Palette.surfaceSunken,
) {
    val shape = RoundedCornerShape(Metrics.chipRadius)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .padding(horizontal = 9.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        if (symbol != null) {
            Icon(
                imageVector = symbol.vector,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(text, style = AppFont.badge, color = tint)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() },
    ) {
        Text(
            text = title.uppercase(),
            style = AppFont.sectionHeader.copy(letterSpacing = 0.6.sp),
            color = Palette.textTertiary,
        )
        Spacer(Modifier.weight(1f))
        if (onAction != null && actionTitle != null) {
            Text(
                text = actionTitle,
                style = AppFont.caption,
                color = Palette.accent,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}

// MARK: - Buttons

/**
 * Compose has no `ButtonStyle` protocol, so the three iOS styles become three
 * composables. The press animation, the minimum height and the shape are all
 * carried across so the buttons feel the same under the thumb.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Brush? = null,
    enabled: Boolean = true,
) {
    val brush = gradient ?: BookTheme.Indigo.gradient
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "primaryButtonScale",
    )
    val shape = RoundedCornerShape(Metrics.controlRadius)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .defaultMinSize(minHeight = 54.dp)
            .clip(shape)
            .background(brush, shape, alpha = if (enabled) 1f else 0.4f)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Text(
            text = text,
            style = AppFont.cardTitle,
            color = Color.White.copy(alpha = if (pressed) 0.9f else 1f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Palette.textPrimary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "secondaryButtonScale",
    )
    val shape = RoundedCornerShape(Metrics.controlRadius)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .defaultMinSize(minHeight = 54.dp)
            .clip(shape)
            .background(Palette.surfaceRaised, shape)
            .border(1.dp, Palette.separator, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Text(text, style = AppFont.cardTitle, color = tint, textAlign = TextAlign.Center)
    }
}

/**
 * The Right / Wrong pair on the practice screen. Large, high-contrast, and
 * icon-led so the two are never distinguished by colour alone.
 */
@Composable
fun AnswerButton(
    text: String,
    symbol: AppSymbol,
    tint: Color,
    soft: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "answerButtonScale",
    )
    val shape = RoundedCornerShape(Metrics.controlRadius)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = 62.dp)
            .clip(shape)
            .background(soft, shape)
            .border(1.5.dp, tint.copy(alpha = 0.35f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Icon(
            imageVector = symbol.vector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = AppFont.cardTitle, color = tint, textAlign = TextAlign.Center)
    }
}

// MARK: - Pronunciation control

/**
 * Speaker button plus the accent switch, kept together because they are one idea
 * to the user. Present on every word and never disappears when the meaning is
 * revealed.
 */
@Composable
fun PronunciationControl(
    term: String,
    accent: SpeechAccent,
    isSpeaking: Boolean,
    gradient: Brush,
    onSpeak: () -> Unit,
    onCycleAccent: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val strings = LocalStrings.current
    val scale by animateFloatAsState(
        targetValue = if (isSpeaking && !reduceMotion) 1.03f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "speakerPulse",
    )
    val speakLabel = strings.format(StringKey.PracticeSpeakLabel, term)
    val accentLabel = "${strings[StringKey.SettingsAccent]}: ${strings[accent.titleKey]}"

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .scale(scale)
                .defaultMinSize(minHeight = Metrics.minimumTapTarget)
                .clip(CircleShape)
                .background(gradient, CircleShape)
                .clickable(onClick = onSpeak)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .clearAndSetSemantics { contentDescription = speakLabel },
        ) {
            Icon(
                imageVector = (
                    if (isSpeaking) AppSymbol.SpeakerWave3 else AppSymbol.SpeakerWave2
                    ).vector,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = strings[StringKey.PracticeTapToHear],
                style = AppFont.caption,
                color = Color.White,
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = Metrics.minimumTapTarget, height = Metrics.minimumTapTarget)
                .clip(CircleShape)
                .background(Palette.surfaceRaised, CircleShape)
                .border(1.dp, Palette.separator, CircleShape)
                .clickable(onClick = onCycleAccent)
                .clearAndSetSemantics { contentDescription = accentLabel },
        ) {
            Text(accent.badge, style = AppFont.badge, color = Palette.textPrimary)
        }
    }
}

// MARK: - Usage tip

/**
 * The grammar note that some words carry after the `---` marker in the source
 * data — "followed by in", "usually comes before the noun it describes".
 *
 * Deliberately styled as an aside rather than as more definition text: it is
 * advice about *using* the word, and a learner who reads it as part of the
 * meaning is memorising the wrong thing. The lightbulb and the warm tint are the
 * conventional "tip" pairing, and the label repeats what the icon says so the
 * emoji is never the only cue.
 */
@Composable
fun UsageTipView(tip: String, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(Metrics.controlRadius)
    val label = "${strings[StringKey.PracticeTip]}: $tip"

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.warningSoft, shape)
            .border(1.dp, Palette.warning.copy(alpha = 0.28f), shape)
            .padding(horizontal = 13.dp, vertical = 11.dp)
            .clearAndSetSemantics { contentDescription = label },
    ) {
        Text("💡", style = AppFont.body.copy(fontSize = 18.sp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = strings[StringKey.PracticeTip].uppercase(),
                style = AppFont.badge.copy(letterSpacing = 0.6.sp),
                color = Palette.warning,
            )
            Text(tip, style = AppFont.body, color = Palette.textSecondary)
        }
    }
}

// MARK: - Empty state

@Composable
fun EmptyStateView(
    symbol: AppSymbol,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = Metrics.screenPadding),
    ) {
        Icon(
            imageVector = symbol.vector,
            contentDescription = null,
            tint = Palette.textTertiary,
            modifier = Modifier.size(42.dp),
        )
        Text(title, style = AppFont.title, color = Palette.textPrimary, textAlign = TextAlign.Center)
        Text(
            text = message,
            style = AppFont.body,
            color = Palette.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// MARK: - Helpers

/** Single-line text that shrinks rather than truncating, as `lineLimit(1)` does. */
@Composable
fun SingleLineText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
