package com.odukle.viddit

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.common.base.CharMatcher
import com.google.gson.JsonParser
import com.odukle.viddit.Helper.Companion.currentPlayer
import com.odukle.viddit.Helper.Companion.getSubredditInfo
import com.odukle.viddit.Helper.Companion.getUserIcon
import com.odukle.viddit.Helper.Companion.searchAdapter
import com.odukle.viddit.Helper.Companion.snapHelper
import com.odukle.viddit.Helper.Companion.videoAdapter
import com.odukle.viddit.Helper.Companion.videoAdapterForMain
import com.odukle.viddit.Helper.Companion.videoList
import com.odukle.viddit.MainActivity.Companion.main
import com.odukle.viddit.databinding.FragmentMainBinding
import com.odukle.viddit.databinding.LayoutCommentsBinding
import com.odukle.viddit.databinding.LayoutMenuBinding
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import net.dean.jraw.RedditClient
import net.dean.jraw.models.MultiredditPatch
import net.dean.jraw.models.Submission
import net.dean.jraw.pagination.DefaultPaginator
import okhttp3.*
import kotlin.properties.Delegates


private const val TAG = "MainFragment"

class MainFragment : Fragment() {

    lateinit var binder: FragmentMainBinding
    lateinit var commentsBinder: LayoutCommentsBinding
    private lateinit var menuBinder: LayoutMenuBinding
    lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var adapter: VideoAdapter
    lateinit var pool: ExoPool
    lateinit var subreddit: String
    private lateinit var after: String
    lateinit var calledFor: String
    var job: Job? = null
    var rvPosition by Delegates.notNull<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireArguments().apply {
            subreddit = getString(SUBREDDIT)!!
            after = getString(AFTER) ?: ""
            calledFor = getString(CALLED_FOR)!!
            rvPosition = getInt(RV_POSITION)

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binder = DataBindingUtil.inflate(inflater, R.layout.fragment_main, container, false)
        init()
        return binder.root
    }

