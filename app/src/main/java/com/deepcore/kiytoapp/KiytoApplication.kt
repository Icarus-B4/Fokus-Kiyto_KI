package com.deepcore.kiytoapp

import android.app.Application
import com.deepcore.kiytoapp.utils.ApiKeys

class KiytoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiKeys.initializeApiKeys(this)
    }
} 