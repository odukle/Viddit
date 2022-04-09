package com.odukle.viddit

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class Video(
    val title: String,
    val name: String,
    val subreddit: String,
    val subredditPrefixed: String,
    val selfText: String,
    val selfTextHtml: String,
    val author: String,
    val flair: String,
    val upVotes: String,
    val comments: String,
    val created: Long,
    val isVideo: Boolean,
    val video: String,
    val videoDownloadUrl: String,
    val thumbnail: String,
    val gif: String,
    val permalink: String,
) : Parcelable