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
    private var inputEvents: PublishRelay<InputEvent> = PublishRelay.create()

    init {
        disposable = inputEvents
            .doOnNext {
                Timber.d("--- emitted event $it")
            }
            .eventToResult() //publish(merge())
            .doOnNext {
                Timber.d("--- result event $it")
            }
            .resultToViewState() //scan
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

    // Why not just do a flatMap + when statement here?
    private fun Observable<InputEvent>.eventToResult(): Observable<MainResult> {
        return publish { multicastedEvent ->
            Observable.merge(
                multicastedEvent.ofType(InputEvent.LoadShopsEvent.ScreenLoadEvent::class.java).loadShops(),
                multicastedEvent.ofType(InputEvent.TapItemEvent::class.java).onItemTap(),
                multicastedEvent.ofType(InputEvent.LoadShopsEvent.ReloadShopsEvent::class.java).loadShops(),
                multicastedEvent.ofType(InputEvent.LoadShopsEvent.ScrollToEndEvent::class.java).loadShops()
            )
        }
    }

    private fun Observable<out InputEvent.LoadShopsEvent>.loadShops(): Observable<MainResult.QueryYelpResult> {
        return this.switchMap { event ->
            return@switchMap yelpApi.getShops(event.currentPage)
                    .subscribeOn(Schedulers.io())
                    .map {
                        val newList = event.list.toMutableList().apply {
                            addAll(it)
                        }
                        val newPage = event.currentPage + 1
                        val diffResult = DiffUtil.calculateDiff(ItemDiffHelper(oldList = event.list, newList = newList))
                        MainResult.QueryYelpResult(
                            shopList = newList,
                            networkError = false,
                            diffResult = diffResult,
                            currentPage = newPage)
                    }
                    .onErrorReturn {
                        val diffResult = DiffUtil.calculateDiff(ItemDiffHelper(oldList = event.list, newList = event.list))
                        MainResult.QueryYelpResult(shopList = event.list,
                            networkError = true,
                            forceRender = true,
                            diffResult = diffResult, currentPage = event.currentPage)
                    }
        }
    }

    private fun Observable<InputEvent.TapItemEvent>.onItemTap(): Observable<MainResult.TapItemResult> {
        return map {
            MainResult.TapItemResult(totalItemTaps = it.totalItemTaps + 1)
        }
    }

    private fun Observable<MainResult>.resultToViewState(): Observable<MainViewState> {
        return scan(MainViewState()) { lastState, result ->
            when(result) {
                is MainResult.QueryYelpResult -> {
                    //TODO: Should be using
                    lastState.copy(
                        currentPage = result.currentPage,
                        shopList = result.shopList,
                        showNetworkError = result.networkError,
                        forceRender = if (result.forceRender) UUID.randomUUID().toString() else "")
                }
                is MainResult.TapItemResult -> {
                    lastState.copy(totalItemTaps = result.totalItemTaps)
                }
            }
        }.distinctUntilChanged()
    }

    fun processInput(event: InputEvent) {
        inputEvents.accept(event)
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