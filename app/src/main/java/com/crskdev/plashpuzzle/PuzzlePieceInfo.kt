package com.crskdev.plashpuzzle

import android.graphics.Bitmap

/**
 * Created by Cristian Pela on 07.07.2019.
 */
data class PuzzlePieceInfo(val source: Bitmap?, val index: Int) {
    companion object {
        val EMPTY = PuzzlePieceInfo(null, -1)
    }
}
