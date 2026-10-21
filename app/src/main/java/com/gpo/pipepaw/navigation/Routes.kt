package com.gpo.pipepaw.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every pushed destination. `Main` is the shell (floating bar sections); the rest push on top
 * with the page transitions. The player is NOT a route: it is the shell overlay
 * (feature/player, predictive-back class ShellOverlayDown) and floats over every page.
 */
@Serializable
sealed interface Route : NavKey {
    @Serializable data object Main : Route

    /** [hint]: the field starts with this text (editing the last query). */
    @Serializable data class Search(val hint: String = "") : Route
    @Serializable data class SearchResult(val query: String) : Route

    @Serializable data class Channel(val url: String, val name: String = "", val avatar: String = "") : Route

    @Serializable data object Settings : Route

    /** Google's sign-in page in a WebView (feature/account). */
    @Serializable data object SignIn : Route

    /** A signed-in account list ([com.gpo.pipepaw.feature.account.AccountList] name). */
    @Serializable data class AccountVideos(val list: String) : Route
}
