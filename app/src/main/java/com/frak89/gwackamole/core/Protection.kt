package com.frak89.gwackamole.core

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import com.frak89.gwackamole.admin.GwackAdminReceiver
import com.frak89.gwackamole.data.Store

/**
 * Protezioni contro disinstallazione / arresto forzato / disattivazione.
 *
 * - Device Admin: serve per spegnere e bloccare lo schermo (lockNow).
 * - Device Owner (via adb, una volta sola): blocca la disinstallazione, l'arresto forzato,
 *   la modalità provvisoria, e abilita la lock-task mode per il minigioco (niente gesture di uscita).
 *
 * L'unica via d'uscita è "Disattiva protezioni" nell'app, che parte dopo il cooldown.
 */
object Protection {

    fun admin(ctx: Context) = ComponentName(ctx, GwackAdminReceiver::class.java)
    private fun dpm(ctx: Context) = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isAdmin(ctx: Context) = dpm(ctx).isAdminActive(admin(ctx))
    fun isDeviceOwner(ctx: Context) = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    const val ADB_COMMAND = "adb shell dpm set-device-owner com.frak89.gwackamole/.admin.GwackAdminReceiver"

    /** Applica (o rimuove) tutte le difese disponibili in base allo stato "armato". */
    fun apply(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val d = dpm(ctx)
        val a = admin(ctx)
        val on = Store.armed
        try {
            d.setUninstallBlocked(a, ctx.packageName, on)
            if (Build.VERSION.SDK_INT >= 30) {
                d.setUserControlDisabledPackages(a, if (on) listOf(ctx.packageName) else emptyList())
            }
            d.setLockTaskPackages(a, arrayOf(ctx.packageName))
            if (Build.VERSION.SDK_INT >= 28) {
                // Durante il minigioco: niente Home, niente Recenti, niente notifiche. Resta il menu di accensione.
                d.setLockTaskFeatures(a, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS)
            }
            setRestriction(ctx, UserManager.DISALLOW_SAFE_BOOT, on)
            setRestriction(ctx, UserManager.DISALLOW_DEBUGGING_FEATURES, on && Store.blockAdb)
        } catch (e: SecurityException) {
            // non Device Owner o policy rifiutata dal produttore: si continua con le altre difese
        }
    }

    private fun setRestriction(ctx: Context, key: String, on: Boolean) {
        val d = dpm(ctx)
        if (on) d.addUserRestriction(admin(ctx), key) else d.clearUserRestriction(admin(ctx), key)
    }

    fun isLockTaskPermitted(ctx: Context) = dpm(ctx).isLockTaskPermitted(ctx.packageName)

    /** Spegne lo schermo e blocca il telefono, se l'app è amministratore. */
    fun lockNow(ctx: Context): Boolean = try {
        if (isAdmin(ctx)) { dpm(ctx).lockNow(); true } else false
    } catch (e: SecurityException) { false }

    /** Rilascio completo (dopo il cooldown): toglie le difese e lascia disinstallare. */
    fun release(ctx: Context, dropOwner: Boolean) {
        Store.armed = false
        apply(ctx)
        if (dropOwner && isDeviceOwner(ctx)) {
            try {
                @Suppress("DEPRECATION")
                dpm(ctx).clearDeviceOwnerApp(ctx.packageName)
            } catch (_: SecurityException) { }
        }
    }
}
