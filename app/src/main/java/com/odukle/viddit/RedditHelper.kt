package com.odukle.viddit

import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.odukle.viddit.MainActivity.Companion.main
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dean.jraw.RedditClient
import net.dean.jraw.http.OkHttpNetworkAdapter
import net.dean.jraw.http.UserAgent
import net.dean.jraw.oauth.Credentials
import net.dean.jraw.oauth.OAuthHelper
import net.dean.jraw.oauth.StatefulAuthHelper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException


private const val TAG = "RedditHelper"
private const val AUTH_URL = "https://www.reddit.com/api/v1/authorize.compact?client_id=%s" +
        "&response_type=code&state=%s&redirect_uri=%s&" +
        "duration=permanent&scope=identity edit history read save submit subscribe vote"
private const val CLIENT_ID = "dpM8BKY1nsPNYYwhwpeYIg"

//TODO hide client id
private const val REDIRECT_URI = "https://odukle.github.io/"
private const val STATE = "MY_RANDOM_STRING_1"
private const val ACCESS_TOKEN_URL = "https://www.reddit.com/api/v1/access_token"

class RedditHelper {

    lateinit var accessToken: String
    lateinit var refreshToken: String
    lateinit var credentials: Credentials
    lateinit var adapter: OkHttpNetworkAdapter
    lateinit var authHelper: StatefulAuthHelper
    var reddit: RedditClient? = null

    fun init() {
        val userAgent = UserAgent("Android", BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, "odukle")
        credentials = Credentials.installedApp(CLIENT_ID, REDIRECT_URI)
        adapter = OkHttpNetworkAdapter(userAgent)
        authHelper = OAuthHelper.interactive(adapter, credentials)
    }

    fun startSignIn() {
        Log.d(TAG, "startSignIn: called")
//        val url = String.format(AUTH_URL, CLIENT_ID, STATE, REDIRECT_URI)
//        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
//        main.startActivity(intent)
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
                        reddit = authHelper.onUserChallenge(url)
                        reddit!!.autoRenew = true
                        main.runOnUiThread {
                            main.shortToast("Logged in as " + reddit!!.me().username)
                        }
                        main.binder.browserLayout.hide()
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

    private fun getAccessToken(code: String) {
        Log.d(TAG, "getAccessToken: called")
        val client = OkHttpClient()
        val authString = "$CLIENT_ID:"
        val encodedAuthString: String = Base64.encodeToString(
            authString.toByteArray(),
            Base64.NO_WRAP
        )

        val request: Request = Request.Builder()
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Authorization", "Basic $encodedAuthString")
            .url(ACCESS_TOKEN_URL)
            .post(
                ("grant_type=authorization_code&code=" + code +
                        "&redirect_uri=" + REDIRECT_URI
                        ).toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
            )
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "ERROR: $e")
            }

            override fun onResponse(call: Call, response: Response) {
                val json: String = response.body?.string() ?: "null"
                val data: JSONObject?
                try {
                    data = JSONObject(json)
                    accessToken = data.optString("access_token")
                    refreshToken = data.optString("refresh_token")
                    putPref(ACCESS_TOKEN, accessToken)
                    putPref(REFRESH_TOKEN, refreshToken)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        })
    }

    fun onOAuthResult(intent: Intent?) {
        if (intent != null && intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri!!.getQueryParameter("error") != null) {
                val error = uri.getQueryParameter("error")
                Log.e(TAG, "An error has occurred : $error")
            } else {
                val state = uri.getQueryParameter("state")
                if (state == STATE) {
                    val code = uri.getQueryParameter("code")
                    if (code != null) {
                        getAccessToken(code)
                    } else {
                        Log.d(TAG, "onResume: code is null")
                    }
                }
            }
        }
    }

    suspend fun vote(
        token: String,
        postId: String,
        vote: Int,
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "vote: called")
        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("id", postId)
            .addFormDataPart("dir", vote.toString())
            .build()
        val headers = Headers.Builder()
            .add("Authorization", "bearer $token")
            .add("User-Agent", USER_AGENT)
            .build()
        val request = Request.Builder()
            .url(ENDPOINT_VOTE)
            .headers(headers)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val json = response.body?.string()
        json
    }
}