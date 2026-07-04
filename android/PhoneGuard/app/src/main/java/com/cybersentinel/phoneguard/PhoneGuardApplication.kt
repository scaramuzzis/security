package com.cybersentinel.phoneguard

import android.app.Application
import com.cybersentinel.phoneguard.util.CrashHandler

class PhoneGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
