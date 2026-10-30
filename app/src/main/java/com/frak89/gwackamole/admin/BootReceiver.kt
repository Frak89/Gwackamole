package com.frak89.gwackamole.admin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frak89.gwackamole.core.Protection
import com.frak89.gwackamole.data.Store

/** Al riavvio riapplica le policy (il servizio di accessibilità riparte da solo). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Protection.apply(context)
    }
}
