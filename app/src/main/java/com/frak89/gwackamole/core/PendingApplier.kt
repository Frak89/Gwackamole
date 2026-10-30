package com.frak89.gwackamole.core

import android.content.Context
import com.frak89.gwackamole.data.AppRule
import com.frak89.gwackamole.data.Blocklist
import com.frak89.gwackamole.data.PendingChange
import com.frak89.gwackamole.data.Store
import org.json.JSONObject

/**
 * Tutte le modifiche alle regole passano da qui.
 * Rendere le regole più severe ha effetto subito; allentarle (con protezioni armate)
 * entra in coda e parte solo dopo il cooldown, come la "strict mode" di AppBlock.
 */
object PendingApplier {

    sealed class Outcome {
        object Applied : Outcome()
        data class Queued(val change: PendingChange) : Outcome()
    }

    fun request(ctx: Context, type: String, payload: String, desc: String, looser: Boolean): Outcome {
        if (!looser || !Store.armed) {
            apply(ctx, PendingChange(0, type, payload, 0, desc))
            return Outcome.Applied
        }
        return Outcome.Queued(Store.addPending(type, payload, desc))
    }

    fun apply(ctx: Context, p: PendingChange) {
        when (p.type) {
            "rule" -> Store.putRule(AppRule.fromJson(JSONObject(p.payload)))
            "remove_rule" -> Store.removeRule(p.payload)
            "remove_site" -> Store.removeSite(p.payload)
            "forget_learned" -> Blocklist.forget(p.payload)
            "setting" -> {
                val o = JSONObject(p.payload)
                val v = o.get("value")
                when (o.getString("key")) {
                    "bonus_minutes" -> Store.bonusMinutes = (v as Number).toInt()
                    "lock_base" -> Store.lockBaseMinutes = (v as Number).toInt()
                    "lock_step" -> Store.lockStepMinutes = (v as Number).toInt()
                    "site_grace" -> Store.siteGraceSec = (v as Number).toInt()
                    "screen_limit" -> Store.screenLimitMin = (v as Number).toInt()
                    "cooldown_h" -> Store.cooldownHours = (v as Number).toInt()
                    "random_checks" -> Store.randomChecks = v as Boolean
                    "adult_filter" -> Store.adultFilter = v as Boolean
                    "serp_cover" -> Store.serpCover = v as Boolean
                    "block_adb" -> { Store.blockAdb = v as Boolean; Protection.apply(ctx) }
                }
            }
            "release" -> Protection.release(ctx, dropOwner = p.payload == "owner")
        }
    }

    fun setting(key: String, value: Any): String = JSONObject().put("key", key).put("value", value).toString()
}
