package io.github.a1mohamad.toeflvocab.features.section

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.a1mohamad.toeflvocab.app.InlineTopBar
import io.github.a1mohamad.toeflvocab.app.LocalContentProvider
import io.github.a1mohamad.toeflvocab.app.LocalProgressStore
import io.github.a1mohamad.toeflvocab.app.LocalRouter
import io.github.a1mohamad.toeflvocab.core.engine.AdaptiveOrdering
import io.github.a1mohamad.toeflvocab.core.engine.MetricSummary
import io.github.a1mohamad.toeflvocab.core.localization.LocalStrings
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import io.github.a1mohamad.toeflvocab.core.models.AppSymbol
import io.github.a1mohamad.toeflvocab.core.models.BookTheme
import io.github.a1mohamad.toeflvocab.core.models.LastLocation
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.VocabCategory
import io.github.a1mohamad.toeflvocab.core.models.VocabSection
import io.github.a1mohamad.toeflvocab.designsystem.AppFont
import io.github.a1mohamad.toeflvocab.designsystem.Chip
import io.github.a1mohamad.toeflvocab.designsystem.EmptyStateView
import io.github.a1mohamad.toeflvocab.designsystem.Metrics
import io.github.a1mohamad.toeflvocab.designsystem.Palette
import io.github.a1mohamad.toeflvocab.designsystem.PrimaryButton
import io.github.a1mohamad.toeflvocab.designsystem.SectionHeader
import io.github.a1mohamad.toeflvocab.designsystem.card
import io.github.a1mohamad.toeflvocab.designsystem.gradient
import io.github.a1mohamad.toeflvocab.designsystem.screenBackground
import io.github.a1mohamad.toeflvocab.designsystem.solid
import io.github.a1mohamad.toeflvocab.designsystem.vector
import io.github.a1mohamad.toeflvocab.designsystem.wash
import io.github.a1mohamad.toeflvocab.navigation.PracticeConfiguration

/**
 * Section preview and list picker — the last stop before practice starts.
 *
 * Shows which words are queued first, so the adaptive ordering is visible rather
 * than mysterious: after a bad run the user can see their worst words waiting at
 * the front before they tap Begin.
 */
@Composable
fun SectionIntroScreen(
    bookID: String,
    sectionID: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = LocalContentProvider.current
    val progress = LocalProgressStore.current
    val router = LocalRouter.current
    val strings = LocalStrings.current

    val book = content.catalog.book(bookID)
    val section = book?.section(sectionID)

    var selectedCategory by remember(bookID, sectionID) {
        mutableStateOf(VocabCategory.Main)
    }

    // `504/review_1` has no extras, so the default must be whatever the section
    // actually has rather than a hard-coded Main.
    LaunchedEffect(section) {
        val available = section?.availableCategories.orEmpty()
        if (available.isNotEmpty() && selectedCategory !in available) {
            selectedCategory = available[0]
        }
    }

    Column(modifier = modifier.fillMaxSize().screenBackground()) {
        InlineTopBar(title = section?.title ?: "", onBack = onBack)

        if (book == null || section == null) {
            EmptyStateView(
                symbol = AppSymbol.QuestionFolder,
                title = strings[StringKey.LibraryEmpty],
                message = strings[StringKey.LibraryEmptyHint],
            )
            return@Column
        }

        val theme = book.theme

        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Metrics.screenPadding)
                .padding(bottom = 24.dp),
        ) {
            // Intro
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().card(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = section.kind.symbol.vector,
                        contentDescription = null,
                        tint = theme.solid,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(section.title, style = AppFont.title, color = Palette.textPrimary)
                }
                if (section.intro.isNotEmpty()) {
                    Text(section.intro, style = AppFont.body, color = Palette.textSecondary)
                }
            }

            // Category picker
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = strings[StringKey.SectionChooseList])

                for (category in VocabCategory.allCases) {
                    val items = section.items(category)
                    if (items.isEmpty()) {
                        if (category == VocabCategory.Extra) {
                            Text(
                                text = strings[StringKey.SectionNoExtras],
                                style = AppFont.caption,
                                color = Palette.textTertiary,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    } else {
                        CategoryCard(
                            category = category,
                            theme = theme,
                            summary = MetricSummary.make(items) {
                                progress.stats(it, PracticeMode.Main)
                            },
                            isSelected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                        )
                    }
                }
            }

            // Queue preview
            val preview = AdaptiveOrdering.order(section.items(selectedCategory)) {
                progress.stats(it, PracticeMode.Main)
            }.take(3)

            if (preview.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = strings[StringKey.SectionBegin])
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (item in preview) {
                            Text(
                                text = item.term,
                                style = AppFont.caption,
                                color = Palette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Palette.surfaceSunken, CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        Text("…", style = AppFont.caption, color = Palette.textTertiary)
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Begin
            PrimaryButton(
                text = strings[StringKey.SectionBegin],
                gradient = theme.gradient,
                enabled = section.items(selectedCategory).isNotEmpty(),
                onClick = {
                    progress.rememberLocation(
                        LastLocation(
                            bookID = book.id,
                            sectionID = section.id,
                            category = selectedCategory,
                        )
                    )
                    router.startPractice(
                        PracticeConfiguration.section(
                            bookID = book.id,
                            sectionID = section.id,
                            category = selectedCategory,
                        )
                    )
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// MARK: - Category card

@Composable
private fun CategoryCard(
    category: VocabCategory,
    theme: BookTheme,
    summary: MetricSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(Metrics.cardRadius)

    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) theme.wash else Palette.surface, shape)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) theme.solid else Palette.separator,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { selected = isSelected },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(theme.wash, CircleShape),
        ) {
            Icon(
                imageVector = category.symbol.vector,
                contentDescription = null,
                tint = theme.solid,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = strings[category.titleKey],
                style = AppFont.cardTitle,
                color = Palette.textPrimary,
            )
            Text(
                text = strings[category.subtitleKey],
                style = AppFont.caption,
                color = Palette.textSecondary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Chip(text = strings.format(StringKey.BookWordsCount, summary.total))
                if (summary.needsWork > 0) {
                    Chip(
                        text = strings.format(StringKey.SectionNeedWork, summary.needsWork),
                        symbol = AppSymbol.ExclamationCircle,
                        tint = Palette.warning,
                        background = Palette.dangerSoft,
                    )
                }
            }
        }

        Icon(
            imageVector = (
                if (isSelected) AppSymbol.RadioSelected else AppSymbol.RadioUnselected
                ).vector,
            contentDescription = null,
            tint = if (isSelected) theme.solid else Palette.textTertiary,
            modifier = Modifier.size(22.dp),
        )
    }
}
