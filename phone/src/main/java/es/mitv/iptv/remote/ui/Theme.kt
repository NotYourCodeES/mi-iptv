package es.mitv.iptv.remote.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Same palette as the TV app and the iPhone app, so they feel like one product. */
object Palette {
    val bg = Color(0xFF0A0E15)
    val bg2 = Color(0xFF111827)
    val surface = Color(0xFF141A24)
    val surface2 = Color(0xFF1C2432)
    val line = Color(0xFF252E3F)
    val text = Color(0xFFF2F4F8)
    val text2 = Color(0xFFC9D0DC)
    val muted = Color(0xFF8590A3)
    val accent = Color(0xFF5B8CFF)
    val accent2 = Color(0xFF8B6CFF)
    val gold = Color(0xFFFFC94D)
    val live = Color(0xFFFF4D6A)
    val green = Color(0xFF3DDC84)

    val gradient = Brush.linearGradient(listOf(accent, accent2))
}

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.accent,
            onPrimary = Color.White,
            secondary = Palette.accent2,
            background = Palette.bg,
            onBackground = Palette.text,
            surface = Palette.bg2,
            onSurface = Palette.text,
            surfaceVariant = Palette.surface2,
            onSurfaceVariant = Palette.muted,
            surfaceContainer = Palette.surface,
            surfaceContainerHigh = Palette.surface2,
            surfaceContainerHighest = Palette.surface2,
            outline = Palette.line,
            outlineVariant = Palette.line,
            error = Palette.live,
        ),
        content = content,
    )
}

/** Dark background with a soft glow at the top, like the TV's welcome screen. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Palette.bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Palette.accent.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width / 2, 0f),
                        radius = 420.dp.toPx(),
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Palette.accent2.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = 320.dp.toPx(),
                    )
                )
            },
        content = content,
    )
}

/** Rounded card with the app's surface and hairline border. */
fun Modifier.card(shape: Shape = RoundedCornerShape(18.dp), color: Color = Palette.surface): Modifier =
    this.background(color, shape).border(1.dp, Palette.line, shape)

/** Small "LIVE" / "PAUSED" / "ON TV" capsule. */
@Composable
fun LivePill(text: String, color: Color = Palette.live) {
    Row(
        Modifier
            .background(color.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

/** Short vibrations for taps, like the iPhone app's haptics. */
class Haptics(private val view: View) {
    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    fun press() = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    fun select() = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    fun success() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    )
    fun error() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    )
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
