package com.crskdev.plashpuzzle

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Created by Cristian Pela on 28.07.2019.
 */
interface GDPRChecker {

    fun load(): Flow<Status>

    fun save(status: Status): Flow<Unit>

    class Status(val enabled: Boolean, val dontRemindMe: Boolean)
}

class GDPRCheckerImpl(context: Context): GDPRChecker {

    private val store by lazy {
        context.getSharedPreferences("GDPRStatus", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_ENABLED = "KEY_ENABLED"
        private const val KEY_DONT_REMIND_ME = "KEY_DONT_REMIND_ME"
    }

    override fun load(): Flow<GDPRChecker.Status> = flow{
        emit(
            GDPRChecker.Status(
                store.getBoolean(KEY_ENABLED, false),
                store.getBoolean(KEY_DONT_REMIND_ME, false)
            )
        )
    }

    override fun save(status: GDPRChecker.Status): Flow<Unit> = flow {
        store.edit(true){
            putBoolean(KEY_ENABLED, status.enabled)
            putBoolean(KEY_DONT_REMIND_ME, status.dontRemindMe)
        }
        emit(Unit)
    }

}