package com.bartmuskala.mykaroohud

import android.app.Application
import timber.log.Timber

class MyKarooHudApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
