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
    val forceRender: String = "",
    val currentPage: Int = 0,
    val totalItemTaps: Int = 0
)

// Events are user inputs or lifecycle events
sealed class MainEvent {
    sealed class LoadShopsEvent(val list: List<YelpResult>, val currentPage: Int) : MainEvent() {
        data class ScreenLoadEvent(val lastList: List<YelpResult>) : LoadShopsEvent(lastList, 0)
        data class ReloadShopsEvent(val lastList: List<YelpResult>, val page: Int) : LoadShopsEvent(lastList, page)
        data class ScrollToEndEvent(val lastList: List<YelpResult>, val page: Int): LoadShopsEvent(lastList, page)
    }
    data class TapItemEvent(val shopName: String, val totalItemTaps: Int) : MainEvent()
}

// Each event triggers a result
sealed class MainResult {
    data class QueryYelpResult(val shopList: List<YelpResult>,
                               val diffResult: DiffUtil.DiffResult,
                               val currentPage: Int,
                               val networkError: Boolean,
                               val forceRender: Boolean = false) : MainResult()
    data class TapItemResult(val totalItemTaps: Int) : MainResult()
}

