package com.frak89.gwackamole.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.frak89.gwackamole.core.Protection
import com.frak89.gwackamole.data.Store

class GwackAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Store.init(context)
        Protection.apply(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Store.init(context)
        Protection.apply(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Se disattivi l'amministratore, le talpe non potranno più bloccare lo schermo. " +
            "Per togliere le protezioni usa \"Disattiva protezioni\" dentro Gwackamole: parte dopo il cooldown."
}
