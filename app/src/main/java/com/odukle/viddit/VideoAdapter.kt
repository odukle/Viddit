package com.odukle.viddit

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.imageview.ShapeableImageView
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.odukle.viddit.Helper.Companion.backstack
import com.odukle.viddit.Helper.Companion.currentPlayer
import com.odukle.viddit.Helper.Companion.getSubredditInfo
import com.odukle.viddit.Helper.Companion.getUserIcon
import com.odukle.viddit.Helper.Companion.getVideos
import com.odukle.viddit.Helper.Companion.isOnline
import com.odukle.viddit.MainActivity.Companion.main
import com.odukle.viddit.databinding.ItemViewVideoBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request


private const val TAG = "VideoAdapter"

class VideoAdapter(
    val list: MutableList<Video>,
    private val subredditPrefixed: String,
    private val fragment: MainFragment,
    private val order: String = "hot",
    private val time: String = "day",
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    inner class VideoViewHolder(val binder: ItemViewVideoBinding) : RecyclerView.ViewHolder(binder.root) {
        lateinit var player: ExoPlayer
    }

    private lateinit var attachedHolder: VideoViewHolder
    private lateinit var detachedHolder: VideoViewHolder
    private var tempPlayer: ExoPlayer? = null
    private var mute = false
    val unShuffledList = mutableListOf<Video>()
    var playWhenReady = false
    private var allowPlay = true

    init {
        unShuffledList.addAll(list)
        runAfter(500) {
            if (fragment.rvPosition > itemCount / 2) {
                CoroutineScope(IO).launch {
                    try {
                        loadMoreData(unShuffledList[itemCount - 1].name)
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binder = DataBindingUtil.inflate<ItemViewVideoBinding>(LayoutInflater.from(parent.context), R.layout.item_view_video, parent, false)
        return VideoViewHolder(binder)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val post = list[position]
        holder.binder.apply {

            /////////////////////////////////////////////////////////////////////SET VIEW DATA

            tvComments.text = post.comments
            tvUpvotes.text = post.upVotes
            tvUser.text = "u/" + post.author + " • " + post.created.toTimeAgo()
            tvTitle.text = post.title.replace("amp;", "")
            tvFullTitle.text = post.title.replace("amp;", "")
            tvSubreddit.text = "r/" + post.subreddit
            shimmerIcon.visibility = View.VISIBLE
            shimmerUserIcon.visibility = View.VISIBLE
            ivIcon.visibility = View.GONE
            ivUserIcon.visibility = View.GONE


            CoroutineScope(IO).launch {
                val subreddit = if (isOnline(main)) {
                    getSubredditInfo(post.subredditPrefixed)
                } else {
                    main.longToast("No internet 😔")
                    SubReddit("", "", "", "", "", "", "", "")
                }
                val userIcon = if (isOnline(main)) {
                    getUserIcon(post.author)
                } else {
                    main.longToast("No internet 😔")
                    ""
                }
                withContext(Main) {
                    Glide.with(root)
                        .load(subreddit.icon)
                        .placeholder(R.drawable.ic_reddit)
                        .into(ivIcon)

                    Glide.with(root)
                        .load(userIcon)
                        .placeholder(R.drawable.ic_reddit_user)
                        .into(ivUserIcon)

                    shimmerIcon.visibility = View.GONE
                    ivIcon.visibility = View.VISIBLE
                    shimmerUserIcon.visibility = View.GONE
                    ivUserIcon.visibility = View.VISIBLE

                    arrayOf(tvSubreddit, ivIcon).forEach {
                        it.setOnClickListener {
                            val fragmentTxn = main.supportFragmentManager.beginTransaction()
                            fragmentTxn.replace(R.id.container, SubRedditFragment.newInstance(subreddit))
                            fragmentTxn.addToBackStack("${backstack++}")
                            fragmentTxn.commit()
                        }
                    }

                    arrayOf(ivComments, tvComments).forEach {
                        it.setOnClickListener {
                            val bottomSheetDialog = BottomSheetDialog(main)
                            fragment.commentsBinder = DataBindingUtil.inflate(
                                LayoutInflater.from(main),
                                R.layout.layout_comments,
                                null,
                                false
                            )
                            bottomSheetDialog.setContentView(fragment.commentsBinder.root)
                            bottomSheetDialog.show()
                            getComments(post.permalink)
                        }
                    }

                    ivDownload.setOnClickListener {
                        if (main.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                            Log.d(TAG, "requesting permission")
                            Helper.tempPermalink = post.permalink
                            Helper.tempVideoUrl = post.videoDownloadUrl
                            Helper.tempName = post.name
                            main.requestPermissions(
                                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                111
                            )
                        } else {
                            startDownloading(post.permalink, post.videoDownloadUrl, post.name)
                        }
                    }
                }
            }

            ////////////////////////////////////////////////////////////////// SET ON CLICK LISTENERS

            //// controller visibility listener
            playerView.setControllerVisibilityListener {
                if (it == View.VISIBLE) {
                    fragment.binder.layoutToolbar.visibility = View.VISIBLE
                    btnTogglePlay.visibility = View.VISIBLE
                    if (post.nsfw != null && nsfwAllowed()) uncheckNsfw.visibility = View.VISIBLE
                    btnMute.visibility = View.VISIBLE
                } else {
                    fragment.binder.layoutToolbar.visibility = View.GONE
                    btnTogglePlay.visibility = View.GONE
                    uncheckNsfw.visibility = View.GONE
                    btnMute.visibility = View.GONE
                }
            }

            tvTitle.setOnClickListener {
                tvTitle.visibility = View.GONE
                tvFullTitle.visibility = View.VISIBLE
            }

            tvFullTitle.setOnClickListener {
                tvTitle.visibility = View.VISIBLE
                tvFullTitle.visibility = View.GONE
            }

            ivShare.setOnClickListener {
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, post.permalink)
                }

                main.startActivity(intent)
            }

            arrayOf(ivUpvotes, tvUpvotes).forEach {
                it.setOnClickListener {
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    customTabsIntent.launchUrl(main, Uri.parse(post.permalink))
                }
            }

            btnWatchAnyway.setOnClickListener {
                layoutNsfw.visibility = View.GONE
                holder.player.play()
                if (checkNsfw.isChecked) {
                    allowNSFW()
                }
            }

            uncheckNsfw.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    doNotAllowNSFW()
                    checkNsfw.isChecked = false
                    layoutNsfw.visibility = View.VISIBLE
                    holder.player.pause()
                } else allowNSFW()
            }
        }
    }

    override fun onViewAttachedToWindow(holder: VideoViewHolder) {
        val post = list[holder.absoluteAdapterPosition]
        val player = fragment.pool.acquire()
        holder.player = player
        player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
        ////////////////////////////////////////////////////////////////////////////SET EXOPLAYER

        holder.binder.apply {
            playerView.controllerAutoShow = false
            playerView.player = null
            playerView.player = player
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(post.video))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = false

            if (mute) {
                player.volume = 0f
                btnMute.setImageResource(R.drawable.ic_mute)
            } else {
                player.volume = 1f
                btnMute.setImageResource(R.drawable.ic_volume)
            }

        }

        ///////////////////////////////////////////////////////////////////////////

        if (post.nsfw != null && !nsfwAllowed()) {
            holder.binder.layoutNsfw.visibility = View.VISIBLE
            allowPlay = false
        } else {
            holder.binder.layoutNsfw.visibility = View.GONE
            if (post.nsfw != null) holder.binder.playerView.performClick()
            allowPlay = true
        }

        attachedHolder = holder
        val lastPost = unShuffledList[itemCount - 1]
        if ((holder.absoluteAdapterPosition == 0 || playWhenReady) && allowPlay) holder.player.play()
        tempPlayer = currentPlayer
        currentPlayer = holder.player

        when (holder.absoluteAdapterPosition) {
            0 -> {
                fragment.binder.ivGoToTop.visibility = View.GONE
                fragment.binder.ivReload.visibility = View.VISIBLE
                fragment.binder.ivReload.bringToFront()
            }

            (itemCount * 4) / 5 -> {
                CoroutineScope(IO).launch {
                    loadMoreData(lastPost.name)
                }
            }

            itemCount - 1 -> {
                fragment.binder.refreshLayout.isRefreshing = true
            }

            else -> {
                fragment.binder.ivGoToTop.visibility = View.VISIBLE
                fragment.binder.ivGoToTop.bringToFront()
                fragment.binder.ivReload.visibility = View.GONE
            }

        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    holder.binder.btnTogglePlay.setImageResource(R.drawable.ic_pause)
                } else {
                    holder.binder.btnTogglePlay.setImageResource(R.drawable.ic_round_play_circle_filled_24)
                }
                super.onIsPlayingChanged(isPlaying)
            }
        })

        holder.binder.apply {
            btnTogglePlay.setOnClickListener {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }

            btnMute.setOnClickListener {
                if (player.volume > 0f) {
                    player.volume = 0f
                    btnMute.setImageResource(R.drawable.ic_mute)
                    mute = true
                } else {
                    player.volume = 1f
                    btnMute.setImageResource(R.drawable.ic_volume)
                    mute = false
                }
            }
        }

        super.onViewAttachedToWindow(holder)
    }

    override fun onViewDetachedFromWindow(holder: VideoViewHolder) {
        fragment.binder.layoutToolbar.visibility = View.GONE
        detachedHolder = holder
        if (attachedHolder == detachedHolder) {
            currentPlayer = tempPlayer
        }

        try {
            if (allowPlay) currentPlayer?.play()
        } catch (e: Exception) {
        }
        holder.player.apply {
            pause()
            fragment.pool.release(this)
        }
        super.onViewDetachedFromWindow(holder)
    }

    override fun getItemCount(): Int {
        return unShuffledList.size
    }

    suspend fun loadMoreData(lastPost: String, shuffle: Boolean = false, callCount: Int = 0, refresh: Boolean = false): Unit = withContext(IO) {
        try {
            val oldSize = itemCount
            if (callCount < 2) {
                val vList = getVideos(subredditPrefixed, lastPost, 0, order, time)
                unShuffledList.addAll(vList)
                list.addAll(vList)

                withContext(Main) {
                    if (!shuffle && !refresh) fragment.refreshAdapter(oldSize, itemCount)
                }

                loadMoreData(vList[vList.size - 1].name, shuffle, callCount + 1)
            } else {
                withContext(Main) {
                    if (shuffle || refresh) fragment.refreshAdapter(oldSize, itemCount)
                }
            }
        } catch (e: Exception) {
        }

    }

    fun shuffle() {
        Log.d(TAG, "shuffle: called")
        Log.d(TAG, "shuffle: ${list.size}")
        Log.d(TAG, "shuffle: ${unShuffledList.size}")
        list.shuffle()
        notifyItemRangeChanged(0, itemCount)
    }

    companion object {
        @SuppressLint("Range")
        fun startDownloading(permalink: String, fallbackUrl: String, name: String): Long {
            main.shortToast("Download started")
            val audioUrl = fallbackUrl.substring(0, fallbackUrl.indexOf("DASH_")) + "DASH_audio.mp4?source=fallback"
            val url = "https://sd.redditsave.com/download.php?permalink=$permalink&video_url=$fallbackUrl&audio_url=$audioUrl"

            val request = DownloadManager.Request(Uri.parse(url))
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                .setDescription("Downloading reddit video...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "Viddit/$name.mp4")

            val manager = main.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val mId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == mId) {
                        main.longToast("Downloaded $name.mp4 to Movies/Viddit")
                    }
                }

            }

            main.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            return id
        }
    }

    private fun getComments(permalink: String) {

        if (!isOnline(main)) {
            main.longToast("No internet 😔")
            return
        }

        fragment.commentsBinder.shimmerRc.visibility = View.VISIBLE
        CoroutineScope(IO).launch {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("$permalink.json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val json = response.body?.string()
            val jsonObject = JsonParser.parseString(json).asJsonArray[1].asJsonObject
            val commentsArray = jsonObject["data"].asJsonObject["children"].asJsonArray
            withContext(Main) {

                val childLayout2 = LinearLayout(main)
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.setMargins(10, 10, 10, 10)
                childLayout2.layoutParams = params

                addCommentView(commentsArray, 10F, fragment.commentsBinder.commentsLayout, true)
            }

            withContext(Main) {
                fragment.commentsBinder.shimmerRc.visibility = View.GONE
            }
        }
    }

    private fun addCommentView(commentsArray: JsonArray?, margin: Float, parentLayout: LinearLayout, isMainLayout: Boolean = false) {
        commentsArray?.forEach { comment ->
            var replies: JsonArray? = null
            val layout = LinearLayout(main)
            layout.layoutTransition = LayoutTransition()
            layout.orientation = LinearLayout.VERTICAL
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

            val r = main.resources
            val pxLeft = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                margin,
                r.displayMetrics
            ).toInt()

            val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                10F,
                r.displayMetrics
            ).toInt()
            params.setMargins(pxLeft, px * 2, 0, px)
            layout.layoutParams = params
            layout.background = main.getDrawable(R.drawable.reddit_indent)
            ///////////////////////ADD LAYOUT ONLY AFTER BODY IS NOT NULL
            ////////////////////////////////////////////////////////////////////////
            if (comment.isJsonObject) {
                val data = comment.asJsonObject["data"].asJsonObject
                val author = try {
                    data["author"].asString
                } catch (e: Exception) {
                    "null"
                }
                val dateCreated = try {
                    data["created_utc"].asLong
                } catch (e: Exception) {
                    0L
                }
                val score = try {
                    data["score"].asString
                } catch (e: Exception) {
                    "null"
                }
                val body = try {
                    data["body"].asString
                } catch (e: Exception) {
                    return
                }

                parentLayout.addView(layout)
                ///////////////////////////////////////////////////// ADD VIEW TO THE LAYOUT
                if (author != "null") {
                    val tvAuthor = TextView(main)
                    tvAuthor.setPadding(px, 0, 0, 0)
                    tvAuthor.text = "u/" + author + " • " + dateCreated.toTimeAgo()
                    tvAuthor.textSize = 12F
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        tvAuthor.typeface = main.resources.getFont(R.font.bold)
                    }

                    val iv = ShapeableImageView(main)
                    val ivParams = LinearLayout.LayoutParams(24F.toDp(), 24F.toDp())
                    ivParams.setMargins(px, 0, 0, 0)
                    iv.layoutParams = ivParams
                    iv.shapeAppearanceModel = iv.shapeAppearanceModel
                        .toBuilder()
                        .setAllCornerSizes(24F)
                        .build()
                    CoroutineScope(IO).launch {
                        val icon = getUserIcon(author)
                        main.runOnUiThread {
                            Glide.with(main)
                                .load(icon)
                                .into(iv)
                        }
                    }

                    val ll = LinearLayout(main)
                    ll.setVerticalGravity(Gravity.CENTER_VERTICAL)
                    ll.orientation = LinearLayout.HORIZONTAL

                    ll.addView(iv)
                    ll.addView(tvAuthor)

                    if (author.lowercase() != "automoderator" && !author.lowercase().endsWith("bot")) {
                        layout.addView(ll)
                    }
                }

                if (body != "null") {
                    val tvBody = TextView(main)
                    tvBody.setPadding(px, 0, 0, 0)
                    tvBody.text = body.replace("amp;", "").trim()
                    tvBody.textSize = 18F
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        tvBody.typeface = main.resources.getFont(R.font.bold)
                    }
                    if (author.lowercase() != "automoderator" && !author.lowercase().endsWith("bot")) {
                        layout.addView(tvBody)
                    }

                    tvBody.setOnLongClickListener {
                        layout.children.forEach { child ->
                            if (layout.indexOfChild(child) > 1
                                && layout.childCount > 3
                                && layout.indexOfChild(child) != layout.childCount - 1
                            ) {
                                try {
                                    child.apply {
                                        visibility = if (isVisible) {
                                            (layout[layout.childCount - 1] as TextView).text = "Show replies..."
                                            (layout[layout.childCount - 1] as TextView).setTextColor(main.getColor(android.R.color.holo_green_light))
                                            View.GONE
                                        } else {
                                            (layout[layout.childCount - 1] as TextView).text = "Hide replies"
                                            (layout[layout.childCount - 1] as TextView).setTextColor(main.getColor(android.R.color.holo_red_dark))
                                            View.VISIBLE
                                        }
                                    }
                                } catch (e: Exception) {
                                }
                            }
                        }

                        true
                    }
                }

                if (score != "null") {
                    val tvScore = TextView(main)
                    tvScore.setPadding(px, px, 0, 0)
                    tvScore.text = "upvotes: " + score
                    tvScore.textSize = 12F
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        tvScore.typeface = main.resources.getFont(R.font.bold)
                    }
                    if (author.lowercase() != "automoderator" && !author.lowercase().endsWith("bot")) {
                        layout.addView(tvScore)
                    }
                }

                /////////////////////////////////////////////////////
                replies = try {
                    data["replies"].asJsonObject["data"].asJsonObject["children"].asJsonArray
                } catch (e: java.lang.Exception) {
                    null
                }

                addCommentView(replies, 20F, layout)
            }

            if (replies != null) {
                if (!isMainLayout) {
                    val tv = TextView(main)
                    tv.setPadding(px, px, 0, 0)
                    tv.text = "Hide replies"
                    tv.textSize = 12F
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        tv.typeface = main.resources.getFont(R.font.bold_italic)
                    }
                    tv.setTextColor(main.getColor(android.R.color.holo_red_light))

                    parentLayout.addView(tv)

                    tv.setOnClickListener {
                        layout.apply {
                            visibility = if (isVisible) {
                                tv.text = "Show replies..."
                                tv.setTextColor(main.getColor(android.R.color.holo_green_light))
                                View.GONE
                            } else {
                                tv.text = "Hide replies"
                                tv.setTextColor(main.getColor(android.R.color.holo_red_light))
                                View.VISIBLE
                            }
                        }
                    }
                }

            }
        }
    }
}