package io.github.a1mohamad.toeflvocab.features.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.a1mohamad.toeflvocab.app.InlineTopBar
import io.github.a1mohamad.toeflvocab.app.LocalContentProvider
import io.github.a1mohamad.toeflvocab.app.LocalProgressStore
import io.github.a1mohamad.toeflvocab.app.LocalRouter
import io.github.a1mohamad.toeflvocab.core.engine.MetricSummary
import io.github.a1mohamad.toeflvocab.core.localization.LocalStrings
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import io.github.a1mohamad.toeflvocab.core.models.AppSymbol
import io.github.a1mohamad.toeflvocab.core.models.Book
import io.github.a1mohamad.toeflvocab.core.models.BookTheme
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.VocabItem
import io.github.a1mohamad.toeflvocab.core.models.VocabSection
import io.github.a1mohamad.toeflvocab.designsystem.AppFont
import io.github.a1mohamad.toeflvocab.designsystem.Chip
import io.github.a1mohamad.toeflvocab.designsystem.EmptyStateView
import io.github.a1mohamad.toeflvocab.designsystem.LocalIsDarkTheme
import io.github.a1mohamad.toeflvocab.designsystem.MeterBar
import io.github.a1mohamad.toeflvocab.designsystem.Metrics
import io.github.a1mohamad.toeflvocab.designsystem.Palette
import io.github.a1mohamad.toeflvocab.designsystem.ProgressRingLabelled
import io.github.a1mohamad.toeflvocab.designsystem.SectionHeader
import io.github.a1mohamad.toeflvocab.designsystem.card
import io.github.a1mohamad.toeflvocab.designsystem.gradient
import io.github.a1mohamad.toeflvocab.designsystem.screenBackground
import io.github.a1mohamad.toeflvocab.designsystem.solid
import io.github.a1mohamad.toeflvocab.designsystem.vector
import io.github.a1mohamad.toeflvocab.designsystem.wash
import io.github.a1mohamad.toeflvocab.navigation.Route

/**
 * Book preview: what this book is, how far through it you are, and the list of
 * sections. The intro is deliberately shown before the section list rather than
 * buried behind a disclosure — the preview is part of the flow, not a footnote.
 */
@Composable
fun BookIntroScreen(
    bookID: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = LocalContentProvider.current
    val progress = LocalProgressStore.current
    val router = LocalRouter.current
    val strings = LocalStrings.current

    val book = content.catalog.book(bookID)

    fun summary(items: List<VocabItem>): MetricSummary =
        MetricSummary.make(items) { progress.stats(it, PracticeMode.Main) }

    Column(modifier = modifier.fillMaxSize().screenBackground()) {
        InlineTopBar(title = book?.shortTitle ?: "", onBack = onBack)

        if (book == null) {
            EmptyStateView(
                symbol = AppSymbol.QuestionFolder,
                title = strings[StringKey.LibraryEmpty],
                message = strings[StringKey.LibraryEmptyHint],
            )
            return@Column
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Metrics.screenPadding)
                .padding(bottom = 24.dp),
        ) {
            BookHero(book = book, summary = summary(book.allItems))

            // About this book
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().card(),
            ) {
                Text(
                    text = strings[StringKey.BookAbout].uppercase(),
                    style = AppFont.sectionHeader.copy(letterSpacing = 0.6.sp),
                    color = Palette.textTertiary,
                )
                Text(book.intro, style = AppFont.body, color = Palette.textSecondary)
            }

            // Sections
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = strings.format(StringKey.BookSectionsCount, book.sections.size)
                )
                for (section in book.sections) {
                    SectionRow(
                        section = section,
                        theme = book.theme,
                        summary = summary(section.allItems),
                        onClick = {
                            router.open(
                                Route.SectionRoute(bookID = book.id, sectionID = section.id)
                            )
                        },
                    )
                }
            }
        }
    }
}

// MARK: - Hero

@Composable
private fun BookHero(book: Book, summary: MetricSummary) {
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(Metrics.cardRadius)

    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(book.theme.gradient, shape)
            .padding(Metrics.cardPadding),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(book.title, style = AppFont.title, color = Color.White)
                if (book.author.isNotEmpty()) {
                    Text(
                        text = book.author,
                        style = AppFont.caption,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }

            // The ring sits on the book's colour gradient, so its labels need
            // the light-on-dark treatment in both appearances.
            CompositionLocalProvider(LocalIsDarkTheme provides true) {
                ProgressRingLabelled(
                    progress = summary.completedFraction,
                    caption = strings[StringKey.BookProgress],
                    gradient = Brush.linearGradient(listOf(Color.White, Color.White)),
                    diameter = 84.dp,
                    labelColor = Color.White,
                    captionColor = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat("${summary.total}", strings[StringKey.StatWords], Modifier.weight(1f))
            HeroStat("${summary.seen}", strings[StringKey.ReportsSeen], Modifier.weight(1f))
            HeroStat("${summary.mastered}", strings[StringKey.ReportsMastered], Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.15f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Text(value, style = AppFont.metricValueSmall, color = Color.White)
        Text(label, style = AppFont.badge, color = Color.White.copy(alpha = 0.8f))
    }
}

// MARK: - Section row

@Composable
private fun SectionRow(
    section: VocabSection,
    theme: BookTheme,
    summary: MetricSummary,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Metrics.cardRadius))
            .clickable(onClick = onClick)
            .card(padding = 14.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.wash, RoundedCornerShape(12.dp)),
        ) {
            Icon(
                imageVector = section.kind.symbol.vector,
                contentDescription = null,
                tint = theme.solid,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(section.title, style = AppFont.cardTitle, color = Palette.textPrimary)
                if (summary.total > 0 && summary.completed == summary.total) {
                    Icon(
                        imageVector = AppSymbol.CheckmarkSeal.vector,
                        contentDescription = null,
                        tint = Palette.success,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            MeterBar(
                progress = summary.completedFraction,
                gradient = theme.gradient,
                height = 6.dp,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(text = strings.format(StringKey.BookWordsCount, section.wordCount))
                if (summary.needsWork > 0) {
                    Chip(
                        text = strings.format(StringKey.SectionNeedWork, summary.needsWork),
                        symbol = AppSymbol.ExclamationCircle,
                        tint = Palette.warning,
                        background = Palette.dangerSoft,
                    )
                } else if (summary.isUntouched) {
                    Chip(text = strings[StringKey.SectionNotStarted])
                }
                Spacer(Modifier.weight(1f))
            }
        }

        Icon(
            imageVector = AppSymbol.ChevronForward.vector,
            contentDescription = null,
            tint = Palette.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}
