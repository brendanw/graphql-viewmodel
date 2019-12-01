package com.basebeta.envoycoffee.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.recyclerview.widget.DiffUtil
import com.basebeta.envoycoffee.App
import com.basebeta.envoycoffee.YelpApi
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.*
import kotlin.coroutines.CoroutineContext

@UseExperimental(FlowPreview::class)
class CoMainViewModel(
    private val yelpApi: YelpApi = App.yelpApi,
    private var app: Application = App.instance
    ): AndroidViewModel(app), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job

    @UseExperimental(ExperimentalCoroutinesApi::class)
    val viewState: ConflatedBroadcastChannel<MainViewState> = ConflatedBroadcastChannel()
    var inputEvents: BroadcastChannel<InputEvent> = BroadcastChannel(capacity = BUFFERED)

    init {
        inputEvents.asFlow()
            .onEach { Timber.d("input event $it") }
            .eventToResult()
            .onEach { Timber.d("result $it") }
            .resultToViewState()
    }

    private fun Flow<InputEvent>.eventToResult(): Flow<MainResult> {
        return flatMapMerge { inputEvent ->
            when(inputEvent) {
                is InputEvent.LoadShopsEvent.ScreenLoadEvent -> {
                    flow<InputEvent.LoadShopsEvent.ScreenLoadEvent> { emit(inputEvent) }.loadShops()
                }
                is InputEvent.LoadShopsEvent.ReloadShopsEvent -> {
                    flow<InputEvent.LoadShopsEvent.ReloadShopsEvent> { emit(inputEvent) }.loadShops()
                }
                is InputEvent.LoadShopsEvent.ScrollToEndEvent -> {
                    flow<InputEvent.LoadShopsEvent.ScrollToEndEvent> { emit(inputEvent) }.loadShops()
                }
                is InputEvent.TapItemEvent -> {
                    flow { emit(MainResult.TapItemResult(totalItemTaps = 0)) }
                }
            }
        }
    }

    @UseExperimental(ExperimentalCoroutinesApi::class)
    private fun Flow<MainResult>.resultToViewState(): Flow<MainViewState> {
        return scan(MainViewState()) { lastState, result ->
            when (result) {
                is MainResult.QueryYelpResult -> {
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
        }
    }

    @UseExperimental(ExperimentalCoroutinesApi::class)
    private fun Flow<InputEvent.LoadShopsEvent>.loadShops(): Flow<MainResult.QueryYelpResult> {
        return flatMapLatest { event ->
            return@flatMapLatest yelpApi.coGetShops(event.currentPage)
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
                .catch {
                    val diffResult = DiffUtil.calculateDiff(ItemDiffHelper(oldList = event.list, newList = event.list))
                    MainResult.QueryYelpResult(shopList = event.list,
                        networkError = true,
                        forceRender = true,
                        diffResult = diffResult, currentPage = event.currentPage)
                }.flowOn(Dispatchers.IO)
        }
    }
}