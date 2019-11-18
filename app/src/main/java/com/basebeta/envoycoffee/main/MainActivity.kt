package com.basebeta.envoycoffee.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import com.basebeta.envoycoffee.App
import com.basebeta.envoycoffee.R
import com.google.android.material.snackbar.Snackbar
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.main.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {
  private var errorSnackbar: Snackbar? = null
  private var compositeDisposable = CompositeDisposable()
  private lateinit var viewModel: MainViewModel
  private var lastList = emptyList<YelpResult>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)
    setSupportActionBar(toolbar)
    setupList()

    viewModel = ViewModelProviders.of(
      this,
      MainViewModel.MainViewModelFactory(App.yelpApi, application)
    ).get(MainViewModel::class.java)

    compositeDisposable.add(
      viewModel
        .viewState
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext { Timber.d("----- onNext VS $it") }
        .subscribeBy(onNext = {
          render(it)
          lastList = it.shopList
        }, onError = {
          Timber.w(it, "something went terribly wrong processing view state")
        })
    )

    if (!viewModel.hasInited) {
      viewModel.processInput(MainEvent.LoadShopsEvent.ScreenLoadEvent(lastList))
      viewModel.hasInited = true
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    compositeDisposable.clear()
  }

  private fun render(viewState: MainViewState) {
    setItems(viewState.shopList, viewState.diffResult)
    showErrorMessage(viewState)
  }

  private fun showErrorMessage(viewState: MainViewState) {
    if (viewState.showNetworkError) {
      errorSnackbar = Snackbar.make(root, R.string.network_error, Snackbar.LENGTH_INDEFINITE)
      errorSnackbar?.setAction(R.string.reload_shops) {
        viewModel.processInput(MainEvent.LoadShopsEvent.ReloadShopsEvent(lastList))
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
      adapter = MainAdapter { item -> viewModel.processInput(MainEvent.TapItemEvent(shopName = item.name)) }
      layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity).apply {
        orientation = androidx.recyclerview.widget.LinearLayoutManager.VERTICAL
      }
      val divider = DividerItemDecoration(
        context,
        DividerItemDecoration.VERTICAL
      )
      divider.setDrawable(ContextCompat.getDrawable(context,
        R.drawable.divider
      )!!)
      addItemDecoration(divider)
    }
  }
}
