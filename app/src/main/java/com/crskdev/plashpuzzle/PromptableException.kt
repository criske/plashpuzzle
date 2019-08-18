package com.crskdev.plashpuzzle

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Created by Cristian Pela on 28.07.2019.
 */
open class PromptableException(cause: Exception?) : Throwable(cause) {

    constructor(message: String) : this(java.lang.Exception(message))

    fun promptStackTrace(): String? = cause?.let {
        val stringWriter = StringWriter()
        it.printStackTrace(PrintWriter(stringWriter))
        stringWriter.toString()
    }
}