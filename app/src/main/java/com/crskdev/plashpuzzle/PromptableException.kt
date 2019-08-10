package com.crskdev.plashpuzzle

import java.lang.Error

/**
 * Created by Cristian Pela on 28.07.2019.
 */
open class PromptableException(cause: Exception?) : Throwable(cause){
    constructor(message: String):this(java.lang.Exception(message))
}