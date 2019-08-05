package com.crskdev.plashpuzzle

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.DragEvent
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.puzzle_piece_layout.view.*
import kotlin.properties.Delegates


/**
 * Created by Cristian Pela on 07.07.2019.
 */
class PuzzlePieceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        LayoutInflater.from(context).inflate(R.layout.puzzle_piece_layout, this, true)
    }

    var pieceInfo: PuzzlePieceInfo by Delegates.observable(PuzzlePieceInfo.EMPTY) { _, _, new ->
        new.source?.also {
            imgPiece.setImageBitmap(it)
        }
    }

    var showHint: Boolean by Delegates.observable(false) { _, _, new ->
        chipPiece.isVisible = new
        val gridIndex =  tag?.toString()?.toInt() ?: -1
        if (new) {
            if(gridIndex != -1) {
                val hintColor = if (pieceInfo.index == tag?.toString()?.toInt() ?: -1) {
                    Color.GREEN
                } else {
                    Color.RED
                }
                imgPiece.setColorFilter(hintColor, PorterDuff.Mode.ADD)
                chipPiece.text = (pieceInfo.index + 1).toString()
            }
        } else {
            imgPiece.colorFilter = null
        }
    }

    var onPuzzlePieceDrop: (PuzzlePieceView, Int) -> Unit = { _, _ -> }

    var hintChipColor: Int by Delegates.observable(context.colorRes(R.color.colorPrimary)){ _, _, c->
        chipPiece.background?.also {
            val d = it.mutate()
            if(d is GradientDrawable){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    d.color = ColorStateList.valueOf(ColorUtils.setAlphaComponent(c, 120))
                    chipPiece.background = d
                }
            }
        }
    }

    var onPuzzlePieceDragging: (PuzzlePieceView) -> Unit = { _ -> }

    var canDrag = true

    var dragEnteredColor: Int = ContextCompat.getColor(context, R.color.colorAccent)



    init {
        setOnLongClickListener {
            if (canDrag) {
                val item = ClipData.Item(tag.toString())
                val data =
                    ClipData("PuzzleDrag", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
                val dragShadow = DragShadowBuilder(it)
                ViewCompat.startDragAndDrop(it, data, dragShadow, Any(), 0)
            }
            canDrag
        }
        setOnDragListener { v, event ->
            require(v is PuzzlePieceView)
            when (event.action) {
                DragEvent.ACTION_DROP -> {
                    val item = event.clipData.getItemAt(0)
                    val sourceIndex = item.text.toString().toInt()
                    imgPiece.colorFilter = null
                    onPuzzlePieceDrop(v, sourceIndex)
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    imgPiece.setColorFilter(dragEnteredColor, PorterDuff.Mode.ADD)
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    imgPiece.colorFilter = null
                }
                DragEvent.ACTION_DRAG_LOCATION -> {
                    onPuzzlePieceDragging(v)
                }
            }
            true
        }
    }

}
