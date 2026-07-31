package io.github.a1mohamad.toeflvocab.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import io.github.a1mohamad.toeflvocab.designsystem.AppFont
import io.github.a1mohamad.toeflvocab.designsystem.Chip
import io.github.a1mohamad.toeflvocab.designsystem.MeterBar
import io.github.a1mohamad.toeflvocab.designsystem.Metrics
import io.github.a1mohamad.toeflvocab.designsystem.Palette
import io.github.a1mohamad.toeflvocab.designsystem.card
import io.github.a1mohamad.toeflvocab.designsystem.EmptyStateView
import io.github.a1mohamad.toeflvocab.designsystem.gradient
import io.github.a1mohamad.toeflvocab.designsystem.screenBackground
import io.github.a1mohamad.toeflvocab.designsystem.solid
import io.github.a1mohamad.toeflvocab.designsystem.vector
import io.github.a1mohamad.toeflvocab.navigation.Route
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    val content = LocalContentProvider.current
    val progress = LocalProgressStore.current
    val router = LocalRouter.current
    val strings = LocalStrings.current

    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier
            .fillMaxSize()
            .screenBackground()
            .verticalScroll(rememberScrollState())
            .padding(Metrics.screenPadding)
            .padding(bottom = 24.dp),
    ) {
        // MARK: Header
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                text = strings[StringKey.LibraryTitle],
                style = AppFont.screenTitle,
                color = Palette.textPrimary,
            )
            Text(
                text = strings.format(
                    StringKey.LibrarySubtitle,
                    content.catalog.totalWordCount,
                ),
                style = AppFont.body,
                color = Palette.textSecondary,
            )
        }

        val error = content.loadError
        when {
            error != null -> ContentErrorCard(error)

            content.catalog.isEmpty -> EmptyStateView(
                symbol = AppSymbol.BooksVertical,
                title = strings[StringKey.LibraryEmpty],
                message = strings[StringKey.LibraryEmptyHint],
            )

            else -> {
                // Last place the user practised, resolved against the current
                // catalog so a removed section cannot produce a dead card.
                val location = progress.lastLocation
                val resumeBook = location?.let { content.catalog.book(it.bookID) }
                val resumeSection = resumeBook?.section(location.sectionID)
                if (location != null && resumeBook != null && resumeSection != null) {
                    ContinueCard(
                        target = ResumeTarget(
                            bookID = resumeBook.id,
                            sectionID = resumeSection.id,
                            bookTitle = resumeBook.shortTitle,
                            sectionTitle = resumeSection.title,
                            category = location.category,
                            theme = resumeBook.theme,
                        ),
                        onClick = {
                            router.open(
                                Route.SectionRoute(
                                    bookID = resumeBook.id,
                                    sectionID = resumeSection.id,
                                )
                            )
                        },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (book in content.catalog.books) {
                        BookCard(
                            book = book,
                            summary = MetricSummary.make(book.allItems) {
                                progress.stats(it, PracticeMode.Main)
                            },
                            onClick = { router.open(Route.BookRoute(book.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentErrorCard(message: String) {
    val strings = LocalStrings.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .card(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AppSymbol.ExclamationTriangle.vector,
                contentDescription = null,
                tint = Palette.warning,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = strings[StringKey.LibraryEmpty],
                style = AppFont.cardTitle,
                color = Palette.warning,
            )
        }
        Text(message, style = AppFont.body, color = Palette.textSecondary)
    }
}

// MARK: - Resume

data class ResumeTarget(
    val bookID: String,
    val sectionID: String,
    val bookTitle: String,
    val sectionTitle: String,
    val category: VocabCategory,
    val theme: BookTheme,
)

@Composable
private fun ContinueCard(target: ResumeTarget, onClick: () -> Unit) {
    val strings = LocalStrings.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Metrics.cardRadius))
            .clickable(onClick = onClick)
            .card(background = Palette.surfaceRaised)
            .semantics(mergeDescendants = true) { },
    ) {
        Icon(
            imageVector = AppSymbol.PlayCircle.vector,
            contentDescription = null,
            tint = target.theme.solid,
            modifier = Modifier.size(32.dp),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = strings[StringKey.LibraryContinue],
                style = AppFont.badge,
                color = Palette.textTertiary,
            )
            Text(
                text = "${target.bookTitle} · ${target.sectionTitle}",
                style = AppFont.cardTitle,
                color = Palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = strings[target.category.titleKey],
                style = AppFont.caption,
                color = Palette.textSecondary,
            )
        }

        Icon(
            imageVector = AppSymbol.ChevronForward.vector,
            contentDescription = null,
            tint = Palette.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

// MARK: - Book card

@Composable
private fun BookCard(book: Book, summary: MetricSummary, onClick: () -> Unit) {
    val strings = LocalStrings.current
    val percent = (summary.completedFraction * 100).roundToInt()

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Metrics.cardRadius))
            .clickable(onClick = onClick)
            .card()
            .semantics(mergeDescendants = true) { },
    ) {
        // Top row
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Stylised "spine" standing in for cover art — no image assets to
            // ship.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 58.dp, height = 74.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(book.theme.gradient, RoundedCornerShape(14.dp)),
            ) {
                Text(
                    text = book.id,
                    style = AppFont.cardTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.padding(4.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(book.title, style = AppFont.title, color = Palette.textPrimary)
                if (book.author.isNotEmpty()) {
                    Text(book.author, style = AppFont.caption, color = Palette.textSecondary)
                }
            }
        }

        Text(
            text = book.intro,
            style = AppFont.body,
            color = Palette.textSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        // Footer
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MeterBar(progress = summary.completedFraction, gradient = book.theme.gradient)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(
                    text = strings.format(StringKey.BookSectionsCount, book.sections.size),
                    symbol = AppSymbol.Layers,
                )
                Chip(
                    text = strings.format(StringKey.BookWordsCount, book.wordCount),
                    symbol = AppSymbol.Alphabet,
                )
                Spacer(Modifier.weight(1f))
                Text("$percent%", style = AppFont.caption, color = book.theme.solid)
            }
        }
    }
}
