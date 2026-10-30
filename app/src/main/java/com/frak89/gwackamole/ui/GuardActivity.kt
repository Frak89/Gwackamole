package com.frak89.gwackamole.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.frak89.gwackamole.data.Store

/** Mostrata quando si prova a disinstallare / fermare / disattivare Gwackamole con le protezioni armate. */
class GuardActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val count = intent.getIntExtra("count", 1)
        val col = page()
        col.gap(40)
        col.eyebrow("Tentativo di bypass · $count oggi")
        col.title("Le talpe fanno la guardia", 30f)
        col.gap(8)
        col.body("Disinstallare, forzare l'arresto o togliere i permessi di Gwackamole non è possibile da qui.", C.INK, 16f)
        col.gap(8)
        col.card {
            eyebrow("Vuoi davvero cambiare le regole?")
            body("Apri Gwackamole › Disattiva protezioni. La richiesta parte dopo ${Store.cooldownHours} ore e puoi annullarla quando vuoi.")
        }
        if (count >= 3) col.body("Terzo tentativo: arrivano le talpe.", C.ORANGE, 15f)
        col.button("Torna alla Home") { goHome() }
        col.button("Apri Gwackamole", primary = false) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
        }
        col.postDelayed({ if (!isFinishing) goHome() }, 8000)
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
