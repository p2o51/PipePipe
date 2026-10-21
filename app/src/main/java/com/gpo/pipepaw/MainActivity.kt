package com.gpo.pipepaw

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.rememberNavBackStack
import com.gpo.pipepaw.navigation.AppNavHost
import com.gpo.pipepaw.navigation.LinkRouter
import com.gpo.pipepaw.navigation.Navigator
import com.gpo.pipepaw.navigation.Route
import com.gpo.pipepaw.ui.components.LocalSnackbarHost
import com.gpo.pipepaw.ui.components.LocalToastScope
import com.gpo.pipepaw.ui.theme.ApplySystemBars
import com.gpo.pipepaw.ui.theme.LocalPawDark
import com.gpo.pipepaw.ui.theme.PawTheme
import kotlinx.coroutines.channels.Channel

class MainActivity : ComponentActivity() {
    /** YouTube links from outside (a share, a tapped link): opened once the navigator exists. */
    private val links = Channel<String>(Channel.UNLIMITED)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        handleIntent(intent)
        setContent {
            PawTheme {
                val snackbar = remember { SnackbarHostState() }
                val rootScope = rememberCoroutineScope()
                val backStack = rememberNavBackStack(Route.Main)
                val navigator = remember(backStack) { Navigator(backStack, this) }
                LaunchedEffect(Unit) { for (url in links) navigator.openUrl(url) }
                // The single owner of the bar icon colour: pages' wishes, else the app's light/dark.
                ApplySystemBars(window, LocalPawDark.current)
                CompositionLocalProvider(
                    LocalSnackbarHost provides snackbar,
                    LocalToastScope provides rootScope,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost(navigator)
                        SnackbarHost(
                            snackbar,
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val url = when (intent.action) {
            Intent.ACTION_SEND -> LinkRouter.firstLink(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty())
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        links.trySend(url)
    }
}