    @SuppressLint("SetTextI18n")
    private fun init() {
        Log.d(TAG, "init: called")
        videoList.clear()
        currentPlayer = null
        pool = object : ExoPool {
            override fun acquire(): ExoPlayer {
                val trackSelector = DefaultTrackSelector(main).apply {
                    setParameters(buildUponParameters().setMaxVideoSize(1280, 720))
                }
                return ExoPlayer.Builder(main)
                    .setTrackSelector(trackSelector)
                    .build()
            }

            override fun release(player: ExoPlayer) {
                player.release()
            }
        }


        binder.apply {
            if (calledFor == FOR_MAIN && videoAdapter == null) {
                chipGroup.show()
                runAfter(500) { chipPopular.isChecked = true }
            } else {
                populateRV()
                if (calledFor == SUBREDDIT) {
                    chipGroup.hide()
                    chipGroupCf.hide()
                    btnBackToMain.hide()
                }
            }
            snapHelper.attachToRecyclerView(vpViddit)
            vpViddit.layoutManager = CustomLLM(main)
            ////////////////////////////////////////////////////////////////////////////////////////////////SET LISTENERS
            ivGoToTop.setOnClickListener {
                val position = vpViddit.getCurrentPosition()
                if (position <= 10) vpViddit.smoothScrollToPosition(0)
                else {
                    vpViddit.smoothScrollToPosition(position - 10)
                    runAfter(1000) {
                        vpViddit.scrollToPosition(0)
                    }
                }
            }

            arrayOf(tvDiscover, ivSearch).forEach {
                it.setOnClickListener {
                    searchAdapter = null
                    val fragmentTxn = main.supportFragmentManager.beginTransaction()
                    fragmentTxn.setCustomAnimations(R.anim.slide_from_left, R.anim.slide_to_right)
                    fragmentTxn.replace(R.id.container, SearchFragment.newInstance())
                    fragmentTxn.addToBackStack("${Helper.backstack++}")
                    fragmentTxn.commit()
                }
            }

            ivReload.setOnClickListener {
                Log.d(TAG, "init: clicked")
                populateRV(true, refresh = false)
            }

            refreshLayout.setOnRefreshListener {
                populateRV(false, refresh = true)
            }

            refreshLayout.setProgressViewOffset(false, 140F.toDp(), 180F.toDp())

            chipGroup.setOnCheckedChangeListener { group, checkedId ->
                val chip = group.findViewById<Chip>(checkedId)
                if (chip != null) {
                    chip.bounce()
                    val multiReddit = chip.tag.toString()
                    val calledFor = multiReddit.substring(multiReddit.lastIndexOf("/") + 1)
                    if (videoAdapter != null && videoAdapter!!.calledFor == calledFor) return@setOnCheckedChangeListener
                    populateRV(multiReddit)
                }
            }

            chipGroupCf.setOnCheckedChangeListener { group, checkedId ->
                val chip = group.findViewById<Chip>(checkedId)
                if (chip != null) {
                    chip.bounce()
                    runAfter(100) {
                        scrollViewCfChips.smoothScrollTo((chip.x / 1.5).toInt(), 0)
                        btnBackToMain.text = "Go back to main feed"
                    }
                    val name = chip.tag.toString()
                    val reddit = getReddit()
                    if (reddit != null) {
                        if (videoAdapter != null && videoAdapter!!.calledFor == name) {
                            chipGroupCf.show()
                            chipGroup.hide()
                            return@setOnCheckedChangeListener
                        }
                        job?.cancel()
                        job = ioScope().launch {
                            populateRvFromPages(reddit, name)
                        }
                    }
                }
            }

            btnBackToMain.setOnClickListener {
                btnBackToMain.bounce()
                chipGroupCf.apply {
                    if (isVisible) {
                        clearCheck()
                        hide()
                        chipGroup.show()
                        chipPopular.isChecked = true
                        btnBackToMain.text = "Go to your custom feeds"
                    } else {
                        if (chipGroupCf.childCount > 0) {
                            show()
                            (get(0) as Chip).isChecked = true
                            chipGroup.clearCheck()
                            chipGroup.hide()
                            btnBackToMain.text = "Go back to main feed"
                        } else {
                            ivMenu.performClick()
                        }
                    }
                }
            }

            bottomSheetDialog = BottomSheetDialog(main)
            menuBinder = DataBindingUtil.inflate(
                LayoutInflater.from(main),
                R.layout.layout_menu,
                null,
                false
            )
            bottomSheetDialog.setContentView(menuBinder.root)
            menuBinder.apply {
                val reddit = getReddit()
                if (reddit == null) {
                    layoutUser.hide()
                    layoutSignIn.show()
                    layoutSignIn.setOnClickListener {
                        bottomSheetDialog.dismiss()
                        main.redditHelper.startSignIn()
                    }
                } else {
                    val userName = reddit.authManager.currentUsername()
                    layoutUser.show()
                    layoutSignIn.hide()
                    tvUsername.text = "u/$userName"
                    ioScope().launch {
                        val userSubreddit = getSubredditInfo("u/$userName", true)
                        val icon = getUserIcon(userName)
                        mainScope().launch {
                            Glide.with(root).load(icon).into(ivUserIcon)
                        }

                        mainScope().launch {
                            layoutUserName.setOnClickListener {
                                bottomSheetDialog.dismiss()
                                val fragmentTxn = main.supportFragmentManager.beginTransaction()
                                fragmentTxn.replace(R.id.container, SubRedditFragment.newInstance(userSubreddit, true))
                                fragmentTxn.addToBackStack("${Helper.backstack++}")
                                fragmentTxn.commit()
                            }
                        }
                    }

                    layoutMultis.setOnClickListener {
                        if (layoutFeeds.isVisible && layoutFeeds.childCount > 0) {
                            layoutFeeds.hide()
                            createNewFeed.hide()
                        } else {
                            layoutFeeds.show()
                            createNewFeed.show()
                            if (layoutFeeds.childCount == 0) {
                                progressBarCf.show()
                                ioScope().launch {
                                    val cfJson = getCustomFeeds(reddit)
                                    val feedArray = JsonParser.parseString(cfJson).asJsonArray
                                    if (feedArray.isEmpty) {
                                        main.shortToast("You have not added any custom feeds yet!")
                                        progressBarCf.hide()
                                        return@launch
                                    }
                                    feedArray.forEach { ele ->
                                        val feed = ele.asJsonObject["data"].asJsonObject
                                        val multiReddit = MultiReddit(
                                            feed["name"].asString,
                                            feed["display_name"].asString,
                                            feed["icon_url"].asString,
                                            feed["subreddits"].asJsonArray.map { it.asJsonObject["name"].asString }.toList()
                                        )
                                        runMain {
                                            addFeedView(multiReddit, bottomSheetDialog, menuBinder = menuBinder, mainBinder = binder)
                                        }
                                    }
                                    progressBarCf.hide()
                                }
                            }
                        }
                    }

                    createNewFeed.setOnClickListener {
                        layoutAddNewFeed.apply {
                            if (isVisible) hide() else show()
                        }
                    }

                    etNewFeed.filters = arrayOf(filter)
                    etNewFeed.setOnEditorActionListener { v, actionId, event ->
                        if (v.text.isNullOrEmpty()) return@setOnEditorActionListener false

                        layoutAddNewFeed.hide()
                        val displayName = (v as TextInputEditText).text.toString()
                        val charMatcher = CharMatcher.anyOf(displayName)
                        if (charMatcher.matchesAnyOf(blockCharacterSet)) {
                            main.shortToast("Please enter a name without special characters")
                            return@setOnEditorActionListener false
                        }
                        val name = displayName.replace(" ", "")
                        main.shortToast("Adding new feed...")
                        val patch = MultiredditPatch.Builder()
                            .iconName("png")
                            .displayName(displayName)
                            .build()
                        ioScope().launch {
                            try {
                                reddit.me().multi("meme")
                                reddit.me().createMulti(name, patch)
                            } catch (e: Exception) {
                            }

                            val aboutJson = getMultiRedditAbout(reddit, name)
                            Log.d(TAG, "init: $aboutJson")
                            val feed = JsonParser.parseString(aboutJson).asJsonObject
                            val multiReddit = MultiReddit(
                                name,
                                displayName,
                                feed["data"].asJsonObject["icon_url"].asString,
                                feed["data"].asJsonObject["subreddits"].asJsonArray.map { it.asJsonObject["name"].asString }.toList()
                            )
                            runMain {
                                addFeedView(multiReddit, bottomSheetDialog, menuBinder = menuBinder, mainBinder = binder)
                                main.shortToast("Added successfully 🎉")
                            }
                        }

                        false
                    }

                    btnAdd.setOnClickListener {
                        etNewFeed.onEditorAction(EditorInfo.IME_ACTION_DONE)
                    }

                    layoutSignOut.setOnClickListener {
                        bottomSheetDialog.dismiss()
                        Snackbar.make(binder.root, "u/$userName", Snackbar.LENGTH_SHORT)
                            .setAction("Sign out") {
                                ioScope().launch {
                                    main.shortToast("Signing you out...")
                                    reddit.authManager.revokeAccessToken()
                                    reddit.authManager.revokeRefreshToken()
                                    main.redditHelper.reddit = null
                                    main.shortToast("Signed out")
                                    delay(200)
                                    main.triggerRebirth()
                                }
                            }.show()

                    }
                }
            }

            ivMenu.setOnClickListener {
                bottomSheetDialog.show()
            }

            chipAddSubreddits.setOnClickListener {
                tvDiscover.performClick()
            }
        } //binder.apply
    }


