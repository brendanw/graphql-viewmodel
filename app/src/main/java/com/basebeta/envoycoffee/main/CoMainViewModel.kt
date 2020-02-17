package com.basebeta.envoycoffee.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import com.basebeta.envoycoffee.App
import com.basebeta.envoycoffee.YelpApi
import com.basebeta.envoycoffee.multicast.Multicaster
import com.jakewharton.rxrelay2.PublishRelay
import hu.akarnokd.kotlin.flow.publish
import io.reactivex.Observable
import io.reactivex.internal.functions.Functions
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

@UseExperimental(FlowPreview::class)
class CoMainViewModel(
    private val yelpApi: YelpApi = App.yelpApi,
    private var app: Application = App.instance
) : AndroidViewModel(app), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job

    @UseExperimental(ExperimentalCoroutinesApi::class)
    val viewStateChannel: ConflatedBroadcastChannel<MainViewState> = ConflatedBroadcastChannel()
    @UseExperimental(ExperimentalCoroutinesApi::class)
    var inputEvents: ConflatedBroadcastChannel<InputEvent> = ConflatedBroadcastChannel()

    init {
        launch {
            val mcaster = Multicaster(
                scope = this@CoMainViewModel,
                source = {
                    inputEvents.asFlow()
                        .onEach { Timber.i("input event $it") }
                },
                onEach = {}
            )
            merge(
                mcaster.create()
                    .filterIsInstance<InputEvent.LoadShopsEvent.ScreenLoadEvent>()
                    .loadShops(),
                mcaster.create().filterIsInstance<InputEvent.LoadShopsEvent.ReloadShopsEvent>().loadShops(),
                mcaster.create().filterIsInstance<InputEvent.LoadShopsEvent.ScrollToEndEvent>().loadShops(),
                mcaster.create().filterIsInstance<InputEvent.TapItemEvent>().onItemTap()
            )
                .onEach { Timber.i("result $it") }
                .resultToViewState()
                .collect { viewState ->
                    viewStateChannel.send(viewState)
                }
        }
    }

    /**
     * WARNING: Limited to 16 concurrent flows
     */
    fun <T> merge(vararg flows: Flow<T>): Flow<T> = flowOf(*flows).flattenMerge()

    fun <T> Flow<T>.myPublish(transform: suspend (value: T) -> T): Flow<T> {
        return Multicaster(
            scope = this@CoMainViewModel,
            source = { this },
            onEach = {}
        ).create()
    }

    private fun Flow<InputEvent>.eventToResult(): Flow<MainResult> {
        return publish { multicastFlow ->
            merge(
                multicastFlow
                    .filterIsInstance<InputEvent.LoadShopsEvent.ScreenLoadEvent>()
                    .loadShops(),
                multicastFlow.filterIsInstance<InputEvent.LoadShopsEvent.ReloadShopsEvent>().loadShops(),
                multicastFlow.filterIsInstance<InputEvent.LoadShopsEvent.ScrollToEndEvent>().loadShops(),
                multicastFlow.filterIsInstance<InputEvent.TapItemEvent>().onItemTap()
            )
        }
    }

    private fun Flow<InputEvent.TapItemEvent>.onItemTap(): Flow<MainResult.TapItemResult> {
        return map {
            MainResult.TapItemResult(totalItemTaps = it.totalItemTaps + 1)
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
                        forceRender = if (result.forceRender) UUID.randomUUID().toString() else ""
                    )
                }
                is MainResult.TapItemResult -> {
                    lastState.copy(totalItemTaps = result.totalItemTaps)
                }
            }
        }
            .distinctUntilChanged()
    }

    fun processInput(event: InputEvent) {
        launch {
            inputEvents.send(event)
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
                    val diffResult = DiffUtil.calculateDiff(
                        ItemDiffHelper(
                            oldList = event.list,
                            newList = newList
                        )
                    )
                    MainResult.QueryYelpResult(
                        shopList = newList,
                        networkError = false,
                        diffResult = diffResult,
                        currentPage = newPage
                    )
                }
                .catch {
                    val diffResult = DiffUtil.calculateDiff(
                        ItemDiffHelper(
                            oldList = event.list,
                            newList = event.list
                        )
                    )
                    MainResult.QueryYelpResult(
                        shopList = event.list,
                        networkError = true,
                        forceRender = true,
                        diffResult = diffResult, currentPage = event.currentPage
                    )
                }.flowOn(Dispatchers.IO)
        }
    }

    class CoMainViewModelFactory(
        private val yelpApi: YelpApi = App.yelpApi,
        private val app: Application
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return CoMainViewModel(yelpApi = yelpApi, app = app) as T
        }
    }
}