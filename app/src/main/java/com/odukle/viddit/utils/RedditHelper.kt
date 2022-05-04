package com.odukle.viddit.utils

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.odukle.viddit.App.Companion.accountHelper
import com.odukle.viddit.App.Companion.tokenStore
import com.odukle.viddit.BuildConfig
import com.odukle.viddit.MainActivity.Companion.main
import com.odukle.viddit.hide
import com.odukle.viddit.mainScope
import com.odukle.viddit.show
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dean.jraw.RedditClient
import net.dean.jraw.http.OkHttpNetworkAdapter
import net.dean.jraw.http.UserAgent
import net.dean.jraw.models.Submission
import net.dean.jraw.models.SubredditSort
import net.dean.jraw.oauth.StatefulAuthHelper
import net.dean.jraw.pagination.DefaultPaginator


private const val TAG = "RedditHelper"

suspend fun getSubmissionPages(reddit: RedditClient, title: String): DefaultPaginator<Submission> = withContext(IO) {
    val multi = reddit.me().multi(title)
    multi.posts().sorting(SubredditSort.HOT).limit(100).build()
}

class RedditHelper {

    private lateinit var adapter: OkHttpNetworkAdapter
    lateinit var authHelper: StatefulAuthHelper
    var reddit: RedditClient? = null

    fun init() {
        val userAgent = UserAgent("Android", BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, "odukle")
//        credentials = Credentials.installedApp(CLIENT_ID, REDIRECT_URI)
        adapter = OkHttpNetworkAdapter(userAgent)
        authHelper = accountHelper.switchToNewUser()
//        authHelper = OAuthHelper.interactive(adapter, credentials)
        try {
            if (tokenStore.usernames.isNotEmpty()) {
                reddit = accountHelper.switchToUser(tokenStore.usernames[0])
            }
        } catch (e: IllegalStateException) {
        }
        reddit?.let { main.shortToast("logged in as ${it.authManager.currentUsername()}") } ?: main.shortToast("Browsing anonymously")
    }

    fun startSignIn() {

        val authUrl = authHelper.getAuthorizationUrl(
            true, true,
            "identity", "edit", "history", "read", "save", "submit", "subscribe", "vote"
        )

        val browser = main.binder.browser
        main.binder.browserLayout.show()
        browser.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                main.binder.loader.show()
                if (url != null && authHelper.isFinalRedirectUrl(url)) {
                    browser.stopLoading()
                    CoroutineScope(IO).launch {
                        Log.d(TAG, "onPageStarted: $url")
                        reddit = authHelper.onUserChallenge(url)
                        reddit!!.autoRenew = true
                        reddit!!.authManager.refreshToken?.let {
                            reddit!!.authManager.current?.let { it1 -> tokenStore.storeLatest(reddit!!.authManager.currentUsername(), it1) }
                            tokenStore.storeRefreshToken(reddit!!.authManager.currentUsername(), it)
                        }
                        mainScope().launch {
                            main.shortToast("Logged in as " + reddit!!.me().username)
                            main.clearCookies()
                            main.binder.browserLayout.hide()
                            delay(200)
                            main.triggerRebirth()
                        }
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                main.binder.loader.hide()
                super.onPageFinished(view, url)
            }
        }

        browser.loadUrl(authUrl)
    }

}