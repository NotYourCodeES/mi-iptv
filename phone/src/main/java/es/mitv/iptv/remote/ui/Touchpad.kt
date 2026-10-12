package es.mitv.iptv.remote.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.mitv.iptv.remote.R
import es.mitv.iptv.remote.RemoteKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Apple TV-style touch surface: swipe to move (keep dragging to keep moving), tap for OK,
 * touch and hold to add the channel to favorites.
 */
@Composable
fun Touchpad(modifier: Modifier = Modifier, onKey: (RemoteKey) -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var touch by remember { mutableStateOf<Offset?>(null) }
    var longPressed by remember { mutableStateOf(false) }
    val hintAlpha by animateFloatAsState(if (touch == null) 1f else 0f, tween(200), label = "hint")
    val shape = RoundedCornerShape(30.dp)
    val label = stringResource(R.string.touch_surface)

    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Palette.surface2, Palette.surface)))
            .card(shape, Color.Transparent)
            .semantics {
                contentDescription = label
                onClick { onKey(RemoteKey.Ok); true }
            }
            .pointerInput(Unit) {
                val step = 52.dp.toPx()          // distance that counts as one arrow press
                val slop = 12.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var anchor = down.position
                    var last = down.position
                    var moved = false
                    longPressed = false
                    touch = down.position
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val hold = scope.launch {
                        delay(650)
                        if (!moved) {
                            longPressed = true
                            haptics.press()
                            onKey(RemoteKey.Fav)
                        }
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        last = change.position
                        touch = change.position
                        if ((change.position - down.position).getDistance() > slop) hold.cancel()
                        if (!longPressed) {
                            val d = change.position - anchor
                            if (max(abs(d.x), abs(d.y)) >= step) {
                                moved = true
                                haptics.tick()
                                onKey(direction(d))
                                anchor = change.position
                            }
                        }
                        change.consume()
                        if (!change.pressed) break
                    }
                    hold.cancel()
                    touch = null
                    if (!longPressed && !moved) {
                        val distance = last - down.position
                        if (distance.getDistance() < slop) {
                            haptics.tap()
                            onKey(RemoteKey.Ok)
                        } else {
                            // A quick flick shorter than one step still counts as a move
                            val v = tracker.calculateVelocity()
                            val t = distance + Offset(v.x, v.y) * 0.15f
                            if (max(abs(t.x), abs(t.y)) >= step * 0.8f) {
                                haptics.tick()
                                onKey(direction(t))
                            }
                        }
                    }
                    longPressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.alpha(hintAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.TouchApp, null, tint = Palette.muted.copy(alpha = 0.75f), modifier = Modifier.size(30.dp))
            Text(
                stringResource(R.string.touchpad_hint),
                color = Palette.muted.copy(alpha = 0.75f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        touch?.let { p ->
            val glow = if (longPressed) 0.6f else 0.4f
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Palette.accent.copy(alpha = glow), Color.Transparent),
                                center = p,
                                radius = 80.dp.toPx(),
                            ),
                            radius = 80.dp.toPx(),
                            center = p,
                        )
                    }
            )
        }
    }
}

private fun direction(d: Offset): RemoteKey =
    if (abs(d.x) > abs(d.y)) {
        if (d.x > 0) RemoteKey.Right else RemoteKey.Left
    } else {
        if (d.y > 0) RemoteKey.Down else RemoteKey.Up
    }

/** Classic arrow ring with OK in the middle (touch and hold OK for favorite). */
@Suppress("DEPRECATION") // the arrows are physical directions, so they must not flip in right-to-left languages
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DPad(modifier: Modifier = Modifier, onKey: (RemoteKey) -> Unit) {
    val haptics = rememberHaptics()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = minOf(maxWidth, maxHeight)
        Box(
            Modifier
                .size(size)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Palette.surface2, Palette.surface)))
                .card(CircleShape, Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            val offset = size * 0.35f
            Arrow(Icons.Rounded.KeyboardArrowUp, Modifier.offset(y = -offset)) { haptics.tick(); onKey(RemoteKey.Up) }
            Arrow(Icons.Rounded.KeyboardArrowDown, Modifier.offset(y = offset)) { haptics.tick(); onKey(RemoteKey.Down) }
            Arrow(Icons.Rounded.KeyboardArrowLeft, Modifier.offset(x = -offset)) { haptics.tick(); onKey(RemoteKey.Left) }
            Arrow(Icons.Rounded.KeyboardArrowRight, Modifier.offset(x = offset)) { haptics.tick(); onKey(RemoteKey.Right) }

            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(), label = "ok")
            Box(
                Modifier
                    .size(size * 0.38f)
                    .scale(scale)
                    .shadow(16.dp, CircleShape, ambientColor = Palette.accent, spotColor = Palette.accent)
                    .background(Palette.gradient, CircleShape)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onLongClick = { haptics.press(); onKey(RemoteKey.Fav) },
                        onClick = { haptics.tap(); onKey(RemoteKey.Ok) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("OK", color = Color.White, fontSize = (size.value * 0.09f).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Arrow(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(), label = "arrow")
    Box(
        modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            tint = if (pressed) Palette.accent else Palette.text,
            modifier = Modifier.size(36.dp).scale(scale),
        )
    }
}
