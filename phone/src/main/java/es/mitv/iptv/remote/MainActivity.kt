package es.mitv.iptv.remote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.mitv.iptv.remote.ui.AppBackground
import es.mitv.iptv.remote.ui.ChannelsScreen
import es.mitv.iptv.remote.ui.ConnectScreen
import es.mitv.iptv.remote.ui.MoreScreen
import es.mitv.iptv.remote.ui.Palette
import es.mitv.iptv.remote.ui.RemoteScreen
import es.mitv.iptv.remote.ui.RemoteTheme
import es.mitv.iptv.remote.ui.card
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            RemoteTheme {
                AppBackground {
                    Root(model)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.appBecameActive()
    }

    override fun onStop() {
        super.onStop()
        model.appWentToBackground()
    }

    /** The phone's volume buttons change the TV's volume while the remote is open. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> RemoteKey.VolumeUp
            KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteKey.VolumeDown
            else -> null
        }
        if (key != null && model.volumeKeys && model.connection == Connection.Online) {
            model.press(key)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            model.volumeKeys && model.connection == Connection.Online
        ) return true
        return super.onKeyUp(keyCode, event)
    }
}

@Composable
private fun Root(model: AppModel) {
    Crossfade(model.paired != null, label = "root") { paired ->
        if (paired) MainTabs(model) else ConnectScreen(model)
    }
    Box(Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        ToastHost(model)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainTabs(model: AppModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // The tabs hide while typing, like most Android apps, so they don't ride on top of the keyboard
    val typing = WindowInsets.isImeVisible
    Column(Modifier.systemBarsPadding()) {
        Box(Modifier.weight(1f).imePadding()) {
            when (tab) {
                0 -> RemoteScreen(model)
                1 -> ChannelsScreen(model)
                else -> MoreScreen(model)
            }
        }
        if (!typing) NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp, windowInsets = WindowInsets(0)) {
            val items = listOf(
                Icons.Rounded.SettingsRemote to R.string.tab_remote,
                Icons.AutoMirrored.Rounded.ListAlt to R.string.tab_channels,
                Icons.Rounded.MoreHoriz to R.string.tab_more,
            )
            items.forEachIndexed { i, (icon, label) ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Icon(icon, null) },
                    label = { Text(stringResource(label)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Palette.accent,
                        selectedTextColor = Palette.accent,
                        indicatorColor = Palette.surface2,
                        unselectedIconColor = Palette.text2,
                        unselectedTextColor = Palette.text2,
                    ),
                )
            }
        }
    }
}

/** Notices from the TV ("Added to favorites", "This channel can't be played"...). */
@Composable
private fun ToastHost(model: AppModel) {
    val toast = model.toast
    var text by remember { mutableStateOf("") } // kept while the toast slides away
    if (toast != null) text = toast.text
    LaunchedEffect(toast?.id) {
        val id = toast?.id ?: return@LaunchedEffect
        delay(2600)
        model.dismissToast(id)
    }
    AnimatedVisibility(
        visible = toast != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Row(
            Modifier
                .padding(top = 6.dp, start = 24.dp, end = 24.dp)
                .shadow(16.dp, CircleShape)
                .card(CircleShape, Palette.surface2)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Tv, null, tint = Palette.accent, modifier = Modifier.size(18.dp))
            Text(text, color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        }
    }
}
