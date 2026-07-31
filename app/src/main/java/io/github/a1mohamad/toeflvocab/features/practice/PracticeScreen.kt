package io.github.a1mohamad.toeflvocab.features.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.a1mohamad.toeflvocab.BuildConfig
import io.github.a1mohamad.toeflvocab.app.LocalContentProvider
import io.github.a1mohamad.toeflvocab.app.LocalProgressStore
import io.github.a1mohamad.toeflvocab.app.LocalPronunciationService
import io.github.a1mohamad.toeflvocab.app.LocalReduceMotion
import io.github.a1mohamad.toeflvocab.app.LocalRouter
import io.github.a1mohamad.toeflvocab.app.LocalSettingsStore
import io.github.a1mohamad.toeflvocab.app.ScreenshotHarness
import io.github.a1mohamad.toeflvocab.core.audio.Haptics
import io.github.a1mohamad.toeflvocab.core.localization.LocalStrings
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import io.github.a1mohamad.toeflvocab.core.models.AppSymbol
import io.github.a1mohamad.toeflvocab.core.models.PracticeMode
import io.github.a1mohamad.toeflvocab.core.models.SpeechAccent
import io.github.a1mohamad.toeflvocab.core.models.VocabItem
import io.github.a1mohamad.toeflvocab.designsystem.AnswerButton
import io.github.a1mohamad.toeflvocab.designsystem.AppFont
import io.github.a1mohamad.toeflvocab.designsystem.ChecklistView
import io.github.a1mohamad.toeflvocab.designsystem.Chip
import io.github.a1mohamad.toeflvocab.designsystem.MeterBar
import io.github.a1mohamad.toeflvocab.designsystem.Metrics
import io.github.a1mohamad.toeflvocab.designsystem.Palette
import io.github.a1mohamad.toeflvocab.designsystem.PrimaryButton
import io.github.a1mohamad.toeflvocab.designsystem.PronunciationControl
import io.github.a1mohamad.toeflvocab.designsystem.UsageTipView
import io.github.a1mohamad.toeflvocab.designsystem.gradient
import io.github.a1mohamad.toeflvocab.designsystem.vector
import io.github.a1mohamad.toeflvocab.navigation.PracticeConfiguration

// MARK: - Container

/**
 * Builds the view model from the environment. Split out because the model has to
 * be constructed with the stores, which are only readable inside composition.
 *
 * `remember(configuration.id)` is what makes "next section" work: changing the
 * configuration gives the child a new key, so a fresh view model is built
 * instead of the finished one being reused.
 */
@Composable
fun PracticeContainer(
    configuration: PracticeConfiguration,
    modifier: Modifier = Modifier,
) {
    val content = LocalContentProvider.current
    val progress = LocalProgressStore.current

    val viewModel = remember(configuration.id) {
        PracticeViewModel(
            configuration = configuration,
            catalog = content.catalog,
            progress = progress,
        )
    }

    PracticeScreen(viewModel = viewModel, modifier = modifier)
}

// MARK: - Practice

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier,
) {
    val speech = LocalPronunciationService.current
    val settings = LocalSettingsStore.current
    val router = LocalRouter.current
    val strings = LocalStrings.current
    val reduceMotion = LocalReduceMotion.current
    val view = LocalView.current

    fun speakCurrent() {
        val item = viewModel.currentItem ?: return
        speech.speak(
            item.term,
            settings.settings.accent,
            settings.settings.clampedSpeechRate,
        )
    }

    fun autoSpeakIfEnabled() {
        if (!settings.settings.autoSpeak) return
        if (viewModel.phase != PracticeViewModel.Phase.Question) return
        speakCurrent()
    }

    LaunchedEffect(viewModel) {
        if (BuildConfig.DEBUG) {
            ScreenshotHarness.advance(viewModel)
        }
        autoSpeakIfEnabled()
    }

    LaunchedEffect(viewModel.index) { autoSpeakIfEnabled() }

    LaunchedEffect(viewModel.dismissRequested) {
        if (viewModel.dismissRequested) {
            speech.stop()
            router.endPractice()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Palette.background)) {
        if (viewModel.phase == PracticeViewModel.Phase.Finished) {
            SessionSummaryScreen(viewModel = viewModel)
        } else {
            SessionBody(
                viewModel = viewModel,
                reduceMotion = reduceMotion,
                onSpeak = ::speakCurrent,
                onCycleAccent = {
                    val accents = SpeechAccent.allCases
                    val current = accents.indexOf(settings.settings.accent)
                    if (current >= 0) {
                        settings.update {
                            it.copy(accent = accents[(current + 1) % accents.size])
                        }
                    }
                    Haptics.tap(view, settings.settings.haptics)
                    speakCurrent()
                },
                onAnswer = { correct ->
                    viewModel.answer(correct)
                    Haptics.answer(view, correct, settings.settings.haptics)
                },
            )
        }
    }

    if (viewModel.showQuitConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.showQuitConfirmation = false },
            title = { Text(strings[StringKey.PracticeQuitTitle]) },
            text = { Text(strings[StringKey.PracticeQuitMessage]) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.showQuitConfirmation = false
                    viewModel.confirmQuit()
                }) {
                    Text(strings[StringKey.CommonQuit], color = Palette.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showQuitConfirmation = false }) {
                    Text(strings[StringKey.CommonKeepGoing])
                }
            },
            containerColor = Palette.surfaceRaised,
            titleContentColor = Palette.textPrimary,
            textContentColor = Palette.textSecondary,
        )
    }
}

