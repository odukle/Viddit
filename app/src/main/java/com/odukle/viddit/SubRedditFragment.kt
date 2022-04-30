package com.odukle.viddit

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.common.base.CharMatcher
import com.google.gson.JsonParser
import com.odukle.viddit.utils.Helper.Companion.getVideos
import com.odukle.viddit.utils.Helper.Companion.isOnline
import com.odukle.viddit.utils.Helper.Companion.subredditAdapter
import com.odukle.viddit.utils.Helper.Companion.subredditName
import com.odukle.viddit.MainActivity.Companion.main
import com.odukle.viddit.databinding.BottomsheetCustomFeedsBinding
import com.odukle.viddit.databinding.FragmentSubRedditBinding
import com.odukle.viddit.utils.CustomGLM
import com.odukle.viddit.utils.IS_USER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dean.jraw.models.MultiredditPatch
import kotlin.properties.Delegates

private const val TAG = "SubRedditFragment"

class SubRedditFragment : Fragment() {

    lateinit var subReddit: SubReddit
    var isUser by Delegates.notNull<Boolean>()
    lateinit var binder: FragmentSubRedditBinding
    lateinit var cfBinder: BottomsheetCustomFeedsBinding
    lateinit var bottomSheetDialogCF: BottomSheetDialog
    lateinit var adapter: SubredditAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subReddit = requireArguments().getParcelable(SUBREDDIT)!!
        isUser = requireArguments().getBoolean(IS_USER)
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

            if (isUser) {
                tvMembers.hide()
                chipAddToCf.hide()
                val params = scrollViewChips.layoutParams as RelativeLayout.LayoutParams
                params.addRule(RelativeLayout.BELOW, ivIcon.id)
                scrollViewChips.layoutParams = params
            }

            tvSubredditName.text = if (!isUser) subReddit.title else subReddit.titlePrefixed
            tvMembers.text = subReddit.subscribers + " members"
            tvDesc.text = subReddit.desc
            tvDescFull.text = subReddit.desc
            Glide.with(root)
                .load(subReddit.icon)
                .placeholder(R.drawable.ic_reddit)
                .into(ivIcon)

