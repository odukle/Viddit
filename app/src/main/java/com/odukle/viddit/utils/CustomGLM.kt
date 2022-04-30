package com.odukle.viddit.utils

import android.content.Context
import android.util.Log
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CustomGLM(context: Context, spanCount: Int) : GridLayoutManager(context, spanCount) {
    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: Exception) {
            Log.e(this::class.simpleName, "onLayoutChildren: ${e.message}", )
        }
    }
}