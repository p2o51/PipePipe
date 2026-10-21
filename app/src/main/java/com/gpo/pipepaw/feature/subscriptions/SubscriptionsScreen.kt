package com.gpo.pipepaw.feature.subscriptions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpo.pipepaw.ui.components.ChannelRow
import com.gpo.pipepaw.ui.components.EmptyState
import com.gpo.pipepaw.ui.components.LocalBottomBarInset
import com.gpo.pipepaw.ui.components.readingWidth
import com.gpo.pipepaw.ui.components.seam.seamViewport
import com.gpo.yoin.symbols.YoinSymbols

/** 订阅: the subscribed channels, newest first. Each opens its channel page. */
@Composable
fun SubscriptionsScreen() {
    val channels by Subscriptions.channels.collectAsState()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (channels.isEmpty()) {
        EmptyState("还没有订阅", Modifier.padding(top = statusTop), icon = YoinSymbols.Library)
        return
    }
    val state = rememberLazyListState()
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().readingWidth().seamViewport(state, underStatusBar = true),
        contentPadding = PaddingValues(top = statusTop, bottom = LocalBottomBarInset.current + 16.dp),
    ) {
        item(key = "title") {
            Text("订阅", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        }
        items(channels, key = { it.url }) { ChannelRow(it) }
    }
}
