package com.odukle.viddit.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.odukle.viddit.FOR_SUBREDDIT
import com.odukle.viddit.MainActivity.Companion.main
import com.odukle.viddit.R
import com.odukle.viddit.databinding.ItemViewSubredditBinding
import com.odukle.viddit.fragments.MainFragment
import com.odukle.viddit.fragments.SubRedditFragment
import com.odukle.viddit.models.Video
import com.odukle.viddit.runAfter
import com.odukle.viddit.utils.Helper
import com.odukle.viddit.utils.Helper.Companion.backstack
import com.odukle.viddit.utils.Helper.Companion.imageLoadingListener
import com.odukle.viddit.utils.Helper.Companion.videoAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SubredditAdapter"
class SubredditAdapter(
    private val list: MutableList<Video>,
    private val fragment: SubRedditFragment,
    private val order: String,
    private val time: String
) : RecyclerView.Adapter<SubredditAdapter.SRViewHolder>() {
    inner class SRViewHolder(val binder: ItemViewSubredditBinding) : RecyclerView.ViewHolder(binder.root)

    private var attachCount = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SRViewHolder {
        val binder = DataBindingUtil.inflate<ItemViewSubredditBinding>(
            LayoutInflater.from(parent.context),
            R.layout.item_view_subreddit,
            parent,
            false
        )

        return SRViewHolder(binder)
    }

    override fun onBindViewHolder(holder: SRViewHolder, position: Int) {
        try {
            val post = list[position]


            holder.binder.apply {
                val thumbnail = post.nsfw ?: post.thumbnail

                if (thumbnail.isNotEmpty()) {
                    Glide.with(root)
                        .load(thumbnail)
                        .centerCrop()
                        .addListener(imageLoadingListener(this))
                        .into(ivThumb)
                }

                //////////////////////////////////////////////////////ocl
                ivThumb.setOnClickListener {
                    val fragment = MainFragment.newInstance(post.subredditPrefixed, "", position, FOR_SUBREDDIT)
                    videoAdapter = VideoAdapter(list, post.subredditPrefixed, order, time, passedFragment = fragment)
                    val fragmentTxn = main.supportFragmentManager.beginTransaction()
                    fragmentTxn.replace(R.id.container, fragment)
                    fragmentTxn.addToBackStack("${backstack++}")
                    fragmentTxn.commit()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onBindViewHolder: ${e.stackTraceToString()}" )
        }
    }

    override fun onViewAttachedToWindow(holder: SRViewHolder) {
        val post = list[list.size - 1]

        try {
            if (holder.absoluteAdapterPosition == list.size - 1) {
                attachCount++
                if (attachCount <= 1) {
                    fragment.binder.cardLoadMore.animate().translationY(0f).duration = 500
                    fragment.binder.cardLoadMore.visibility = View.VISIBLE
                    CoroutineScope(Dispatchers.IO).launch {
                        val vList = Helper.getVideos(post.subredditPrefixed, post.name, 0, order, time)
                        val oldSize = list.size
                        list.addAll(vList)
                        if (oldSize < list.size) {
                            withContext(Dispatchers.Main) {
                                fragment.refreshAdapter(oldSize, list.size)
                                attachCount = 0
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                fragment.binder.cardLoadMore.animate().translationY(500f).duration = 500

                                runAfter(500) {
                                    fragment.binder.cardLoadMore.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onViewAttachedToWindow: ${e.stackTraceToString()}" )
        }
        super.onViewAttachedToWindow(holder)
    }

    override fun getItemCount(): Int {
        return list.size
    }
}