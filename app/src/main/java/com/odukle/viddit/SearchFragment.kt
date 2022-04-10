package com.odukle.viddit

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.odukle.viddit.Helper.Companion.searchAdapter
import com.odukle.viddit.Helper.Companion.searchQuery
import com.odukle.viddit.MainActivity.Companion.main
import com.odukle.viddit.databinding.FragmentSearchBinding
import kotlinx.android.synthetic.main.fragment_search.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

private const val TAG = "SearchFragment"

class SearchFragment : Fragment() {

    lateinit var binder: FragmentSearchBinding
    var adapter: SearchAdapter? = null


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

    private fun init() {
        Log.d(TAG, "init: called")
        binder.apply {
            if (searchAdapter != null) {
                rvSearch.adapter = searchAdapter
                rvSearch.layoutManager = CustomLLM(main)
                cardLoading.visibility = View.GONE
                tvQuery.text = if (searchQuery.isNullOrEmpty()) "Top subreddits today" else "Search results"
            } else {
                chipVideos.isChecked = true
                if (Helper.isOnline(main)) {
                    cardLoading.y = -500f
                    cardLoading.visibility = View.VISIBLE
                    cardLoading.animate().translationY(0f).duration = 500
                    contactLayout.visibility = View.GONE

                    CoroutineScope(IO).launch {
                        val rList = Helper.getTopSubreddits()
                        main.runOnUiThread {
                            adapter = SearchAdapter(rList, this@SearchFragment, "")
                            rvSearch.adapter = adapter
                            rvSearch.layoutManager = CustomLLM(main)
                            cardLoading.animate().translationY(-500f).duration = 500
                            kotlinx.coroutines.Runnable {
                                main.runOnUiThread {
                                    cardLoading.visibility = View.GONE
                                }
                            }.runAfter(500)
                            contactLayout.visibility = View.VISIBLE
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

                cardLoading.y = -500f
                cardLoading.visibility = View.VISIBLE
                cardLoading.animate().translationY(0f).duration = 500
                CoroutineScope(IO).launch {
                    val rList = Helper.getSubreddits(query)
                    main.runOnUiThread {
                        adapter = SearchAdapter(rList, this@SearchFragment, query)
                        searchAdapter = adapter
                        rvSearch.adapter = adapter
                        rvSearch.layoutManager = CustomLLM(main)
                        cardLoading.animate().translationY(-500f).duration = 500

                        Runnable {
                            main.runOnUiThread {
                                cardLoading.visibility = View.GONE
                            }
                        }.runAfter(500)

                        v.clearFocus()
                        tvQuery.text = "Search results"
                    }
                }

                chipGroup.clearCheck()
                false
            }

            btnSearch.setOnClickListener {
                etSearch.onEditorAction(EditorInfo.IME_ACTION_DONE)
            }

            arrayOf(tvFeed, ivBackToFeed).forEach {
                it.setOnClickListener { main.onBackPressed() }
            }

            chipGroup.setOnCheckedChangeListener { _, checkedId ->
                try {
                    val chip = chipGroup.findViewById<Chip>(checkedId)
                    val query = chip.text.toString()

                    if (chip.id == chipVideos.id) {
                        return@setOnCheckedChangeListener
                    }

                    if (searchAdapter != null && searchAdapter!!.query == query) {

                        Runnable {
                            main.runOnUiThread {
                                scrollViewChips.smoothScrollTo((chip.x / 1.5).toInt(), 0)
                            }
                        }.runAfter(500)

                        return@setOnCheckedChangeListener
                    }

                    scrollViewChips.smoothScrollTo((chip.x / 1.5).toInt(), 0)
                    cardLoading.y = -500f
                    cardLoading.visibility = View.VISIBLE
                    cardLoading.animate().translationY(0f).duration = 500
                    CoroutineScope(IO).launch {
                        val rList = Helper.getSubreddits(query)
                        main.runOnUiThread {
                            adapter = SearchAdapter(rList, this@SearchFragment, query)
                            searchAdapter = adapter
                            rvSearch.adapter = adapter
                            rvSearch.layoutManager = CustomLLM(main)
                            cardLoading.animate().translationY(-500f).duration = 500

                            Runnable {
                                main.runOnUiThread {
                                    cardLoading.visibility = View.GONE
                                }
                            }.runAfter(500)

                        }
                    }

                    etSearch.text?.clear()
                    tvQuery.text = "Top subreddits today"
                } catch (e: Exception) {}
            }

            chipVideos.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    if (searchAdapter != null && searchAdapter!!.query == "") {
                        return@setOnCheckedChangeListener
                    }

                    if (Helper.isOnline(main)) {
                        cardLoading.y = -500f
                        cardLoading.visibility = View.VISIBLE
                        cardLoading.animate().translationY(0f).duration = 500

                        CoroutineScope(IO).launch {
                            val rList = Helper.getTopSubreddits()
                            main.runOnUiThread {
                                adapter = SearchAdapter(rList, this@SearchFragment, "")
                                searchAdapter = adapter
                                rvSearch.adapter = adapter
                                rvSearch.layoutManager = CustomLLM(main)
                                cardLoading.animate().translationY(-500f).duration = 500

                                Runnable {
                                    main.runOnUiThread {
                                        cardLoading.visibility = View.GONE
                                    }
                                }.runAfter(500)

                            }
                        }
                    } else {
                        main.longToast("No Internet 😔")
                    }
                }
            }

            switchNsfw.isChecked = nsfwAllowed()
            switchNsfw.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    allowNSFW()
                    if (!etSearch.text.isNullOrEmpty())  btnSearch.performClick()
                } else {
                    doNotAllowNSFW()
                    if (!etSearch.text.isNullOrEmpty())  btnSearch.performClick()
                }
            }
        }
    }

    override fun onDestroyView() {
        searchAdapter = adapter
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