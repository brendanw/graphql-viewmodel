package com.basebeta.envoycoffee

import android.app.Application
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class App : Application() {
    companion object {
        lateinit var instance: App

        val yelpApi: YelpApi by lazy {
            YelpApi()
        }

        val retrofit: Retrofit by lazy {
            Retrofit.Builder()
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
