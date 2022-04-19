package com.odukle.viddit

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.annotation.Nullable
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.exoplayer2.ExoPlayer
import com.google.gson.JsonParser
import com.odukle.viddit.MainActivity.Companion.main
import com.odukle.viddit.databinding.ItemViewSubredditBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import okhttp3.*
import java.net.SocketTimeoutException
import java.util.*


private const val TAG = "Helper"

class Helper {
    companion object {


        ///////////////////////////////////////////////////////VARIABLES
        val videoList = mutableListOf<Video>()
        var currentPlayer: ExoPlayer? = null
        var backstack = 0
        var subredditAdapter: SubredditAdapter? = null
        var subredditName = ""
        var searchQuery: String? = null
        lateinit var tempPost: Video

        ////////////////////////////////////////////////////////Adapters
        var videoAdapter: VideoAdapter? = null
        var videoAdapterForMain: VideoAdapter? = null
        var searchAdapter: SearchAdapter? = null

        /////////////////////////////////////////////////////////////KEYS
        const val SUBREDDIT = "subreddit"
        const val AFTER = "after"
        const val RV_POSITION = "after"
        const val CALLED_FOR = "calledFor"
        const val FOR_SUBREDDIT = "calledForSR"
        const val FOR_MAIN = "calledForMAIN"

        suspend fun getVideos(
            subreddit: String,
            after: String,
            callCount: Int = 0,
            order: String = "hot",
            time: String = "day"
        ): MutableList<Video> = withContext(IO) {
            if (!isOnline(main)) {
                main.runOnUiThread {
                    main.longToast("Please check your internet connection and retry")
                }
                return@withContext mutableListOf<Video>()
            }
            /////////////////////////////////////////////////////////////////////////////////////SET VISIBILITIES

            val vList = mutableListOf<Video>()
            val uri = Uri.parse("https://www.reddit.com/$subreddit/$order/.json")
                .buildUpon()
                .appendQueryParameter("limit", "100")
                .appendQueryParameter("after", after)
                .appendQueryParameter("t", time)

            if (nsfwAllowed()) uri.appendQueryParameter("restrict_sr", "true")
                .appendQueryParameter("include_over_18", "on")

            try {
                val client = OkHttpClient()
                subreddit.replace(" ", "")
                val request = Request.Builder()
                    .url(uri.toString())
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val json = response.body?.string()
                val jsonObject = JsonParser.parseString(json).asJsonObject
                val posts = jsonObject["data"].asJsonObject["children"].asJsonArray
                posts.forEach { element ->
                    val post = element.asJsonObject["data"].asJsonObject
                    val title = try {
                        post["title"].asString
                    } catch (e: Exception) {
                        "null"
                    }
                    val name = try {
                        post["name"].asString
                    } catch (e: Exception) {
                        "null"
                    }
                    val id = try {
                        post["id"].asString
                    } catch (e: Exception) {
                        "null"
                    }
                    val subreddit = try {
                        post["subreddit"].asString
                    } catch (e: Exception) {
                        "null"
                    }
                    val subredditPrefixed = try {
                        post["subreddit_name_prefixed"].asString
                    } catch (e: Exception) {
                        "null"
                    }
                    val selfText = try {
                        post["selftext"].asString
                    } catch (e: Exception) {
                        "null"
                    }
                    val selfTextHtml = try {
                        post["selftext_html"].asString
                    } catch (e: Exception) {
                        "null"
                    }
                    val author = try {
                        post["author"].asString
                    } catch (e: Exception) {
                        "null"
                    }
                    val flair = try {
                        post["link_flair_css_class"].asString
                    } catch (e: Exception) {
                        "null"
                    }
                    val upVotes = try {
                        post["ups"].asString
                    } catch (e: Exception) {
                        "null"
                    }
                    val comments = try {
                        post["num_comments"].asString
                    } catch (e: Exception) {
                        "null"
                    }
                    val created = post["created"].asLong
                    val isVideo = post["is_video"].asBoolean
                    val video = try {
                        post["media"].asJsonObject["reddit_video"].asJsonObject["hls_url"].asString.replace("amp;", "")
                    } catch (e: Exception) {
                        "null"
                    }
                    val videoDownloadUrl = try {
                        post["media"]
                            .asJsonObject["reddit_video"]
                            .asJsonObject["fallback_url"]
                            .asString.replace("amp;", "")
                    } catch (e: Exception) {
                        "null"
                    }
                    val thumbnail = try {
                        post["preview"]
                            .asJsonObject["images"]
                            .asJsonArray[0]
                            .asJsonObject["source"]
                            .asJsonObject["url"]
                            .asString
                            .replace("amp;", "")
                    } catch (e: Exception) {
                        "null"
                    }

                    val nsfw = try {
                        post["preview"]
                            .asJsonObject["images"]
                            .asJsonArray[0]
                            .asJsonObject["variants"]
                            .asJsonObject["nsfw"]
                            .asJsonObject["source"]
                            .asJsonObject["url"]
                            .asString
                            .replace("amp;", "")
                    } catch (e: Exception) {
                        null
                    }

                    val gif = try {
                        post["preview"]
                            .asJsonObject["images"]
                            .asJsonArray[0]
                            .asJsonObject["variants"]
                            .asJsonObject["gif"]
                            .asJsonObject["source"]
                            .asJsonObject["url"]
                            .asString
                            .replace("amp;", "")
                    } catch (e: Exception) {
                        "null"
                    }
                    val gifmp4 = try {
                        post["preview"]
                            .asJsonObject["images"]
                            .asJsonArray[0]
                            .asJsonObject["variants"]
                            .asJsonObject["mp4"]
                            .asJsonObject["source"]
                            .asJsonObject["url"]
                            .asString
                            .replace("amp;", "")
                    } catch (e: Exception) {
                        "null"
                    }

                    val permalink = try {
                        "https://www.reddit.com/" + post["permalink"].asString
                    } catch (e: Exception) {
                        "null"
                    }

                    if (isVideo || gif != "null") {
                        vList.add(
                            Video(
                                title,
                                name,
                                id,
                                subreddit,
                                subredditPrefixed,
                                selfText,
                                selfTextHtml,
                                author,
                                flair,
                                upVotes,
                                comments,
                                created,
                                isVideo,
                                video,
                                videoDownloadUrl,
                                thumbnail,
                                nsfw,
                                gif,
                                gifmp4,
                                permalink
                            )
                        )
                    }
                }

                if (vList.isEmpty()) {
                    if (callCount < 3) {
                        try {
                            vList.addAll(
                                getVideos(
                                    subreddit,
                                    posts[posts.size() - 1].asJsonObject["data"].asJsonObject["name"].asString,
                                    callCount + 1
                                )
                            )
                        } catch (e: Exception) {
                            main.shortToast("All Videos loaded")
                        }
                    } else {
                        main.shortToast("All Videos loaded")
                    }
                }

            } catch (e: Exception) {
                if (e is SocketTimeoutException) {
                    main.longToast("Network timed out! Please check your internet connection and retry")
                } else {
                    Log.e(TAG, "getVideos: ${e.stackTraceToString()}")
                }
            }

            vList
        }

        suspend fun getSubredditInfo(subreddit: String): SubReddit = withContext(IO) {

            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://www.reddit.com/$subreddit/about.json")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val json = response.body?.string()
                val jsonObject = JsonParser.parseString(json).asJsonObject
                val data = jsonObject["data"].asJsonObject
                val title = try {
                    data["title"].asString
                } catch (e: Exception) {
                    "null"
                }
                val titlePrefixed = try {
                    data["display_name_prefixed"].asString
                } catch (e: Exception) {
                    "null"
                }
                val desc = try {
                    data["public_description"].asString
                } catch (e: Exception) {
                    "null"
                }
                val headerImage = try {
                    data["header_img"].asString.replace("amp;", "")
                } catch (e: Exception) {
                    "null"
                }
                var icon = try {
                    data["icon_img"].asString.replace("amp;", "")
                } catch (e: Exception) {
                    "null"
                }

                if (icon.isEmpty() || icon == "null") {
                    icon = try {
                        data["community_icon"].asString.replace("amp;", "")
                    } catch (e: Exception) {
                        "null"
                    }
                }

                val banner = try {
                    data["banner_background_image"].asString.replace("amp;", "")
                } catch (e: Exception) {
                    "null"
                }

                val subscribers = try {
                    data["subscribers"].asString
                } catch (e: Exception) {
                    "null"
                }
                val fullDesc = try {
                    data["description"].asString
                } catch (e: Exception) {
                    "null"
                }

                return@withContext SubReddit(title, titlePrefixed, desc, headerImage, icon, banner, subscribers, fullDesc)
            } catch (e: Exception) {
                if (e is SocketTimeoutException) {
                    main.longToast("Network timed out! Please check your internet connection and retry")
                } else {
                    Log.e(TAG, "getSubredditInfo: ${e.stackTraceToString()}")
                }
                return@withContext SubReddit("", "", "", "", "", "", "", "")
            }

        }

