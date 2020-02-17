package com.basebeta.envoycoffee.main

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.basebeta.envoycoffee.App
import com.basebeta.envoycoffee.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.main.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
  private var errorSnackbar: Snackbar? = null
  private lateinit var viewModel: CoMainViewModel
  private var lastState: MainViewState? = null

  var loading = true
  var pastVisiblesItems: Int = 0
  var visibleItemCount: Int = 0
  var totalItemCount: Int = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Activity.RESULT_OK
    setContentView(R.layout.main)
    setSupportActionBar(toolbar)
    setupList()

    viewModel = ViewModelProvider(
      this,
      VMFactory(App.instance)
    ).get(CoMainViewModel::class.java)

    lifecycleScope.launch {
      viewModel
        .viewStateFlow
        .collect { viewState ->
          render(viewState)
        }
    }
    viewModel.processInput(InputEvent.LoadShopsEvent.ScreenLoadEvent(lastState?.shopList ?: emptyList()))

    recycler_view.addOnScrollListener(onScrollListener)
  }

  private val onScrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      // check for scroll down
      if (dy > 0) {
        val layoutManager = (recycler_view.layoutManager as LinearLayoutManager)
        visibleItemCount = layoutManager.childCount
        totalItemCount = layoutManager.itemCount
        pastVisiblesItems = layoutManager.findFirstVisibleItemPosition()

        if (loading) {
          if (visibleItemCount + pastVisiblesItems >= totalItemCount) {
            loading = false
            viewModel.processInput(
              InputEvent.LoadShopsEvent.ScrollToEndEvent(
                lastState?.shopList ?: emptyList(),
                lastState?.currentPage ?: 0
              )
            )
          }
        }
      }
    }
  }

  private fun render(viewState: MainViewState) {
    setItems(viewState.shopList, viewState.diffResult)
    showErrorMessage(viewState)
  }

  private fun showErrorMessage(viewState: MainViewState) {
    if (viewState.showNetworkError) {
      errorSnackbar = Snackbar.make(root, R.string.network_error, Snackbar.LENGTH_INDEFINITE)
      errorSnackbar?.setAction(R.string.reload_shops) {
        viewModel.processInput(
          InputEvent.LoadShopsEvent.ReloadShopsEvent(
            lastState?.shopList ?: emptyList(),
            lastState?.currentPage ?: 0
          )
        )
      }
      errorSnackbar?.show()
    } else {
      errorSnackbar?.dismiss()
    }
  }

  private fun setItems(items: List<YelpResult>, diffResult: DiffUtil.DiffResult?) {
    errorSnackbar?.dismiss()
    val adapter = recycler_view.adapter as MainAdapter
    adapter.setData(items)
    diffResult?.dispatchUpdatesTo(recycler_view.adapter as MainAdapter)
    if (diffResult == null) adapter.notifyDataSetChanged()
  }

  private fun setupList() {
    with(recycler_view) {
      adapter = MainAdapter { item ->
        viewModel.processInput(
          InputEvent.TapItemEvent(
            shopName = item.name,
            totalItemTaps = lastState?.totalItemTaps ?: 0
          )
        )
      }
      layoutManager = LinearLayoutManager(this@MainActivity).apply {
        orientation = LinearLayoutManager.VERTICAL
      }
      val divider = DividerItemDecoration(
        context,
        DividerItemDecoration.VERTICAL
      )
      divider.setDrawable(
        ContextCompat.getDrawable(
          context,
          R.drawable.divider
        )!!
      )
      addItemDecoration(divider)
    }
  }
}

class VMFactory(
  private val app: Application
) : ViewModelProvider.Factory {
  override fun <T : ViewModel?> create(modelClass: Class<T>): T {
    return CoMainViewModel(
      app = app
    ) as T
  }
}