            if (subredditName == subReddit.title && subredditAdapter != null) {
                rvSubreddit.adapter = subredditAdapter
                val spanCount = if (getOrientation() == Configuration.ORIENTATION_LANDSCAPE) 4 else 3
                rvSubreddit.layoutManager = GridLayoutManager(requireContext(), spanCount)
                cardLoadMore.animate().translationY(500f).duration = 500

                runAfter(500) {
                    cardLoadMore.visibility = View.GONE
                }

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
                    chip.bounce()
                    val order = chip.tag as String
                    if (chip.id != chipTop.id) {
                        populateRV(order)
                        chipGroupTime.visibility = View.GONE
                        chipGroupTime.clearCheck()
                        chipTop.text = "Top"
                    }
                }
            }

            chipGroupTime.setOnCheckedChangeListener { group, checkedId ->
                val chip = group.findViewById<Chip>(checkedId)
                if (chip != null) {
                    chip.bounce()
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

            bottomSheetDialogCF = BottomSheetDialog(main)
            cfBinder = DataBindingUtil.inflate(
                LayoutInflater.from(main),
                R.layout.bottomsheet_custom_feeds,
                null,
                false
            )
            bottomSheetDialogCF.setContentView(cfBinder.root)

            chipAddToCf.setOnClickListener {
                chipAddToCf.bounce()
                bottomSheetDialogCF.show()
                addFeedViewToCf()
                subredditToAddOrRemove = subReddit.titlePrefixed.replace("r/", "")

                cfBinder.apply {
                    val reddit = getReddit()
                    if (reddit == null) {
                        layoutMainContent.hide()
                        layoutSignIn.show()
                        layoutSignIn.setOnClickListener {
                            bottomSheetDialogCF.dismiss()
                            main.redditHelper.startSignIn()
                        }
                    } else {
                        layoutMainContent.show()
                        layoutSignIn.hide()

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
                                    reddit.me().createMulti(name, patch)
                                } catch (e: Exception) {
                                }

                                val json = getMultiRedditAbout(reddit, name)
                                val feed = JsonParser.parseString(json).asJsonObject
                                val multiReddit = MultiReddit(
                                    name,
                                    displayName,
                                    feed["data"].asJsonObject["icon_url"].asString,
                                    feed["data"].asJsonObject["subreddits"].asJsonArray.map { it.asJsonObject["name"].asString }.toList()
                                )
                                runMain {
                                    addFeedView(multiReddit, bottomSheetDialogCF, cfBinder = cfBinder, sfBinder = binder)
                                    main.shortToast("Added successfully 🎉")
                                }
                            }

                            false
                        }

                        btnAdd.setOnClickListener {
                            etNewFeed.onEditorAction(EditorInfo.IME_ACTION_DONE)
                        }
                    }
                }
            }
        }
    }

    private fun addFeedViewToCf() {
        cfBinder.apply {
            if (layoutCf.childCount > 0) return
            progressBarCf.show()
            val reddit = getReddit()
            if (reddit != null) {
                ioScope().launch {
                    val cfJson = getCustomFeeds(reddit)
                    val feedArray = JsonParser.parseString(cfJson).asJsonArray
                    feedArray.forEach { ele ->
                        val feed = ele.asJsonObject["data"].asJsonObject
                        val multiReddit = MultiReddit(
                            feed["name"].asString,
                            feed["display_name"].asString,
                            feed["icon_url"].asString,
                            feed["subreddits"].asJsonArray.map { it.asJsonObject["name"].asString }.toList()
                        )
                        runMain {
                            addFeedView(
                                multiReddit,
                                bottomSheetDialogCF,
                                cfBinder = cfBinder,
                                sfBinder = binder
                            )
                        }
                    }
                    progressBarCf.hide()
                }
            }
        }
    }

    fun refreshAdapter(oldSize: Int, newSize: Int) {
        Log.d(TAG, "refreshAdapter: called")
        if (oldSize != 0) {
            binder.rvSubreddit.adapter?.notifyItemRangeInserted(oldSize, newSize - oldSize)
            binder.cardLoadMore.animate().translationY(500f).duration = 500

            runAfter(500) {
                binder.cardLoadMore.visibility = View.GONE
            }
        } else {
            binder.rvSubreddit.adapter?.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: called")
        bottomSheetDialogCF.dismiss()
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
            cardLoadMore.show()
            cardLoadMore.animate().translationY(0f).duration = 500

            if (!isOnline(main)) {
                main.longToast("No internet 😔")
            } else {
                CoroutineScope(IO).launch {
                    val vList = getVideos(subReddit.titlePrefixed, "", 0, order, time, isUser)
                    if (vList.isNotEmpty()) {
                        withContext(Main) {
                            try {
                                adapter = SubredditAdapter(vList, this@SubRedditFragment, order, time)
                                rvSubreddit.adapter = adapter
                                val spanCount = if (getOrientation() == Configuration.ORIENTATION_LANDSCAPE) 4 else 3
                                rvSubreddit.layoutManager = CustomGLM(requireContext(), spanCount)
                                cardLoadMore.animate().translationY(500f).duration = 500

                                runAfter(500) {
                                    cardLoadMore.visibility = View.GONE
                                }

                            } catch (e: Exception) {
                            }
                        }
                    } else {
                        main.runOnUiThread {
                            tvNoVideos.visibility = View.VISIBLE
                            cardLoadMore.animate().translationY(500f).duration = 500

                            runAfter(500) {
                                cardLoadMore.visibility = View.GONE
                            }
                        }
                    }
                }
            }
        }

    }

    companion object {
        @JvmStatic
        fun newInstance(subReddit: SubReddit, isUser: Boolean = false) =
            SubRedditFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(SUBREDDIT, subReddit)
                    putBoolean(IS_USER, isUser)
                }
            }
    }
}