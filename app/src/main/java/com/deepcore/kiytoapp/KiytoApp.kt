package com.deepcore.kiytoapp

import android.app.Application

class KiytoApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: KiytoApp
            private set
    }
} 