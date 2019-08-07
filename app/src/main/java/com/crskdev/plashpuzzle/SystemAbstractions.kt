package com.crskdev.plashpuzzle

import android.content.Context

/**
 * Created by Cristian Pela on 05.08.2019.
 */
interface SystemAbstractions {

    val screenSize: Pair<Int, Int>

    fun dp(int: Int): Int

}

class SystemAbstractionsImpl(private val context: Context): SystemAbstractions{

    override fun dp(int: Int): Int = int.dp(context.resources)

    override val screenSize: Pair<Int, Int> = context.screenSize

}