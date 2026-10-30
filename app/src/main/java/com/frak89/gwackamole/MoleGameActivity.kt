package com.frak89.gwackamole

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.View
import android.widget.Toast

class MoleGameActivity : Activity() {
    private lateinit var rules: Rules
    private lateinit var policy: DevicePolicyManager
    private lateinit var admin: ComponentName
    private var challengeId=""
    private var targetPackage=""
    private var demo=false
    private var done=false

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        rules=Rules(this)
        policy=getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        admin=ComponentName(this,GuardAdminReceiver::class.java)
        challengeId=intent.getStringExtra("challenge_id") ?: ""
        targetPackage=intent.getStringExtra("target_package") ?: ""
        demo=intent.getBooleanExtra("demo",false)
        val level=(intent.getIntExtra("mole_count",1)).coerceIn(1,10)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(13,27,27))
        }
        val top=TextView(this).apply {
            text="Sfida anti-distrazioni · difficoltà " + level + "/10"
            setTextColor(Color.WHITE)
            textSize=16f
            setPadding(20,22,20,14)
        }
        root.addView(top)
        root.addView(WhackView(this,level){win->result(win,level)},
            LinearLayout.LayoutParams(-1,0,1f))
        val emergency=Button(this).apply {
            text="Uscita di sicurezza (tieni premuto)"
            setOnLongClickListener {
                Toast.makeText(this@MoleGameActivity,"Interruzione volontaria della sfida",Toast.LENGTH_SHORT).show()
                exitKiosk()
                finish()
                true
            }
        }
        root.addView(emergency)
        setContentView(root)
        // A device owner may lock ONLY this activity. Accessibility alone cannot trap system gestures.
        if(policy.isDeviceOwnerApp(packageName)) {
            try {
                policy.setLockTaskPackages(admin,arrayOf(packageName))
                startLockTask()
            }catch(_:Exception){}
        }
    }

    private fun result(win:Boolean, level:Int) {
        if(done)return
        done=true
        if(demo) {
            Toast.makeText(this,if(win) "Demo: vittoria!" else "Demo: tempo scaduto",Toast.LENGTH_LONG).show()
            exitKiosk(); finish();return
        }
        if(win) {
            if(challengeId.isNotEmpty())rules.allow(challengeId)
            if(targetPackage.isNotEmpty()) {
                val reward=if(level>=8)15 else if(level>=5)10 else 5
                rules.giveAppExtension(targetPackage,reward)
            }
            Toast.makeText(this,"Vittoria! Accesso temporaneo concesso.",Toast.LENGTH_LONG).show()
            exitKiosk();finish()
        } else {
            rules.startCooldown()
            exitKiosk()
            if(policy.isAdminActive(admin)) {
                try { policy.lockNow() }catch(_:Exception){}
            } else {
                Toast.makeText(this,"Sfida persa: pausa di "+rules.cooldownMinutes()+" minuti (blocco schermo richiede Device Admin).",Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }
    private fun exitKiosk() { try{stopLockTask()}catch(_:Exception){} }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Toast.makeText(this,"Completa la sfida o usa l'uscita di sicurezza.",Toast.LENGTH_SHORT).show()
    }
    override fun onDestroy(){exitKiosk();super.onDestroy()}
}
