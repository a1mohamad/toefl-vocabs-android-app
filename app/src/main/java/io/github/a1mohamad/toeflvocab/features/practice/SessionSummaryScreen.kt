package io.github.a1mohamad.toeflvocab.features.practice

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.a1mohamad.toeflvocab.app.LocalContentProvider
import io.github.a1mohamad.toeflvocab.app.LocalProgressStore
import io.github.a1mohamad.toeflvocab.app.LocalRouter
import io.github.a1mohamad.toeflvocab.app.LocalSettingsStore
import io.github.a1mohamad.toeflvocab.core.audio.Haptics
import io.github.a1mohamad.toeflvocab.core.engine.StatsAggregator
import io.github.a1mohamad.toeflvocab.core.localization.LocalStrings
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import io.github.a1mohamad.toeflvocab.core.models.AppSymbol
import io.github.a1mohamad.toeflvocab.core.models.LastLocation
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import io.github.a1mohamad.toeflvocab.designsystem.AppFont
import io.github.a1mohamad.toeflvocab.designsystem.Metrics
import io.github.a1mohamad.toeflvocab.designsystem.Palette
import io.github.a1mohamad.toeflvocab.designsystem.PrimaryButton
import io.github.a1mohamad.toeflvocab.designsystem.ProgressRingLabelled
import io.github.a1mohamad.toeflvocab.designsystem.SecondaryButton
import io.github.a1mohamad.toeflvocab.designsystem.StatTile
import io.github.a1mohamad.toeflvocab.designsystem.card
import io.github.a1mohamad.toeflvocab.designsystem.gradient
import io.github.a1mohamad.toeflvocab.designsystem.screenBackground
import io.github.a1mohamad.toeflvocab.designsystem.solid
import io.github.a1mohamad.toeflvocab.designsystem.vector
import io.github.a1mohamad.toeflvocab.navigation.PracticeConfiguration

/**
 * End-of-epoch screen: what just happened, and the three ways forward.
 *
 * Also the place the two loop rules surface:
 *  * finishing a drill pass shows the "full pass complete, starting again from
 *    the weakest" notice;
 *  * finishing anything at a moment when *every* word in the library has banked
 *    a five-answer cycle offers the full restart.
 */
