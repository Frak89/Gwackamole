package com.frak89.gwackamole

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var rules: Rules
    private lateinit var content:LinearLayout
    private val pad=16

    private fun text(s:String, size:Float=17f):TextView = TextView(this).apply {
        text=s
        textSize=size
        setTextColor(Color.rgb(235,245,240))
        setPadding(pad,10,pad,10)
    }
    private fun button(s:String,action:()->Unit):Button=Button(this).apply {
        text=s
        setOnClickListener {action()}
    }
    private fun setting(label:String,current:Int,onSave:(Int)->Unit) {
        val line=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        line.addView(text(label,14f),LinearLayout.LayoutParams(0,-2,1f))
        val input=EditText(this).apply {
            setText(current.toString())
            setTextColor(Color.WHITE)
            inputType=InputType.TYPE_CLASS_NUMBER
            minWidth=70
        }
        line.addView(input)
        line.addView(button("Salva") {
            val n=input.text.toString().toIntOrNull()
            if(n!=null){onSave(n);Toast.makeText(this,"Impostazione salvata",Toast.LENGTH_SHORT).show()}
        })
        content.addView(line)
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        rules=Rules(this)
        val scroll=ScrollView(this).apply {setBackgroundColor(Color.rgb(12,25,25))}
        content=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(12,12,12,36)}
        scroll.addView(content)
        setContentView(scroll)
        render()
    }
    private fun render() {
        content.removeAllViews()
        content.addView(text("GWACKAMOLE  🐾",28f))
        content.addView(text("Protezione volontaria · mini-game progressivo · dati salvati solo sul dispositivo",14f))
        content.addView(text("1. ATTIVA I PERMESSI",22f))
        content.addView(button("Accessibilità — rileva risultati web e app") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        content.addView(button("Accesso utilizzo app") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        content.addView(button("Device Admin — blocco schermo in caso di sconfitta") {
            val i=Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,ComponentName(this,GuardAdminReceiver::class.java))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Gwackamole usa lockNow solo dopo una sfida persa.")
            startActivity(i)
        })
        val dpm=getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        content.addView(text(if(dpm.isDeviceOwnerApp(packageName))
            "Device Owner attivo: Lock Task disponibile durante la sfida."
            else "Modalità standard: lo swipe di sistema NON è bloccabile. Device Owner richiede provisioning dedicato.",13f))
        content.addView(text("2. REGOLE E PROTEZIONI",22f))
        val filter=CheckBox(this).apply {
            text="Oscura nei browser i risultati con segnali di contenuti adulti"
            setTextColor(Color.WHITE)
            isChecked=rules.onBrowsing()
            setOnCheckedChangeListener {_,on->
                if(!on && rules.onBrowsing() && !rules.canRelax("filter")) {
                    Toast.makeText(this@MainActivity,"Per disattivare il filtro attendi "+rules.relaxWaitMinutes("filter")+" min e riprova.",Toast.LENGTH_LONG).show()
                    isChecked=true
                } else rules.setBrowsing(on)
            }
        }
        content.addView(filter)
        setting("Copertura link: ritardo 0 o 5 secondi",rules.obscureDelaySeconds()) {rules.setObscureDelaySeconds(it)}
        setting("Pausa dopo sconfitta (minuti)",rules.cooldownMinutes()){rules.setCooldownMinutes(it)}
        setting("Sfida casuale su app blacklist (ogni X min, 0 = off)",rules.randomMinutes()){rules.setRandomMinutes(it)}
        content.addView(text("Il risultato filtrato è coperto da un banner bianco con talpa. Tocca la talpa per la sfida. Ogni nuovo tentativo sullo stesso risultato aumenta la difficoltà, fino a 10 talpe. I risultati non filtrati restano cliccabili.",13f))
        content.addView(button("Prova il minigioco (demo)") {
            startActivity(Intent(this,MoleGameActivity::class.java)
                .putExtra("demo",true).putExtra("mole_count",5))
        })
        content.addView(text("3. BLACKLIST APP + TIMER INIZIALE",22f))
        content.addView(text("Quando un'app raggiunge il suo tempo, una vittoria dà 5, 10 o 15 minuti aggiuntivi SOLO nel minigioco. Per modificare regole esistenti introdurremo un cooldown amministrativo verificato.",13f))

        val launcher=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps=packageManager.queryIntentActivities(launcher,0)
            .map {Pair(it.loadLabel(packageManager).toString(),it.activityInfo.packageName)}
            .distinctBy {it.second}
            .filter {it.second!=packageName}
            .sortedBy {it.first.lowercase() }.take(150)
        for((name,pkg) in apps) {
            val line=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
            val check=CheckBox(this).apply {
                text=name
                isChecked=rules.blacklist(pkg)
                setTextColor(Color.WHITE);textSize=13f
                setOnCheckedChangeListener { _,active->
                    if(!active && rules.blacklist(pkg) && !rules.canRelax("black:"+pkg)) {
                        Toast.makeText(this@MainActivity,
                            "Protezione: riprova tra "+rules.relaxWaitMinutes("black:"+pkg)+" min.",
                            Toast.LENGTH_LONG).show()
                        isChecked=true
                    } else rules.setBlacklist(pkg,active)
                }
            }
            line.addView(check,LinearLayout.LayoutParams(0,-2,1f))
            val mins=EditText(this).apply {
                setText(rules.minutes(pkg).toString())
                inputType=InputType.TYPE_CLASS_NUMBER
                setTextColor(Color.WHITE)
                setSingleLine(true)
                minWidth=70
            }
            line.addView(mins,LinearLayout.LayoutParams(75,-2))
            line.addView(button("min") {
                val n=mins.text.toString().toIntOrNull()
                if(n!=null){
                    if(n>rules.minutes(pkg) && rules.blacklist(pkg) && !rules.canRelax("timer:"+pkg)) {
                        Toast.makeText(this,"Per aumentare il timer attendi "+rules.relaxWaitMinutes("timer:"+pkg)+" min o vinci il minigioco.",Toast.LENGTH_LONG).show()
                        return@button
                    }
                    rules.setMinutes(pkg,n)
                    Toast.makeText(this,"Tempo iniziale: "+n+" min. Usati oggi: "+(rules.usedMs(pkg)/60000)+" min",Toast.LENGTH_SHORT).show()
                }
            })
            content.addView(line)
        }
        content.addView(text("Nota: i filtri tramite Accessibilità sono sperimentali e non possono garantire il blocco di tutti i browser, schede private, download o siti cifrati. Nessuna ricerca viene inviata a server.",13f))
    }
}
