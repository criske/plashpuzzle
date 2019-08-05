package com.crskdev.plashpuzzle

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Created by Cristian Pela on 21.07.2019.
 */
/**
 * Created by Cristian Pela on 26.01.2019.
 */

fun String.linkify(): SpannableString = SpannableString(this)
    .apply { Linkify.addLinks(this, Linkify.WEB_URLS) }


fun TextView.textLinkifyed(@StringRes stringRes: Int) {
    val spannableString = context.getString(stringRes).linkify()
    text = spannableString
    movementMethod = LinkMovementMethod.getInstance()
}


fun AlertDialog.Builder.setMessageLinkifyed(context: Context, @StringRes stringRes: Int):
        AlertDialog.Builder {
    //recreating https://android.googlesource.com/platform/frameworks/base/+/master/core/res/res/layout/alert_dialog.xml
    // from line 71 to 90
    val themeContext = context.asThemeContext()
    val panel = LinearLayout(themeContext).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        orientation = LinearLayout.VERTICAL
        addView(ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16), dp(2), dp(10), dp(12))
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
                setPadding(dp(5))
                textLinkifyed(stringRes)
            })
        })
    }
    return setView(panel)
}

inline fun <reified V : ViewModel> viewModelFromProvider(activity: FragmentActivity, crossinline provider: FragmentActivity.() -> V): V =
    ViewModelProviders.of(activity, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return provider(activity) as T
        }
    }).get(V::class.java)

inline fun <reified V : ViewModel> viewModelFromProvider(fragment: Fragment, crossinline provider: Fragment.() -> V): V =
    ViewModelProviders.of(fragment, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return provider(fragment) as T
        }
    }).get(V::class.java)

fun Float.dp(resources: Resources): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)


fun Int.dpF(resources: Resources): Float =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        resources.displayMetrics
    )

fun Int.dp(resources: Resources): Int = dpF(resources).roundToInt()

fun Activity.dp(float: Float): Float = float.dp(resources)

fun Activity.dpF(int: Int): Float = int.dpF(resources)

fun Activity.dp(int: Int) = int.dp(resources)

fun View.dp(float: Float): Float = float.dp(resources)

fun View.dpF(int: Int): Float = int.dpF(resources)

fun View.dp(int: Int) = int.dp(resources)


val Context.screenSize: Pair<Int, Int>
    get() {
        val metrics = Resources.getSystem().getDisplayMetrics();
        return metrics.widthPixels to metrics.heightPixels
    }

fun <T> Flow<T>.startWith(t: T): Flow<T> = flow {
    emit(t)
    collect { emit(it) }
}

fun Context.asThemeContext(@StyleRes styleRes: Int = R.style.AppTheme) =
    ContextThemeWrapper(this, styleRes)

fun Context.colorRes(@ColorRes colorRes: Int) = ContextCompat.getColor(this, colorRes)

fun Context.attributeRes(@AttrRes attrRes: Int, typedValue: TypedValue = TypedValue()): Int {
    theme.resolveAttribute(attrRes, typedValue, true)
    assert(typedValue.resourceId != 0) {
        "Attr Resource ID invalid ${typedValue.resourceId}"
    }
    return typedValue.resourceId
}

object Flows {

    @FlowPreview
    fun <T> merge(vararg flows: Flow<T>): Flow<T> =
        channelFlow {
            coroutineScope {
                flows.forEach { f ->
                    launch {
                        f.collect {
                            offer(it)
                        }
                    }
                }
            }
        }

    @FlowPreview
    fun <T> flipFlop(flip: T, flop: T): Flow<T> = flowOf(flop).startWith(flip)

}


fun <T> LiveData<T>.asFlow() = callbackFlow<T> {

    val observer = Observer<T> { offer(it) }
    this@asFlow.observeForever(observer)
    this@asFlow.value?.let { offer(it) }

    awaitClose { this@asFlow.removeObserver(observer) }
}

inline fun <T> LiveData<T>.distinctUntilChanged(crossinline predicate: (T, T) -> Boolean): LiveData<T> {
    val mutableLiveData: MediatorLiveData<T> = MediatorLiveData()
    mutableLiveData.addSource(this, object : Observer<T> {
        var lastValue: T? = null
        override fun onChanged(t: T) {
            val prevT = lastValue
            if (prevT == null || predicate(prevT, t)) {
                mutableLiveData.value = t
                lastValue = t
            }
        }
    })
    return mutableLiveData
}


inline fun AlertDialog.Builder.setViewInCard(view: View,
                                             cardConfig: CardView.() -> Unit = {
                                                 val pad = dp(8)
                                                 setContentPadding(pad, pad, pad, pad)
                                                 val dp5 = dpF(5)
                                                 cardElevation = dp5
                                                 radius = dp5
                                             }): AlertDialog.Builder =
    setView(CardView(view.context).apply {
        cardConfig()
        addView(view)
    })


class LiveEvent<T> : MediatorLiveData<T>() {

    private val observers = ConcurrentHashMap<LifecycleOwner, MutableSet<ObserverWrapper<in T>>>()

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        val wrapper = ObserverWrapper(observer)
        val set = observers[owner]
        set?.apply {
            add(wrapper)
        } ?: run {
            val newSet = Collections
                .newSetFromMap(ConcurrentHashMap<ObserverWrapper<in T>, Boolean>())
            newSet.add(wrapper as ObserverWrapper<T>)
            observers[owner] = newSet
        }
        super.observe(owner, wrapper)
    }

    override fun removeObservers(owner: LifecycleOwner) {
        observers.remove(owner)
        super.removeObservers(owner)
    }

    override fun removeObserver(observer: Observer<in T>) {
        observers.forEach {
            if (it.value.remove(observer as ObserverWrapper<in T>)) {
                if (it.value.isEmpty()) {
                    observers.remove(it.key)
                }
                return@forEach
            }
        }
        super.removeObserver(observer)
    }

    @MainThread
    override fun setValue(t: T?) {
        observers.forEach { it.value.forEach { wrapper -> wrapper.newValue() } }
        super.setValue(t)
    }

    /**
     * Used for cases where T is Void, to make calls cleaner.
     */
    @MainThread
    fun call() {
        value = null
    }

    private class ObserverWrapper<T>(private val observer: Observer<T>) : Observer<T> {

        private val pending = AtomicBoolean(false)

        override fun onChanged(t: T?) {
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(t)
            }
        }

        fun newValue() {
            pending.set(true)
        }
    }
}