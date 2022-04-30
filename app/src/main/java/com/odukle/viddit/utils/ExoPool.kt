package com.odukle.viddit.utils

import com.google.android.exoplayer2.ExoPlayer

interface ExoPool {
    fun acquire(): ExoPlayer
    fun release(player: ExoPlayer)
}