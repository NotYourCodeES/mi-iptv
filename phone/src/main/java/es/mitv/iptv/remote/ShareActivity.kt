package es.mitv.iptv.remote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.mitv.iptv.remote.ui.Palette
import es.mitv.iptv.remote.ui.RemoteTheme
import es.mitv.iptv.remote.ui.card
import es.mitv.iptv.remote.ui.rememberHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Share → Mi IPTV": sends the shared link to the paired TV. */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val text = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        val url = text?.let { if (Logic.isWebLink(it)) it.trim() else Logic.firstLink(it) }
        val title = intent?.getStringExtra(Intent.EXTRA_SUBJECT)
        val tv = PairedTvStore.load(this)
        setContent {
            RemoteTheme {
                ShareCard(url, title, tv, { describeError(this, it) }, close = ::finish)
            }
        }
    }
}

private sealed interface Phase {
    object Ready : Phase
    object Sending : Phase
    data class Done(val text: String) : Phase
    data class Failed(val text: String) : Phase
}

@Composable
private fun ShareCard(url: String?, title: String?, tv: PairedTv?, errorText: (Throwable) -> String, close: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<Phase>(Phase.Ready) }
    val sentText = stringResource(R.string.sent_to_tv)

    fun send(asPlaylist: Boolean) {
        if (tv == null || url == null) return
        phase = Phase.Sending
        scope.launch {
            phase = try {
                val message = TvClient(tv.host, tv.port, tv.token).share(url, title, asPlaylist)
                haptics.success()
                Phase.Done(message ?: sentText)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                haptics.error()
                Phase.Failed(errorText(e))
            }
            if (phase is Phase.Done) {
                delay(900)
                close()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .card(RoundedCornerShape(28.dp), Palette.bg2)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).background(Palette.gradient, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tv, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.share_title), color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (tv != null) Text(tv.name, color = Palette.muted, fontSize = 13.sp)
                }
                IconButton(onClick = close) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Palette.muted)
                }
            }

            when {
                tv == null -> Message(Icons.Rounded.Warning, stringResource(R.string.share_need_pairing))
                url == null -> Message(Icons.Rounded.Link, stringResource(R.string.share_no_link))
                else -> {
                    Text(
                        url,
                        color = Palette.text2,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().background(Palette.surface2, RoundedCornerShape(14.dp)).padding(12.dp),
                    )
                    when (val p = phase) {
                        Phase.Ready, is Phase.Failed -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionButton(stringResource(R.string.play_on_tv), Icons.Rounded.PlayArrow, primary = true) { send(false) }
                                ActionButton(stringResource(R.string.use_as_list), Icons.AutoMirrored.Rounded.ListAlt, primary = false) { send(true) }
                            }
                            if (p is Phase.Failed) Text(p.text, color = Palette.live, fontSize = 13.sp)
                        }
                        Phase.Sending -> Box(Modifier.fillMaxWidth().heightIn(min = 104.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        is Phase.Done -> Row(
                            Modifier.fillMaxWidth().heightIn(min = 104.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = Palette.green)
                            Text(p.text, color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.text2)
        Text(text, color = Palette.text2, fontSize = 15.sp)
    }
}

@Composable
private fun ActionButton(title: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (primary) Modifier.background(Palette.gradient, shape) else Modifier.background(Palette.surface2, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (primary) Color.White else Palette.text
        Icon(icon, null, tint = color)
        Text(title, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}
