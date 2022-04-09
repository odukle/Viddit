package com.odukle.viddit

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.odukle.viddit.Helper.Companion.FOR_MAIN
import com.odukle.viddit.Helper.Companion.currentPlayer
import com.odukle.viddit.Helper.Companion.handler
import com.odukle.viddit.Helper.Companion.tempName
import com.odukle.viddit.Helper.Companion.tempPermalink
import com.odukle.viddit.Helper.Companion.tempVideoUrl
import com.odukle.viddit.Helper.Companion.videoList
import com.odukle.viddit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    lateinit var binder: ActivityMainBinding
    private lateinit var toast: Toast

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binder = DataBindingUtil.setContentView(this, R.layout.activity_main)
        main = this
        handler = Handler(Looper.myLooper()!!)
        toast = Toast(this)
        init()
    }

    private fun init() {
        videoList.clear()
        currentPlayer = null
        val fragmentTxn = supportFragmentManager.beginTransaction()
        fragmentTxn.replace(R.id.container, MainFragment.newInstance("r/popular", "", 0, FOR_MAIN))
        fragmentTxn.commit()
    }

    fun longToast(text: String) {
        runOnUiThread {
            toast.cancel()
            toast = Toast(this)
            toast.setText(text)
            toast.duration = Toast.LENGTH_LONG
            toast.show()
        }
    }

    fun shortToast(text: String) {
        runOnUiThread {
            toast.cancel()
            toast = Toast(this)
            toast.setText(text)
            toast.duration = Toast.LENGTH_SHORT
            toast.show()
        }
    }

    override fun onBackPressed() {

        val count = supportFragmentManager.backStackEntryCount
        if (count == 0) {
            finish()
        } else {
            supportFragmentManager.popBackStack()
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
                    VideoAdapter.startDownloading(tempPermalink, tempVideoUrl, tempName)
                } else {
                    shortToast("Permission denied")
                }
            }

        }
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
}