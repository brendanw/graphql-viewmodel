package com.basebeta.envoycoffee.main

import androidx.recyclerview.widget.DiffUtil
import com.basebeta.envoycoffee.YelpApi
import com.basebeta.envoycoffee.flow.FlowRelay
import com.dropbox.flow.multicast.Multicaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

class MainStateMachine(
  private val scope: CoroutineScope,
  private val yelpApi: YelpApi
) {
  val viewState: FlowRelay<MainViewState> = FlowRelay()
  val inputEvents: FlowRelay<InputEvent> = FlowRelay()

  init {
    scope.launch {
      val mcaster = Multicaster(
        scope = scope,
        bufferSize = 16,
        source = inputEvents.onEach { Timber.i("input event $it") },
        piggybackingDownstream = false,
        keepUpstreamAlive = false,
        onEach = { }
      )
      merge(
        mcaster.newDownstream()
          .filterIsInstance<InputEvent.LoadShopsEvent.ScreenLoadEvent>()
          .loadShops(),
        mcaster.newDownstream().filterIsInstance<InputEvent.LoadShopsEvent.ReloadShopsEvent>().loadShops(),
        mcaster.newDownstream().filterIsInstance<InputEvent.LoadShopsEvent.ScrollToEndEvent>().loadShops(),
        mcaster.newDownstream().filterIsInstance<InputEvent.TapItemEvent>().onItemTap()
      )
        .onEach { Timber.i("result $it") }
        .resultToViewState()
        .collect { newState ->
          viewState.send(newState)
        }
    }
  }

  fun dispatchAction(action: InputEvent) {
    scope.launch {
      inputEvents.send(action)
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

}
