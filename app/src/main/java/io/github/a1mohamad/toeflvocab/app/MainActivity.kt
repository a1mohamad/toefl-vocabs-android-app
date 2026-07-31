package io.github.a1mohamad.toeflvocab.app

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.a1mohamad.toeflvocab.BuildConfig
import io.github.a1mohamad.toeflvocab.core.audio.PronunciationService
import io.github.a1mohamad.toeflvocab.core.localization.LayoutDirectionBridge
import io.github.a1mohamad.toeflvocab.core.localization.LocalStrings
import io.github.a1mohamad.toeflvocab.core.persistence.ProgressStore
import io.github.a1mohamad.toeflvocab.core.persistence.SettingsStore
import io.github.a1mohamad.toeflvocab.designsystem.LocalIsDarkTheme
import io.github.a1mohamad.toeflvocab.navigation.Router
import java.io.File

/**
 * The single activity.
 *
 * The five stores are built here and live for as long as the process does,
 * which is the direct equivalent of the SwiftUI `@StateObject`s on the `App`
 * type. They are deliberately not `ViewModel`s: nothing about them is scoped to
 * a screen, and a configuration change should not rebuild the catalog.
 */
class MainActivity : ComponentActivity() {

    private lateinit var content: ContentProvider
    private lateinit var progress: ProgressStore
    private lateinit var settings: SettingsStore
    private lateinit var speech: PronunciationService
    private lateinit var router: Router

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (BuildConfig.DEBUG) {
            ScreenshotHarness.readIntent(intent)
        }

        content = ContentProvider(applicationContext)
        progress = ProgressStore(File(filesDir, ProgressStore.FILE_NAME))
        settings = SettingsStore(
            getSharedPreferences(SettingsStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
        speech = PronunciationService(applicationContext)
        router = Router()

        setContent {
            TOEFLVocabApp(
                content = content,
                progress = progress,
                settings = settings,
                speech = speech,
                router = router,
                onLayoutDirectionChange = { isRtl ->
                    LayoutDirectionBridge.apply(this, isRtl)
                },
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speech.shutdown()
    }
}

/**
 * The root composable: dependency wiring, theme, language and layout direction,
 * all applied once here rather than threaded through every screen.
 */
@Composable
fun TOEFLVocabApp(
    content: ContentProvider,
    progress: ProgressStore,
    settings: SettingsStore,
    speech: PronunciationService,
    router: Router,
    onLayoutDirectionChange: (Boolean) -> Unit,
) {
    val current = settings.settings
    val language = current.language.resolved
    val isRtl = language.isRightToLeft
    val isDark = current.theme.isDark(isSystemInDarkTheme())

    val context = LocalContext.current
    val reduceMotion = remember { animationsDisabled(context) }

    // Debounced writes are pending for up to ~0.6s; flush them before the
    // process can be killed in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                progress.saveNow()
                speech.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keeps the View layer pointing the same way as the Compose one; see
    // LayoutDirectionBridge for why that is not automatic.
    DisposableEffect(isRtl) {
        onLayoutDirectionChange(isRtl)
        onDispose { }
    }

    CompositionLocalProvider(
        LocalContentProvider provides content,
        LocalProgressStore provides progress,
        LocalSettingsStore provides settings,
        LocalPronunciationService provides speech,
        LocalRouter provides router,
        LocalStrings provides settings.strings,
        LocalIsDarkTheme provides isDark,
        LocalReduceMotion provides reduceMotion,
        LocalLayoutDirection provides
            if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        // Keyed on the language so switching it rebuilds the tree rather than
        // re-laying out the existing one. Compose otherwise reuses scroll
        // containers that were measured for the other direction, and a reused
        // container keeps a scroll offset its new contents do not expect.
        androidx.compose.runtime.key(language) {
            RootScreen()
        }
    }
}

/**
 * Mirrors `accessibilityReduceMotion`. Android has no single "reduce motion"
 * switch; turning the animator duration scale to zero in Developer options — or
 * enabling battery saver, which does the same — is the signal apps are expected
 * to read.
 */
private fun animationsDisabled(context: Context): Boolean = try {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
} catch (error: Exception) {
    false
}
