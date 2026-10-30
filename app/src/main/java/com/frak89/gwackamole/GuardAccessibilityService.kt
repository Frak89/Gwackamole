package com.frak89.gwackamole

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.max
import kotlin.math.min

/**
 * Opt-in accessibility overlay for search-result tiles. Only covers visible
 * matched links, not the entire browser; other results remain fully interactive.
 * This is NOT a network filter and does not read TLS traffic or private browser state.
 */
class GuardAccessibilityService : AccessibilityService() {
    private lateinit var rules:Rules
    private lateinit var wm:WindowManager
    private val handler=Handler(Looper.getMainLooper())
    private val overlays=mutableListOf<android.view.View>()
    private var currentApp=""
    private var lastTime=0L
    private var lastLaunch=0L
    private var scanEpoch=0L
    private var visibleBrowser=false

    override fun onServiceConnected(){
        super.onServiceConnected()
        rules=Rules(this)
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        lastTime=SystemClock.elapsedRealtime()
        if(rules.lastChallenge()==0L) rules.markLastChallenge()
        handler.post(tick)
    }
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(!::rules.isInitialized || event==null)return
        val p=event.packageName?.toString() ?: return
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType==AccessibilityEvent.TYPE_WINDOWS_CHANGED){
            setForegroundApp(p)
        }
        if(p!=currentApp && currentApp.isEmpty())setForegroundApp(p)
        if(p!=currentApp)return
        if(Rules.browsers.contains(p) && rules.onBrowsing()) {
            scheduleScan(if(event.eventType==AccessibilityEvent.TYPE_VIEW_SCROLLED) 250L else
                (rules.obscureDelaySeconds()*1000L).toLong().coerceAtLeast(200L))
        } else if(overlays.isNotEmpty())clearOverlays()
    }
    private fun setForegroundApp(next:String){
        if(next==currentApp)return
        accumulate()
        if(currentApp.isNotEmpty() && next!=currentApp)clearOverlays()
        currentApp=next
        lastTime=SystemClock.elapsedRealtime()
        if(next!="" && next!=packageName && rules.blacklist(next))rules.visit(next)
    }
    private fun accumulate(){
        val now=SystemClock.elapsedRealtime()
        if(currentApp!="" && currentApp!=packageName && rules.blacklist(currentApp))
            rules.addUsedMs(currentApp,(now-lastTime).coerceAtLeast(0L))
        lastTime=now
    }
    private val tick=object:Runnable {
        override fun run() {
            if(::rules.isInitialized) {
                accumulate()
                val p=currentApp
                if(p!="" && p!=packageName && rules.blacklist(p)) {
                    val maxMs=rules.minutes(p)*60000L+rules.extraMs(p)
                    val over=rules.usedMs(p)>=maxMs
                    val randomM=rules.randomMinutes()
                    val randomDue=randomM>0 &&
                        (System.currentTimeMillis()-rules.lastChallenge()>=randomM*60000L)
                    if((over || randomDue) && SystemClock.elapsedRealtime()-lastLaunch>15000L) {
                        val id=Rules.fingerprint("app:"+p)
                        launchChallenge(id,p)
                    }
                }
            }
            handler.postDelayed(this,3000L)
        }
    }
    private fun scheduleScan(delayMs:Long){
        val epoch=++scanEpoch
        handler.postDelayed({
            if(epoch==scanEpoch && Rules.browsers.contains(currentApp) && rules.onBrowsing())
                scanVisibleLinks()
        },delayMs)
    }
    private fun scanVisibleLinks(){
        val root=rootInActiveWindow ?: run{clearOverlays();return}
        val candidates=mutableListOf<Pair<Rect,String>>()
        val queue=ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var checked=0
        val seen=mutableSetOf<String>()
        while(queue.isNotEmpty() && checked++<300){
            val node=queue.removeFirst()
            for(i in 0 until min(node.childCount,30)){
                try {node.getChild(i)?.let{queue.add(it)}}catch(_:Exception){}
            }
            val txt=node.text?.toString()?.trim() ?: node.contentDescription?.toString()?.trim() ?: continue
            if(txt.isEmpty() || txt.length>300 || !Rules.adultTerms.containsMatchIn(txt))continue
            val klass=node.className?.toString() ?: ""
            if(klass.contains("EditText",true))continue  // search box must stay editable
            var link:AccessibilityNodeInfo?=node
            var up=0
            while(link!=null && !link.isClickable && up++<5)link=link.parent
            if(link==null)continue
            val linkClass=link.className?.toString() ?: ""
            if(linkClass.contains("EditText",true))continue
            val rect=Rect()
            link.getBoundsInScreen(rect)
            val screenWidth=resources.displayMetrics.widthPixels
            val screenHeight=resources.displayMetrics.heightPixels
            if(rect.width()<120 || rect.height()<32 ||
                rect.height()>260*resources.displayMetrics.density ||
                rect.top<0 || rect.bottom>screenHeight || rect.left<0 ||
                rect.right>screenWidth)continue
            val fingerprint=Rules.fingerprint(currentApp+"|"+txt)
            if(!seen.add(fingerprint)||rules.isAllowed(fingerprint))continue
            if(candidates.none { android.graphics.Rect.intersects(it.first,rect) }) {
                candidates.add(Pair(rect,fingerprint))
            }
            if(candidates.size>=7)break
        }
        clearOverlays()
        for((r,id) in candidates)showMole(r,id)
    }
    private fun showMole(bounds:Rect,id:String){
        val v=MoleBadge(this){
            if(rules.inCooldown()) {
                android.widget.Toast.makeText(this,
                    "Pausa attiva: riprova tra "+((rules.cooldownEnd()-System.currentTimeMillis()).coerceAtLeast(0L)/60000L+1L)+" min",
                    android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val level=rules.incrementAttempts(id)
                launchChallenge(id,"",level)
            }
        }
        val params=WindowManager.LayoutParams(
            bounds.width(),bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity=Gravity.TOP or Gravity.START
            x=bounds.left;y=bounds.top
        }
        try{wm.addView(v,params);overlays.add(v)}catch(_:Exception){}
    }
    private fun clearOverlays() {
        for(v in overlays){try{wm.removeViewImmediate(v)}catch(_:Exception){}}
        overlays.clear()
    }
    private fun launchChallenge(id:String,targetPackage:String,level:Int=1) {
        if(SystemClock.elapsedRealtime()-lastLaunch<3000L)return
        if(rules.inCooldown()) {
            // Do not clear the protective overlay: it would expose the link.
            return
        }
        lastLaunch=SystemClock.elapsedRealtime()
        rules.markLastChallenge()
        clearOverlays()
        val i=Intent(this,MoleGameActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("challenge_id",id)
            putExtra("mole_count",level)
            putExtra("target_package",targetPackage)
        }
        try{startActivity(i)}catch(_:Exception){}
    }
    override fun onInterrupt(){clearOverlays()}
    override fun onDestroy(){
        handler.removeCallbacksAndMessages(null)
        clearOverlays()
        super.onDestroy()
    }
}
