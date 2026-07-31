package io.github.a1mohamad.toeflvocab.features.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.a1mohamad.toeflvocab.BuildConfig
import io.github.a1mohamad.toeflvocab.app.LargeTitle
import io.github.a1mohamad.toeflvocab.app.LocalProgressStore
import io.github.a1mohamad.toeflvocab.app.LocalPronunciationService
import io.github.a1mohamad.toeflvocab.app.LocalRouter
import io.github.a1mohamad.toeflvocab.app.LocalSettingsStore
import io.github.a1mohamad.toeflvocab.core.localization.AppLanguage
import io.github.a1mohamad.toeflvocab.core.localization.LocalStrings
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import io.github.a1mohamad.toeflvocab.core.models.AppSettings
import io.github.a1mohamad.toeflvocab.core.models.AppSymbol
import io.github.a1mohamad.toeflvocab.core.models.AppTheme
import io.github.a1mohamad.toeflvocab.core.models.SpeechAccent
import io.github.a1mohamad.toeflvocab.core.persistence.BackupFile
import io.github.a1mohamad.toeflvocab.core.persistence.ProgressBackup
import io.github.a1mohamad.toeflvocab.core.persistence.exportBackup
import io.github.a1mohamad.toeflvocab.designsystem.AppFont
import io.github.a1mohamad.toeflvocab.designsystem.Metrics
import io.github.a1mohamad.toeflvocab.designsystem.Palette
import io.github.a1mohamad.toeflvocab.designsystem.SegmentedPicker
import io.github.a1mohamad.toeflvocab.designsystem.vector
import io.github.a1mohamad.toeflvocab.navigation.Route

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val settings = LocalSettingsStore.current
    val progress = LocalProgressStore.current
    val speech = LocalPronunciationService.current
    val router = LocalRouter.current
    val strings = LocalStrings.current
    val context = LocalContext.current

    var showResetConfirmation by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<ProgressBackup?>(null) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var showResultAlert by remember { mutableStateOf(false) }
    var resultTitle by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf("") }

    fun present(title: String, message: String) {
        resultTitle = title
        resultMessage = message
        showResultAlert = true
    }

    // The Storage Access Framework hands back a uri, not a file, so the encode
    // happens after the picker returns rather than before it opens. The failure
    // it guards against — an unwritable location — is reported the same way.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            BackupFile.write(context, uri, progress.exportBackup())
        } catch (error: Exception) {
            present(
                strings[StringKey.BackupExportFailed],
                error.message ?: error.toString(),
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            // Decode before prompting, so an unusable file is rejected without
            // the user first agreeing to overwrite their history.
            val json = BackupFile.read(context, uri)
            pendingBackup = ProgressBackup.decode(json)
            showRestoreConfirmation = true
        } catch (error: Exception) {
            present(
                strings[StringKey.BackupImportFailed],
                error.message ?: error.toString(),
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = modifier
            .fillMaxSize()
            .background(Palette.background)
            .verticalScroll(rememberScrollState())
            .padding(Metrics.screenPadding)
            .padding(bottom = 24.dp),
    ) {
        LargeTitle(strings[StringKey.SettingsTitle])

        // MARK: Appearance
        SettingsSection(header = strings[StringKey.SettingsAppearance]) {
            MenuRow(
                title = strings[StringKey.SettingsTheme],
                value = strings[settings.settings.theme.titleKey],
                options = AppTheme.allCases,
                label = { strings[it.titleKey] },
                leading = { settings.settings.theme.symbol },
                onSelect = { next -> settings.update { it.copy(theme = next) } },
            )
            SettingsDivider()
            MenuRow(
                title = strings[StringKey.SettingsLanguage],
                value = if (settings.settings.language == AppLanguage.System) {
                    strings[StringKey.ThemeSystem]
                } else {
                    settings.settings.language.nativeName
                },
                options = AppLanguage.allCases,
                label = {
                    if (it == AppLanguage.System) strings[StringKey.ThemeSystem] else it.nativeName
                },
                leading = null,
                onSelect = { next -> settings.update { it.copy(language = next) } },
            )
        }

        // MARK: Pronunciation
        SettingsSection(
            header = strings[StringKey.SettingsPronunciation],
            footer = strings[StringKey.SettingsAutoSpeakHint],
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = strings[StringKey.SettingsAccent],
                    style = AppFont.body,
                    color = Palette.textPrimary,
                )
                SegmentedPicker(
                    options = SpeechAccent.allCases,
                    selection = settings.settings.accent,
                    label = { strings[it.titleKey] },
                    onSelect = { next -> settings.update { it.copy(accent = next) } },
                )
            }

            SettingsDivider()

            // Speed
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = strings[StringKey.SettingsSpeed],
                        style = AppFont.body,
                        color = Palette.textPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = strings[settings.settings.speedLabelKey],
                        style = AppFont.caption,
                        color = Palette.textSecondary,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AppSymbol.SpeedSlow.vector,
                        contentDescription = null,
                        tint = Palette.textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Slider(
                        value = settings.settings.clampedSpeechRate.toFloat(),
                        onValueChange = { next ->
                            settings.update { it.copy(speechRate = next.toDouble()) }
                        },
                        valueRange = AppSettings.MINIMUM_SPEECH_RATE.toFloat()..
                            AppSettings.MAXIMUM_SPEECH_RATE.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Palette.accent,
                            activeTrackColor = Palette.accent,
                            inactiveTrackColor = Palette.surfaceSunken,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = strings[settings.settings.speedLabelKey]
                            },
                    )
                    Icon(
                        imageVector = AppSymbol.SpeedFast.vector,
                        contentDescription = null,
                        tint = Palette.textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            SettingsDivider()

            // Hearing the change is the only way to judge a speech-rate slider.
            ActionRow(
                title = strings[StringKey.PracticeTapToHear],
                symbol = AppSymbol.SpeakerWave2,
                tint = Palette.accent,
                onClick = {
                    speech.speak(
                        "vocabulary",
                        settings.settings.accent,
                        settings.settings.clampedSpeechRate,
                    )
                },
            )

            SettingsDivider()

            ToggleRow(
                title = strings[StringKey.SettingsAutoSpeak],
                checked = settings.settings.autoSpeak,
                onCheckedChange = { next -> settings.update { it.copy(autoSpeak = next) } },
            )
        }

        // MARK: Feedback
        SettingsSection(header = null) {
            ToggleRow(
                title = strings[StringKey.SettingsHaptics],
                checked = settings.settings.haptics,
                onCheckedChange = { next -> settings.update { it.copy(haptics = next) } },
            )
        }

        // MARK: Data
        SettingsSection(
            header = strings[StringKey.SettingsData],
            footer = strings[StringKey.SettingsBackupHint],
        ) {
            ActionRow(
                title = strings[StringKey.SettingsExportProgress],
                symbol = AppSymbol.ExportUp,
                tint = Palette.accent,
                onClick = { exportLauncher.launch(BackupFile.defaultFileName()) },
            )
            SettingsDivider()
            ActionRow(
                title = strings[StringKey.SettingsImportProgress],
                symbol = AppSymbol.ImportDown,
                tint = Palette.accent,
                onClick = { importLauncher.launch(arrayOf("application/json")) },
            )
            SettingsDivider()
            ActionRow(
                title = strings[StringKey.SettingsResetProgress],
                symbol = AppSymbol.Trash,
                tint = Palette.danger,
                titleColor = Palette.danger,
                onClick = { showResetConfirmation = true },
            )
        }

        // MARK: Privacy
        SettingsSection(header = strings[StringKey.SettingsPrivacy]) {
            Text(
                text = strings[StringKey.SettingsPrivacyBody],
                style = AppFont.caption,
                color = Palette.textSecondary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }

        // MARK: About
        SettingsSection(header = null) {
            ActionRow(
                title = strings[StringKey.SettingsAbout],
                symbol = AppSymbol.Info,
                tint = Palette.accent,
                trailing = AppSymbol.ChevronForward,
                onClick = { router.openInSettings(Route.About) },
            )
            SettingsDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Text(
                    text = strings[StringKey.SettingsVersion],
                    style = AppFont.body,
                    color = Palette.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(versionString, style = AppFont.body, color = Palette.textSecondary)
            }
        }
    }

    // MARK: Dialogs

    if (showResetConfirmation) {
        ConfirmDialog(
            title = strings[StringKey.SettingsResetProgress],
            message = strings[StringKey.SettingsResetMessage],
            confirmTitle = strings[StringKey.CommonReset],
            confirmColor = Palette.danger,
            cancelTitle = strings[StringKey.CommonCancel],
            onConfirm = {
                showResetConfirmation = false
                progress.eraseAll()
            },
            onDismiss = { showResetConfirmation = false },
        )
    }

    if (showRestoreConfirmation) {
        ConfirmDialog(
            title = strings[StringKey.BackupRestoreTitle],
            message = "${strings[StringKey.BackupRestoreMessage]}\n\n" +
                (pendingBackup?.summary() ?: ""),
            confirmTitle = strings[StringKey.BackupRestoreAction],
            confirmColor = Palette.danger,
            cancelTitle = strings[StringKey.CommonCancel],
            onConfirm = {
                showRestoreConfirmation = false
                pendingBackup?.let { backup ->
                    progress.replaceState(backup.progress)
                    present(strings[StringKey.BackupRestoredTitle], backup.summary())
                }
                pendingBackup = null
            },
            onDismiss = {
                showRestoreConfirmation = false
                pendingBackup = null
            },
        )
    }

    if (showResultAlert) {
        AlertDialog(
            onDismissRequest = { showResultAlert = false },
            title = { Text(resultTitle) },
            text = { Text(resultMessage) },
            confirmButton = {
                TextButton(onClick = { showResultAlert = false }) {
                    Text(strings[StringKey.CommonOK])
                }
            },
            containerColor = Palette.surfaceRaised,
            titleContentColor = Palette.textPrimary,
            textContentColor = Palette.textSecondary,
        )
    }
}

