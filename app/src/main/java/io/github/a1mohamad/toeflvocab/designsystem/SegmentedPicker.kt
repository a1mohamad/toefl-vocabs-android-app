package io.github.a1mohamad.toeflvocab.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The iOS `.segmented` picker, which Material has no equivalent of.
 *
 * Material 3's `SegmentedButtonRow` exists but carries the Material colour
 * scheme and a check mark on the selected segment; this app draws its own
 * palette everywhere else, and the check mark makes a three-way accent switch
 * read as a set of independent toggles rather than one choice.
 */
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selection: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outerShape = RoundedCornerShape(9.dp)
    val innerShape = RoundedCornerShape(7.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(outerShape)
            .background(Palette.surfaceSunken, outerShape)
            .padding(2.dp),
    ) {
        for (option in options) {
            val isSelected = option == selection
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(innerShape)
                    .background(
                        if (isSelected) Palette.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent,
                        innerShape,
                    )
                    .clickable { onSelect(option) }
                    .semantics { selected = isSelected },
            ) {
                Text(
                    text = label(option),
                    style = AppFont.caption,
                    color = if (isSelected) Palette.textPrimary else Palette.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
