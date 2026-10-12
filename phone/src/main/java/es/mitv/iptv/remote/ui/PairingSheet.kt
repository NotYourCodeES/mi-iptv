package es.mitv.iptv.remote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.mitv.iptv.remote.AppModel
import es.mitv.iptv.remote.PairingTarget
import es.mitv.iptv.remote.PinResult
import es.mitv.iptv.remote.R
import kotlinx.coroutines.launch

private const val DIGITS = 4

/** Enter the 4-digit code the TV is showing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingSheet(model: AppModel, target: PairingTarget, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val shake = remember { Animatable(0f) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val wrongText = stringResource(R.string.pin_wrong)
    val expiredText = stringResource(R.string.pin_expired)

    fun fail(message: String) {
        haptics.error()
        error = message
        pin = ""
        scope.launch {
            shake.animateTo(0f, keyframes {
                durationMillis = 400
                -12f at 50; 12f at 120; -10f at 190; 8f at 260; -4f at 330
            })
        }
    }

    fun submit(code: String) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val result = model.submitPin(code, target)
            busy = false
            when (result) {
                PinResult.Ok -> haptics.success()
                PinResult.Wrong -> fail(wrongText)
                PinResult.Expired -> fail(model.requestNewCode(target) ?: expiredText)
                is PinResult.Failed -> fail(result.message)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.bg2,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pair_title), color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.pair_subtitle, target.name, DIGITS),
                    color = Palette.muted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }

            val codeLabel = stringResource(R.string.code)
            BasicTextField(
                value = pin,
                onValueChange = { value ->
                    if (busy) return@BasicTextField
                    val clean = value.filter(Char::isDigit).take(DIGITS)
                    pin = clean
                    if (clean.length == DIGITS) submit(clean)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier
                    .focusRequester(focus)
                    .semantics { contentDescription = codeLabel },
                decorationBox = {
                    Row(
                        Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        for (i in 0 until DIGITS) {
                            DigitBox(pin.getOrNull(i)?.toString() ?: "", active = !busy && i == minOf(pin.length, DIGITS - 1))
                        }
                    }
                },
            )

            Box(Modifier.defaultMinSize(minHeight = 36.dp), contentAlignment = Alignment.Center) {
                when {
                    busy -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    error != null -> Text(error!!, color = Palette.live, fontSize = 13.sp, textAlign = TextAlign.Center)
                    else -> Text(stringResource(R.string.pair_hint), color = Palette.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }

            TextButton(onClick = {
                error = null
                pin = ""
                scope.launch { model.requestNewCode(target)?.let { error = it } }
            }) {
                Text(stringResource(R.string.pair_new_code), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
}

@Composable
private fun DigitBox(character: String, active: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .size(width = 58.dp, height = 70.dp)
            .card(shape, Palette.surface2)
            .then(if (active) Modifier.border(2.dp, Palette.accent, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(character, color = Palette.text, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
    }
}
