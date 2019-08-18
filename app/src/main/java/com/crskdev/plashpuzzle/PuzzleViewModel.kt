package com.crskdev.plashpuzzle

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

@ExperimentalCoroutinesApi
@FlowPreview
class PuzzleViewModel(private val repository: ImageRepository,
                      private val puzzleStateLoader: PuzzleStateLoader,
                      private val gdprChecker: GDPRChecker,
                      private val systemAbstractions: SystemAbstractions) : ViewModel() {


    companion object {
        const val GRID_SIZE = 4
        const val GRID_LEN = GRID_SIZE * GRID_SIZE
        val EMPTY_BITMAP: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        private fun randomUri() = "https://picsum.photos/720/1280/?temp=${UUID.randomUUID()}"
    }

    val eventLiveData: LiveData<Event> = LiveEvent<Event>().apply{
        value = Event.Idle
    }

    val stateLiveData: LiveData<State> = MutableLiveData()

    private val intentLiveData: MutableLiveData<Intents> = MutableLiveData()

    init {
        require(eventLiveData is LiveEvent)
        require(stateLiveData is MutableLiveData)
        viewModelScope.launch {
            intentLiveData
                .asFlow()
                .startWith(Intents.Init)
                .flowOn(Dispatchers.Main)
                .flatMapMerge { intent ->
                    when (intent) {
                        is Intents.Init -> {
                            Flows.merge(
                                gdprChecker
                                    .load()
                                    .onEach {
                                        eventLiveData
                                            .postValue(Event.GDPR(it.enabled, it.dontRemindMe))
                                    }
                                    .map { status ->
                                        Action.GDPRStatus(status.enabled, status.dontRemindMe)
                                    },
                                puzzleStateLoader.load()
                                    .switchMap { ss ->
                                        val uri = ss.uri.takeIf { it.isNotEmpty() } ?: randomUri()
                                        repository
                                            .fetch(uri)
                                            .catchDisplayable()
                                            .map { bitmap -> scaleInitialSource(bitmap) }
                                            .map { bitmap -> Action.StoredState(bitmap, ss) }
                                            .startWith(Action.ImageLoading)
                                    }
                            ).flowOn(Dispatchers.IO)
                        }
                        is Intents.LoadFromStore -> repository
                            .fetch(intent.uri)
                            .map { bitmap -> scaleInitialSource(bitmap) }
                            .map { bitmap -> Action.ImageReady(bitmap, intent.uri) }
                            .catchDisplayable(Action.Cancel)
                            .flowOn(Dispatchers.IO)
                            .startWith(Action.ImageLoading)
                        is Intents.ImageSave -> {
                            repository
                                .save(intent.uri)
                                .catchDisplayable()
                                .map { localUri -> Action.ImageSaved(localUri) }
                                .flowOn(Dispatchers.IO)
                                .startWith(Action.ImageLoading)
                        }
                        is Intents.GDPRSave -> {
                            gdprChecker
                                .save(GDPRChecker.Status(intent.enable, intent.dontRemindMe))
                                .map { Action.GDPRStatus(intent.enable, intent.dontRemindMe) }
                                .flowOn(Dispatchers.IO)
                        }
                        is Intents.Scale -> flowOf(Action.Scale(intent.factor))
                        is Intents.Swap -> flowOf(
                            Action.Swap(
                                intent.sourceIndex,
                                intent.targetIndex
                            )
                        )
                        is Intents.Cancel -> repository.cancelFetch().map { Action.Cancel }
                        else -> flowOf(Action.None)
                    }
                }
                .scan(State()) { state, action ->
                    when (action) {
                        Action.ImageLoading -> state.copy(isLoading = true, isSourceChanged = false)
                        Action.Cancel -> state.copy(isLoading = false, isSourceChanged = false)
                        is Action.ImageReady -> withContext(Dispatchers.Default) {
                            ready(
                                state,
                                action
                            )
                        }
                        is Action.ImageSaved -> saved(state, action)
                        is Action.StoredState -> withContext(Dispatchers.Default) {
                            restore(state, action)
                        }
                        is Action.Scale -> withContext(Dispatchers.Default) { scale(state, action) }
                        is Action.Swap -> withContext(Dispatchers.Default) { swap(state, action) }
                        is Action.GDPRStatus -> gdprStatus(state, action)
                        else -> state
                    }
                }
                .collect {
                    stateLiveData.value = it
                }
        }
    }

    private fun <T> Flow<T>.catchDisplayable(resumeValue: T? = null) = catch { e ->
        if (e is PromptableException) {
            if(e.cause !is CancellationException){
                (eventLiveData as MutableLiveData).postValue(Event.Error(e))
            }
            resumeValue?.also {
                emit(it)
            }
        } else {
            throw e
        }
    }

    private fun gdprStatus(state: State, action: Action.GDPRStatus): State =
        state.copy(gdprState = State.GDPRState(action.isEnabled, action.dontRemindMe))

    private fun saved(state: State, action: Action.ImageSaved): State =
        state.copy(isLoading = false, uriLocal = action.localUri)

    private fun swap(state: State, action: Action.Swap): State =
        with(state) {
            val sourceIndex = action.sourceIndex
            val targetIndex = action.targetIndex
            val temp = grid[sourceIndex].copy()
            val mGrid = grid.toMutableList()
            mGrid[sourceIndex] = grid[targetIndex].copy()
            mGrid[targetIndex] = temp
            val isCompleted = checkIfCompleted(mGrid)
            this.copy(isCompleted = isCompleted, grid = mGrid, isSourceChanged = false)
        }

    private fun scale(state: State, action: Action.Scale): State {
        assert(state.source != EMPTY_BITMAP)
        val accumulatedScaleFactor = state.scaleFactor + action.factor
        return if (accumulatedScaleFactor in (-0.3f..1.0f)) {
            with(state) {
                val mGrid = grid.toMutableList()
                scaleGrid(
                    state.source,
                    mGrid,
                    accumulatedScaleFactor,
                    chunkSize(state.source)
                )
                copy(grid = mGrid, scaleFactor = accumulatedScaleFactor, isSourceChanged = false)
            }
        } else {
            state
        }
    }

    private fun scaleInitialSource(source: Bitmap): Bitmap {
        val (w, h) = systemAbstractions.screenSize
            .run {
                val padding = systemAbstractions.dp(32)
                first - padding to second - padding
            }
        return if (w != source.width || h != source.height) {
            Bitmap.createScaledBitmap(source, w, h, true)
        } else {
            source
        }

    }

    private fun scaleGrid(original: Bitmap, grid: MutableList<PuzzlePieceInfo>, factor: Float, size: Pair<Int, Int>) {
        assert(original != EMPTY_BITMAP)
        for (i in 0 until grid.size) {
            val piece = grid[i]
            val (r, c) = gridCoordinate(grid[i].index)
            grid[i] = piece.copy(
                source = scalePiece(chunkBitmapSized(original, r, c, size), factor)
            )
        }
    }

    private fun scalePiece(bitmap: Bitmap, factor: Float): Bitmap? {
        val newWidth = bitmap.width + (bitmap.width * factor)
        val newHeight = bitmap.height + (bitmap.height * factor)
//        return Bitmap.createBitmap(newWidth.toInt(), newHeight.toInt(), Bitmap.Config.ARGB_8888)
//            .applyCanvas {
//                matrix = Matrix().apply { scale(factor, factor) }
//                drawBitmap(bitmap, 0.0f, 0.0f, Paint(FILTER_BITMAP_FLAG or DITHER_FLAG))
//            }

        return Bitmap.createScaledBitmap(
            bitmap,
            newWidth.toInt(),
            newHeight.toInt(),
            true
        )
    }


    private fun ready(state: State, action: Action.ImageReady): State {
        // puzzleState.source.recycle() --> let glide do its thing
        val bitmap = action.bitmap
        val (sizeW, sizeH) = chunkSize(bitmap)
        val grid = mutableListOf<PuzzlePieceInfo>()
        for (r in 0 until GRID_SIZE) {
            for (c in 0 until GRID_SIZE) {
                val piece = chunkBitmap(bitmap, r, c, sizeW, sizeH)
                val index = r * GRID_SIZE + c
                grid.add(PuzzlePieceInfo(piece, index))
            }
        }
        if (state.scaleFactor != 0.0f)
            scaleGrid(
                bitmap,
                grid,
                state.scaleFactor,
                chunkSize(bitmap)
            )
        grid.shuffle()
        return state.copy(
            isLoading = false,
            source = bitmap,
            uri = action.uri,
            uriLocal = "",
            isCompleted = false,
            isSourceChanged = true,
            grid = grid,
            scaleFactor = state.scaleFactor
        )
    }

    private fun chunkBitmap(original: Bitmap, row: Int, column: Int, width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(original, column * width, row * height, width, height)

    private fun chunkBitmapSized(original: Bitmap, row: Int, column: Int, size: Pair<Int, Int>) =
        chunkBitmap(original, row, column, size.first, size.second)

    private fun chunkSize(bitmap: Bitmap): Pair<Int, Int> =
        bitmap.width / GRID_SIZE to bitmap.height / GRID_SIZE

    private fun gridCoordinate(index: Int): Pair<Int, Int> = index / GRID_SIZE to index % GRID_SIZE

    private fun restore(state: State, action: Action.StoredState): State {
        val indices = if (action.puzzleState.indices.size != GRID_LEN) {
            //the grid config was changed, state grid is invalid
            //the grid indices are generated (randomly - if not completed) by the new grid length
            val indices = mutableListOf<Int>().apply {
                for (i in 0 until GRID_LEN) {
                    add(i)
                }
                if (!action.puzzleState.isCompleted)
                    shuffle()
            }
            indices
        } else
            action.puzzleState.indices
        val bitmap = action.bitmap
        val (sizeW, sizeH) = chunkSize(bitmap)
        val grid = indices.map {
            val (r, c) = gridCoordinate(it)
            val piece = chunkBitmap(bitmap, r, c, sizeW, sizeH)
            PuzzlePieceInfo(piece, it)
        }.toMutableList()
        if (action.puzzleState.scaleFactor != 0.0f)
            scaleGrid(
                bitmap,
                grid,
                action.puzzleState.scaleFactor,
                chunkSize(bitmap)
            )
        return state.copy(
            isLoading = false,
            source = bitmap,
            uri = action.puzzleState.uri,
            uriLocal = action.puzzleState.uriLocal,
            isCompleted = action.puzzleState.isCompleted,
            isSourceChanged = true,
            grid = grid,
            scaleFactor = action.puzzleState.scaleFactor
        )
    }

    private fun checkIfCompleted(grid: List<PuzzlePieceInfo>): Boolean {
        var completionCount = GRID_LEN
        grid.forEachIndexed { index, piece ->
            if (piece.index == index) {
                completionCount--
            }
        }
        return completionCount == 0
    }


    fun intent(intent: Intents) {
        require(eventLiveData is MutableLiveData)
        eventLiveData.value = Event.Idle
        intentLiveData.value = intent
    }

    fun retry() {
        require(eventLiveData is MutableLiveData)
        eventLiveData.value = Event.Idle
        intentLiveData.value?.also { prevIntent ->
            intentLiveData.value = prevIntent
        }
    }

    fun save() {
        val state = stateLiveData.value?.copy()
        if (state != null)
            GlobalScope.launch {
                withContext(Dispatchers.Default) {
                    puzzleStateLoader.save(
                        PuzzleStateLoader.StoredState(
                            state.uri,
                            state.uriLocal,
                            state.isCompleted,
                            state.grid.map { it.index },
                            state.scaleFactor
                        )
                    )
                }
            }

    }

    sealed class Action {
        object ImageLoading : Action()
        class ImageSaved(val localUri: String) : Action()
        class ImageReady(val bitmap: Bitmap, val uri: String) : Action()

        object Cancel : Action()
        object None : Action()

        class GDPRStatus(val isEnabled: Boolean, val dontRemindMe: Boolean) : Action()
        class StoredState(val bitmap: Bitmap, val puzzleState: PuzzleStateLoader.StoredState) :
            Action()

        class Scale(val factor: Float) : Action()
        class Swap(val sourceIndex: Int, val targetIndex: Int) : Action()
    }


    sealed class Intents {
        object Cancel : Intents()
        object Init : Intents()
        class GDPRSave(val enable: Boolean, val dontRemindMe: Boolean) : Intents()
        class ImageSave(val uri: String) : Intents()
        class LoadFromStore(val uri: String) : Intents() {
            companion object {
                fun random(): LoadFromStore = LoadFromStore(randomUri())
            }
        }

        class Scale(val factor: Float) : Intents()
        class Swap(val sourceIndex: Int, val targetIndex: Int) : Intents()
    }

    sealed class Event {
        data class GDPR(val isEnabled: Boolean, val dontRemindMe: Boolean) : Event()
        class Error(val promptableException: PromptableException?) : Event() {
            companion object {
                val NO_ERROR = Error(null)
            }
        }
        object Idle: Event()
    }

    data class State(
        val isLoading: Boolean = false,
        val isCompleted: Boolean = false,
        val source: Bitmap = Bitmap.createBitmap(
            1,
            1,
            Bitmap.Config.ARGB_8888
        ),
        val uri: String = "",
        val uriLocal: String = "",
        val isSourceChanged: Boolean = false,
        val scaleFactor: Float = 0.0f,
        val grid: List<PuzzlePieceInfo> = emptyList(),

        val gdprState: GDPRState = GDPRState()) {

        class GDPRState(val isEnabled: Boolean = false, val dontRemindMe: Boolean = false)
    }
}



