package io.github.a1mohamad.toeflvocab.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.a1mohamad.toeflvocab.core.localization.LocalStrings
import io.github.a1mohamad.toeflvocab.core.localization.StringKey
import io.github.a1mohamad.toeflvocab.designsystem.AppFont
import io.github.a1mohamad.toeflvocab.designsystem.Metrics
import io.github.a1mohamad.toeflvocab.designsystem.Palette

/**
 * The inline navigation bar — a centred title with a back chevron, which is what
 * `navigationBarTitleDisplayMode(.inline)` renders on iOS.
 *
 * Written by hand rather than using `TopAppBar` so the surface colour, the title
 * font and the back affordance all come from this project's design system rather
 * than from Material 3's colour scheme, which the app deliberately does not use.
 */
@Composable
fun InlineTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (onBack != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(Metrics.minimumTapTarget)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
            ) {
                Icon(
                    // Auto-mirrored: the back arrow has to point the other way
                    // in Persian.
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings[StringKey.CommonBack],
                    tint = Palette.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Spacer(Modifier.width(Metrics.minimumTapTarget))
        }

        Text(
            text = title,
            style = AppFont.cardTitle,
            color = Palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )

        // Balances the back button so the title stays optically centred.
        Spacer(Modifier.width(Metrics.minimumTapTarget))
    }
}

/**
 * The large navigation title, which iOS draws inline with the scrolling content
 * rather than in a bar. Reports and Settings use it.
 */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.Start,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = title, style = AppFont.screenTitle, color = Palette.textPrimary)
    }
}
