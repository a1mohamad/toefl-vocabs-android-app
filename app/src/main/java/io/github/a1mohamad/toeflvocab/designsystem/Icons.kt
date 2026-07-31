package io.github.a1mohamad.toeflvocab.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.a1mohamad.toeflvocab.core.models.AppSymbol

/**
 * The one place an [AppSymbol] becomes a drawable.
 *
 * SF Symbols and Material Icons are not the same set, so this is the single
 * table where the iOS icon choices were re-decided. Two of them are worth a
 * note:
 *
 *  * `chevron.forward` and `speaker.wave.2` map to the **auto-mirrored**
 *    Material variants. SwiftUI flips `.forward` for right-to-left languages
 *    automatically; on Android that only happens if the auto-mirrored icon is
 *    used, and Persian is a first-class language here.
 *  * `tortoise` / `hare` on the speed slider have no Material equivalent, so the
 *    walking and running figures carry the same slow/fast idea.
 */
val AppSymbol.vector: ImageVector
    get() = when (this) {
        // Tabs
        AppSymbol.Study -> Icons.Filled.LibraryBooks
        AppSymbol.Reports -> Icons.Filled.BarChart
        AppSymbol.Settings -> Icons.Filled.Settings

        // Content
        AppSymbol.BookClosed -> Icons.Filled.MenuBook
        AppSymbol.Sparkles -> Icons.Filled.AutoAwesome
        AppSymbol.Sparkle -> Icons.Filled.AutoAwesome
        AppSymbol.Calendar -> Icons.Filled.CalendarToday
        AppSymbol.Review -> Icons.Filled.Autorenew
        AppSymbol.Layers -> Icons.Filled.Layers
        AppSymbol.Alphabet -> Icons.Filled.Abc
        AppSymbol.AppMark -> Icons.Filled.MenuBook

        // Navigation and state
        AppSymbol.ChevronForward -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        AppSymbol.PlayCircle -> Icons.Filled.PlayCircleFilled
        AppSymbol.Checkmark -> Icons.Filled.Check
        AppSymbol.Xmark -> Icons.Filled.Close
        AppSymbol.CheckmarkCircle -> Icons.Filled.CheckCircle
        AppSymbol.XmarkCircle -> Icons.Filled.Cancel
        AppSymbol.CheckmarkSeal -> Icons.Filled.Verified
        AppSymbol.ExclamationCircle -> Icons.Filled.Error
        AppSymbol.ExclamationTriangle -> Icons.Filled.Warning
        AppSymbol.RadioSelected -> Icons.Filled.RadioButtonChecked
        AppSymbol.RadioUnselected -> Icons.Filled.RadioButtonUnchecked

        // Theme
        AppSymbol.ThemeSystem -> Icons.Filled.Brightness4
        AppSymbol.ThemeLight -> Icons.Filled.LightMode
        AppSymbol.ThemeDark -> Icons.Filled.DarkMode

        // Practice and reports
        AppSymbol.SpeakerWave2 -> Icons.AutoMirrored.Filled.VolumeUp
        AppSymbol.SpeakerWave3 -> Icons.Filled.VolumeUp
        AppSymbol.Bolt -> Icons.Filled.Bolt
        AppSymbol.FlagCheckered -> Icons.Filled.SportsScore
        AppSymbol.CycleGrid -> Icons.Filled.GridView
        AppSymbol.ChartDocument -> Icons.Filled.InsertChart
        AppSymbol.QuestionFolder -> Icons.Filled.FolderOff
        AppSymbol.BooksVertical -> Icons.Outlined.LibraryBooks

        // Settings
        AppSymbol.Info -> Icons.Outlined.Info
        AppSymbol.Trash -> Icons.Filled.Delete
        AppSymbol.ExportUp -> Icons.Filled.FileUpload
        AppSymbol.ImportDown -> Icons.Filled.FileDownload
        AppSymbol.SpeedSlow -> Icons.Filled.DirectionsWalk
        AppSymbol.SpeedFast -> Icons.Filled.DirectionsRun
    }
