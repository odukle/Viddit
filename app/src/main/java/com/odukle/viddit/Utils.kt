package com.odukle.viddit

import android.animation.ObjectAnimator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.odukle.viddit.MainActivity.Companion.main
import com.odukle.viddit.databinding.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dean.jraw.Endpoint
import net.dean.jraw.JrawUtils
import net.dean.jraw.RedditClient
import net.dean.jraw.http.HttpRequest
import java.util.*

/////////////////////////////////////////////////////////////KEYS
const val AFTER = "after"
const val CALLED_FOR = "calledFor"
const val FOR_SUBREDDIT = "calledForSR"
const val FOR_MAIN = "calledForMAIN"
const val NSFW = "nsfw"
const val POPULAR = "popular"
const val MEMES = "meme"
const val FUNNY = "funny"
const val WEIRD = "weird"
const val MAIN_FEED = "mf"
const val CUSTOM_FEED = "cf"
private const val TAG = "Utils"
const val ADD = "Add"
const val REMOVE = "Remove"
const val GO_BACK_TO_MAIN_FEED = "Go back to main feed"
const val GO_TO_CUSTOM_FEED = "Go to your custom feeds"
const val RV_POSITION = "rvPosition"
const val FRAGMENT = "fragment"
const val MAIN = "main"
const val SUBREDDIT = "subreddit"
const val SEARCH = "search"

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

fun nsfwAllowed(): Boolean = main.sharedPreferences.getBoolean(NSFW, false)
fun doNotAllowNSFW() = main.sharedPreferences.edit().putBoolean(NSFW, false).apply()
fun allowNSFW() = main.sharedPreferences.edit().putBoolean(NSFW, true).apply()
fun putPref(key: String, data: Any) {
    when (data) {
        is String -> main.sharedPreferences.edit().putString(key, data).apply()
        is Boolean -> main.sharedPreferences.edit().putBoolean(key, data).apply()
        is Long -> main.sharedPreferences.edit().putLong(key, data).apply()
        is Int -> main.sharedPreferences.edit().putInt(key, data).apply()
        is Float -> main.sharedPreferences.edit().putFloat(key, data).apply()
    }
}

fun runIO(block: () -> Unit): Job {
    return CoroutineScope(IO).launch {
        run(block)
    }
}

suspend fun runMain(block: () -> Unit) {
    withContext(Main) {
        run(block)
    }
}

fun ioScope() = CoroutineScope(IO)
fun mainScope() = CoroutineScope(Main)

suspend fun getCustomFeeds(reddit: RedditClient) = withContext(IO) {
    val request = HttpRequest.Builder()
        .secure(true)
        .host("oauth.reddit.com")
        .endpoint(Endpoint.GET_MULTI_MINE)
        .header("Authorization", "bearer ${reddit.authManager.accessToken}")
        .build()

    Log.d(TAG, "getCustomFeeds: ${request.parsedUrl}")
    val res = reddit.request(request)

    res.body
}

suspend fun getMultiRedditAbout(reddit: RedditClient, title: String) = withContext(IO) {
    val multiPath = "user/${JrawUtils.urlEncode(reddit.me().username)}/m/${JrawUtils.urlEncode(title)}"
    val request = HttpRequest.Builder()
        .secure(true)
        .host("oauth.reddit.com")
        .endpoint(Endpoint.GET_MULTI_MULTIPATH, multiPath)
        .header("Authorization", "bearer ${reddit.authManager.accessToken}")
        .build()

    Log.d(TAG, "getSubredditAbout: ${request.parsedUrl}")
    val res = reddit.request(request)

    Log.d(TAG, "getMultiRedditAbout: ${res.body}")
    res.body
}

