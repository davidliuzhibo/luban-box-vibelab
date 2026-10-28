package com.vrgames.lubanbox.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.vrgames.lubanbox.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
