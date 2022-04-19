package com.odukle.viddit

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.odukle.viddit.MainActivity.Companion.main
import java.util.*

val NSFW = "nsfw"

fun Float.toDp(): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        main.resources.displayMetrics
    ).toInt()
}

fun runAfter(ms: Long, block: () -> Unit) {
    val handler = Handler(Looper.getMainLooper())
    handler.postDelayed(
        block,
        ms
    )
}

fun View.show() {
    main.runOnUiThread {
        this.visibility = View.VISIBLE
    }
}
fun View.hide() {
    main.runOnUiThread {
        this.visibility = View.GONE
    }
}

fun Long.toTimeAgo(): String {

    val hr = ((Calendar.getInstance().timeInMillis - this * 1000L) / 3600000L)

    return when (hr) {
        0L -> ((Calendar.getInstance().timeInMillis - this * 1000L) / 60000L).toString() + "m"
        in 1 until 24 -> hr.toString() + "h"
        in 24 until 168 -> (hr / 24).toString() + "d"
        in 168 until Integer.MAX_VALUE -> (hr / (24 * 7)).toString() + "w"
        else -> "xxx"
    }
}

fun RecyclerView?.getCurrentPosition(): Int {
    return (this?.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
}

fun log(c: Class<Any>, method: Function<Any>, text: String) {
    Log.d(c::class.simpleName, "$method: $text")
}

fun nsfwAllowed(): Boolean = MainActivity.main.sharedPreferences.getBoolean(NSFW, false)
fun doNotAllowNSFW() = MainActivity.main.sharedPreferences.edit().putBoolean(NSFW, false).apply()
fun allowNSFW() = MainActivity.main.sharedPreferences.edit().putBoolean(NSFW, true).apply()
fun putPref(key: String, data: Any) {
    when (data) {
        is String -> MainActivity.main.sharedPreferences.edit().putString(key, data).apply()
        is Boolean -> MainActivity.main.sharedPreferences.edit().putBoolean(key, data).apply()
        is Long -> MainActivity.main.sharedPreferences.edit().putLong(key, data).apply()
        is Int -> MainActivity.main.sharedPreferences.edit().putInt(key, data).apply()
        is Float -> MainActivity.main.sharedPreferences.edit().putFloat(key, data).apply()
    }
}