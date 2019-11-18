package com.basebeta.envoycoffee

import SearchQuery
import android.util.Log
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import com.apollographql.apollo.rx2.Rx2Apollo
import com.basebeta.envoycoffee.main.YelpResult
import io.reactivex.Observable
import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.Executor

interface IYelpApi {
    fun getShops(): Observable<List<YelpResult>>
}

class YelpApi : IYelpApi {
    companion object {
        const val API_URL = "https://api.yelp.com/v3/graphql"
        const val API_KEY =
            "rUr76JWphjAvPy36mO8-zGZNsVoqT5pFmIEnSMlAD4EOHMJHCABKqtY02uQjAKgu_TZ-xomUZURkS-E7JmvJXzgK-1CCUjTYaqstRslzIuk2w9qKTrzyEOE7rAbLXXYx"
    }

    private val apolloClient: ApolloClient

    init {
        val loggingInterceptor: HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient: OkHttpClient = OkHttpClient.Builder().apply {
            addInterceptor(loggingInterceptor)
            addInterceptor { chain ->
                val request = chain
                    .request()
                    .newBuilder()
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .build()
                chain.proceed(request)
            }
        }.build()

        apolloClient = ApolloClient.builder().apply {
            serverUrl(API_URL)
            okHttpClient(okHttpClient)
            addApplicationInterceptor(object : ApolloInterceptor {
                override fun dispose() {

                }

                override fun interceptAsync(
                    request: ApolloInterceptor.InterceptorRequest,
                    chain: ApolloInterceptorChain,
                    dispatcher: Executor,
                    callBack: ApolloInterceptor.CallBack
                ) {
                    Log.d("YelpApi", "raw query: ${request.operation.queryDocument()}")
                    chain.proceedAsync(request, dispatcher, callBack)
                }
            })
        }.build()
    }

    /**
     * Apollo kotlin sample shows handling nullable for graphql types specified as non-null. Odd.
     */
    override fun getShops(): Observable<List<YelpResult>> {
        val apolloCallable =
            apolloClient.query(
                SearchQuery(
                    limit = 10,
                    location = "410 Townsend Street, San Francisco, CA",
                    term = "coffee"
                )
            )
        return Rx2Apollo.from(apolloCallable).map { response ->
            val results = response.data()!!.search!!.business!!
            results.map {
                YelpResult(
                    name = it!!.name!!,
                    address = it.location!!.address1!!,
                    cost = it.price,
                    imageUrl = it.photos!![0]!!
                )
            }
        }
    }
}