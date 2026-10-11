package es.mitv.iptv.remote.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.mitv.iptv.remote.AppModel
import es.mitv.iptv.remote.BuildConfig
import es.mitv.iptv.remote.Logic
import es.mitv.iptv.remote.R
import kotlinx.coroutines.launch

/** TV details, provider setup, sending links, phone settings and unpairing. */
@Composable
fun MoreScreen(model: AppModel) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var link by rememberSaveable { mutableStateOf("") }
    var sending by rememberSaveable { mutableStateOf(false) }
    var linkResult by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmUnpair by rememberSaveable { mutableStateOf(false) }

    fun send(asPlaylist: Boolean) {
        val url = link.trim()
        sending = true
        linkResult = null
        scope.launch {
            linkResult = model.share(url, asPlaylist)
            sending = false
            haptics.success()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.tab_more),
            color = Palette.text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        )

        SectionHeader(stringResource(R.string.section_tv))
        Section {
            model.paired?.let { tv ->
                Value(stringResource(R.string.name), tv.name)
                Divider()
                Value(stringResource(R.string.address), tv.address)
                Divider()
            }
            model.state?.status?.takeIf { it.isNotEmpty() }?.let {
                Value(stringResource(R.string.status), it)
                Divider()
            }
            val setup = model.state?.setupUrl ?: model.paired?.let { "http://${it.host}:${it.port}" }
            if (setup != null) {
                Action(Icons.Rounded.Tune, stringResource(R.string.setup_provider), stringResource(R.string.setup_provider_desc)) {
                    openSetupPage(context, setup)
                }
                Divider()
            }
            Action(Icons.Rounded.Refresh, stringResource(R.string.reload_channels)) {
                haptics.tap()
                model.reloadOnTv()
            }
        }

        SectionHeader(stringResource(R.string.send_link))
        Section {
            OutlinedTextField(
                value = link,
                onValueChange = {
                    link = it
                    linkResult = null
                },
                placeholder = { Text("https://…") },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                trailingIcon = if (link.isNotEmpty()) {
                    { Icon(Icons.Rounded.Cancel, stringResource(R.string.clear), Modifier.clickable { link = "" }, tint = Palette.muted) }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
            if (link.isEmpty()) {
                Action(Icons.Rounded.ContentPaste, stringResource(R.string.paste)) {
                    paste(context)?.let { link = it }
                }
            } else {
                val enabled = Logic.isWebLink(link) && !sending
                Action(Icons.Rounded.LiveTv, stringResource(R.string.play_on_tv), enabled = enabled) { send(asPlaylist = false) }
                Divider()
                Action(Icons.AutoMirrored.Rounded.ListAlt, stringResource(R.string.use_as_list), enabled = enabled) { send(asPlaylist = true) }
            }
            linkResult?.let {
                Text(it, color = Palette.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
        Footer(stringResource(R.string.send_link_tip))

        SectionHeader(stringResource(R.string.phone_section))
        Section {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { model.updateVolumeKeys(!model.volumeKeys) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Palette.accent)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.volume_keys), color = Palette.text, fontSize = 16.sp)
                    Text(stringResource(R.string.volume_keys_desc), color = Palette.muted, fontSize = 13.sp)
                }
                Switch(
                    checked = model.volumeKeys,
                    onCheckedChange = model::updateVolumeKeys,
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.accent),
                )
            }
        }

        Spacer(Modifier.size(8.dp))
        Section {
            Action(null, stringResource(R.string.unpair_phone), color = Palette.live) { confirmUnpair = true }
        }
        Footer(stringResource(R.string.unpair_footer))

        SectionHeader(stringResource(R.string.about))
        Section {
            Value(stringResource(R.string.version), BuildConfig.VERSION_NAME)
            Divider()
            Action(Icons.Rounded.Code, stringResource(R.string.source_code)) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NotYourCodeES/mi-iptv")))
                }
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            containerColor = Palette.bg2,
            title = { Text(stringResource(R.string.unpair_confirm_title)) },
            text = { Text(stringResource(R.string.unpair_confirm_message), color = Palette.muted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    model.disconnect()
                }) { Text(stringResource(R.string.unpair), color = Palette.live) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun paste(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    return text?.trim()?.takeIf { it.isNotEmpty() }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = Palette.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp),
    )
}

@Composable
private fun Footer(text: String) {
    Text(text, color = Palette.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().card(RoundedCornerShape(16.dp)), content = content)
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = Palette.line)

@Composable
private fun Value(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, color = Palette.text, fontSize = 16.sp)
        Text(value, color = Palette.muted, fontSize = 16.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun Action(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    color: Color = Palette.accent,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color)
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Palette.muted, fontSize = 13.sp)
        }
    }
}
