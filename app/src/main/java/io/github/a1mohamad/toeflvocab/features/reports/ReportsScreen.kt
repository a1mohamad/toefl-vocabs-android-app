package io.github.a1mohamad.toeflvocab.features.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.a1mohamad.toeflvocab.app.LargeTitle
import io.github.a1mohamad.toeflvocab.app.LocalContentProvider
import io.github.a1mohamad.toeflvocab.app.LocalProgressStore
import io.github.a1mohamad.toeflvocab.app.LocalRouter
import io.github.a1mohamad.toeflvocab.app.LocalSettingsStore
import io.github.a1mohamad.toeflvocab.core.engine.BookReport
import io.github.a1mohamad.toeflvocab.core.engine.ReportData
import io.github.a1mohamad.toeflvocab.core.engine.SectionReport
import io.github.a1mohamad.toeflvocab.core.engine.StatsAggregator
import io.github.a1mohamad.toeflvocab.core.engine.WeakWord
import io.github.a1mohamad.toeflvocab.core.localization.LocalStrings
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import io.github.a1mohamad.toeflvocab.core.models.AppSymbol
import io.github.a1mohamad.toeflvocab.core.models.BookTheme
import io.github.a1mohamad.toeflvocab.core.models.ExtraPracticeScope
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.SectionKind
import io.github.a1mohamad.toeflvocab.core.models.SessionRecord
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import io.github.a1mohamad.toeflvocab.designsystem.AppFont
import io.github.a1mohamad.toeflvocab.designsystem.ChecklistView
import io.github.a1mohamad.toeflvocab.designsystem.Chip
import io.github.a1mohamad.toeflvocab.designsystem.EmptyStateView
import io.github.a1mohamad.toeflvocab.designsystem.MeterBar
import io.github.a1mohamad.toeflvocab.designsystem.Metrics
import io.github.a1mohamad.toeflvocab.designsystem.Palette
import io.github.a1mohamad.toeflvocab.designsystem.PrimaryButton
import io.github.a1mohamad.toeflvocab.designsystem.ProgressRingLabelled
import io.github.a1mohamad.toeflvocab.designsystem.SectionHeader
import io.github.a1mohamad.toeflvocab.designsystem.SegmentedPicker
import io.github.a1mohamad.toeflvocab.designsystem.StatTile
import io.github.a1mohamad.toeflvocab.designsystem.card
import io.github.a1mohamad.toeflvocab.designsystem.gradient
import io.github.a1mohamad.toeflvocab.designsystem.screenBackground
import io.github.a1mohamad.toeflvocab.designsystem.solid
import io.github.a1mohamad.toeflvocab.designsystem.vector
import io.github.a1mohamad.toeflvocab.navigation.PracticeConfiguration
import kotlin.math.roundToInt

/**
 * Analytics without a wall of rows.
 *
 * The shape of the screen is deliberate: one number that matters at the top,
 * then a per-book breakdown where each section is a single coloured cell in a
 * grid rather than its own row, then only the handful of words actually worth
 * acting on. Seventeen sections across two books would be an unreadable list; as
 * a heat grid it is one glance.
 */
@Composable
fun ReportsScreen(modifier: Modifier = Modifier) {
    val content = LocalContentProvider.current
    val progress = LocalProgressStore.current
    val router = LocalRouter.current
    val settings = LocalSettingsStore.current
    val strings = LocalStrings.current

    val data = StatsAggregator.build(content.catalog, progress.state)

    Column(
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = modifier
            .fillMaxSize()
            .screenBackground()
            .verticalScroll(rememberScrollState())
            .padding(Metrics.screenPadding)
            .padding(bottom = 24.dp),
    ) {
        LargeTitle(strings[StringKey.ReportsTitle])

        if (data.hasData) {
            OverviewCard(data)
            DrillCard(
                scope = settings.settings.extraPracticeScope,
                weakCount = data.overall.needsWork,
                enabled = true,
                onScopeChange = { next -> settings.update { it.copy(extraPracticeScope = next) } },
                onStart = {
                    router.startPractice(
                        PracticeConfiguration.drill(settings.settings.extraPracticeScope)
                    )
                },
            )
            BooksSection(data)
            WeakestSection(data)
            TrendSection(data)
            SplitSection(data)
        } else {
            DrillCard(
                scope = settings.settings.extraPracticeScope,
                weakCount = 0,
                enabled = !content.catalog.isEmpty,
                onScopeChange = { next -> settings.update { it.copy(extraPracticeScope = next) } },
                onStart = {
                    router.startPractice(
                        PracticeConfiguration.drill(settings.settings.extraPracticeScope)
                    )
                },
            )

            EmptyStateView(
                symbol = AppSymbol.ChartDocument,
                title = strings[StringKey.ReportsEmpty],
                message = strings[StringKey.ReportsEmptyHint],
            )
        }
    }
}