val versionString: String
    get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

// MARK: - Form pieces

/**
 * One grouped block, which is what a SwiftUI `Section` inside a `Form` draws:
 * an uppercase header, a rounded card of rows, and an optional footnote.
 */
@Composable
private fun SettingsSection(
    header: String?,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Metrics.cardRadius)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (header != null) {
            Text(
                text = header.uppercase(),
                style = AppFont.sectionHeader.copy(letterSpacing = 0.6.sp),
                color = Palette.textTertiary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Palette.surface, shape)
                .border(1.dp, Palette.separator, shape),
        ) {
            content()
        }
        if (footer != null) {
            Text(
                text = footer,
                style = AppFont.caption,
                color = Palette.textTertiary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = Palette.separator, modifier = Modifier.padding(start = 14.dp))
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Text(title, style = AppFont.body, color = Palette.textPrimary)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Palette.accent,
                uncheckedTrackColor = Palette.surfaceSunken,
                uncheckedBorderColor = Palette.separator,
            ),
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    symbol: AppSymbol,
    tint: Color,
    onClick: () -> Unit,
    titleColor: Color = Palette.textPrimary,
    trailing: AppSymbol? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Icon(
            imageVector = symbol.vector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(title, style = AppFont.body, color = titleColor)
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Icon(
                imageVector = trailing.vector,
                contentDescription = null,
                tint = Palette.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The `.menu` picker style: a row that opens a dropdown of the options. */
@Composable
private fun <T> MenuRow(
    title: String,
    value: String,
    options: List<T>,
    label: (T) -> String,
    leading: (() -> AppSymbol)?,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .semantics(mergeDescendants = true) { },
        ) {
            Text(title, style = AppFont.body, color = Palette.textPrimary)
            Spacer(Modifier.weight(1f))
            if (leading != null) {
                Icon(
                    imageVector = leading().vector,
                    contentDescription = null,
                    tint = Palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(value, style = AppFont.body, color = Palette.textSecondary)
            Icon(
                imageVector = AppSymbol.ChevronForward.vector,
                contentDescription = null,
                tint = Palette.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (option in options) {
                DropdownMenuItem(
                    text = {
                        Text(label(option), style = AppFont.body, color = Palette.textPrimary)
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmTitle: String,
    confirmColor: Color,
    cancelTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmTitle, color = confirmColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelTitle) }
        },
        containerColor = Palette.surfaceRaised,
        titleContentColor = Palette.textPrimary,
        textContentColor = Palette.textSecondary,
    )
}