    suspend fun populateRvFromPages(reddit: RedditClient, title: String) = withContext(IO) {
        runMain { binder.refreshLayout.isRefreshing = true }
        val pages = getSubmissionPages(reddit, title)
        val vList = getVideosFromPages(pages)
        binder.layoutEmptyFeed.apply {
            if (vList.isEmpty()) show() else hide()
        }
        adapter = VideoAdapter(vList, "", "", "", pages)
        adapter.content = CUSTOM_FEED
        adapter.loadGifsExternally = true
        runMain {
            binder.apply {
                vpViddit.adapter = adapter
                layoutToolbar.show()
                adapter.playWhenReady = calledFor != FOR_MAIN
                videoAdapter = adapter
                videoAdapter!!.calledFor = title
                refreshLayout.isRefreshing = false
                btnBackToMain.show()
            }
        }
    }

    suspend fun getVideosFromPages(pages: DefaultPaginator<Submission>): MutableList<Video> = withContext(IO) {
        val vList = mutableListOf<Video>()
        if (pages.iterator().hasNext()) {
            val listing = pages.next()
            listing.forEach { post ->
                post.apply {
                    val video = Video(
                        title,
                        fullName,
                        id,
                        subreddit,
                        "r/$subreddit",
                        selfText ?: "null",
                        selfText ?: "null",
                        author,
                        linkFlairCssClass ?: "null",
                        score.toString(),
                        commentCount.toString(),
                        created.time / 1000,
                        postHint?.contains("video") ?: false,
                        embeddedMedia?.redditVideo?.hlsUrl ?: "null",
                        embeddedMedia?.redditVideo?.fallbackUrl ?: "null",
                        preview?.images?.get(0)?.source?.url ?: "null",
                        if (isNsfw) preview?.images?.get(0)?.source?.url else null,
                        preview?.images?.get(0)?.source?.url ?: "null",
                        (preview?.images?.get(0)?.source?.url ?: "null").replace("png8", "mp4"),
                        "https://www.reddit.com$permalink"
                    )

                    val isGif = video.gif.contains(".gif?")
                    if (video.video != "null" || isGif) {
                        vList.add(video)
                    }
                }
            }
        } else main.shortToast("All Videos Loaded")

        vList
    }

