package com.odukle.viddit

import android.app.Application
import android.util.Log
import net.dean.jraw.RedditClient
import net.dean.jraw.android.AndroidHelper.accountHelper
import net.dean.jraw.android.AppInfoProvider
import net.dean.jraw.android.ManifestAppInfoProvider
import net.dean.jraw.android.SharedPreferencesTokenStore
import net.dean.jraw.android.SimpleAndroidLogAdapter
import net.dean.jraw.http.LogAdapter
import net.dean.jraw.http.SimpleHttpLogger
import net.dean.jraw.oauth.AccountHelper
import java.util.*

private const val TAG = "App"
class App : Application() {
    override fun onCreate() {
        Log.d(TAG, "onCreate: called")
        super.onCreate()

        // Get UserAgent and OAuth2 data from AndroidManifest.xml
        val provider: AppInfoProvider = ManifestAppInfoProvider(applicationContext)

        // Ideally, this should be unique to every device
        val deviceUuid: UUID = UUID.randomUUID()

        // Store our access tokens and refresh tokens in shared preferences
        tokenStore = SharedPreferencesTokenStore(applicationContext)
        // Load stored tokens into memory
        tokenStore.load()
        // Automatically save new tokens as they arrive
        tokenStore.autoPersist = true

        // An AccountHelper manages switching between accounts and into/out of userless mode.
        accountHelper = accountHelper(provider, deviceUuid, tokenStore)

        // Every time we use the AccountHelper to switch between accounts (from one account to
        // another, or into/out of userless mode), call this function
        accountHelper.onSwitch { redditClient: RedditClient ->
            // By default, JRAW logs HTTP activity to System.out. We're going to use Log.i()
            // instead.
            val logAdapter: LogAdapter = SimpleAndroidLogAdapter(Log.INFO)

            // We're going to use the LogAdapter to write down the summaries produced by
            // SimpleHttpLogger
            redditClient.logger = SimpleHttpLogger(SimpleHttpLogger.DEFAULT_LINE_LENGTH, logAdapter)
            null
        }
    }

    companion object {
        lateinit var accountHelper: AccountHelper
        lateinit var tokenStore: SharedPreferencesTokenStore
    }
}