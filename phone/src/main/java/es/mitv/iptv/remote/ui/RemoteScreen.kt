package es.mitv.iptv.remote.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.mitv.iptv.remote.AppModel
import es.mitv.iptv.remote.Connection
import es.mitv.iptv.remote.R
import es.mitv.iptv.remote.RemoteKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The remote: what's on the TV, a touch surface (or D-pad) and the usual buttons. */
@Composable
fun RemoteScreen(model: AppModel) {
    val haptics = rememberHaptics()
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    var showKeyboard by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            Text(
                model.paired?.name ?: "",
                color = Palette.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 56.dp),
            )
            IconButton(
                onClick = {
                    haptics.select()
                    model.updateUseTouchpad(!model.useTouchpad)
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    if (model.useTouchpad) Icons.Rounded.Gamepad else Icons.Rounded.TouchApp,
                    contentDescription = stringResource(if (model.useTouchpad) R.string.use_arrows else R.string.use_touchpad),
                    tint = Palette.accent,
                )
            }
        }

        ConnectionBanner(model.connection)
        NowPlayingCard(model)

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Crossfade(model.useTouchpad, label = "pad") { touchpad ->
                val pad = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 360.dp)
                if (touchpad) Touchpad(pad.fillMaxSize(), model::press) else DPad(pad, model::press)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RemoteKeyButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.back)) { model.press(RemoteKey.Back) }
            RemoteKeyButton(Icons.Rounded.StarBorder, stringResource(R.string.favorites)) { model.press(RemoteKey.Guide) }
            RemoteKeyButton(Icons.Rounded.Info, stringResource(R.string.info)) { model.press(RemoteKey.Info) }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Rocker(
                stringResource(R.string.ch), Icons.Rounded.KeyboardArrowUp, Icons.Rounded.KeyboardArrowDown,
                stringResource(R.string.channel_up), stringResource(R.string.channel_down),
                up = { model.press(RemoteKey.ChannelUp) }, down = { model.press(RemoteKey.ChannelDown) },
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteKeyButton(
                    if (model.state?.paused == true) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    caption = null,
                    description = stringResource(R.string.play_pause),
                    prominent = true,
                ) { model.press(RemoteKey.PlayPause) }
                RemoteKeyButton(
                    Icons.AutoMirrored.Rounded.VolumeOff,
                    caption = null,
                    description = stringResource(R.string.mute),
                ) { model.press(RemoteKey.Mute) }
            }
            Rocker(
                stringResource(R.string.vol), Icons.Rounded.Add, Icons.Rounded.Remove,
                stringResource(R.string.volume_up), stringResource(R.string.volume_down),
                up = { model.press(RemoteKey.VolumeUp) }, down = { model.press(RemoteKey.VolumeDown) },
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RemoteKeyButton(Icons.Rounded.Tag, stringResource(R.string.number)) { showKeypad = true }
            RemoteKeyButton(Icons.Rounded.Keyboard, stringResource(R.string.type)) { showKeyboard = true }
            RemoteKeyButton(Icons.Rounded.SwapHoriz, stringResource(R.string.previous)) { model.press(RemoteKey.Last) }
        }
    }

    if (showKeypad) KeypadSheet(model) { showKeypad = false }
    if (showKeyboard) TypeOnTvSheet(model) { showKeyboard = false }
}

