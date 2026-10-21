package com.gpo.pipepaw.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gpo.pipepaw.core.account.Accounts
import com.gpo.pipepaw.core.account.Profile
import com.gpo.pipepaw.core.extractor.YtFeeds
import com.gpo.pipepaw.core.settings.AppSettings
import com.gpo.pipepaw.feature.settings.Choice
import com.gpo.pipepaw.feature.settings.ConnectedToggleRow
import com.gpo.pipepaw.ui.components.LoadMoreEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpo.pipepaw.core.extractor.VideoItem
import com.gpo.pipepaw.feature.account.ProfileButton
import com.gpo.pipepaw.core.extractor.Yt
import com.gpo.pipepaw.feature.subscriptions.Subscriptions
import com.gpo.pipepaw.navigation.LocalNavigator
import com.gpo.pipepaw.navigation.Route
import com.gpo.pipepaw.ui.components.EmptyState
import com.gpo.pipepaw.ui.components.ErrorState
import com.gpo.pipepaw.ui.components.LoadingState
import com.gpo.pipepaw.ui.components.LocalBottomBarInset
import com.gpo.pipepaw.ui.components.Page
import com.gpo.pipepaw.ui.components.Pager
import com.gpo.pipepaw.ui.components.RefreshBox
import com.gpo.pipepaw.ui.components.VideoCard
import com.gpo.pipepaw.ui.components.seam.seamViewport
import com.gpo.pipepaw.ui.components.videoGridMinWidth
import com.gpo.pipepaw.ui.shell.LocalTabReselect
import com.gpo.pipepaw.ui.theme.ContinuousRoundedCornerShape
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 首页. Signed out: the newest uploads of the local subscriptions, newest first, from each
 * channel's RSS feed (one light request per channel, six at a time); no algorithm. Signed in:
 * the account's own 订阅 feed (its subscriptions' uploads, the default) or YouTube's 推荐.
 */
enum class HomeFeed(val label: String) { Subscriptions("订阅"), Recommended("推荐") }

class HomeViewModel : ViewModel() {
    var feed by mutableStateOf(HomeFeed.Subscriptions)
        private set

    val pager = Pager<VideoItem>(viewModelScope, dedupKey = { it.url }) { cursor -> load(cursor) }

    init { pager.refresh() }

    fun select(f: HomeFeed) {
        if (f == feed) return
        feed = f
        pager.refresh()
    }

    private suspend fun load(cursor: String?): Page<VideoItem> {
        if (Accounts.youtube.value is Profile.SignedIn) {
            val page = YtFeeds.videos(if (feed == HomeFeed.Recommended) YtFeeds.Feed.Home else YtFeeds.Feed.Subscriptions, cursor)
            val hide = AppSettings.hideShorts.value
            return page.copy(items = page.items.filterNot { hide && it.isShort })
        }
        return local()
    }

    private suspend fun local(): Page<VideoItem> = coroutineScope {
        val channels = Subscriptions.channels.value
        val gate = Semaphore(6)
        // The channel-page fallback carries no uploader avatar: the subscription has it.
        val results = channels.map { c ->
            async {
                gate.withPermit {
                    runCatching { Yt.latest(c.url).map { if (it.channelAvatar.isEmpty()) it.copy(channelAvatar = c.avatar) else it } }
                }
            }
        }.awaitAll()
        // One channel failing still shows the rest; all of them failing is the page's error.
        if (results.isNotEmpty() && results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        val all = results.flatMap { it.getOrDefault(emptyList()) }.filterNot { it.isShort }.sortedByDescending { it.uploadedAt }
        Page(all, hasMore = false)
    }
}

@Composable
fun HomeScreen() {
    val vm = viewModel { HomeViewModel() }
    val pager = vm.pager
    val nav = LocalNavigator.current
    val subs by Subscriptions.channels.collectAsState()
    val gridState = rememberLazyGridState()
    val reselect = LocalTabReselect.current
    LaunchedEffect(reselect) {
        reselect.collect {
            if (gridState.firstVisibleItemIndex > 0) gridState.animateScrollToItem(0) else pager.refresh()
        }
    }
    // A subscription added or removed elsewhere, or signing in / out: the feed follows.
    val profile by Accounts.youtube.collectAsState()
    val signedIn = profile is Profile.SignedIn
    LaunchedEffect(subs.size, signedIn) { if (!pager.initialLoading) pager.refresh() }
    LoadMoreEffect(gridState) { pager.loadMore() }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = LocalBottomBarInset.current
    Box(Modifier.fillMaxSize()) {
        when {
            !signedIn && subs.isEmpty() -> HomeMessage(onSearch = { nav.push(Route.Search()) }, statusTop = statusTop) {
                EmptyState("还没有订阅", icon = YoinSymbols.Library)
                Button(
                    onClick = { nav.push(Route.Search()) },
                    modifier = Modifier.align(Alignment.Center).padding(top = 120.dp),
                ) { Text("搜索") }
            }
            pager.initialLoading -> LoadingState()
            pager.error != null && pager.items.isEmpty() -> HomeMessage(onSearch = { nav.push(Route.Search()) }, statusTop = statusTop) {
                ErrorState(pager.error.orEmpty(), onRetry = pager::refresh)
            }
            pager.items.isEmpty() -> HomeMessage(onSearch = { nav.push(Route.Search()) }, statusTop = statusTop) {
                EmptyState("订阅的频道还没有视频", icon = YoinSymbols.Library)
            }
            else -> RefreshBox(
                isRefreshing = pager.isRefreshing,
                onRefresh = pager::refresh,
                indicatorPadding = PaddingValues(top = statusTop),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(videoGridMinWidth),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().seamViewport(gridState, underStatusBar = true),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusTop, bottom = bottom + 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                        Column {
                            HomeHeader { nav.push(Route.Search()) }
                            if (signedIn) FeedToggle(vm.feed, vm::select)
                        }
                    }
                    items(pager.items, key = { it.url }) { VideoCard(it) }
                }
            }
        }
    }
}

/** The header over a full-page state, with the same 16dp sides the grid's content padding gives. */
@Composable
private fun HomeMessage(onSearch: () -> Unit, statusTop: Dp, content: @Composable BoxScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = statusTop, start = 16.dp, end = 16.dp)) {
        HomeHeader(onSearch)
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
    }
}

/** Signed in: 订阅 / 推荐, the account's two feeds. */
@Composable
private fun FeedToggle(selected: HomeFeed, onSelect: (HomeFeed) -> Unit) {
    ConnectedToggleRow(
        choices = HomeFeed.entries.map { Choice(it, it.label) },
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.widthIn(max = 320.dp).padding(bottom = 12.dp),
    )
}

/** The app's name and the search pill. */
@Composable
private fun HomeHeader(onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileButton()
        Text("PipePaw", style = MaterialTheme.typography.titleLarge)
        Row(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(ContinuousRoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onSearch)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(YoinSymbols.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("搜索", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