var subredditToAddOrRemove: String? = null
fun addFeedView(
    multiReddit: MultiReddit,
    bottomSheetDialog: BottomSheetDialog,
    menuBinder: LayoutMenuBinding? = null,
    cfBinder: BottomsheetCustomFeedsBinding? = null,
    mainBinder: FragmentMainBinding? = null,
    searchBinder: FragmentSearchBinding? = null,
    sfBinder: FragmentSubRedditBinding? = null,
    addChipsToSF: Boolean = true
) {
    val iv = ShapeableImageView(main)
    iv.shapeAppearanceModel = iv.shapeAppearanceModel
        .toBuilder()
        .setTopRightCorner(CornerFamily.ROUNDED, 10F)
        .setTopLeftCorner(CornerFamily.ROUNDED, 10F)
        .setBottomRightCorner(CornerFamily.ROUNDED, 10F)
        .setBottomLeftCorner(CornerFamily.ROUNDED, 10F)
        .build()
    val ivParams = LinearLayout.LayoutParams(30f.toDp(), 30f.toDp())
    iv.layoutParams = ivParams

    menuBinder?.let { Glide.with(it.root).load(multiReddit.iconUrl).into(iv) }
        ?: cfBinder?.let { Glide.with(it.root).load(multiReddit.iconUrl).into(iv) }

    val tv = TextView(main)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        tv.typeface = main.resources.getFont(R.font.bold)
    }
    tv.textSize = 20f
    tv.text = multiReddit.displayName
    val tvParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    tvParams.setMargins(10f.toDp(), 0, 0, 0)
    tvParams.gravity = Gravity.CENTER_VERTICAL
    tv.layoutParams = tvParams

    val chip = Chip(main)
    chip.text = multiReddit.displayName
    chip.tag = multiReddit.name
    chip.isCheckable = true

    if (addChipsToSF) {
        mainBinder?.chipGroupCf?.addView(chip) ?: searchBinder?.chipGroupCf?.addView(chip)
        searchBinder?.apply {
            val rList = mutableListOf<Pair<String, String>>()
            if (multiReddit.subreddits.isNotEmpty()) {
                multiReddit.subreddits.forEach { srName ->
                    ioScope().launch {
                        val subReddit = Helper.getSubredditInfo("r/$srName")
                        rList.add(Pair(subReddit.icon, subReddit.titlePrefixed))
                    }
                }
            }


            chipGroupCf.show()
            chipGroup.clearCheck()
            chipGroup.hide()
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    chip.bounce()
                    runAfter(100) {
                        scrollViewChipsCf.smoothScrollTo((chip.x / 1.5).toInt(), 0)
                    }
                    val fragment = getCurrentFragment() as SearchFragment
                    fragment.populateRv(rList, false)
                }
            }
        }
    }

    val ll = LinearLayout(main)
    ll.orientation = LinearLayout.HORIZONTAL
    val llParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    ll.setPadding(0, 10f.toDp(), 0, 10f.toDp())
    val outValue = TypedValue()
    main.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
    ll.foreground = main.getDrawable(outValue.resourceId)
    ll.layoutParams = llParams

    ll.addView(iv)
    ll.addView(tv)

    ll.setOnClickListener {
        val reddit = getReddit()
        if (reddit != null) {
            mainBinder?.apply {
                bottomSheetDialog.hide()
                chipGroup.clearCheck()
                chipGroup.hide()
                chipGroupCf.show()
                chipGroupCf.check(chip.id)
            } ?: searchBinder?.apply {
                if (cfBinder == null) {
                    bottomSheetDialog.hide()
                    menuBinder?.layoutFeeds?.hide()
                    menuBinder?.createNewFeed?.hide()
                    chipGroup.clearCheck()
                    chipGroup.hide()
                    chipGroupCf.show()
                    chipDeleteFeed.show()
                    chipGroupCf.check(chip.id)
                    tvQuery.text = "Your custom feeds"
                } else {
                    ioScope().launch {
                        if (subredditToAddOrRemove != null) {
                            addSubRedditToCf(reddit, multiReddit.name)
                            mainScope().launch {
                                bottomSheetDialog.hide()
                            }
                        }
                    }
                }
            } ?: sfBinder.apply {
                ioScope().launch {
                    if (subredditToAddOrRemove != null) {
                        addSubRedditToCf(reddit, multiReddit.name)
                        runMain {
                            bottomSheetDialog.hide()
                        }
                    }
                }
            }
        }
    }

    menuBinder?.layoutFeeds?.addView(ll) ?: cfBinder?.layoutCf?.addView(ll)
}

fun addSubRedditToCf(reddit: RedditClient, name: String) {
    if (subredditToAddOrRemove != null) {
        main.shortToast("Adding..")
        reddit.me().multi(name).addSubreddit(subredditToAddOrRemove!!)
        main.shortToast("Added successfully 🎉")
        subredditToAddOrRemove = null
    }
}

fun getCurrentFragment() = main.supportFragmentManager.findFragmentById(R.id.container)
fun getOrientation() = main.resources.configuration.orientation
fun getReddit() = main.redditHelper.reddit

const val blockCharacterSet = "~`!@#$%^&*()_-+=|\\}]{[:;'\"?/>.<,₹" //Special characters to block
val filter = InputFilter { source, _, _, _, _, _ ->
    if (source != null && blockCharacterSet.contains("" + source)) {
        ""
    } else null
}

fun View.bounce() {
    ObjectAnimator.ofFloat(this, "scaleX", 1F, 0.7F, 1F).setDuration(500).start()
    ObjectAnimator.ofFloat(this, "scaleY", 1F, 0.7F, 1F).setDuration(500).start()
}