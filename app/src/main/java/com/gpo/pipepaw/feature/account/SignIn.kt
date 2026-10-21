package com.gpo.pipepaw.feature.account

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.gpo.pipepaw.core.account.Accounts
import com.gpo.pipepaw.core.account.Profile
import com.gpo.pipepaw.navigation.LocalNavigator
import com.gpo.pipepaw.ui.components.PawTopBar
import com.gpo.pipepaw.ui.components.rememberToast

/**
 * Signing in and out of YouTube. Signing in happens on Google's own page in a WebView (the
 * person types their credentials there; the app never sees them), as PipePipe does: once
 * youtube.com holds a session (its SAPISID / SID cookies), the cookies are the account.
 */
object SignIn {
    const val Url = "https://www.youtube.com/signin"

    /** A session YouTube's API accepts: the cookie the SAPISIDHASH authorization is made from, and the session id. */
    fun hasSession(cookies: String?): Boolean {
        if (cookies.isNullOrEmpty()) return false
        val names = cookies.split(';').map { it.substringBefore('=').trim() }.toSet()
        return ("SAPISID" in names || "__Secure-3PAPISID" in names) && ("SID" in names || "__Secure-3PSID" in names)
    }

    /** youtube.com's cookies as the WebView holds them now. */
    fun youtubeCookies(): String? = CookieManager.getInstance().getCookie("https://www.youtube.com")

    /** Back to signed out: the profile, the WebView's cookies and its storage all go. */
    fun signOut() {
        Accounts.setYouTube(Profile.SignedOut)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }
}

/**
 * 登录: Google's sign-in page continuing to youtube.com, full screen under a plain top bar.
 * Every page load checks youtube.com's cookies; the first time they hold a session the
 * profile becomes signed in and the page closes.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInScreen() {
    val nav = LocalNavigator.current
    val toast = rememberToast()
    var loading by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PawTopBar(title = "登录 YouTube", onBack = { nav.pop() })
        Box(Modifier.fillMaxWidth()) {
            if (loading) LinearWavyProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
        }
        AndroidView(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // A phone browser: Google signs in a WebView it recognises as one.
                    settings.userAgentString = MobileUserAgent
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            if (done) return
                            val cookies = SignIn.youtubeCookies()
                            if (SignIn.hasSession(cookies)) {
                                done = true
                                CookieManager.getInstance().flush()
                                Accounts.setYouTube(Profile.SignedIn(cookies = cookies!!))
                                toast("已登录")
                                nav.pop()
                            }
                        }
                    }
                    loadUrl(SignIn.Url)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

private const val MobileUserAgent =
    "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
