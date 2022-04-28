package com.odukle.viddit

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.util.Linkify
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
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
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.odukle.viddit.Helper.Companion.backstack
import com.odukle.viddit.Helper.Companion.currentPlayer
import com.odukle.viddit.Helper.Companion.getGifMp4
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
import net.dean.jraw.RedditClient
import net.dean.jraw.models.Submission
import net.dean.jraw.models.VoteDirection
import net.dean.jraw.pagination.DefaultPaginator
import okhttp3.OkHttpClient
import okhttp3.Request


private const val TAG = "VideoAdapter"

class VideoAdapter(
    val list: MutableList<Video>,
    private val subredditPrefixed: String,
    private val order: String = "hot",
    private val time: String = "day",
    private val pages: DefaultPaginator<Submission>? = null,
    private val passedFragment: MainFragment? = null
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    inner class VideoViewHolder(val binder: ItemViewVideoBinding) : RecyclerView.ViewHolder(binder.root) {
        lateinit var player: ExoPlayer
    }

    private lateinit var attachedHolder: VideoViewHolder
    private lateinit var detachedHolder: VideoViewHolder
    var fragment: MainFragment
    lateinit var binder: ItemViewVideoBinding
    private var upperHolderPos = -1
    var middleHolderPos = 0
    private var lowerHolderPos = 1
    private lateinit var holderPosTracker: Runnable
    private var tempPlayer: ExoPlayer? = null
    private var mute = false
    val unShuffledList = mutableListOf<Video>()
    var playWhenReady = false
    var calledFor = POPULAR
    var content = MAIN_FEED
    var loadGifsExternally = false
    private var allowPlay = true

    init {
        fragment = passedFragment ?: getCurrentFragment() as MainFragment
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
        binder = DataBindingUtil.inflate<ItemViewVideoBinding>(LayoutInflater.from(parent.context), R.layout.item_view_video, parent, false)
        return VideoViewHolder(binder)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {

        if (!fragment.isVisible) fragment = getCurrentFragment() as MainFragment
        val post = list[position]
        val reddit = main.redditHelper.reddit
        holder.binder.apply {

            /////////////////////////////////////////////////////////////////////SET VIEW DATA

            tvComments.text = post.comments
            tvUpvotes.text = post.upVotes
            tvUser.text = "u/" + post.author + " • " + post.created.toTimeAgo()
            tvTitle.text = post.title.replace("amp;", "")
            tvFullTitle.text = post.title.replace("amp;", "")
            tvSubreddit.text = "r/" + post.subreddit
            shimmerIcon.show()
            shimmerUserIcon.show()
            ivIcon.hide()
            ivUserIcon.hide()

            CoroutineScope(IO).launch {
                if (reddit != null) {
                    val submission = reddit.submission(post.id).inspect()
                    if (submission.vote == VoteDirection.UP) {
                        main.runOnUiThread {
                            ivUpvotes.setImageResource(R.drawable.ic_upvote_red)
                            tvUpvotes.setTextColor(main.getColor(R.color.orange))
                        }
                    } else {
                        main.runOnUiThread {
                            ivUpvotes.setImageResource(R.drawable.ic_upvote)
                            tvUpvotes.setTextColor(main.getColor(R.color.white))
                        }
                    }
                }

                val (subreddit, userSubreddit) = if (isOnline(main)) {
                    Pair(getSubredditInfo(post.subredditPrefixed), getSubredditInfo("u/${post.author}", true))
                } else {
                    main.longToast("No internet 😔")
                    Pair(
                        SubReddit("", "", "", "", "", "", "", ""),
                        SubReddit("", "", "", "", "", "", "", "")
                    )
                }
                val userIcon = if (isOnline(main)) {
                    getUserIcon(post.author)
                } else {
                    main.longToast("No internet 😔")
                    ""
                }
                withContext(Main) {
                    try {
                        Glide.with(root)
                            .load(subreddit.icon)
                            .placeholder(R.drawable.ic_reddit)
                            .into(ivIcon)

                        Glide.with(root)
                            .load(userIcon)
                            .placeholder(R.drawable.ic_reddit_user)
                            .into(ivUserIcon)
                    } catch (e: Exception) {
                    }

                    shimmerIcon.hide()
                    shimmerUserIcon.hide()
                    ivIcon.show()
                    ivUserIcon.show()

                    arrayOf(tvSubreddit, ivIcon).forEach {
                        it.setOnClickListener {
                            fragment.binder.chipGroup.clearCheck()
                            val fragmentTxn = main.supportFragmentManager.beginTransaction()
                            fragmentTxn.replace(R.id.container, SubRedditFragment.newInstance(subreddit))
                            fragmentTxn.addToBackStack("${backstack++}")
                            fragmentTxn.commit()
                        }
                    }

                    layoutUser.setOnClickListener {
                        fragment.binder.chipGroup.clearCheck()
                        val fragmentTxn = main.supportFragmentManager.beginTransaction()
                        fragmentTxn.replace(R.id.container, SubRedditFragment.newInstance(userSubreddit, true))
                        fragmentTxn.addToBackStack("${backstack++}")
                        fragmentTxn.commit()
                    }

                    commentsLayout.setOnClickListener {
                        commentsLayout.bounce()
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


                    saveLayout.setOnClickListener {
                        saveLayout.bounce()
                        if (main.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                            Log.d(TAG, "requesting permission")
                            Helper.tempPost = post
                            main.requestPermissions(
                                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                111
                            )
                        } else {
                            startDownloading(post, this@apply)
                        }
                    }
                }
            }

            ////////////////////////////////////////////////////////////////// SET ON CLICK LISTENERS

            //// controller visibility listener
            playerView.setControllerVisibilityListener {
                val detailsParams = layoutPostDetails.layoutParams
                if (it == View.VISIBLE) {
                    if (getOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
                        fragment.binder.layoutChips?.show()
                    }
                    fragment.binder.layoutToolbar.show()
                    btnTogglePlay.show()
                    if (post.nsfw != null && nsfwAllowed()) uncheckNsfw.show()
                    btnMute.show()
                    (detailsParams as RelativeLayout.LayoutParams).setMargins(20F.toDp(), 0, 20F.toDp(), 80F.toDp())
                    layoutPostDetails.layoutParams = detailsParams
                } else {
                    if (getOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
                        fragment.binder.layoutChips?.hide()
                    }
                    fragment.binder.layoutToolbar.hide()
                    btnTogglePlay.hide()
                    uncheckNsfw.hide()
                    btnMute.hide()
                    (detailsParams as RelativeLayout.LayoutParams).setMargins(20F.toDp(), 0, 20F.toDp(), 20F.toDp())
                    layoutPostDetails.layoutParams = detailsParams
                }
            }

            tvTitle.setOnClickListener {
                tvTitle.hide()
                tvFullTitle.show()
            }

            tvFullTitle.setOnClickListener {
                tvTitle.show()
                tvFullTitle.hide()
            }

            shareLayout.setOnClickListener {
                shareLayout.bounce()
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, post.permalink)
                }

                main.startActivity(intent)
            }

            upvoteLayout.setOnClickListener {
                vote(VoteDirection.UP, reddit, post.id, this)
            }

            downvoteLayout.setOnClickListener {
                vote(VoteDirection.DOWN, reddit, post.id, this)
            }

            btnWatchAnyway.setOnClickListener {
                btnWatchAnyway.bounce()
                layoutNsfw.hide()
                holder.player.play()
                if (checkNsfw.isChecked) {
                    allowNSFW()
                }
            }

            uncheckNsfw.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    doNotAllowNSFW()
                    checkNsfw.isChecked = false
                    layoutNsfw.show()
                    holder.player.pause()
                } else allowNSFW()
            }
        }
    }

    override fun onViewAttachedToWindow(holder: VideoAdapter.VideoViewHolder) {
        if (!fragment.isVisible) fragment = getCurrentFragment() as MainFragment
        if (holder.absoluteAdapterPosition == lowerHolderPos) animateViewHolder(holder.itemView, true)
        else animateViewHolder(holder.itemView, false)
        holderPosTracker = Runnable {
            middleHolderPos = holder.absoluteAdapterPosition
            upperHolderPos = middleHolderPos - 1
            lowerHolderPos = middleHolderPos + 1
        }

        val post = list[holder.absoluteAdapterPosition]
        val player = fragment.pool.acquire()
        holder.player = player
        player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
        ////////////////////////////////////////////////////////////////////////////SET EXOPLAYER

        holder.binder.apply {
            playerView.controllerAutoShow = false
            playerView.player = null
            playerView.player = player

            if (post.isVideo) {
                val uri = post.video
                val mimeType = MimeTypes.APPLICATION_M3U8
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(uri))
                    .setMimeType(mimeType)
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = false
            } else {
                if (loadGifsExternally) {
                    ioScope().launch {
                        val pair = getGifMp4(post.permalink)
                        val gifMp4 = pair.second
                        mainScope().launch {
                            val mimeType = MimeTypes.APPLICATION_MP4
                            val mediaItem = MediaItem.Builder()
                                .setUri(Uri.parse(gifMp4))
                                .setMimeType(mimeType)
                                .build()
                            player.setMediaItem(mediaItem)
                            player.prepare()
                            player.playWhenReady = true
                        }
                    }
                } else {
                    val uri = post.gifMp4
                    val mimeType = MimeTypes.APPLICATION_MP4
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(uri))
                        .setMimeType(mimeType)
                        .build()
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playWhenReady = false
                }
            }



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
            holder.binder.layoutNsfw.show()
            allowPlay = false
        } else {
            holder.binder.layoutNsfw.hide()
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
                fragment.binder.ivGoToTop.hide()
                fragment.binder.ivReload.show()
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
                fragment.binder.ivGoToTop.show()
                fragment.binder.ivGoToTop.bringToFront()
                fragment.binder.ivReload.hide()
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

    override fun onViewDetachedFromWindow(holder: VideoAdapter.VideoViewHolder) {
        if (holder.absoluteAdapterPosition == middleHolderPos) holderPosTracker.run()
        fragment.binder.layoutToolbar.hide()
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

    ///////////////////////////////////////////////////////////////////////////METHODS

    private fun animateViewHolder(view: View, isNext: Boolean) {
        if (isNext) {
            view.top = 0
            ObjectAnimator.ofFloat(view, "scaleX", 0.7F, 1F).setDuration(500).start()
            ObjectAnimator.ofFloat(view, "scaleY", 0.7F, 1F).setDuration(500).start()
        } else {
            ObjectAnimator.ofFloat(view, "scaleX", 1.3F, 1F).setDuration(500).start()
            ObjectAnimator.ofFloat(view, "scaleY", 1.3F, 1F).setDuration(500).start()
        }
    }

    suspend fun loadMoreData(lastPost: String, shuffle: Boolean = false, callCount: Int = 0, refresh: Boolean = false): Unit = withContext(IO) {
        try {
            val oldSize = itemCount
            if (callCount < 2) {
                val vList = if (subredditPrefixed.isNotEmpty()) getVideos(subredditPrefixed, lastPost, 0, order, time)
                else fragment.getVideosFromPages(pages!!)
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

    private fun getComments(permalink: String) {

        if (!isOnline(main)) {
            main.longToast("No internet 😔")
            return
        }

        fragment.commentsBinder.shimmerRc.show()
        CoroutineScope(IO).launch {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("$permalink.json?raw_json=1")
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
                fragment.commentsBinder.shimmerRc.hide()
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
                    tvAuthor.setTextIsSelectable(true)
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
                    tvBody.setTextIsSelectable(true)
                    Linkify.addLinks(tvBody, Linkify.WEB_URLS)
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
                                            (layout[layout.childCount - 1] as TextView).setTextColor(main.getColor(android.R.color.holo_red_light))
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

    private fun vote(
        dir: VoteDirection,
        reddit: RedditClient?,
        id: String,
        binder: ItemViewVideoBinding
    ) {
        binder.apply {
            val strVote = if (dir == VoteDirection.UP) "upvote" else "downvote"
            val strVoted = if (dir == VoteDirection.UP) "Upvoted" else "Downvoted"
            val tv = if (dir == VoteDirection.UP) tvUpvotes else tvDownvote
            val iv = if (dir == VoteDirection.UP) ivUpvotes else ivDownvote
            val tvEx = if (dir == VoteDirection.UP) tvDownvote else tvUpvotes
            val ivEx = if (dir == VoteDirection.UP) ivDownvote else ivUpvotes
            val src = if (dir == VoteDirection.UP) R.drawable.ic_upvote else R.drawable.ic_downvote
            val srcEx = if (dir == VoteDirection.UP) R.drawable.ic_downvote else R.drawable.ic_upvote
            val srcRed = if (dir == VoteDirection.UP) R.drawable.ic_upvote_red else R.drawable.ic_downvote_red
            var score = tvUpvotes.text.toString().toInt()
            if (reddit == null) {
                currentPlayer?.pause()
                Snackbar.make(fragment.binder.root, "Sign in to $strVote", Snackbar.LENGTH_SHORT)
                    .setAction("Sign in") { main.redditHelper.startSignIn() }.show()
            } else {
                CoroutineScope(IO).launch {
                    main.shortToast("Voting...")
                    val submission = reddit.submission(id)
                    val voteDir = submission.inspect().vote
                    if (voteDir != dir) {
                        //change vote image color
                        main.runOnUiThread {
                            main.shortToast(strVoted)
                            iv.setImageResource(srcRed)
                            tv.setTextColor(main.getColor(R.color.orange))
                            iv.bounce(); tv.bounce()
                            if (voteDir != VoteDirection.NONE) {
                                ivEx.setImageResource(srcEx)
                                tvEx.setTextColor(main.getColor(R.color.white))
                            }
                            tvUpvotes.text = if (dir == VoteDirection.DOWN) (--score).toString() else (++score).toString()
                        }
                        //upvote
                        submission.setVote(dir)
                    } else {
                        //change vote image color
                        main.runOnUiThread {
                            main.shortToast("Removed $strVote")
                            iv.setImageResource(src)
                            iv.bounce(); tv.bounce()
                            tv.setTextColor(main.getColor(R.color.white))
                            tvUpvotes.text = if (dir == VoteDirection.DOWN) (++score).toString() else (--score).toString()
                        }
                        //remove vote
                        submission.setVote(VoteDirection.NONE)
                    }
                }
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    companion object {
        @SuppressLint("Range")
        fun startDownloading(post: Video, binder: ItemViewVideoBinding): Long {
            val permalink = post.permalink
            val name = post.name
            main.shortToast("Download started")
            binder.progressDownload.show()
            binder.saveLayout.hide()
            val url = if (post.isVideo) {
                val fallbackUrl = post.videoDownloadUrl
                val audioUrl = fallbackUrl.substring(0, fallbackUrl.indexOf("DASH_")) + "DASH_audio.mp4?source=fallback"
                "https://sd.redditsave.com/download.php?permalink=$permalink&video_url=$fallbackUrl&audio_url=$audioUrl"
            } else {
                post.gifMp4
            }

            Log.d(TAG, "startDownloading: $url")

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
                        binder.progressDownload.hide()
                        binder.saveLayout.show()
                    }
                }

            }

            main.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            return id
        }
    }
}