/** "Connecting…" / "No connection" strip above the remote. */
@Composable
fun ConnectionBanner(connection: Connection) {
    AnimatedVisibility(connection != Connection.Online) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.surface, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (connection == Connection.Offline) {
                Icon(Icons.Rounded.WifiOff, null, tint = Palette.gold, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.banner_offline), color = Palette.text2, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            } else {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Palette.muted)
                Text(stringResource(R.string.banner_connecting), color = Palette.text2, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** What the TV is playing, with a favorite toggle. */
@Composable
fun NowPlayingCard(model: AppModel) {
    val context = LocalContext.current
    val state = model.state
    val channel = state?.channel
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .card(RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (channel != null && state.screen != "background") {
            ChannelLogo(channel.n, channel.logo, channel.name, model.client, size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (channel.n > 0) {
                        Text(channel.n.toString(), color = Palette.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        channel.name,
                        color = Palette.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.paused) LivePill(stringResource(R.string.paused), Palette.gold)
                    else LivePill(stringResource(R.string.live))
                    Text(channel.group, color = Palette.muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (channel.n > 0) {
                val fav = model.isFavorite(channel.name)
                val haptics = rememberHaptics()
                IconButton(onClick = {
                    haptics.press()
                    model.toggleFavorite(channel.n, channel.name)
                }) {
                    Icon(
                        if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(if (fav) R.string.remove_favorite else R.string.add_favorite),
                        tint = if (fav) Palette.gold else Palette.muted,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        } else {
            val icon = when {
                state?.screen == "background" -> Icons.Rounded.Bedtime
                state?.screen == "welcome" && state.loading -> Icons.Rounded.Download
                state?.screen == "welcome" -> Icons.Rounded.Tune
                else -> Icons.Rounded.Tv
            }
            Box(
                Modifier.size(56.dp).background(Palette.surface2, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Palette.accent, modifier = Modifier.size(28.dp))
            }
            val (title, subtitle) = when {
                state == null -> R.string.np_default to R.string.np_connecting
                state.screen == "background" -> R.string.np_closed to R.string.np_open_app
                state.loading -> R.string.np_loading to R.string.np_wait
                !state.configured -> R.string.np_needs_provider to R.string.np_add_provider
                else -> R.string.np_pick to R.string.np_use_channels
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(subtitle), color = Palette.muted, fontSize = 15.sp, maxLines = 2)
            }
            val setup = state?.setupUrl
            if (state?.configured == false && setup != null) {
                Button(
                    onClick = { openSetupPage(context, setup) },
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(stringResource(R.string.set_up), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Opens the TV's own setup page (served by the TV, so it's always in sync with it). */
fun openSetupPage(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder().setToolbarColor(0xFF0A0E15.toInt()).build()
            )
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .build()
            .launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (ignored: ActivityNotFoundException) {
        }
    }
}

// ---------------------------------------------------------------- Buttons

/** Round remote button with an optional caption underneath. */
@Composable
fun RemoteKeyButton(
    icon: ImageVector,
    caption: String?,
    description: String? = caption,
    prominent: Boolean = false,
    diameter: Dp = 58.dp,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(), label = "key")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(diameter)
                .scale(scale)
                .then(
                    if (prominent) Modifier
                        .shadow(12.dp, CircleShape, ambientColor = Palette.accent, spotColor = Palette.accent)
                        .background(Palette.gradient, CircleShape)
                    else Modifier.card(CircleShape, Palette.surface2)
                )
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                    haptics.tap()
                    onClick()
                }
                .semantics { if (description != null) contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (prominent) Color.White else Palette.text, modifier = Modifier.size(24.dp))
        }
        if (caption != null) {
            Text(caption, color = Palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Tall up/down control for channels and volume. Holding a half repeats it. */
@Composable
fun Rocker(
    title: String,
    upIcon: ImageVector,
    downIcon: ImageVector,
    upDescription: String,
    downDescription: String,
    up: () -> Unit,
    down: () -> Unit,
) {
    Column(
        Modifier.width(66.dp).card(CircleShape, Palette.surface2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RepeatButton(upIcon, upDescription, up)
        Text(
            title,
            color = Palette.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        RepeatButton(downIcon, downDescription, down)
    }
}

/** Fires once on touch, then repeats while held (for volume and channel zapping). */
@Composable
private fun RepeatButton(icon: ImageVector, description: String, action: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(), label = "repeat")
    Box(
        Modifier
            .size(width = 66.dp, height = 58.dp)
            .padding(4.dp)
            .background(Palette.accent.copy(alpha = if (pressed) 0.22f else 0f), CircleShape)
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick { action(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptics.tick()
                    action()
                    val repeater = scope.launch {
                        delay(450)
                        while (true) {
                            haptics.tick()
                            action()
                            delay(160)
                        }
                    }
                    tryAwaitRelease()
                    repeater.cancel()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Palette.text, modifier = Modifier.size(24.dp).scale(scale))
    }
}
