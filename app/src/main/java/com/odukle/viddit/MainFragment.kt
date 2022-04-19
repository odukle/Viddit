package com.odukle.viddit

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.odukle.viddit.Helper.Companion.AFTER
import com.odukle.viddit.Helper.Companion.CALLED_FOR
import com.odukle.viddit.Helper.Companion.FOR_MAIN
import com.odukle.viddit.Helper.Companion.FOR_SUBREDDIT
import com.odukle.viddit.Helper.Companion.RV_POSITION
import com.odukle.viddit.Helper.Companion.SUBREDDIT
import com.odukle.viddit.Helper.Companion.currentPlayer
import com.odukle.viddit.Helper.Companion.searchAdapter
import com.odukle.viddit.Helper.Companion.snapHelper
import com.odukle.viddit.Helper.Companion.videoAdapter
import com.odukle.viddit.Helper.Companion.videoAdapterForMain
import com.odukle.viddit.Helper.Companion.videoList
import com.odukle.viddit.MainActivity.Companion.main
import com.odukle.viddit.databinding.FragmentMainBinding
import com.odukle.viddit.databinding.LayoutCommentsBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import okhttp3.*
import kotlin.properties.Delegates


private const val TAG = "MainFragment"

class MainFragment : Fragment() {

    lateinit var binder: FragmentMainBinding
    lateinit var commentsBinder: LayoutCommentsBinding
    lateinit var adapter: VideoAdapter
    lateinit var pool: ExoPool
    lateinit var subreddit: String
    lateinit var after: String
    lateinit var calledFor: String
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
    ): View? {
        binder = DataBindingUtil.inflate(inflater, R.layout.fragment_main, container, false)
        init()
        return binder.root
    }

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
            populateRV()
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

            refreshLayout.setProgressViewOffset(false, 60F.toDp(), 100F.toDp())

        }

    }

    private fun populateRV(doShuffle: Boolean = false, refresh: Boolean = false) {

        if (videoAdapter != null && !doShuffle && !refresh) {
            binder.apply {
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
                refreshLayout.isRefreshing = false
            }
        } else {
            CoroutineScope(IO).launch {
                withContext(Main) {
                    binder.apply {
                        if (refresh || !doShuffle) refreshLayout.isRefreshing = true

                        adapter = VideoAdapter(mutableListOf(), subreddit, this@MainFragment)
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
                            snapHelper.attachToRecyclerView(vpViddit)
                            vpViddit.layoutManager = CustomLLM(main)
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

    fun refreshAdapter(oldSize: Int, newSize: Int) {
        if (oldSize != 0) {
            binder.apply {
                vpViddit.adapter?.notifyItemRangeInserted(oldSize, newSize - oldSize)
                refreshLayout.isRefreshing = false
            }
        } else {
            binder.vpViddit.adapter?.notifyDataSetChanged()
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