@Composable
fun SessionSummaryScreen(
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier,
) {
    val content = LocalContentProvider.current
    val progress = LocalProgressStore.current
    val router = LocalRouter.current
    val settings = LocalSettingsStore.current
    val strings = LocalStrings.current
    val view = LocalView.current

    var showRestartConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Haptics.milestone(view, settings.settings.haptics)
    }

    val allWordsCompleted = StatsAggregator.allWordsCompleted(content.catalog, progress.state)

    val nextSection = run {
        if (viewModel.configuration.mode != PracticeMode.Main) return@run null
        val bookID = viewModel.configuration.bookID ?: return@run null
        val sectionID = viewModel.configuration.sectionID ?: return@run null
        val book = content.catalog.book(bookID) ?: return@run null
        val next = book.sectionAfter(sectionID) ?: return@run null
        book to next
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
            .fillMaxSize()
            .screenBackground()
            .verticalScroll(rememberScrollState())
            .padding(Metrics.screenPadding)
            .padding(top = 30.dp, bottom = 30.dp),
    ) {
        // MARK: Headline
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(viewModel.theme.gradient, CircleShape),
            ) {
                Icon(
                    imageVector = AppSymbol.Checkmark.vector,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }

            Text(
                text = strings[StringKey.SummaryHeadline],
                style = AppFont.screenTitle,
                color = Palette.textPrimary,
            )

            val subtitle = when {
                viewModel.configuration.mode == PracticeMode.Extra ->
                    strings[StringKey.ReportsExtraPractice]

                viewModel.headerTitle != null ->
                    "${strings[StringKey.SummaryTitle]} · ${viewModel.headerTitle}"

                else -> strings[StringKey.SummaryTitle]
            }
            Text(
                text = subtitle,
                style = AppFont.body,
                color = Palette.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        // MARK: Stats
        val outcome = viewModel.outcome
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().card(),
        ) {
            ProgressRingLabelled(
                progress = outcome.accuracy,
                caption = strings[StringKey.SummaryAccuracy],
                gradient = viewModel.theme.gradient,
                diameter = 100.dp,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                StatTile(
                    value = "${outcome.correct}/${outcome.answered}",
                    label = strings[StringKey.SummaryAnswered],
                    symbol = AppSymbol.CheckmarkCircle,
                    tint = Palette.success,
                    modifier = Modifier.fillMaxWidth(),
                )
                StatTile(
                    value = "${outcome.cyclesCompleted}",
                    label = strings[StringKey.PracticeCycleComplete],
                    symbol = AppSymbol.CycleGrid,
                    tint = viewModel.theme.solid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (viewModel.configuration.mode == PracticeMode.Extra) {
            NoticeCard(
                title = strings[StringKey.ExtraLoopTitle],
                message = strings[StringKey.ExtraLoopMessage],
                symbol = AppSymbol.Review,
                tint = viewModel.theme.solid,
            )
        }

        if (allWordsCompleted) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NoticeCard(
                    title = strings[StringKey.RestartTitle],
                    message = strings[StringKey.RestartMessage],
                    symbol = AppSymbol.FlagCheckered,
                    tint = viewModel.theme.solid,
                )
                SecondaryButton(
                    text = strings[StringKey.RestartAction],
                    tint = viewModel.theme.solid,
                    onClick = { showRestartConfirmation = true },
                )
            }
        }

        // MARK: Actions
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (nextSection != null) {
                val (book, section) = nextSection
                PrimaryButton(
                    text = strings[StringKey.SummaryNextSection],
                    gradient = viewModel.theme.gradient,
                    onClick = {
                        // Keep the same list where the next section has one —
                        // `504/review_1` has no extras, so fall back to whatever
                        // it does have.
                        val requested = viewModel.configuration.category ?: VocabCategory.Main
                        val category = if (section.availableCategories.contains(requested)) {
                            requested
                        } else {
                            section.availableCategories.firstOrNull() ?: VocabCategory.Main
                        }

                        progress.rememberLocation(
                            LastLocation(
                                bookID = book.id,
                                sectionID = section.id,
                                category = category,
                            )
                        )
                        router.replacePractice(
                            PracticeConfiguration.section(
                                bookID = book.id,
                                sectionID = section.id,
                                category = category,
                            )
                        )
                    },
                )
            } else if (viewModel.configuration.mode == PracticeMode.Main) {
                Text(
                    text = strings[StringKey.SummaryBookComplete],
                    style = AppFont.caption,
                    color = Palette.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                )
            }

            SecondaryButton(
                text = strings[StringKey.SummaryPracticeAgain],
                onClick = { viewModel.restart() },
            )

            SecondaryButton(
                text = strings[StringKey.SummaryBackToMenu],
                tint = Palette.textSecondary,
                onClick = { router.returnToLibrary() },
            )
        }
    }

    if (showRestartConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestartConfirmation = false },
            title = { Text(strings[StringKey.RestartTitle]) },
            text = { Text(strings[StringKey.RestartMessage]) },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirmation = false
                    progress.beginNewRun()
                    router.returnToLibrary()
                }) {
                    Text(strings[StringKey.RestartAction])
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirmation = false }) {
                    Text(strings[StringKey.RestartLater])
                }
            },
            containerColor = Palette.surfaceRaised,
            titleContentColor = Palette.textPrimary,
            textContentColor = Palette.textSecondary,
        )
    }
}

@Composable
private fun NoticeCard(
    title: String,
    message: String,
    symbol: AppSymbol,
    tint: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .card(background = Palette.surfaceRaised)
            .semantics(mergeDescendants = true) { },
    ) {
        Icon(
            imageVector = symbol.vector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(title, style = AppFont.cardTitle, color = Palette.textPrimary)
            Text(message, style = AppFont.body, color = Palette.textSecondary)
        }
        Spacer(Modifier.size(0.dp))
    }
}
