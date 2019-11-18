package com.basebeta.envoycoffee.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import com.basebeta.envoycoffee.App
import com.basebeta.envoycoffee.YelpApi
import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*

class MainViewModel(
    private val yelpApi: YelpApi = App.yelpApi,
    app: Application
): AndroidViewModel(app) {
    val viewState: BehaviorRelay<MainViewState> = BehaviorRelay.create()
    private val disposable: Disposable
    private var eventEmitter: PublishRelay<MainEvent> = PublishRelay.create()
    var hasInited = false

    init {
        disposable = eventEmitter
            .doOnNext {
                Timber.d("--- emitted event $it")
            }
            .eventToResult()
            .doOnNext {
                Timber.d("--- result event $it")
            }
            .resultToViewState()
            .doOnNext {
                Timber.d("--- view state $it")
            }.subscribeBy(onNext = {
                viewState.accept(it)
            })
    }

    override fun onCleared() {
        super.onCleared()
        disposable.dispose()
    }

    private fun Observable<MainEvent>.eventToResult(): Observable<MainResult> {
        return publish { o ->
            Observable.merge(
                o.ofType(MainEvent.LoadShopsEvent.ScreenLoadEvent::class.java).loadShops(),
                o.ofType(MainEvent.TapItemEvent::class.java).onItemTap(),
                o.ofType(MainEvent.LoadShopsEvent.ReloadShopsEvent::class.java).loadShops()
            )
        }
    }

    private fun Observable<out MainEvent.LoadShopsEvent>.loadShops(): Observable<MainResult.QueryYelpResult> {
        return this.switchMap { event ->
            return@switchMap if (event.list.isEmpty()) {
                yelpApi.getShops()
                    .subscribeOn(Schedulers.io())
                    .map {
                        val diffResult = DiffUtil.calculateDiff(ItemDiffHelper(oldList = event.list, newList = it))
                        MainResult.QueryYelpResult(
                            shopList = it,
                            networkError = false,
                            diffResult = diffResult)
                    }
                    .onErrorReturn {
                        val diffResult = DiffUtil.calculateDiff(ItemDiffHelper(oldList = event.list, newList = event.list))
                        MainResult.QueryYelpResult(shopList = event.list,
                            networkError = true,
                            forceRender = true,
                            diffResult = diffResult)
                    }
            } else {
                Observable.defer {
                    val diffResult = DiffUtil.calculateDiff(ItemDiffHelper(oldList = event.list, newList = event.list))
                    Observable.just(MainResult.QueryYelpResult(shopList = event.list,
                        networkError = true,
                        forceRender = true,
                        diffResult = diffResult))
                }.subscribeOn(Schedulers.io())
            }
        }
    }

    private fun Observable<MainEvent.TapItemEvent>.onItemTap(): Observable<MainResult.TapItemResult> {
        return map {
            MainResult.TapItemResult
        }
    }

    private fun Observable<MainResult>.resultToViewState(): Observable<MainViewState> {
        return scan(MainViewState()) { viewState, result ->
            when(result) {
                is MainResult.QueryYelpResult -> {
                    viewState.copy(
                        shopList = result.shopList,
                        showNetworkError = result.networkError,
                        forceRender = if (result.forceRender) UUID.randomUUID().toString() else "")
                }
                is MainResult.TapItemResult -> {
                    viewState
                }
            }
        }.distinctUntilChanged()
    }

    fun processInput(event: MainEvent) {
        eventEmitter.accept(event)
    }

    class MainViewModelFactory(
        private val yelpApi: YelpApi = App.yelpApi,
        private val app: Application
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return MainViewModel(yelpApi = yelpApi, app = app) as T
        }
    }
}