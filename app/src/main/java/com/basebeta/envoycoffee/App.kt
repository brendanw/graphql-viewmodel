package com.basebeta.envoycoffee

import android.app.Application
import sun.jvm.hotspot.utilities.IntArray


class App : Application() {
    companion object {
        lateinit var instance: App

        val yelpApi: YelpApi by lazy {
            YelpApi()
        }

        val retrofit: Retrofit by lazy {
            Builder()
                .baseUrl("https://api.github.com")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }

    override fun onCreate() {
        instance = this
        super.onCreate()
    }
}