package com.crskdev.plashpuzzle

import android.Manifest
import android.animation.LayoutTransition
import android.app.Dialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Window
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.forEach
import androidx.core.view.forEachIndexed
import androidx.core.view.isVisible
import androidx.core.view.setMargins
import androidx.lifecycle.Observer
import androidx.palette.graphics.Palette
import com.crashlytics.android.Crashlytics
import com.google.android.material.snackbar.Snackbar
import io.fabric.sdk.android.Fabric
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.gdpr_activation_toggle_layout.view.*
import kotlinx.android.synthetic.main.gdpr_alert_info_layout.view.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview


@FlowPreview
@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity() {

    companion object {
        const val PERMISSION_CODE_WRITE_EXTERNAL_STORAGE = 1223
    }

    private lateinit var model: PuzzleViewModel

    private var snackbarError: Snackbar? = null

    private var palette: Palette? = null

    private var dialogPreview: Dialog? = null

    private var dialogErrorDetailed: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        container.layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)

        model = viewModelFromProvider(this) {
            val context = applicationContext
            PuzzleViewModel(
                ImageRepositoryImpl(context),
                PuzzleStateLoaderImpl(context),
                GDPRCheckerImpl(context),
                SystemAbstractionsImpl(context)
            )
        }

        toolbar.apply {
            inflateMenu(R.menu.menu)
            menu
                .findItem(R.id.action_hint)
                ?.actionView = ImageButton(context).apply {
                setImageResource(R.drawable.ic_help_black_24dp)
                setBackgroundResource(context.attributeRes(android.R.attr.selectableItemBackgroundBorderless))
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            gridLayout.forEach {
                                (it as PuzzlePieceView).showHint = true
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            gridLayout.forEach {
                                (it as PuzzlePieceView).showHint = false
                            }
                        }
                    }
                    true
                }
            }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_reporting -> {
                        val gdprToggleLayout = LayoutInflater
                            .from(this@MainActivity)
                            .inflate(R.layout.gdpr_activation_toggle_layout, null, true)
                            .apply {
                                val gdprState = model.stateLiveData.value?.gdprState
                                switchGdpr.isChecked = gdprState?.isEnabled ?: false
                                switchGdpr.setOnCheckedChangeListener { _, isChecked ->
                                    model.intent(
                                        PuzzleViewModel.Intents.GDPRSave(
                                            isChecked,
                                            gdprState?.dontRemindMe ?: false
                                        )
                                    )
                                }
                            }
                        AlertDialog.Builder(this@MainActivity)
                            .setViewInCard(gdprToggleLayout) {
                                radius = dp(10.0f)
                            }
                            .setCancelable(true)
                            .create()
                            .apply {
                                requestWindowFeature(Window.FEATURE_NO_TITLE)
                                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                                window?.setGravity(Gravity.BOTTOM)
                                show()
                                window?.setLayout(dp(300), dp(100))
                            }
                        true
                    }
                    R.id.action_about -> {
                        AlertDialog.Builder(this@MainActivity.asThemeContext(R.style.AppDialogTheme))
                            .setTitle(R.string.about)
                            .setMessageLinkifyed(this@MainActivity, R.string.about_detail)
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .setCancelable(true)
                            .create()
                            .apply { show() }
                    }
                    R.id.action_peek -> {
                        model.stateLiveData.value?.source?.let { bitmap ->
                            dialogPreview = AlertDialog.Builder(this@MainActivity)
                                .setViewInCard(ImageView(this@MainActivity).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    adjustViewBounds = true
                                    val (w, h) = screenSize.let { size ->
                                        size.first - dp(56) to size.second - dp(56)
                                    }
                                    maxWidth = w
                                    maxHeight = h
                                    setImageBitmap(bitmap)
                                    palette?.getDarkMutedColor(
                                        ContextCompat.getColor(
                                            this@MainActivity,
                                            R.color.colorPrimary
                                        )
                                    )?.let { c ->
                                        setBackgroundColor(c)
                                    }
                                    setOnClickListener {
                                        dialogPreview?.dismiss()
                                    }
                                }) {
                                    setContentPadding(dp(1), dp(1), dp(1), dp(1))
                                    palette?.let { p ->
                                        setCardBackgroundColor(
                                            p.getLightMutedColor(
                                                ContextCompat.getColor(
                                                    this@MainActivity,
                                                    R.color.colorPrimary
                                                )
                                            )
                                        )
                                    }
                                }
                                .setCancelable(true)
                                .create()
                                .apply { show() }

                        }
                        true
                    }
                    R.id.action_new -> {
                        if(model.stateLiveData.value?.isCompleted == false) {
                            AlertDialog.Builder(this@MainActivity.asThemeContext(R.style.AppDialogTheme))
                                .setTitle("Warning")
                                .setMessage("This puzzle is not completed. Load new puzzle image?")
                                .setPositiveButton("OK") { d, _ ->
                                    d.dismiss()
                                    model.intent(PuzzleViewModel.Intents.LoadFromStore.random())
                                }
                                .setNegativeButton("Cancel") { d, _ ->
                                    d.dismiss()
                                }
                                .setCancelable(true)
                                .apply { show() }
                        }else{
                            model.intent(PuzzleViewModel.Intents.LoadFromStore.random())
                        }
                        true
                    }
                }
                false
            }
        }


        model.stateLiveData.observe(this, Observer { state ->
            buttonsVisibility(!state.isLoading)
            progressLoading.isVisible = state.isLoading
            if (state.isSourceChanged || gridLayout.childCount == 0) {
                if (!state.source.isRecycled) // or else app will crash from palette; [see Palette.java line 618]
                    Palette.from(state.source).generate { p ->
                        palette = p
                        p?.run {
                            val darkMutedColor = p.getDarkMutedColor(
                                ContextCompat.getColor(this@MainActivity, R.color.colorPrimary)
                            )
                            val lightVibrantColor = p.getLightVibrantColor(
                                ContextCompat.getColor(this@MainActivity, R.color.colorAccent)
                            )
                            val lightMutedColor = p.getLightMutedColor(
                                ContextCompat.getColor(this@MainActivity, R.color.colorPrimary)
                            )
                            val backgroundGradient = GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                intArrayOf(lightMutedColor, darkMutedColor)
                            )
                            progressLoading.setCardBackgroundColor(
                                ColorUtils.setAlphaComponent(
                                    darkMutedColor,
                                    150
                                )
                            )
                            btnDownload.backgroundTintList =
                                ColorStateList.valueOf(lightVibrantColor)
                            container.background = backgroundGradient
                            gridLayout.post {
                                gridLayout.forEach {
                                    (it as PuzzlePieceView).apply {
                                        dragEnteredColor = lightMutedColor
                                        hintChipColor = darkMutedColor
                                    }
                                }
                            }
                        }
                    }
                // imgPeak.setImageBitmap(puzzleState.source)
                gridLayout.removeAllViews()
                state.grid.forEachIndexed { index, piece ->
                    val view = PuzzlePieceView(this@MainActivity).apply {
                        pieceInfo = piece
                        onPuzzlePieceDrop = { targetView, sourceViewIndex ->
                            val targetViewIndex = targetView.tag as Int
                            model.intent(
                                PuzzleViewModel.Intents.Swap(
                                    sourceViewIndex,
                                    targetViewIndex
                                )
                            )
                        }
                        onPuzzlePieceDragging = { v ->
                            val scrollAmount = 30
                            val vScrollViewHeight = vScrollView.measuredHeight
                            val vScroll = vScrollView.scrollY
                            if (v.bottom > vScroll + vScrollViewHeight - v.measuredHeight / 4) {
                                vScrollView.smoothScrollBy(0, scrollAmount)
                            }
                            if (v.top < vScroll + v.measuredHeight / 4) {
                                vScrollView.smoothScrollBy(0, -scrollAmount)
                            }

                            val hScrollViewWidth = hScrollView.measuredWidth
                            val hScroll = hScrollView.scrollX
                            if (v.right > hScroll + hScrollViewWidth - v.measuredWidth / 4) {
                                hScrollView.smoothScrollBy(scrollAmount, 0)
                            }
                            if (v.left < hScroll + v.measuredWidth / 4) {
                                hScrollView.smoothScrollBy(-scrollAmount, -0)
                            }
                        }
                        tag = index
                    }
                    val params = GridLayout.LayoutParams(
                        GridLayout.spec(index / PuzzleViewModel.GRID_SIZE),
                        GridLayout.spec(index % PuzzleViewModel.GRID_SIZE)
                    ).apply {
                        this.height = GridLayout.LayoutParams.WRAP_CONTENT
                        this.width = GridLayout.LayoutParams.WRAP_CONTENT
                        this.setGravity(Gravity.CENTER)
                        if (!state.isCompleted)
                            setMargins(1.dp(resources))

                    }
                    gridLayout.addView(view, params)
                }
            } else if (!state.isLoading) {
                gridLayout.forEachIndexed { index, view ->
                    (view as PuzzlePieceView).pieceInfo = state.grid[index]
                }
            }

            btnDownload.isVisible =
                !state.isLoading && state.isCompleted && state.uriLocal.isEmpty()

            if (state.isCompleted) {
                gridLayout.forEach { view ->
                    require(view is PuzzlePieceView)
                    view.layoutParams = view.layoutParams.apply {
                        require(this is GridLayout.LayoutParams)
                        setMargins(0)
                    }
                    view.canDrag = false
                }
            }
        })

        model.eventLiveData.observe(this, Observer { event ->
            when (event) {
                is PuzzleViewModel.Event.GDPR -> {
                    val crashlytics = { Fabric.with(this, Crashlytics()) }
                    if (!event.isEnabled) {
                        if (!event.dontRemindMe) {
                            val gdprMessageView = LayoutInflater.from(this.asThemeContext())
                                .inflate(R.layout.gdpr_alert_info_layout, null)
                            AlertDialog.Builder(this.asThemeContext(R.style.AppDialogTheme))
                                .setTitle(R.string.gdpr_activation_title)
                                .setView(gdprMessageView)
                                .setPositiveButton("OK") { d, _ ->
                                    crashlytics.invoke()
                                    model.intent(
                                        PuzzleViewModel.Intents.GDPRSave(
                                            enable = true,
                                            dontRemindMe = gdprMessageView.checkDontRemindMe.isChecked
                                        )
                                    )
                                    d.dismiss()
                                }
                                .setNegativeButton(R.string.cancel) { d, _ ->
                                    model.intent(
                                        PuzzleViewModel.Intents.GDPRSave(
                                            enable = false,
                                            dontRemindMe = gdprMessageView.checkDontRemindMe.isChecked
                                        )
                                    )
                                    d.dismiss()
                                }
                                .create()
                                .apply { show() }
                        }
                    } else {
                        crashlytics.invoke()
                    }
                }
                is PuzzleViewModel.Event.Error -> {
                    buttonsVisibility(true)
                    progressLoading.isVisible = false
                    if (event == PuzzleViewModel.Event.Error.NO_ERROR) {
                        snackbarError?.dismiss()
                    } else {
                        val error = event.promptableException!!
                        toolbar.menu.findItem(R.id.action_new).isVisible = true
                        val message = error.message ?: getString(R.string.unknown_error)
                        snackbarError = Snackbar
                            .make(container, message, Snackbar.LENGTH_INDEFINITE)
                            .apply {
                                view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                                    .apply {
                                        maxLines = 5
                                        setOnClickListener {
                                            val text =
                                                TextView(context.asThemeContext())
                                                    .apply {
                                                        height = 300.dp(resources)
                                                        width = 150.dp(resources)
                                                        text = error.toString()
                                                        movementMethod = ScrollingMovementMethod()
                                                        setOnLongClickListener {
                                                            dialogErrorDetailed?.dismiss()
                                                            true
                                                        }
                                                    }
                                            dialogErrorDetailed =
                                                AlertDialog.Builder(this@MainActivity)
                                                    .setViewInCard(text)
                                                    .setCancelable(true)
                                                    .create()
                                                    .apply { show() }

                                        }
                                    }

                                setAction("Retry") {
                                    model.retry()
                                }
                                show()
                            }
                    }
                }
            }
        })

        btnZoomPlus.setOnClickListener {
            model.intent(PuzzleViewModel.Intents.Scale(0.1f))
        }

        btnZoomMinus.setOnClickListener {
            model.intent(PuzzleViewModel.Intents.Scale(-0.1f))
        }

        btnCancel.setOnClickListener {
            model.intent(PuzzleViewModel.Intents.Cancel)
        }

        btnDownload.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSION_CODE_WRITE_EXTERNAL_STORAGE
                )
            } else {
                model.stateLiveData.value?.uri?.run {
                    model.intent(PuzzleViewModel.Intents.ImageSave(this))
                }
            }
        }


        //todo scale gesture zoom in/out
