package es.mitv.iptv.remote.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.mitv.iptv.remote.AppModel
import es.mitv.iptv.remote.R

/** First screen: finds the TVs on the Wi-Fi and starts pairing. */
@Composable
fun ConnectScreen(model: AppModel) {
    val haptics = rememberHaptics()
    var showManual by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(24.dp))
            Radar()
            Text(stringResource(R.string.app_name), color = Palette.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.connect_subtitle),
                color = Palette.muted,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tvs_on_wifi), color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

            if (model.discovered.isEmpty() && !model.discoveryProblem) {
                Row(
                    Modifier.fillMaxWidth().card().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Palette.muted)
                    Text(stringResource(R.string.looking_for_tv), color = Palette.text2, fontSize = 15.sp)
                }
            }

            for (tv in model.discovered) {
                TvRow(
                    name = tv.name,
                    address = tv.host,
                    busy = model.connectingTo == tv.id,
                    enabled = model.connectingTo == null,
                ) {
                    haptics.tap()
                    model.choose(tv)
                }
            }

            if (model.discoveryProblem) {
                Column(
                    Modifier.fillMaxWidth().card().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.WifiOff, null, tint = Palette.gold)
                        Text(stringResource(R.string.discovery_problem), color = Palette.gold, fontSize = 15.sp)
                    }
                    TextButton(onClick = model::retryDiscovery) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.search_again), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        model.connectError?.let {
            Text(it, color = Palette.live, fontSize = 15.sp, textAlign = TextAlign.Center)
        }

        Column(
            Modifier.padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = { showManual = true }) {
                Icon(Icons.Rounded.Keyboard, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.enter_address), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                stringResource(R.string.enter_address_hint),
                color = Palette.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showManual) {
        AddressDialog(
            onConnect = {
                showManual = false
                model.chooseManually(it)
            },
            onDismiss = { showManual = false },
        )
    }

    model.pairingTarget?.let { target ->
        PairingSheet(model, target, onDismiss = { model.pairingTarget = null })
    }
}

@Composable
private fun AddressDialog(onConnect: (String) -> Unit, onDismiss: () -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.bg2,
        title = { Text(stringResource(R.string.tv_address)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.tv_address_message), color = Palette.muted)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    placeholder = { Text("192.168.1.50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { if (address.isNotBlank()) onConnect(address) }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConnect(address) }, enabled = address.isNotBlank()) {
                Text(stringResource(R.string.connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun TvRow(name: String, address: String?, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .card()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).background(Palette.gradient, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Tv, null, tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(name, color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (address != null) {
                Text(address, color = Palette.muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Palette.muted)
        }
    }
}

/** Pulsing circles around a remote icon while we look for TVs. */
@Composable
private fun Radar() {
    val transition = rememberInfiniteTransition(label = "radar")
    val phases = (0 until 3).map { i ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(2600, delayMillis = 0, easing = LinearEasing),
                RepeatMode.Restart,
                initialStartOffset = androidx.compose.animation.core.StartOffset(i * 850),
            ),
            label = "ring$i",
        )
    }
    Box(
        Modifier
            .size(190.dp)
            .drawBehind {
                for (p in phases) {
                    val t = p.value
                    val eased = 1 - (1 - t) * (1 - t)
                    drawCircle(
                        Palette.accent.copy(alpha = 0.9f * 0.45f * (1 - t)),
                        radius = size.minDimension / 2 * (0.35f + 0.65f * eased),
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .shadow(26.dp, CircleShape, ambientColor = Palette.accent, spotColor = Palette.accent)
                .background(Palette.gradient, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SettingsRemote, null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
    }
}