// MARK: - Layout

@Composable
private fun SessionBody(
    viewModel: PracticeViewModel,
    reduceMotion: Boolean,
    onSpeak: () -> Unit,
    onCycleAccent: () -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(viewModel)

        // Centres the card in the available space, but still scrolls once a long
        // definition at a large font-scale setting makes it taller than the
        // screen.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Metrics.screenPadding, vertical = 18.dp),
            ) {
                WordCard(
                    viewModel = viewModel,
                    reduceMotion = reduceMotion,
                    onSpeak = onSpeak,
                    onCycleAccent = onCycleAccent,
                )
            }
        }

        Controls(viewModel = viewModel, onAnswer = onAnswer)
    }
}

// MARK: - Top bar

@Composable
private fun TopBar(viewModel: PracticeViewModel) {
    val strings = LocalStrings.current
    val (current, total) = viewModel.positionText

    val headerTitleText = viewModel.headerTitle
        ?: viewModel.headerTitleKey?.let { strings[it] }
        ?: ""

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.surface)
            .padding(horizontal = Metrics.screenPadding)
            .padding(top = 10.dp, bottom = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Always reachable, always in the same place — a practice session
            // the user cannot leave is a trap.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(Metrics.minimumTapTarget)
                    .clip(CircleShape)
                    .background(Palette.surfaceSunken, CircleShape)
                    .clickable { viewModel.requestQuit() },
            ) {
                Icon(
                    imageVector = AppSymbol.Xmark.vector,
                    contentDescription = strings[StringKey.CommonQuit],
                    tint = Palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { },
            ) {
                Text(
                    text = headerTitleText,
                    style = AppFont.caption,
                    color = Palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                viewModel.headerSubtitleKey?.let { key ->
                    Text(strings[key], style = AppFont.badge, color = Palette.textTertiary)
                }
            }

            Text(
                text = strings.format(StringKey.PracticeProgress, current, total),
                style = AppFont.badgeMono,
                color = Palette.textSecondary,
            )
        }

        MeterBar(
            progress = viewModel.progressFraction,
            gradient = viewModel.theme.gradient,
            height = 5.dp,
        )
    }
}

// MARK: - Word card

@Composable
private fun WordCard(
    viewModel: PracticeViewModel,
    reduceMotion: Boolean,
    onSpeak: () -> Unit,
    onCycleAccent: () -> Unit,
) {
    val strings = LocalStrings.current
    val settings = LocalSettingsStore.current
    val speech = LocalPronunciationService.current
    val shape = RoundedCornerShape(Metrics.cardRadius)
    val item = viewModel.currentItem ?: return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface, shape)
            .border(1.dp, Palette.separator, shape)
            .padding(horizontal = 18.dp, vertical = 26.dp),
    ) {
        // Term
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.term,
                style = AppFont.word,
                color = Palette.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            TallyRow(viewModel)
        }

        PronunciationControl(
            term = item.term,
            accent = settings.settings.accent,
            isSpeaking = speech.isSpeaking,
            gradient = viewModel.theme.gradient,
            onSpeak = onSpeak,
            onCycleAccent = onCycleAccent,
            reduceMotion = reduceMotion,
        )

        // Checklist
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ChecklistView(display = viewModel.checklist)
            Text(
                text = if (viewModel.checklist.isRecap) {
                    strings[StringKey.PracticeLastFive]
                } else {
                    strings[StringKey.PracticeThisCycle]
                },
                style = AppFont.badge,
                color = if (viewModel.checklist.isRecap) Palette.accent else Palette.textTertiary,
            )
        }

        MeaningBlock(viewModel = viewModel, item = item, reduceMotion = reduceMotion)
    }
}

