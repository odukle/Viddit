package com.odukle.viddit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.odukle.viddit.Helper.Companion.backstack
import com.odukle.viddit.Helper.Companion.isOnline
import com.odukle.viddit.MainActivity.Companion.main
import com.odukle.viddit.databinding.ItemViewSearchBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

class SearchAdapter(private val list: List<Pair<String, String>>,private val fragment: SearchFragment, var query: String): RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

    inner class SearchViewHolder(val binder: ItemViewSearchBinding): RecyclerView.ViewHolder(binder.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binder  = DataBindingUtil.inflate<ItemViewSearchBinding>(
            LayoutInflater.from(parent.context),
            R.layout.item_view_search,
            parent,
            false
        )

        return SearchViewHolder(binder)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val pair = list[position]
        holder.binder.apply {
            Glide.with(root)
                .load(pair.first)
                .placeholder(R.drawable.ic_reddit)
                .into(ivIcon)

            tvSubredditName.text = pair.second

            cardSubreddit.setOnClickListener {
                if (!isOnline(main)) {
                    main.longToast("No internet 😔")
                    return@setOnClickListener
                }

                fragment.binder.cardLoading.y = -500f
                fragment.binder.cardLoading.visibility = View.VISIBLE
                fragment.binder.cardLoading.animate().translationY(0f).duration = 500
                CoroutineScope(IO).launch {
                    val subreddit = Helper.getSubredditInfo(pair.second)
                    main.runOnUiThread {
                        val fragmentTxn = main.supportFragmentManager.beginTransaction()
                        fragmentTxn.setCustomAnimations(R.anim.slide_from_bottom, R.anim.slide_to_bottom)
                        fragmentTxn.replace(R.id.container, SubRedditFragment.newInstance(subreddit))
                        fragmentTxn.addToBackStack("${backstack++}")
                        fragmentTxn.commit()
                    }
                }
            }

//            if (position == list.size - 1) {
//                fragment.binder.contactLayout.visibility = View.VISIBLE
//            } else {
//                fragment.binder.contactLayout.visibility = View.GONE
//            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }
}