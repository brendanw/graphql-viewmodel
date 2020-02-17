package com.basebeta.envoycoffee.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.basebeta.envoycoffee.App
import com.basebeta.envoycoffee.YelpApi
import com.basebeta.envoycoffee.flow.FlowRelay
import kotlinx.coroutines.FlowPreview

@UseExperimental(FlowPreview::class)
class MainViewModel(
  yelpApi: YelpApi = App.yelpApi,
  app: Application
) : AndroidViewModel(app) {

  private val mainStateMachine = MainStateMachine(
    viewModelScope,
    yelpApi
  )
  val viewState: FlowRelay<MainViewState> = mainStateMachine.viewState

  fun dispatchAction(event: InputEvent) {
    mainStateMachine.dispatchAction(event)
  }
}