        suspend fun getUserIcon(user: String) = withContext(IO) {

            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://www.reddit.com/user/$user/about/.json")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val json = response.body?.string()
                val jsonObject = JsonParser.parseString(json).asJsonObject
                val data = jsonObject["data"].asJsonObject
                return@withContext data["icon_img"].asString.replace("amp;", "")
            } catch (e: Exception) {
                if (e is SocketTimeoutException) {
                    main.longToast("Network timed out! Please check your internet connection and retry")
                } else {
                    Log.e(TAG, "getSubreddits: ${e.stackTraceToString()}")
                }
                return@withContext ""
            }
        }

        fun isOnline(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        Log.i("Internet", "NetworkCapabilities.TRANSPORT_CELLULAR")
                        return true
                    }

                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        Log.i("Internet", "NetworkCapabilities.TRANSPORT_WIFI")
                        return true
                    }

                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        Log.i("Internet", "NetworkCapabilities.TRANSPORT_ETHERNET")
                        return true
                    }
                }
            }
            return false
        }

        suspend fun getTopSubreddits() = withContext(IO) {
            searchQuery = null
            val rList = mutableListOf<Pair<String, String>>()
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://parsehub.com/api/v2/projects/tFUsBtX0e0CL/last_ready_run/data?api_key=t0GcPvB4jaai&format=json")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val json = response.body?.string()
                val jsonObject = JsonParser.parseString(json).asJsonObject
                val subreddits = jsonObject["growing"].asJsonArray
                subreddits.forEach {
                    val image = try {
                        it.asJsonObject["image"].asString.replace("amp;", "")
                    } catch (e: java.lang.Exception) {
                        ""
                    }
                    val namePrefixed = it.asJsonObject["url"].asString.replace("https://www.reddit.com/", "").removeSuffix("/")
                    val pair = Pair(image, namePrefixed)
                    rList.add(pair)
                }
            } catch (e: Exception) {
                if (e is SocketTimeoutException) {
                    main.longToast("Network timed out! Please check your internet connection and retry")
                } else {
                    Log.e(TAG, "getTopSubreddits: ${e.stackTraceToString()}")
                }
            }

            rList
        }

        suspend fun getSubreddits(query: String) = withContext(IO) {
            Log.d(TAG, "getSubreddits: called")
            val rList = mutableListOf<Pair<String, String>>()
            val strNsfw = if (nsfwAllowed()) "&restrict_sr=true&include_over_18=on" else ""
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://www.reddit.com/subreddits/search.json?q=$query&$strNsfw")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val json = response.body?.string()
                val jsonObject = JsonParser.parseString(json).asJsonObject
                val data = jsonObject["data"].asJsonObject
                val subreddits = data["children"].asJsonArray
                subreddits.forEach {
                    val subreddit = it.asJsonObject["data"].asJsonObject
                    var image = try {
                        subreddit["icon_img"].asString.replace("amp;", "")
                    } catch (e: Exception) {
                        "null"
                    }

                    if (image.isEmpty() || image == "null") {
                        image = try {
                            subreddit["community_icon"].asString.replace("amp;", "")
                        } catch (e: Exception) {
                            "null"
                        }
                    }
                    val namePrefixed = subreddit["display_name_prefixed"].asString

                    rList.add(Pair(image, namePrefixed))
                }
            } catch (e: Exception) {
                if (e is SocketTimeoutException) {
                    main.longToast("Network timed out! Please check your internet connection and retry")
                } else {
                    Log.e(TAG, "getSubreddits: ${e.stackTraceToString()}")
                }
            }

            rList
        }

        fun imageLoadingListener(binder: ItemViewSubredditBinding): RequestListener<Drawable?> {
            binder.progressThumb.visibility = View.VISIBLE
            binder.ivPlay.visibility = View.GONE
            return object : RequestListener<Drawable?> {
                override fun onLoadFailed(@Nullable e: GlideException?, model: Any?, target: Target<Drawable?>?, isFirstResource: Boolean): Boolean {
                    binder.progressThumb.visibility = View.GONE
                    binder.ivPlay.visibility = View.VISIBLE
                    binder.ivThumb.setImageResource(android.R.drawable.ic_menu_report_image)
                    return true
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    binder.progressThumb.visibility = View.GONE
                    binder.ivPlay.visibility = View.VISIBLE
                    return false
                }
            }
        }

        val snapHelper = object : PagerSnapHelper() {
            override fun findTargetSnapPosition(layoutManager: RecyclerView.LayoutManager, velocityX: Int, velocityY: Int): Int {
                val targetPos = super.findTargetSnapPosition(layoutManager, velocityX, velocityY);
                return targetPos;
            }

            @Nullable
            override fun findSnapView(layoutManager: RecyclerView.LayoutManager): View? {
                val view = super.findSnapView(layoutManager);
                return view;
            }
        }

        fun Float.toDp(): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                this,
                main.resources.displayMetrics
            ).toInt()
        }
    }
}