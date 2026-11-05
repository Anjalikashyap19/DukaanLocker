package com.iadv.dukaanlocker

import android.app.Application
import com.easebuzz.core.EasebuzzSdk

class EasebuzzApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val merchantKey = "DZ1MRVFV5L"
        val merchantSalt = "XE92INNXUB"

        EasebuzzSdk.init(this, merchantKey, merchantSalt)
    }
}