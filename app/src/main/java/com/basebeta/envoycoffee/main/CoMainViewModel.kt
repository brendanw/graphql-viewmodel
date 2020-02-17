package com.basebeta.envoycoffee.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import com.basebeta.envoycoffee.App
import com.basebeta.envoycoffee.YelpApi
import com.basebeta.envoycoffee.flow.FlowRelay
import com.dropbox.flow.multicast.Multicaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

@UseExperimental(FlowPreview::class)
class CoMainViewModel(
  private val yelpApi: YelpApi = App.yelpApi,
  app: Application
) : AndroidViewModel(app) {

  val viewStateFlow: FlowRelay<MainViewState> = FlowRelay()
  var inputEvents: FlowRelay<InputEvent> = FlowRelay()

  init {
    viewModelScope.launch {
      val mcaster = Multicaster(
        scope = viewModelScope,
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
        .collect { viewState ->
          viewStateFlow.send(viewState)
        }
    }
  }

  /**
   * WARNING: Limited to 16 concurrent flows
   */
  private fun <T> merge(vararg flows: Flow<T>): Flow<T> = flowOf(*flows).flattenMerge()

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
    viewModelScope.launch {
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
}
