package com.odukle.viddit

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.odukle.viddit.Helper.Companion.SUBREDDIT
import com.odukle.viddit.Helper.Companion.getVideos
import com.odukle.viddit.Helper.Companion.isOnline
import com.odukle.viddit.Helper.Companion.subredditAdapter
import com.odukle.viddit.Helper.Companion.subredditName
import com.odukle.viddit.MainActivity.Companion.main
import com.odukle.viddit.databinding.FragmentSubRedditBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SubRedditFragment"

class SubRedditFragment : Fragment() {

    lateinit var subReddit: SubReddit
    lateinit var binder: FragmentSubRedditBinding
    lateinit var adapter: SubredditAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subReddit = requireArguments().getParcelable(SUBREDDIT)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binder = DataBindingUtil.inflate(inflater, R.layout.fragment_sub_reddit, container, false)
        init()
        return binder.root
    }

    @SuppressLint("SetTextI18n")
    fun init() {
        binder.apply {

            tvSubredditName.text = subReddit.title
            tvMembers.text = subReddit.subscribers + " members"
            tvDesc.text = subReddit.desc
            tvDescFull.text = subReddit.desc
            Glide.with(root)
                .load(subReddit.icon)
                .placeholder(R.drawable.ic_reddit)
                .into(ivIcon)


            if (subredditName == subReddit.title && subredditAdapter != null) {
                rvSubreddit.adapter = subredditAdapter
                rvSubreddit.layoutManager = GridLayoutManager(requireContext(), 3)
                cardLoadMore.animate().translationY(500f).duration = 500

                Runnable {
                    main.runOnUiThread {
                        cardLoadMore.visibility = View.GONE
                    }
                }.runAfter(500)

            } else {
                populateRV()
            }

            //////////////////////////////////////////////////// SOCL
            tvDesc.setOnClickListener {
                tvDesc.visibility = View.GONE
                tvDescFull.visibility = View.VISIBLE
            }

            tvDescFull.setOnClickListener {
                tvDesc.visibility = View.VISIBLE
                tvDescFull.visibility = View.GONE
            }

            chipGroup.setOnCheckedChangeListener { group, checkedId ->
                val chip = group.findViewById<Chip>(checkedId)
                if (chip != null) {
                    val order = chip.tag as String
                    if (chip.id != chipTop.id) {
                        populateRV(order)
                        chipGroupTime.visibility = View.GONE
                        chipGroupTime.clearCheck()
                    }
                }
            }

            chipGroupTime.setOnCheckedChangeListener { group, checkedId ->
                val chip = group.findViewById<Chip>(checkedId)
                if (chip != null) {
                    val time = (chip.tag ?: "day") as String
                    populateRV("top", time)
                    chipTop.text = chip.text
                    chipGroupTime.visibility = View.GONE
                }
            }

            chipTop.setOnClickListener {
                if (chipGroupTime.checkedChipId == View.NO_ID) chipTopToday.isChecked = true
                chipGroupTime.apply {
                    visibility = if (isVisible) View.GONE else View.VISIBLE
                }
            }
        }
    }

    fun refreshAdapter(oldSize: Int, newSize: Int) {
        Log.d(TAG, "refreshAdapter: called")
        if (oldSize != 0) {
            binder.rvSubreddit.adapter?.notifyItemRangeInserted(oldSize, newSize - oldSize)
            binder.cardLoadMore.animate().translationY(500f).duration = 500

            Runnable {
                main.runOnUiThread {
                    binder.cardLoadMore.visibility = View.GONE
                }
            }.runAfter(500)
        } else {
            binder.rvSubreddit.adapter?.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: called")
        subredditAdapter = try {
            adapter
        } catch (e: Exception) {
            null
        }
        subredditName = subReddit.title
        super.onDestroyView()
    }

    private fun populateRV(order: String = "hot", time: String = "day") {
        binder.apply {

            cardLoadMore.y = 500f
            cardLoadMore.visibility = View.VISIBLE
            cardLoadMore.animate().translationY(0f).duration = 500

            if (!isOnline(main)) {
                main.longToast("No internet 😔")
            } else {
                CoroutineScope(IO).launch {
                    val vList = getVideos(subReddit.titlePrefixed, "", 0, order, time)
                    if (vList.isNotEmpty()) {
                        withContext(Main) {
                            try {
                                adapter = SubredditAdapter(vList, this@SubRedditFragment, order, time)
                                rvSubreddit.adapter = adapter
                                rvSubreddit.layoutManager = CustomGLM(requireContext(), 3)
                                cardLoadMore.animate().translationY(500f).duration = 500

                                Runnable {
                                    main.runOnUiThread {
                                        cardLoadMore.visibility = View.GONE
                                    }
                                }.runAfter(500)

                            } catch (e: Exception) {
                            }
                        }
                    } else {
                        main.runOnUiThread {
                            tvNoVideos.visibility = View.VISIBLE
                            cardLoadMore.animate().translationY(500f).duration = 500

                            Runnable {
                                main.runOnUiThread {
                                    cardLoadMore.visibility = View.GONE
                                }
                            }.runAfter(500)
                        }
                    }
                }
            }
        }

    }

    companion object {
        @JvmStatic
        fun newInstance(subReddit: SubReddit) =
            SubRedditFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(SUBREDDIT, subReddit)
                }
            }
    }
}