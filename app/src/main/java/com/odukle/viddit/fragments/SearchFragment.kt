package com.odukle.viddit.fragments

import android.os.Build
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.common.base.CharMatcher
import com.google.gson.JsonParser
import com.odukle.viddit.*
import com.odukle.viddit.MainActivity.Companion.main
import com.odukle.viddit.adapters.SearchAdapter
import com.odukle.viddit.databinding.BottomsheetCustomFeedsBinding
import com.odukle.viddit.databinding.FragmentSearchBinding
import com.odukle.viddit.databinding.LayoutMenuBinding
import com.odukle.viddit.models.MultiReddit
import com.odukle.viddit.utils.CustomLLM
import com.odukle.viddit.utils.Helper
import com.odukle.viddit.utils.Helper.Companion.getTopSubreddits
import com.odukle.viddit.utils.Helper.Companion.searchAdapter
import com.odukle.viddit.utils.Helper.Companion.searchQuery
import kotlinx.android.synthetic.main.fragment_search.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dean.jraw.models.MultiredditPatch

private const val TAG = "SearchFragment"

class SearchFragment : Fragment() {

    lateinit var binder: FragmentSearchBinding
    lateinit var menuBinder: LayoutMenuBinding
    lateinit var cfBinder: BottomsheetCustomFeedsBinding
    lateinit var bottomSheetDialogCF: BottomSheetDialog
    lateinit var bottomSheetDialogMenu: BottomSheetDialog
    var adapter: SearchAdapter? = null
    var job: Job? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binder = DataBindingUtil.inflate(inflater, R.layout.fragment_search, container, false)
        sf = this
        init()
        return binder.root
    }

    fun init() {
        Log.d(TAG, "init: called")
        binder.apply {
            if (searchAdapter != null) {
                rvSearch.adapter = searchAdapter
                rvSearch.layoutManager = CustomLLM(main)
                hideLoading()
                if (searchAdapter!!.calledFor == MAIN_FEED) {
                    tvQuery.text = if (searchQuery.isNullOrEmpty()) "Top subreddits today" else "Search results"
                } else {
                    tvQuery.text = if (searchQuery.isNullOrEmpty()) "Your custom feeds" else "Search results"
                    runAfter(200) {
                        menuBinder.layoutMultis.performClick()
                    }
                    chipGroupCf.show()
                    chipDeleteFeed.show()
                    chipGroup.hide()
                }
            } else {
                chipVideos.isChecked = true
                if (Helper.isOnline(main)) {
                    showLoading()
                    contactLayout.hide()

                    ioScope().launch {
                        val rList = Helper.getTopSubreddits()
                        runMain {
                            adapter = SearchAdapter(rList, this@SearchFragment, "")
                            adapter!!.calledFor = MAIN_FEED
                            rvSearch.adapter = adapter
                            rvSearch.layoutManager = CustomLLM(main)
                            hideLoading()
                            contactLayout.show()
                        }
                    }
                } else {
                    main.longToast("No Internet 😔")
                }
            }

            //////////////////////////////////////////////////////////////////////////////////////SET OCL
            etSearch.setOnEditorActionListener { v, actionId, event ->
                if (v.text.isNullOrEmpty()) return@setOnEditorActionListener false

                val query = (v as TextInputEditText).text.toString()
                searchQuery = query

                showLoading()
                ioScope().launch {
                    val rList = Helper.getSubreddits(query)
                    runMain {
                        populateRv(rList)
                        v.clearFocus()
                        tvQuery.text = "Search results"
                    }
                }

                chipGroup.clearCheck()
                false
            }

            btnSearch.setOnClickListener {
                btnSearch.bounce()
                etSearch.onEditorAction(EditorInfo.IME_ACTION_DONE)
            }

            arrayOf(tvFeed, ivBackToFeed).forEach {
                it.setOnClickListener { main.onBackPressed() }
            }

            chipGroup.setOnCheckedChangeListener { _, checkedId ->
                try {
                    job?.cancel()
                    adapter = null
                    val chip = chipGroup.findViewById<Chip>(checkedId)
                    if (chip != null) {
                        chip.bounce()
                        scrollViewChips.smoothScrollTo((chip.x / 1.5).toInt(), 0)
                        showLoading()
                        if (chip.id == chipVideos.id) {
                            job = ioScope().launch {
                                val rList = getTopSubreddits()
                                runMain {
                                    populateRv(rList)
                                }
                            }
                            return@setOnCheckedChangeListener
                        }
                        val query = chip.text.toString()
                        if (searchAdapter != null && searchAdapter!!.query == query) {
                            runAfter(500) {
                                scrollViewChips.smoothScrollTo((chip.x / 1.5).toInt(), 0)
                            }
                            return@setOnCheckedChangeListener
                        }

                        job = ioScope().launch {
                            val rList = Helper.getSubreddits(query)
                            runMain {
                                populateRv(rList)
                            }
                        }

                        etSearch.text?.clear()
                        tvQuery.text = "Top subreddits today"
                    }
                } catch (e: Exception) {
                }
            }

            chipCustomFeed.setOnClickListener {
                chipCustomFeed.bounce()
                if (getReddit() != null) {
                    chipGroup.clearCheck()
                    chipGroup.hide()
                    chipGroupCf.show()
                    chipDeleteFeed.show()
                    tvQuery.text = "Your custom feeds"
                    chipGroupCf.removeAllViews()
                    updateCustomFeeds(0)
                } else {
                    ivMenu.performClick()
                }
            }

            switchNsfw.isChecked = nsfwAllowed()
            switchNsfw.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    allowNSFW()
                    if (!etSearch.text.isNullOrEmpty()) btnSearch.performClick()

                } else {
                    doNotAllowNSFW()
                    if (!etSearch.text.isNullOrEmpty()) btnSearch.performClick()
                }
            }

            setUpMenuBinder()
            setUpCFBinder()
            ivMenu.setOnClickListener {
                bottomSheetDialogMenu.show()
            }

            chipDeleteFeed.setOnClickListener {
                chipDeleteFeed.bounce()
                val chip = chipGroupCf.findViewById<Chip>(chipGroupCf.checkedChipId)
                if (chip != null) {
                    val name = chip.tag.toString()
                    val displayName = chip.text.toString()
                    Snackbar.make(root, "m/$displayName", Snackbar.LENGTH_SHORT)
                        .setAction("Delete") {
                            ioScope().launch {
                                main.shortToast("Deleting...")
                                getReddit()?.me()?.multi(name)?.delete()
                                main.shortToast("Deleted Succesfully 🎉")
                                runMain {
                                    updateCustomFeeds(0)
                                }
                            }
                        }.show()
                }
            }
        }
    }

    fun populateRv(rList: List<Pair<String, String>>, calledForMain: Boolean = true) {
        binder.apply {
            if (rList.isNotEmpty()) {
                rvSearch.show()
                tvSuchEmpty.hide()
                val addOrRemove = if (calledForMain) ADD else REMOVE
                adapter = SearchAdapter(rList, this@SearchFragment, "", addOrRemove)
                adapter!!.calledFor = if (calledForMain) MAIN_FEED else CUSTOM_FEED
                searchAdapter = adapter
                rvSearch.adapter = adapter
                rvSearch.layoutManager = CustomLLM(main)
                if (calledForMain) {
                    hideLoading()
                }
            } else {
                rvSearch.hide()
                tvSuchEmpty.show()
            }
        }
    }

    fun updateCustomFeeds(checkId: Int) {
        menuBinder.apply {
            layoutFeeds.removeAllViews()
            layoutFeeds.hide()
            layoutMultisOnClick(checkId)
        }
    }

    fun addFeedViewToCf() {
        bottomSheetDialogCF.show()
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
                                searchBinder = binder,
                                addChipsToSF = false
                            )
                        }
                    }
                    progressBarCf.hide()
                }
            }
        }
    }

    private fun setUpMenuBinder() {
        bottomSheetDialogMenu = BottomSheetDialog(main)
        menuBinder = DataBindingUtil.inflate(
            LayoutInflater.from(main),
            R.layout.layout_menu,
            null,
            false
        )
        bottomSheetDialogMenu.setContentView(menuBinder.root)
        val reddit = getReddit()
        menuBinder.apply {
            if (reddit == null) {
                layoutUser.hide()
                layoutSignIn.show()
                layoutSignIn.setOnClickListener {
                    bottomSheetDialogMenu.dismiss()
                    main.redditHelper.startSignIn()
                }
            } else {
                val userName = reddit.authManager.currentUsername()
                layoutUser.show()
                layoutSignIn.hide()
                tvUsername.text = "u/$userName"
                ioScope().launch {
                    val userSubreddit = Helper.getSubredditInfo("u/$userName", true)
                    val icon = Helper.getUserIcon(userName)
                    mainScope().launch {
                        Glide.with(root).load(icon).into(ivUserIcon)
                    }

                    mainScope().launch {
                        layoutUserName.setOnClickListener {
                            bottomSheetDialogMenu.dismiss()
                            val fragmentTxn = main.supportFragmentManager.beginTransaction()
                            fragmentTxn.replace(R.id.container, SubRedditFragment.newInstance(userSubreddit, true))
                            fragmentTxn.addToBackStack("${Helper.backstack++}")
                            fragmentTxn.commit()
                        }
                    }
                }

                layoutMultis.setOnClickListener {
                    layoutMultisOnClick(0)
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
                            addFeedView(multiReddit, bottomSheetDialogMenu, menuBinder = menuBinder, searchBinder = binder)
                            main.shortToast("Added successfully 🎉")
                        }
                    }

                    false
                }

                btnAdd.setOnClickListener {
                    etNewFeed.onEditorAction(EditorInfo.IME_ACTION_DONE)
                }

                layoutSignOut.setOnClickListener {
                    bottomSheetDialogMenu.hide()
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
    }

    private fun setUpCFBinder() {
        bottomSheetDialogCF = BottomSheetDialog(main)
        cfBinder = DataBindingUtil.inflate(
            LayoutInflater.from(main),
            R.layout.bottomsheet_custom_feeds,
            null,
            false
        )
        bottomSheetDialogCF.setContentView(cfBinder.root)
        val reddit = getReddit()
        cfBinder.apply {
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
                            addFeedView(multiReddit, bottomSheetDialogCF, cfBinder = cfBinder, searchBinder = binder)
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

    private fun layoutMultisOnClick(checkId: Int) {
        menuBinder.apply {

            layoutFeeds.show()
            createNewFeed.show()
            layoutFeeds.removeAllViews()
            if (layoutFeeds.childCount == 0) {
                progressBarCf.show()
                showLoading()
                ioScope().launch {
                    getReddit()?.let { reddit ->
                        val cfJson = getCustomFeeds(reddit)
                        val feedArray = JsonParser.parseString(cfJson).asJsonArray
                        runMain {
                            binder.chipGroupCf.removeAllViews()
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
                                addFeedView(multiReddit, bottomSheetDialogMenu, menuBinder = menuBinder, searchBinder = binder)
                            }
                        }
                        mainScope().launch {
                            val chip = Chip(main)
                            chip.text = "go back to discover"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                chip.typeface = main.resources.getFont(R.font.regular)
                            }
                            chip.setChipBackgroundColorResource(R.color.orange)
                            binder.apply {
                                chipGroupCf.addView(chip)
                                chipCustomFeed.show()
                                chip.setOnClickListener {
                                    chipGroupCf.clearCheck()
                                    chipGroupCf.hide()
                                    chipDeleteFeed.hide()
                                    chipGroup.show()
                                    chipVideos.isChecked = true
                                    tvQuery.text = "Top subreddits today"
                                }
                            }

                            val id = (binder.chipGroupCf[checkId] as Chip).id
                            val chipToCheck = binder.chipGroupCf.findViewById<Chip>(id)
                            chipToCheck.isChecked = true
                            // just to be sure
                            delay(500)
                            chipToCheck.performClick()
                            delay(500)
                            chipToCheck.performClick()
                            //
                            progressBarCf.hide()
                            hideLoading()
                        }
                    }

                }
            }
        }
    }

    private fun hideLoading() {
        binder.apply {
            cardLoading.animate().translationY(-500f).duration = 500
            runAfter(500) {
                cardLoading.hide()
            }
        }
    }

    private fun showLoading() {
        binder.apply {
            cardLoading.y = -500f
            cardLoading.show()
            cardLoading.animate().translationY(0f).duration = 500
        }
    }

    override fun onDestroyView() {
        searchAdapter = adapter
        bottomSheetDialogMenu.dismiss()
        bottomSheetDialogCF.dismiss()
        super.onDestroyView()
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            SearchFragment().apply {
                arguments = Bundle().apply {
                }
            }

        lateinit var sf: SearchFragment
    }
}