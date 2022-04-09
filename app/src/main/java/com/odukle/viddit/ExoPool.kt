package com.odukle.viddit

import com.google.android.exoplayer2.ExoPlayer

interface ExoPool {
    fun acquire(): ExoPlayer
    fun release(player: ExoPlayer)
}