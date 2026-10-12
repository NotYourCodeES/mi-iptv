package es.mitv.iptv.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.mitv.iptv.remote.AppModel
import es.mitv.iptv.remote.ChannelFilter
import es.mitv.iptv.remote.R
import kotlinx.coroutines.launch

/** The TV's channel list on the phone: search with the phone keyboard, tap to watch on the TV. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(model: AppModel) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf<ChannelFilter>(ChannelFilter.All) }
    var didPickDefault by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val results = model.filteredChannels(query, filter)

    // Start on Favorites when there are some, like the TV does
    LaunchedEffect(model.channels.isEmpty()) {
        if (!didPickDefault && model.channels.isNotEmpty()) {
            didPickDefault = true
            filter = if (model.favorites.isEmpty()) ChannelFilter.All else ChannelFilter.Favorites
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.tab_channels),
            color = Palette.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
        )
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                SearchField(query, { query = it }, stringResource(R.string.search_hint), onSearch = { model.searchOnTv(query) })
            }
            if (query.isNotEmpty()) {
                TextButton(onClick = {
                    haptics.tap()
                    model.searchOnTv(query)
                }) {
                    Icon(Icons.Rounded.Tv, null, Modifier.size(18.dp))
                    Text(" " + stringResource(R.string.show_on_tv), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (model.channels.isNotEmpty()) {
            FilterChips(model, filter) {
                haptics.select()
                filter = it
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    model.loadChannels()
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            when {
                model.channels.isEmpty() && model.loadingChannels -> Centered {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.loading_channels), color = Palette.muted)
                }
                model.channels.isEmpty() -> Empty(Icons.Rounded.Tv, stringResource(R.string.no_channels), stringResource(R.string.no_channels_desc))
                results.isEmpty() && query.isEmpty() && filter == ChannelFilter.Favorites ->
                    Empty(Icons.Rounded.Star, stringResource(R.string.no_favorites), stringResource(R.string.no_favorites_desc))
                results.isEmpty() -> Empty(Icons.Rounded.Search, stringResource(R.string.no_results, query), null)
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(results, key = { it.n }) { channel ->
                        ChannelRow(model, channel)
                        HorizontalDivider(Modifier.padding(start = 108.dp), color = Palette.line)
                    }
                }
            }
        }
    }
}

/** Favorites · All · categories, scrolling sideways. */
@Composable
private fun FilterChips(model: AppModel, selected: ChannelFilter, onSelect: (ChannelFilter) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) {
        val index = when (selected) {
            ChannelFilter.Favorites -> 0
            ChannelFilter.All -> 1
            is ChannelFilter.Group -> selected.index + 2
        }
        state.animateScrollToItem(maxOf(0, index - 1))
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Chip(stringResource(R.string.favorites), selected == ChannelFilter.Favorites, star = true) { onSelect(ChannelFilter.Favorites) } }
        item { Chip(stringResource(R.string.all), selected == ChannelFilter.All) { onSelect(ChannelFilter.All) } }
        itemsIndexed(model.groups) { index, name ->
            Chip(name, selected == ChannelFilter.Group(index)) { onSelect(ChannelFilter.Group(index)) }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, star: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .then(if (selected) Modifier.background(Palette.gradient, CircleShape) else Modifier.card(CircleShape, Palette.surface2))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (star) Icon(Icons.Rounded.Star, null, tint = if (selected) Color.White else Palette.gold, modifier = Modifier.size(14.dp))
        Text(text, color = if (selected) Color.White else Palette.text2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun Empty(icon: ImageVector, title: String, description: String?) {
    // Scrollable so pull-to-refresh also works on an empty list
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, null, tint = Palette.muted, modifier = Modifier.size(44.dp))
                Text(title, color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (description != null) {
                    Text(description, color = Palette.muted, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
