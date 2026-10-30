package com.frak89.gwackamole.ui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.frak89.gwackamole.core.PendingApplier
import com.frak89.gwackamole.data.Store

/** Piccolo kit di UI programmatica (nessuna dipendenza esterna). */
object C {
    const val GROUND = 0xFFF7F1E3.toInt()
    const val CARD = 0xFFFFFFFF.toInt()
    const val INK = 0xFF2B2118.toInt()
    const val MUTED = 0xFF5C4D3D.toInt()
    const val GREEN = 0xFF2F4A17.toInt()
    const val GRASS = 0xFF5B8C32.toInt()
    const val ORANGE = 0xFFC2410C.toInt()
    const val SOFT = 0xFFF2EBDA.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color); cornerRadius = radius
    if (stroke > 0) setStroke(stroke, strokeColor)
}

/** Pagina scorrevole con colonna verticale. */
fun Activity.page(): LinearLayout {
    val scroll = ScrollView(this).apply { setBackgroundColor(C.GROUND); fitsSystemWindows = true }
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(20), dp(18), dp(32))
    }
    scroll.addView(col)
    setContentView(scroll)
    return col
}

fun LinearLayout.gap(h: Int = 12) = addView(View(context), LinearLayout.LayoutParams(1, context.dp(h)))

fun LinearLayout.eyebrow(t: String) = TextView(context).apply {
    text = t.uppercase(); textSize = 12f; setTextColor(C.MUTED); typeface = Typeface.DEFAULT_BOLD
    letterSpacing = 0.1f
}.also { addView(it) }

fun LinearLayout.title(t: String, size: Float = 28f) = TextView(context).apply {
    text = t; textSize = size; setTextColor(C.GREEN); typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
}.also { addView(it) }

fun LinearLayout.body(t: String, color: Int = C.MUTED, size: Float = 14f) = TextView(context).apply {
    text = t; textSize = size; setTextColor(color); setLineSpacing(0f, 1.15f)
}.also { addView(it) }

fun LinearLayout.card(build: LinearLayout.() -> Unit): LinearLayout {
    val c = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(C.CARD, context.dp(18).toFloat())
        setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(14))
    }
    c.build()
    addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { bottomMargin = context.dp(10) })
    return c
}

fun LinearLayout.button(t: String, primary: Boolean = true, onClick: () -> Unit): Button {
    val b = Button(context).apply {
        text = t; isAllCaps = false; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) 0xFFFFFFFF.toInt() else C.GREEN)
        background = if (primary) rounded(C.GREEN, context.dp(14).toFloat())
        else rounded(C.SOFT, context.dp(14).toFloat())
        minHeight = context.dp(48)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }
    addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = context.dp(8) })
    return b
}

fun LinearLayout.switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): Switch {
    val s = Switch(context).apply {
        text = label; textSize = 15f; setTextColor(C.INK); isChecked = checked
        minHeight = context.dp(48)
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }
    addView(s)
    return s
}

/** Riga con slider: chiama onCommit quando l'utente stacca il dito. */
fun LinearLayout.sliderRow(
    label: String, min: Int, max: Int, value: Int,
    format: (Int) -> String = { it.toString() }, onCommit: (Int) -> Unit,
) {
    val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val l = TextView(context).apply { text = label; textSize = 15f; setTextColor(C.INK); typeface = Typeface.DEFAULT_BOLD }
    val v = TextView(context).apply { text = format(value); textSize = 15f; setTextColor(C.GREEN); typeface = Typeface.DEFAULT_BOLD }
    head.addView(l, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    head.addView(v)
    addView(head)
    val sb = SeekBar(context).apply {
        this.max = max - min
        progress = value - min
        minimumHeight = context.dp(40)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { v.text = format(p + min) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { onCommit(s.progress + min) }
        })
    }
    addView(sb)
}

fun LinearLayout.input(hint: String): EditText = EditText(context).apply {
    this.hint = hint; inputType = InputType.TYPE_TEXT_VARIATION_URI; textSize = 15f
    minHeight = context.dp(48)
}.also { addView(it) }

fun Context.toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

/** Messaggio standard dopo una richiesta di modifica. */
fun Context.report(o: PendingApplier.Outcome) {
    when (o) {
        is PendingApplier.Outcome.Applied -> toast("Fatto.")
        is PendingApplier.Outcome.Queued -> toast("Allenta le regole: partirà tra ${Store.cooldownHours} h. Puoi annullarla in Regole.")
    }
}
