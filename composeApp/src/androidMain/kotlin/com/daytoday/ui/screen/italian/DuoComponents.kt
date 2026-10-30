package com.daytoday.ui.screen.italian

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Palette borrowed from the Duolingo look: bright accents over the app's own surfaces. */
internal object Duo {
    val Green = Color(0xFF58CC02)
    val GreenDark = Color(0xFF58A700)
    val Blue = Color(0xFF1CB0F6)
    val BlueDark = Color(0xFF1899D6)
    val Red = Color(0xFFFF4B4B)
    val RedDark = Color(0xFFEA2B2B)
    val Gold = Color(0xFFFFC800)
    val GoldDark = Color(0xFFE5A800)
    val Orange = Color(0xFFFF9600)
    val LockedFill = Color(0xFF4B4B55)
    val LockedLedge = Color(0xFF383840)

    /** One accent per unit, cycled, so the path changes colour as the learner progresses. */
    val UnitAccents = listOf(
        Green to GreenDark,
        Blue to BlueDark,
        Color(0xFFCE82FF) to Color(0xFFA568CC),
        Orange to Color(0xFFE08400),
    )
}

/** Thick rounded progress bar that animates between exercises. */
@Composable
internal fun DuoProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Duo.Green,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "lesson-progress")
    Box(
        modifier = modifier
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (animated > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color),
            )
        }
    }
}

/** Chunky full-width action button (CHECK / CONTINUE). */
@Composable
internal fun DuoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Duo.Green,
    contentColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            text = text.uppercase(),
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
