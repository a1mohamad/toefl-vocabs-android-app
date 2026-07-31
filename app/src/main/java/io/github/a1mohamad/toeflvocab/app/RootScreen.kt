package io.github.a1mohamad.toeflvocab.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.a1mohamad.toeflvocab.BuildConfig
import io.github.a1mohamad.toeflvocab.core.localization.LocalStrings
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import io.github.a1mohamad.toeflvocab.core.models.AppSymbol
import io.github.a1mohamad.toeflvocab.designsystem.AppFont
import io.github.a1mohamad.toeflvocab.designsystem.Metrics
import io.github.a1mohamad.toeflvocab.designsystem.Palette
import io.github.a1mohamad.toeflvocab.designsystem.vector
import io.github.a1mohamad.toeflvocab.features.book.BookIntroScreen
import io.github.a1mohamad.toeflvocab.features.library.LibraryScreen
import io.github.a1mohamad.toeflvocab.features.practice.PracticeContainer
import io.github.a1mohamad.toeflvocab.features.reports.ReportsScreen
import io.github.a1mohamad.toeflvocab.features.section.SectionIntroScreen
import io.github.a1mohamad.toeflvocab.features.settings.AboutScreen
import io.github.a1mohamad.toeflvocab.features.settings.SettingsScreen
import io.github.a1mohamad.toeflvocab.navigation.Route
import io.github.a1mohamad.toeflvocab.navigation.Router

@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    val router = LocalRouter.current
    val progress = LocalProgressStore.current
    val content = LocalContentProvider.current

    LaunchedEffect(Unit) {
        if (BuildConfig.DEBUG) {
            ScreenshotHarness.prepare(
                progress = progress,
                catalog = content.catalog,
                router = router,
            )
        }
    }

    // The system back gesture unwinds the current tab's stack, and only falls
    // through to finishing the activity once that stack is empty.
    BackHandler(enabled = router.activePractice == null) {
        router.popCurrentStack()
    }

    Box(modifier = modifier.fillMaxSize().background(Palette.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                when (router.tab) {
                    Router.Tab.Study -> StudyTab(router)
                    Router.Tab.Reports -> ReportsScreen()
                    Router.Tab.Settings -> SettingsTab(router)
                }
            }
            TabBar(router)
        }

        // Practice is presented modally rather than pushed: it is a
        // self-contained task with its own quit affordance.
        AnimatedVisibility(
            visible = router.activePractice != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            val configuration = router.activePractice
            if (configuration != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Palette.background)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    PracticeContainer(configuration = configuration)
                }
            }
        }
    }
}

@Composable
private fun StudyTab(router: Router) {
    when (val route = router.studyPath.lastOrNull()) {
        null -> LibraryScreen()

        is Route.BookRoute -> BookIntroScreen(
            bookID = route.bookID,
            onBack = { router.popCurrentStack() },
        )

        is Route.SectionRoute -> SectionIntroScreen(
            bookID = route.bookID,
            sectionID = route.sectionID,
            onBack = { router.popCurrentStack() },
        )

        // About is only ever pushed onto the settings stack; rendering the
        // library is the safe fallback rather than a blank screen.
        Route.About -> LibraryScreen()
    }
}

@Composable
private fun SettingsTab(router: Router) {
    when (router.settingsPath.lastOrNull()) {
        null -> SettingsScreen()
        Route.About -> AboutScreen(onBack = { router.popCurrentStack() })
        else -> SettingsScreen()
    }
}

// MARK: - Tab bar

@Composable
private fun TabBar(router: Router) {
    val strings = LocalStrings.current

    Column {
        HorizontalDivider(color = Palette.separator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.surface)
                .navigationBarsPadding()
                .padding(vertical = 6.dp),
        ) {
            TabItem(
                symbol = AppSymbol.Study,
                title = strings[StringKey.TabStudy],
                isSelected = router.tab == Router.Tab.Study,
                onClick = { router.tab = Router.Tab.Study },
                modifier = Modifier.weight(1f),
            )
            TabItem(
                symbol = AppSymbol.Reports,
                title = strings[StringKey.TabReports],
                isSelected = router.tab == Router.Tab.Reports,
                onClick = { router.tab = Router.Tab.Reports },
                modifier = Modifier.weight(1f),
            )
            TabItem(
                symbol = AppSymbol.Settings,
                title = strings[StringKey.TabSettings],
                isSelected = router.tab == Router.Tab.Settings,
                onClick = { router.tab = Router.Tab.Settings },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabItem(
    symbol: AppSymbol,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint: Color = if (isSelected) Palette.accent else Palette.textTertiary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { selected = isSelected },
    ) {
        Icon(
            imageVector = symbol.vector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(title, style = AppFont.badge, color = tint)
    }
}
