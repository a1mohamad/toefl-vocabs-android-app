package io.github.a1mohamad.toeflvocab.features.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.a1mohamad.toeflvocab.app.InlineTopBar
import io.github.a1mohamad.toeflvocab.app.LocalContentProvider
import io.github.a1mohamad.toeflvocab.core.localization.LocalStrings
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import io.github.a1mohamad.toeflvocab.core.models.AppSymbol
import io.github.a1mohamad.toeflvocab.core.models.BookTheme
import io.github.a1mohamad.toeflvocab.designsystem.AppFont
import io.github.a1mohamad.toeflvocab.designsystem.Metrics
import io.github.a1mohamad.toeflvocab.designsystem.Palette
import io.github.a1mohamad.toeflvocab.designsystem.card
import io.github.a1mohamad.toeflvocab.designsystem.gradient
import io.github.a1mohamad.toeflvocab.designsystem.screenBackground
import io.github.a1mohamad.toeflvocab.designsystem.vector

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = LocalContentProvider.current
    val strings = LocalStrings.current

    Column(modifier = modifier.fillMaxSize().screenBackground()) {
        InlineTopBar(title = strings[StringKey.AboutTitle], onBack = onBack)

        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Metrics.screenPadding)
                .padding(bottom = 24.dp),
        ) {
            // App card
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().card(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BookTheme.Indigo.gradient, RoundedCornerShape(16.dp)),
                    ) {
                        Icon(
                            imageVector = AppSymbol.AppMark.vector,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("TOEFL Vocab", style = AppFont.title, color = Palette.textPrimary)
                        Text(
                            text = "${strings[StringKey.SettingsVersion]} $versionString",
                            style = AppFont.caption,
                            color = Palette.textSecondary,
                        )
                    }
                }

                Text(
                    text = strings[StringKey.AboutBody],
                    style = AppFont.body,
                    color = Palette.textSecondary,
                )
            }

            TextBlock(
                title = strings[StringKey.AboutContentTitle],
                body = strings[StringKey.AboutContentBody],
            )
            TextBlock(
                title = strings[StringKey.SettingsPrivacy],
                body = strings[StringKey.SettingsPrivacyBody],
            )

            // Library summary
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().card(),
            ) {
                for (book in content.catalog.books) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) { },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(book.theme.gradient, CircleShape)
                        )
                        Text(book.title, style = AppFont.caption, color = Palette.textPrimary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = strings.format(StringKey.BookWordsCount, book.wordCount),
                            style = AppFont.badge,
                            color = Palette.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextBlock(title: String, body: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().card(),
    ) {
        Text(
            text = title.uppercase(),
            style = AppFont.sectionHeader.copy(letterSpacing = 0.6.sp),
            color = Palette.textTertiary,
        )
        Text(body, style = AppFont.body, color = Palette.textSecondary)
    }
}