// MARK: - Overview

@Composable
private fun OverviewCard(data: ReportData) {
    val strings = LocalStrings.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().card(),
    ) {
        ProgressRingLabelled(
            progress = data.overall.masteredFraction,
            caption = strings[StringKey.ReportsMastered],
            diameter = 108.dp,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Metric(
                value = "${(data.overall.accuracy * 100).roundToInt()}%",
                label = strings[StringKey.ReportsAccuracy],
            )
            Metric(
                value = "${data.overall.seen}/${data.overall.total}",
                label = strings[StringKey.ReportsSeen],
            )
            Metric(
                value = "${data.overall.needsWork}",
                label = strings[StringKey.ReportsWeakest],
            )
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { },
    ) {
        Text(value, style = AppFont.metricValueSmall, color = Palette.textPrimary)
        Text(label, style = AppFont.badge, color = Palette.textSecondary)
    }
}

// MARK: - Drill entry point

@Composable
private fun DrillCard(
    scope: ExtraPracticeScope,
    weakCount: Int,
    enabled: Boolean,
    onScopeChange: (ExtraPracticeScope) -> Unit,
    onStart: () -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().card(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AppSymbol.Bolt.vector,
                contentDescription = null,
                tint = Palette.warning,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = strings[StringKey.ReportsExtraPractice],
                style = AppFont.title,
                color = Palette.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (weakCount > 0) {
                Chip(
                    text = strings.format(StringKey.SectionNeedWork, weakCount),
                    tint = Palette.warning,
                    background = Palette.dangerSoft,
                )
            }
        }

        Text(
            text = strings[StringKey.ReportsExtraSubtitle],
            style = AppFont.body,
            color = Palette.textSecondary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = strings[StringKey.ReportsScope].uppercase(),
                style = AppFont.badge,
                color = Palette.textTertiary,
            )
            SegmentedPicker(
                options = ExtraPracticeScope.allCases,
                selection = scope,
                label = { strings[it.titleKey] },
                onSelect = onScopeChange,
            )
        }

        PrimaryButton(
            text = strings[StringKey.ReportsStartDrill],
            gradient = BookTheme.Amber.gradient,
            enabled = enabled,
            onClick = onStart,
        )
    }
}

// MARK: - Books

@Composable
private fun BooksSection(data: ReportData) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = strings[StringKey.ReportsByBook])
        for (book in data.books) {
            BookReportCard(book)
        }
    }
}

@Composable
private fun BookReportCard(report: BookReport) {
    val strings = LocalStrings.current

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().card(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(report.shortTitle, style = AppFont.cardTitle, color = Palette.textPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(report.summary.accuracy * 100).roundToInt()}%",
                style = AppFont.metricValueSmall,
                color = report.theme.solid,
            )
            Text(
                text = strings[StringKey.ReportsAccuracy],
                style = AppFont.badge,
                color = Palette.textSecondary,
            )
        }

        MeterBar(progress = report.summary.completedFraction, gradient = report.theme.gradient)
        SectionHeatGrid(sections = report.sections, theme = report.theme)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(text = strings.format(StringKey.BookWordsCount, report.summary.total))
            Chip(
                text = "${report.summary.mastered} ${strings[StringKey.ReportsMastered]}",
                symbol = AppSymbol.CheckmarkSeal,
                tint = Palette.success,
                background = Palette.successSoft,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * Every section as one cell, shaded by accuracy. Turns seventeen rows into a
 * single glanceable block.
 *
 * Laid out as manual rows of six rather than a `LazyVerticalGrid`: a lazy grid
 * cannot be nested inside a scrolling column without a fixed height, and the
 * whole point of the card is that it sizes to its content.
 */
@Composable
private fun SectionHeatGrid(sections: List<SectionReport>, theme: BookTheme) {
    val columns = 6

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in sections.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (section in row) {
                    HeatCell(section = section, theme = theme, modifier = Modifier.weight(1f))
                }
                // Keeps the last, partly filled row's cells the same width as
                // every other row's.
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeatCell(
    section: SectionReport,
    theme: BookTheme,
    modifier: Modifier = Modifier,
) {
    // Untouched sections stay neutral; touched ones fade in with accuracy, so
    // "not started" never looks like "doing badly".
    val fill = if (section.summary.isUntouched) {
        Palette.surfaceSunken
    } else {
        theme.solid.copy(alpha = (0.25 + 0.75 * section.summary.accuracy).toFloat())
    }
    val textColor =
        if (section.summary.isUntouched) Palette.textTertiary else Color.White

    // "Day 3" -> "3", "Review 1" -> "R1" so the cell stays readable.
    val digits = section.title.filter { it.isDigit() }
    val label = when {
        section.kind == SectionKind.Review -> if (digits.isEmpty()) "R" else "R$digits"
        digits.isEmpty() -> section.title
        else -> digits
    }

    val description = "${section.title}: " +
        "${(section.summary.accuracy * 100).roundToInt()} percent accuracy, " +
        "${section.summary.seen} of ${section.summary.total} seen"

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(fill, RoundedCornerShape(8.dp))
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = AppFont.badge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(2.dp),
        )
    }
}

