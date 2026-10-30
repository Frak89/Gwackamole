package com.frak89.gwackamole

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.random.Random

/** Simple original vector drawings: no external images or tracking. */
class MoleBadge(context:Context, private val tap:()->Unit): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),MeasureSpec.getSize(heightMeasureSpec))
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val w=width.toFloat(); val h=height.toFloat()
        canvas.drawColor(Color.WHITE)
        val centerX= min(w*0.18f,64f*resources.displayMetrics.density)
        val centerY=h/2
        val r=min(28f*resources.displayMetrics.density,h*0.37f)
        paint.color=Color.rgb(123,75,48)
        canvas.drawCircle(centerX,centerY,r,paint)
        paint.color=Color.rgb(232,161,164)
        canvas.drawCircle(centerX-r*.63f,centerY-r*.58f,r*.30f,paint)
        canvas.drawCircle(centerX+r*.63f,centerY-r*.58f,r*.30f,paint)
        paint.color=Color.WHITE
        canvas.drawCircle(centerX-r*.33f,centerY-r*.11f,r*.20f,paint)
        canvas.drawCircle(centerX+r*.33f,centerY-r*.11f,r*.20f,paint)
        paint.color=Color.BLACK
        canvas.drawCircle(centerX-r*.33f,centerY-r*.11f,r*.08f,paint)
        canvas.drawCircle(centerX+r*.33f,centerY-r*.11f,r*.08f,paint)
        paint.color=Color.rgb(255,172,175)
        canvas.drawOval(RectF(centerX-r*.25f,centerY+r*.20f,centerX+r*.25f,centerY+r*.55f),paint)
        paint.color=Color.BLACK
        paint.textSize=min(16f*resources.displayMetrics.scaledDensity,h*.28f)
        paint.typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD)
        canvas.drawText("Gwackamole  •  Tocca per sfidare",centerX+r+14f,centerY+paint.textSize*.32f,paint)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(event.action==MotionEvent.ACTION_UP){performClick();tap();return true}
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
}

class WhackView(context:Context, private val moleCount:Int, private val onDone:(Boolean)->Unit):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val d=resources.displayMetrics.density
    private val count=moleCount.coerceIn(1,10)
    private val positions=ArrayList<Pair<Float,Float>>()
    private val active=mutableSetOf<Int>()
    private val rng=Random(System.nanoTime())
    private val required=2+count*2
    private val durationMs=(18000L-(count-1)*900L).coerceAtLeast(9000L)
    private val moleLifetime=(1600L-(count-1)*110L).coerceAtLeast(500L)
    private var deadline=android.os.SystemClock.uptimeMillis()+durationMs
    private var lastSpawn=0L
    private var hits=0
    private var finished=false
    init { contentDescription="Mini game talpe difficoltà " + count }
    private fun layoutHoles() {
        positions.clear()
        val cols=if(count<=3) count else if(count<=6) 3 else 4
        val rows=(count+cols-1)/cols
        val startY=height*.37f; val usableH=height*.55f
        for(i in 0 until count) {
            val row=i/cols
            val col=i%cols
            positions.add(Pair(width*(col+1f)/(cols+1f),startY+usableH*(row+.5f)/rows))
        }
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){layoutHoles()}
    override fun onDraw(canvas:Canvas) {
        canvas.drawColor(Color.rgb(13,27,27))
        val now=android.os.SystemClock.uptimeMillis()
        if(!finished && now>=deadline) {
            finished=true
            post {onDone(false)}
        }
        if(!finished && now-lastSpawn>moleLifetime) {
            active.clear()
            val simultaneous=(1+(count-1)/3).coerceAtMost(count)
            while(active.size<simultaneous) active.add(rng.nextInt(count))
            lastSpawn=now
        }
        paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign=Paint.Align.CENTER
        paint.color=Color.WHITE;paint.textSize=25*d
        canvas.drawText("GWACKAMOLE!",width/2f,44*d,paint)
        paint.textSize=17*d
        canvas.drawText("Talpe: "+count+"     Prese: "+hits+"/"+required,width/2f,78*d,paint)
        paint.color=Color.rgb(255,205,74)
        canvas.drawText("Tempo: "+((deadline-now).coerceAtLeast(0L)/1000L)+" s",width/2f,110*d,paint)
        paint.color=Color.rgb(30,160,94)
        canvas.drawRoundRect(RectF(16*d,130*d,width-16*d,138*d),4*d,4*d,paint)
        paint.color=Color.rgb(52,45,39)
        for((i,pos) in positions.withIndex()){
            val x=pos.first;val y=pos.second;val radius=(min(width/6f, 34*d)).coerceAtLeast(22*d)
            canvas.drawOval(RectF(x-radius*1.15f,y-radius*.40f,x+radius*1.15f,y+radius*.45f),paint)
            if(active.contains(i)){
                paint.color=Color.rgb(135,83,49);canvas.drawCircle(x,y-radius*.38f,radius,paint)
                paint.color=Color.WHITE; canvas.drawCircle(x-radius*.3f,y-radius*.55f,radius*.18f,paint)
                canvas.drawCircle(x+radius*.3f,y-radius*.55f,radius*.18f,paint)
                paint.color=Color.BLACK;canvas.drawCircle(x-radius*.3f,y-radius*.55f,radius*.07f,paint)
                canvas.drawCircle(x+radius*.3f,y-radius*.55f,radius*.07f,paint)
                paint.color=Color.rgb(236,144,144)
                canvas.drawCircle(x,y-radius*.22f,radius*.19f,paint)
                paint.color=Color.rgb(52,45,39)
            }
        }
        if(!finished)postInvalidateDelayed(70L)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(event.action!=MotionEvent.ACTION_UP||finished) return true
        val radius=min(width/6f,34*d).coerceAtLeast(22*d)
        for((i,pos) in positions.withIndex()) {
            val dx=event.x-pos.first;val dy=event.y-(pos.second-radius*.4f)
            if(active.contains(i)&&dx*dx+dy*dy<(radius*1.3f)*(radius*1.3f)){
                active.remove(i);hits++
                if(hits>=required) {finished=true;post{onDone(true)}}
                break
            }
        }
        invalidate();return true
    }
}
