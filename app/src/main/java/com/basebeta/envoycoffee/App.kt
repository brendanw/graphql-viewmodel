package com.basebeta.envoycoffee

import android.app.Application

class App : Application() {
    companion object {
        lateinit var instance: App

        val yelpApi: YelpApi by lazy {
            YelpApi()
        }
    }

    override fun onCreate() {
        instance = this
        super.onCreate()
    }
}