// MARK: - Weakest

@Composable
private fun WeakestSection(data: ReportData) {
    if (data.weakest.isEmpty()) return
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(Metrics.cardRadius)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = strings[StringKey.ReportsWeakest])
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Palette.surface, shape)
                .border(1.dp, Palette.separator, shape),
        ) {
            data.weakest.forEachIndexed { index, word ->
                WeakWordRow(word)
                if (index < data.weakest.size - 1) {
                    HorizontalDivider(
                        color = Palette.separator,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeakWordRow(word: WeakWord) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(word.item.term, style = AppFont.cardTitle, color = Palette.textPrimary)
            Text(
                text = "${word.bookShortTitle} · ${word.sectionTitle}",
                style = AppFont.badge,
                color = Palette.textTertiary,
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TallyLabel(
                    value = word.mainStats?.correct ?: 0,
                    symbol = AppSymbol.Checkmark,
                    tint = Palette.success,
                )
                TallyLabel(
                    value = word.mainStats?.incorrect ?: 0,
                    symbol = AppSymbol.Xmark,
                    tint = Palette.danger,
                )
            }
            word.mainStats?.let { ChecklistView(display = it.checklist, compact = true) }
        }
    }
}

@Composable
private fun TallyLabel(value: Int, symbol: AppSymbol, tint: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = symbol.vector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Text("$value", style = AppFont.badge, color = tint)
    }
}

// MARK: - Trend

@Composable
private fun TrendSection(data: ReportData) {
    if (data.recentSessions.size < 2) return
    val strings = LocalStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = strings[StringKey.ReportsRecent])
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().card(),
        ) {
            SessionTrendChart(data.recentSessions)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(Palette.accent, strings[StringKey.TabStudy])
                LegendDot(Palette.warning, strings[StringKey.ReportsExtraPractice])
            }
        }
    }
}

/**
 * A bar per recent session, accuracy on a fixed 0–1 scale.
 *
 * Drawn on a `Canvas` rather than pulled in from a charting library. The iOS
 * build used Swift Charts, which is a system framework; the Android equivalents
 * are all third-party dependencies, and one chart of twelve bars is not worth
 * adding a library — or a network fetch at build time — to an app that ships
 * entirely offline.
 */
@Composable
private fun SessionTrendChart(sessions: List<SessionRecord>) {
    val strings = LocalStrings.current
    val accent = Palette.accent
    val warning = Palette.warning
    val axis = Palette.separator

    // Reversed because `recentSessions` arrives newest-first for the list, and a
    // chart reads oldest-to-newest.
    val points = sessions.reversed()

    val label = strings[StringKey.ReportsRecent]

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clearAndSetSemantics { contentDescription = label },
    ) {
        if (points.isEmpty()) return@Canvas

        // Gridlines at 0, 0.5 and 1.0 — the y-axis marks Swift Charts drew.
        for (fraction in listOf(0f, 0.5f, 1f)) {
            val y = size.height * (1f - fraction)
            drawLine(
                color = axis,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }

        val slot = size.width / points.size
        val barWidth = slot * 0.62f
        val corner = 3.dp.toPx()

        points.forEachIndexed { index, session ->
            val height = (size.height * session.accuracy).toFloat().coerceAtLeast(1f)
            val left = index * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = if (session.mode == PracticeMode.Extra) warning else accent,
                topLeft = Offset(left, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color, CircleShape)
        )
        Text(label, style = AppFont.badge, color = Palette.textSecondary)
    }
}

// MARK: - Split

@Composable
private fun SplitSection(data: ReportData) {
    val strings = LocalStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = strings[StringKey.ReportsMainVsExtra])
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                value = "${data.mainSummary.attempts}",
                label = strings[StringKey.CategoryMain],
                symbol = VocabCategory.Main.symbol,
                tint = Palette.accent,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "${data.extraSummary.attempts}",
                label = strings[StringKey.CategoryExtra],
                symbol = VocabCategory.Extra.symbol,
                tint = Palette.accent,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "${data.extraAttempts}",
                label = strings[StringKey.ReportsExtraPractice],
                symbol = AppSymbol.Bolt,
                tint = Palette.warning,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = strings.format(StringKey.ReportsRun, data.runNumber),
            style = AppFont.badge,
            color = Palette.textTertiary,
        )
    }
}
