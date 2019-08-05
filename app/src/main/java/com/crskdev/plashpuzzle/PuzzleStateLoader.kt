package com.crskdev.plashpuzzle

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface PuzzleStateLoader {

    data class StoredState(
        val uri: String = "",
        val uriLocal: String = "",
        val isCompleted: Boolean = false,
        val indices: List<Int> = emptyList(),
        val scaleFactor: Float = 0.0f)

    fun load(): Flow<StoredState>

    suspend fun save(storedState: StoredState)

}

class PuzzleStateLoaderImpl(context: Context) :
    PuzzleStateLoader {

    private val store = context.getSharedPreferences(
        "PuzzleState",
        Context.MODE_PRIVATE
    )

    companion object {
        const val KEY_STATE_INDICES = "KEY_STATE_INDICES_"
        const val KEY_STATE_IS_COMPLETED = "KEY_STATE_IS_COMPLETED"
        const val KEY_STATE_SCALE_FACTOR = "KEY_STATE_SCALE_FACTOR"
        const val KEY_STATE_URI = "KEY_IMAGE_URI"
        const val KEY_STATE_URI_LOCAL = "KEY_IMAGE_URI_LOCAL"
    }

    override fun load(): Flow<PuzzleStateLoader.StoredState> =
        flow {

                val uri = store.getString(KEY_STATE_URI, "")?: ""
                val uriLocal = store.getString(KEY_STATE_URI_LOCAL, "")?: ""
                val indices =
                    store.getString(KEY_STATE_INDICES, null)
                        ?.split(",")
                        ?.map {
                            try {
                                it.toInt()
                            }catch (ex: Exception){
                                0
                            }
                        } ?: emptyList()
                val isCompleted = store.getBoolean(KEY_STATE_IS_COMPLETED, false)
                val scaleFactor = store.getFloat(KEY_STATE_SCALE_FACTOR, 0.0f)

                val state = PuzzleStateLoader.StoredState(
                    uri = uri,
                    uriLocal = uriLocal,
                    isCompleted = isCompleted,
                    scaleFactor = scaleFactor,
                    indices = indices
                )

            emit(state)
        }

    override suspend fun save(storedPuzzleState: PuzzleStateLoader.StoredState) =
        coroutineScope {
                store.edit(true) {
                    if(storedPuzzleState.uri.isNotEmpty())
                        putString(KEY_STATE_URI, storedPuzzleState.uri)
                    putString(KEY_STATE_INDICES, storedPuzzleState.indices.joinToString(","))
                    putFloat(KEY_STATE_SCALE_FACTOR, storedPuzzleState.scaleFactor)
                    putBoolean(KEY_STATE_IS_COMPLETED, storedPuzzleState.isCompleted)
                    putString(KEY_STATE_URI_LOCAL, storedPuzzleState.uriLocal)
                }
        }

}