//        val scaleGestureDetector = ScaleGestureDetector(
//            this,
//            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
//                var prevFactor = 0.0f
//                override fun onScale(detector: ScaleGestureDetector): Boolean {
//                    Log.d("SCALE _OB", detector.scaleFactor.toString())
//                    return true
//                }
//
//                override fun onScaleEnd(detector: ScaleGestureDetector) {
//                    val factor = detector.scaleFactor
//                    val delta = factor - prevFactor
//                    Log.d("SCALE", factor.toString() + " d: ${delta}")
//                    model.intent(PuzzleViewModel.Intents.Scale(delta))
//                    prevFactor = factor
//                }
//            })

//        zoomPane.setOnTouchListener { v, event ->
//            if(event.pointerCount == 2){
//                scaleGestureDetector.onTouchEvent(event)
//            }else{
//                gridLayout.dispatchTouchEvent(event)
//            }
//            true
//
//        }


    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_CODE_WRITE_EXTERNAL_STORAGE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    model.stateLiveData.value?.uri?.run {
                        model.intent(PuzzleViewModel.Intents.ImageSave(this))
                    }
                }
            }
        }
    }

    private fun buttonsVisibility(isVisible: Boolean, vararg skip: Int) {
        with(toolbar.menu) {
            if (!skip.contains(R.id.action_new))
                findItem(R.id.action_new).isVisible = isVisible
            if (!skip.contains(R.id.action_peek))
                findItem(R.id.action_peek).isVisible = isVisible
            if (!skip.contains(R.id.action_hint))
                findItem(R.id.action_hint).isVisible = isVisible
            if (!skip.contains(R.id.action_settings))
                findItem(R.id.action_settings).isVisible = isVisible
        }
        if (!skip.contains(R.id.btnZoomPlus))
            btnZoomPlus.isVisible = isVisible
        if (!skip.contains(R.id.btnZoomMinus))
            btnZoomMinus.isVisible = isVisible
    }


    override fun onStop() {
        model.save()
        super.onStop()
    }

    override fun onBackPressed() {
        when {
            dialogPreview?.isShowing == true -> dialogPreview?.dismiss()
            dialogErrorDetailed?.isShowing == true -> dialogErrorDetailed?.dismiss()
            else -> super.onBackPressed()
        }
    }

}
