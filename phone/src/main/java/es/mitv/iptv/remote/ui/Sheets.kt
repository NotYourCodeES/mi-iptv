package es.mitv.iptv.remote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.mitv.iptv.remote.AppModel
import es.mitv.iptv.remote.ChannelFilter
import es.mitv.iptv.remote.R
import es.mitv.iptv.remote.TvChannel
import kotlinx.coroutines.delay

/** Number pad: type a channel number and send it to the TV in one go. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeypadSheet(model: AppModel, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    var digits by remember { mutableStateOf("") }
    val target = digits.toIntOrNull()?.let(model::channel)

    fun go() {
        val n = digits.toIntOrNull() ?: return
        if (n <= 0) return
        haptics.success()
        model.play(n)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.bg2,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 28.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AnimatedContent(digits, label = "digits") { d ->
                    Text(
                        d.ifEmpty { "–" },
                        color = if (d.isEmpty()) Palette.muted else Palette.text,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val (text, color) = when {
                    target != null -> target.name to Palette.accent
                    digits.isEmpty() -> stringResource(R.string.keypad_prompt) to Palette.muted
                    else -> stringResource(R.string.keypad_none) to Palette.muted
                }
                Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (row in rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        for (d in row) {
                            PadKey(PadKind.Digit, onClick = {
                                haptics.tap()
                                if (digits.length < 5) digits += d
                            }) { Text(d, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Palette.text) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    PadKey(PadKind.Secondary, onClick = {
                        haptics.tap()
                        digits = digits.dropLast(1)
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.Backspace, stringResource(R.string.delete), tint = Palette.text)
                    }
                    PadKey(PadKind.Digit, onClick = {
                        haptics.tap()
                        if (digits.length < 5) digits += "0"
                    }) { Text("0", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Palette.text) }
                    PadKey(PadKind.Primary, enabled = digits.toIntOrNull() != null, onClick = ::go) {
                        Icon(Icons.Rounded.PlayArrow, stringResource(R.string.watch), tint = Color.White)
                    }
                }
            }
        }
    }
}

private enum class PadKind { Digit, Secondary, Primary }

@Composable
private fun RowScope.PadKey(kind: PadKind, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(), label = "pad")
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .weight(1f)
            .height(62.dp)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                when (kind) {
                    PadKind.Primary -> Modifier.background(Palette.gradient, shape)
                    PadKind.Digit -> Modifier.card(shape, Palette.surface2)
                    PadKind.Secondary -> Modifier.card(shape, Palette.surface)
                }
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Type on the phone, see the search on the TV (and pick a result from either). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeOnTvSheet(model: AppModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    // Send after a short pause so the TV isn't searching on every keystroke
    LaunchedEffect(text) {
        if (text.isEmpty()) return@LaunchedEffect
        delay(350)
        model.searchOnTv(text)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.bg2,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.type_title), color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.type_subtitle), color = Palette.muted, fontSize = 15.sp)
            }
            SearchField(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.type_field),
                modifier = Modifier.focusRequester(focus),
                onSearch = { model.searchOnTv(text) },
            )
            if (text.isNotEmpty()) {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(model.filteredChannels(text, ChannelFilter.All).take(40), key = { it.n }) { channel ->
                        ChannelRow(model, channel, compact = true) { onDismiss() }
                        HorizontalDivider(color = Palette.line)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.surface2, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = Palette.muted)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Palette.muted, fontSize = 17.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Palette.text, fontSize = 17.sp),
                cursorBrush = SolidColor(Palette.accent),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Cancel,
                stringResource(R.string.clear),
                tint = Palette.muted,
                modifier = Modifier.size(20.dp).clickable(role = Role.Button) { onValueChange("") },
            )
        }
    }
}

/** A channel: tap to watch it on the TV, star to add or remove it from favorites. */
@Composable
fun ChannelRow(model: AppModel, channel: TvChannel, compact: Boolean = false, onPlay: (() -> Unit)? = null) {
    val haptics = rememberHaptics()
    val fav = model.isFavorite(channel.name)
    val playing = model.isPlaying(channel)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (playing) Palette.accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(role = Role.Button) {
                haptics.select()
                model.play(channel)
                onPlay?.invoke()
            }
            .padding(start = 8.dp, top = if (compact) 6.dp else 8.dp, bottom = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            channel.n.toString(),
            color = Palette.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 34.dp),
        )
        ChannelLogo(channel.n, channel.logo, channel.name, model.client, size = if (compact) 36.dp else 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(channel.name, color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!compact) {
                Text(model.groupName(channel), color = Palette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (playing) LivePill(stringResource(R.string.on_tv))
        IconButton(onClick = {
            haptics.press()
            model.toggleFavorite(channel)
        }) {
            Icon(
                if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = stringResource(if (fav) R.string.remove_favorite else R.string.add_favorite),
                tint = if (fav) Palette.gold else Palette.muted.copy(alpha = 0.6f),
            )
        }
    }
}
