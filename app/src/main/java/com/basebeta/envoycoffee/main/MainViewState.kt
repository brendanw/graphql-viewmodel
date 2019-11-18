package com.basebeta.envoycoffee.main

import androidx.recyclerview.widget.DiffUtil

data class YelpResult(
    val name: String,
    val imageUrl: String,
    val address: String,
    val cost: String?)

data class MainViewState(
    val shopList: List<YelpResult> = emptyList(),
    val diffResult: DiffUtil.DiffResult? = null,
    val showNetworkError: Boolean = false,
    val forceRender: String = ""
)

// Events are user inputs or lifecycle events
sealed class MainEvent {
    sealed class LoadShopsEvent(val list: List<YelpResult>) : MainEvent() {
        data class ScreenLoadEvent(val newList: List<YelpResult>) : LoadShopsEvent(newList)
        data class ReloadShopsEvent(val newList: List<YelpResult>) : LoadShopsEvent(newList)
    }
    data class TapItemEvent(val shopName: String) : MainEvent()

}

// Each event triggers a result
sealed class MainResult {
    data class QueryYelpResult(val shopList: List<YelpResult>,
                               val diffResult: DiffUtil.DiffResult,
                               val networkError: Boolean,
                               val forceRender: Boolean = false) : MainResult()
    object TapItemResult : MainResult()
}