    private fun populateRV(doShuffle: Boolean = false, refresh: Boolean = false) {

        if (videoAdapter != null && !doShuffle && !refresh) {
            binder.apply {
                layoutEmptyFeed.hide()
                val adapter = if (calledFor == FOR_MAIN) videoAdapterForMain else videoAdapter
                adapter?.playWhenReady = true
                snapHelper.attachToRecyclerView(vpViddit)
                vpViddit.adapter = adapter
                vpViddit.layoutManager = CustomLLM(main)
                if (calledFor == FOR_SUBREDDIT) {
                    runAfter(200) {
                        vpViddit.scrollToPosition(rvPosition)
                    }
                }
                if (getOrientation() == Configuration.ORIENTATION_LANDSCAPE) {

                }
                refreshLayout.isRefreshing = false
                //
                if (adapter?.content == CUSTOM_FEED) {
                    chipGroupCf.show()
                    chipGroup.hide()
                    btnBackToMain.text = GO_BACK_TO_MAIN_FEED
                }
            }
        } else {
            job?.cancel()
            job = ioScope().launch {
                withContext(Main) {
                    binder.apply {
                        if (refresh || !doShuffle) refreshLayout.isRefreshing = true

                        adapter = VideoAdapter(mutableListOf(), subreddit)
                        adapter.content = MAIN_FEED
                        val lastPost = try {
                            val unList = mutableListOf<Video>()
                            unList.addAll(videoAdapter!!.unShuffledList)
                            unList[videoAdapter!!.itemCount - 1].name
                        } catch (e: Exception) {
                            ""
                        }

                        if (!doShuffle && !refresh) {
                            Log.d(TAG, "populateRV: first")
                            vpViddit.adapter = adapter   //Called for the first time so attach adapter right away to reduce waiting time
                            videoAdapter = adapter
                            adapter.playWhenReady = calledFor != FOR_MAIN
                            adapter.loadMoreData(lastPost, doShuffle)
                            refreshLayout.isRefreshing = false
                        } else {
                            val vpAdapter = vpViddit.adapter as VideoAdapter
                            if (doShuffle) vpAdapter.shuffle()                                             //shuffle
                            vpAdapter.loadMoreData(lastPost, doShuffle, refresh = refresh) // called by reload or shuffle button
                            if (refresh) {
                                Log.d(TAG, "populateRV: is refresh")
                                vpAdapter.list.clear()
                                vpAdapter.unShuffledList.let { vpAdapter.list.addAll(it) }
                                vpAdapter.notifyDataSetChanged()
                                vpAdapter.shuffle()
                            }
                            vpAdapter.playWhenReady = calledFor != FOR_MAIN
                            refreshLayout.isRefreshing = false
                        }
                    }
                }
            }
        }
    }

    private fun populateRV(multiReddit: String) {

        this.subreddit = multiReddit
        adapter = VideoAdapter(mutableListOf(), multiReddit)
        adapter.content = MAIN_FEED
        job?.cancel()
        job = ioScope().launch {
            mainScope().launch {
                binder.apply {
                    layoutEmptyFeed.hide()
                    vpViddit.adapter = adapter
                    layoutToolbar.show()
                    refreshLayout.isRefreshing = true
                    adapter.playWhenReady = calledFor != FOR_MAIN
                    adapter.loadMoreData("")
                    videoAdapter = adapter
                    videoAdapter!!.calledFor = multiReddit.substring(multiReddit.lastIndexOf("/") + 1)
                    refreshLayout.isRefreshing = false
                }
            }
        }
    }

    fun refreshAdapter(oldSize: Int, newSize: Int) {
        if (oldSize != 0) {
            binder.apply {
                vpViddit.adapter?.notifyItemRangeInserted(oldSize, newSize - oldSize)
                refreshLayout.isRefreshing = false
            }
        } else {
            binder.vpViddit.adapter?.notifyDataSetChanged()
            (binder.vpViddit.adapter as VideoAdapter).shuffle()
        }
    }

    override fun onPause() {
        if (calledFor == FOR_MAIN) {
            videoAdapter = try {
                videoAdapterForMain = binder.vpViddit.adapter as VideoAdapter
                binder.vpViddit.adapter as VideoAdapter
            } catch (e: Exception) {
                null
            }
        }
        currentPlayer?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        currentPlayer?.play()
    }


    override fun onDestroyView() {
        currentPlayer?.let { pool.release(it) }
        bottomSheetDialog.dismiss()
        super.onDestroyView()
    }

    companion object {
        @JvmStatic
        fun newInstance(subreddit: String, after: String, rvPosition: Int, calledFor: String) =
            MainFragment().apply {
                arguments = Bundle().apply {
                    putString(SUBREDDIT, subreddit)
                    putString(AFTER, after)
                    putString(CALLED_FOR, calledFor)
                    putInt(RV_POSITION, rvPosition)
                }
            }
    }
}