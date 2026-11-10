package com.frak89.gwackamole.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

/** Stile cyberpunk condiviso: nero, viola e blu neon, bianco dove serve. */
object Neon {
    const val VIOLET = 0xFFB65CFF.toInt()
    const val BLUE = 0xFF2DD4FF.toInt()
    const val BG = 0xFF0A0A12.toInt()

    @Suppress("DEPRECATION")
    fun immersive(a: Activity) {
        a.window.statusBarColor = BG
        a.window.navigationBarColor = BG
        if (Build.VERSION.SDK_INT >= 30) {
            a.window.setDecorFitsSystemWindows(false)
            a.window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            a.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    /** Pulsante pieno con gradiente viola→blu. */
    fun pill(ctx: Context, @Suppress("UNUSED_PARAMETER") tint: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(VIOLET, BLUE)).apply {
            cornerRadius = 16 * ctx.resources.displayMetrics.density
        }

    /** Pulsante vuoto con bordo neon. */
    fun ghost(ctx: Context): GradientDrawable = GradientDrawable().apply {
        setColor(0x00000000)
        cornerRadius = 16 * ctx.resources.displayMetrics.density
        setStroke((1.5f * ctx.resources.displayMetrics.density).toInt(), 0xFF34306A.toInt())
    }

    /** Marchio di Nero Block: quadrato con divieto, alone neon. */
    fun mark(ctx: Context): View = object : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val rr = RectF()
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            p.color = 0xFF17132E.toInt()
            rr.set(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f)
            c.drawRoundRect(rr, w * 0.22f, w * 0.22f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.02f
            p.shader = LinearGradient(0f, 0f, w, h, VIOLET, BLUE, Shader.TileMode.CLAMP)
            c.drawRoundRect(rr, w * 0.22f, w * 0.22f, p)
            p.shader = null; p.style = Paint.Style.FILL
            s.shader = LinearGradient(0f, 0f, w, h, VIOLET, BLUE, Shader.TileMode.CLAMP)
            s.strokeWidth = w * 0.07f
            val cx = w / 2; val cy = h / 2; val r = w * 0.2f
            c.drawCircle(cx, cy, r, s)
            c.drawLine(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f, s)
        }
    }
}
