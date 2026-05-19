package com.bartmuskala.mykaroohud.extension

import com.bartmuskala.mykaroohud.BuildConfig
import com.bartmuskala.mykaroohud.datatype.MyKarooHudDataType
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import timber.log.Timber

class MyKarooHudExtension : KarooExtension("mykaroohud", BuildConfig.VERSION_NAME) {

    private lateinit var karooSystem: KarooSystemService

    override val types by lazy {
        listOf(
            MyKarooHudDataType(karooSystem, applicationContext)
        )
    }

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        karooSystem.connect { Timber.d("Karoo system connected") }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }
}
