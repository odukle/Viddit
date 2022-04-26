package com.odukle.viddit

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.google.firebase.analytics.FirebaseAnalytics
import com.odukle.viddit.Helper.Companion.FOR_MAIN
import com.odukle.viddit.Helper.Companion.currentPlayer
import com.odukle.viddit.Helper.Companion.tempPost
import com.odukle.viddit.Helper.Companion.videoList
import com.odukle.viddit.databinding.ActivityMainBinding


private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    lateinit var binder: ActivityMainBinding
    lateinit var sharedPreferences: SharedPreferences
    lateinit var toast: Toast
    private lateinit var mFirebaseAnalytics: FirebaseAnalytics

    //
    var redditHelper = RedditHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()
    }

    private fun init() {
        ////initialize vars
        binder = DataBindingUtil.setContentView(this, R.layout.activity_main)
        main = this
        toast = Toast(this)
        sharedPreferences = getSharedPreferences("pref", Context.MODE_PRIVATE)
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)
        //
        redditHelper.init()
        //
        videoList.clear()
        currentPlayer = null
        ///

        val cookieManager: CookieManager = CookieManager.getInstance()

        val webView = binder.browser
        val ws = webView.settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ws.forceDark = WebSettings.FORCE_DARK_ON
        }
        ws.cacheMode = WebSettings.LOAD_NO_CACHE

        ////
        val fragmentTxn = supportFragmentManager.beginTransaction()
        fragmentTxn.replace(R.id.container, MainFragment.newInstance("r/popular", "", 0, FOR_MAIN))
        fragmentTxn.commit()
    }

    fun longToast(text: String) {
        runOnUiThread {
            toast.cancel()
            toast = Toast.makeText(this, text, Toast.LENGTH_LONG)
            toast.show()
        }
    }

    fun shortToast(text: String) {
        runOnUiThread {
            toast.cancel()
            toast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
            toast.show()
        }
    }

    override fun onBackPressed() {

        val count = supportFragmentManager.backStackEntryCount
        if (binder.browserLayout.isVisible) {
            binder.browserLayout.hide()
        } else {
            if (count == 0) {
                finishAffinity()
            } else {
                supportFragmentManager.popBackStack()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            111 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    VideoAdapter.startDownloading(tempPost)
                } else {
                    shortToast("Permission denied")
                }
            }

        }
    }

    override fun onResume() {
        super.onResume()

//        redditHelper.onOAuthResult(intent)
    }

    companion object {
        lateinit var main: MainActivity
    }

    fun openInsta(view: View) {
        //Get url from tag
        val url = view.tag as String
        val intent = Intent()
        intent.action = Intent.ACTION_VIEW
        intent.addCategory(Intent.CATEGORY_BROWSABLE)

        //pass the url to intent data
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    fun openEmail(view: View) {
        val id = view.tag as String

        val intent = Intent()
        intent.action = Intent.ACTION_VIEW
        intent.data = Uri.parse("mailto:$id")
        startActivity(intent)
    }

    fun openStore(view: View) {
        //Get url from tag
        val url = view.tag as String
        val intent = Intent()
        intent.action = Intent.ACTION_VIEW
        intent.addCategory(Intent.CATEGORY_BROWSABLE)

        //pass the url to intent data
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    fun triggerRebirth() {
        val packageManager = packageManager
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val componentName = intent!!.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }

    fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
    }
}