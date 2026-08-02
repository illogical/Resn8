package com.app.resn8

import android.app.Application
import com.app.resn8.di.AppContainer
import com.app.resn8.di.DefaultAppContainer

class Resn8Application : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
