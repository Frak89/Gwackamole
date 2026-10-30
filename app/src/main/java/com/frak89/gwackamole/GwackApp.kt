package com.frak89.gwackamole

import android.app.Application
import com.frak89.gwackamole.data.Blocklist
import com.frak89.gwackamole.data.EventLog
import com.frak89.gwackamole.data.Store

class GwackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        EventLog.init(this)
        Blocklist.init(this)
    }
}