/**
 * Right/wrong history for this word. Shown in the drill, where knowing you have
 * missed something five times is the motivation to slow down.
 */
@Composable
private fun TallyRow(viewModel: PracticeViewModel) {
    val strings = LocalStrings.current
    val (right, wrong) = viewModel.currentTallies

    if (viewModel.configuration.mode == PracticeMode.Extra && right + wrong > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(
                text = strings.format(StringKey.PracticeCorrectTally, right),
                symbol = AppSymbol.Checkmark,
                tint = Palette.success,
                background = Palette.successSoft,
            )
            Chip(
                text = strings.format(StringKey.PracticeWrongTally, wrong),
                symbol = AppSymbol.Xmark,
                tint = Palette.danger,
                background = Palette.dangerSoft,
            )
        }
    } else if (viewModel.currentStats == null) {
        Chip(text = strings[StringKey.PracticeNewWord], symbol = AppSymbol.Sparkle)
    }
}

@Composable
private fun MeaningBlock(
    viewModel: PracticeViewModel,
    item: VocabItem,
    reduceMotion: Boolean,
) {
    val strings = LocalStrings.current
    val wasCorrect = viewModel.revealedAnswer

    // Held across the exit animation: `revealedAnswer` goes null the instant the
    // phase changes, and reading it directly would blank the block out from
    // under the fade rather than fading it.
    var lastAnswer by remember { mutableStateOf(false) }
    if (wasCorrect != null) lastAnswer = wasCorrect
    val correct = lastAnswer

    AnimatedVisibility(
        visible = wasCorrect != null,
        enter = if (reduceMotion) fadeIn(androidx.compose.animation.core.snap()) else fadeIn(),
        exit = if (reduceMotion) fadeOut(androidx.compose.animation.core.snap()) else fadeOut(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            HorizontalDivider(color = Palette.separator)

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = (
                        if (correct) AppSymbol.CheckmarkCircle else AppSymbol.XmarkCircle
                        ).vector,
                    contentDescription = null,
                    tint = if (correct) Palette.success else Palette.danger,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = strings[
                        if (correct) StringKey.PracticeKnewIt else StringKey.PracticeDidntKnow
                    ],
                    style = AppFont.caption,
                    color = Palette.textSecondary,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = strings[StringKey.PracticeMeaning].uppercase(),
                    style = AppFont.badge.copy(letterSpacing = 0.6.sp),
                    color = Palette.textTertiary,
                )
                Text(
                    text = item.definition,
                    style = AppFont.definition,
                    color = Palette.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }

            item.usageTip?.let { UsageTipView(tip = it) }
        }
    }
}

// MARK: - Controls

@Composable
private fun Controls(viewModel: PracticeViewModel, onAnswer: (Boolean) -> Unit) {
    val strings = LocalStrings.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Metrics.screenPadding)
            .padding(bottom = 12.dp),
    ) {
        if (viewModel.revealedAnswer == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnswerButton(
                    text = strings[StringKey.PracticeDidntKnow],
                    symbol = AppSymbol.Xmark,
                    tint = Palette.danger,
                    soft = Palette.dangerSoft,
                    onClick = { onAnswer(false) },
                    modifier = Modifier.weight(1f),
                )
                AnswerButton(
                    text = strings[StringKey.PracticeKnewIt],
                    symbol = AppSymbol.Checkmark,
                    tint = Palette.success,
                    soft = Palette.successSoft,
                    onClick = { onAnswer(true) },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            PrimaryButton(
                text = if (viewModel.isOnLastItem) {
                    strings[StringKey.PracticeFinish]
                } else {
                    strings[StringKey.PracticeNextWord]
                },
                gradient = viewModel.theme.gradient,
                onClick = { viewModel.advance() },
            )
        }
    }
}
