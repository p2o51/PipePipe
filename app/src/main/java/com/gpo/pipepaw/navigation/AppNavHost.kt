package com.gpo.pipepaw.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.rememberNavigationEventState
import com.gpo.pipepaw.navigation.transition.BackInputBlocker
import com.gpo.pipepaw.navigation.transition.BackPhase
import com.gpo.pipepaw.navigation.transition.BeneathPage
import com.gpo.pipepaw.navigation.transition.LocalPageTransitions
import com.gpo.pipepaw.navigation.transition.PageBackHandler
import com.gpo.pipepaw.navigation.transition.PageEntryFrame
import com.gpo.pipepaw.navigation.transition.pageClosingTransform
import com.gpo.pipepaw.navigation.transition.pagePopTransform
import com.gpo.pipepaw.navigation.transition.pagePushTransform
import com.gpo.pipepaw.navigation.transition.rememberPageTransitions
import com.gpo.pipepaw.navigation.transition.voteHighFrameRate
import com.gpo.pipepaw.feature.channel.ChannelScreen
import com.gpo.pipepaw.feature.player.PlayerOverlayHost
import com.gpo.pipepaw.feature.search.SearchResultScreen
import com.gpo.pipepaw.feature.search.SearchScreen
import com.gpo.pipepaw.feature.settings.SettingsScreen
import com.gpo.pipepaw.ui.shell.MainShell

/**
 * Single NavDisplay for the whole app, wrapped in the page-transition host
 * (navigation/transition): pushes grow out of the tapped card (container
 * transform) or enter on Yoin's forward mirror; back is the in-window AOSP
 * cross-activity replica for every pushed page (predictive-back Pattern B).
 *
 * The player is not a page: [PlayerOverlayHost] floats over the whole stack as Yoin's
 * Now Playing does (mini bar ⇄ full player, one progress), so a video keeps playing while
 * the user browses underneath.
 *
 * NavDisplay's own predictive pop is not used: it is driven through the
 * scene-state overload with a NavigationEventState that no handler feeds, and
 * [PageBackHandler] (composed first, so in-page BackHandlers outrank it)
 * owns the gesture.
 */
@Composable
fun AppNavHost(navigator: Navigator) {
    val backStack = navigator.backStack
    val transitions = rememberPageTransitions(backStack)
    val configuration = LocalConfiguration.current
    SideEffect {
        navigator.transitions = transitions
        transitions.configStamp = configuration.uiMode * 31 + configuration.densityDpi
    }
    val density = LocalDensity.current
    val decorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator<NavKey>(),
        remember(transitions) {
            NavEntryDecorator<NavKey>(onPop = { key -> transitions.onEntryPopped(key) }) { entry ->
                PageEntryFrame(transitions, entry.contentKey) { entry.Content() }
            }
        },
    )
    val entries = rememberDecoratedNavEntries(backStack, decorators, appEntryProvider)

    CompositionLocalProvider(LocalNavigator provides navigator, LocalPageTransitions provides transitions) {
        // First in composition = lowest back priority (see PageBackHandler).
        PageBackHandler(transitions, canGoBack = backStack.size > 1)
        val backdrop = MaterialTheme.colorScheme.surfaceContainerHighest
        Box(
            Modifier
                .fillMaxSize()
                .background(backdrop)
                .onSizeChanged { transitions.hostSize = it }
                .voteHighFrameRate(transitions.phase != BackPhase.Idle),
        ) {
            BeneathPage(transitions, backdrop)
            Box(
                Modifier
                    .fillMaxSize()
                    .pageClosingTransform(transitions)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                val sceneState = rememberSceneState(
                    entries,
                    listOf(SinglePaneSceneStrategy()),
                    emptyList(),
                    null,
                    { navigator.pop() },
                )
                val scene = sceneState.currentScene
                val navigationState = rememberNavigationEventState(
                    currentInfo = SceneInfo(scene),
                    backInfo = sceneState.previousScenes.map { SceneInfo(it) },
                )
                NavDisplay(
                    sceneState,
                    navigationState,
                    Modifier.fillMaxSize(),
                    Alignment.TopStart,
                    null,
                    { pagePushTransform(transitions, density) },
                    { pagePopTransform() },
                    { _ -> pagePopTransform() },
                )
            }
            BeneathPage(transitions, backdrop, over = true)
            BackInputBlocker(transitions)
            // Last in composition: over every page, and its back handlers outrank the pages'
            // while it is expanded (predictive-back ShellOverlayDown).
            PlayerOverlayHost()
        }
    }
}

private val appEntryProvider = entryProvider<NavKey> {
    entry<Route.Main> { MainShell() }
    entry<Route.Search> { SearchScreen(it) }
    entry<Route.SearchResult> { SearchResultScreen(it) }
    entry<Route.Channel> { ChannelScreen(it) }
    entry<Route.Settings> { SettingsScreen() }
}
