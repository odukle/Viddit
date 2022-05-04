package com.odukle.viddit.models

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class SubReddit (
    val title: String,
    val titlePrefixed: String,
    val desc: String,
    val headerImage: String,
    val icon: String,
    val banner: String,
    val subscribers: String,
    val fullDesc: String,
): Parcelable