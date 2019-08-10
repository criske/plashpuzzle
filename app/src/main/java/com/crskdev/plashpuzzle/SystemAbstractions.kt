package com.crskdev.plashpuzzle

import android.content.Context
import android.content.res.Configuration

/**
 * Created by Cristian Pela on 05.08.2019.
 */
interface SystemAbstractions {

    val screenSize: Pair<Int, Int>

    fun dp(int: Int): Int

}

class SystemAbstractionsImpl(private val context: Context): SystemAbstractions{

    override fun dp(int: Int): Int = int.dp(context.resources)

    override val screenSize: Pair<Int, Int>
        get() {
            val orientation = context.resources.configuration.orientation
            val screeSize = context.screenSize
            return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                screeSize.second to screeSize.first // swap width and height
            } else {
                screeSize
            }
        }

}