package com.basebeta.envoycoffee.main

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.api.load
import coil.transform.CircleCropTransformation
import com.basebeta.envoycoffee.R
import kotlinx.android.synthetic.main.main_item.view.*

class MainAdapter(private val itemTapListener: (YelpResult) -> Unit) : RecyclerView.Adapter<MainAdapter.ItemViewHolder>() {
    private var results: List<YelpResult> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.main_item, parent, false)
        return ItemViewHolder(view)
    }

    override fun getItemCount(): Int {
        return results.size
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = results[position]
        val spannable = SpannableStringBuilder("${item.name}\n${item.address}")
        val colorSpan = ForegroundColorSpan(holder.itemView.resources.getColor(R.color.offgrey))
        holder.itemView.setOnClickListener { itemTapListener.invoke(item) }
        spannable.setSpan(colorSpan, item.name.length + 1, spannable.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(0.8f), item.name.length + 1,spannable.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        holder.nameAndLocation.text = spannable
        holder.price.text = item.cost
        holder.image.load(item.imageUrl) {
            transformations(CircleCropTransformation())
        }
    }

    fun setData(items: List<YelpResult>) {
        this.results = items
    }

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameAndLocation: TextView = itemView.shop_info
        val image: ImageView = itemView.image
        val price: TextView = itemView.price
    }
}

class ItemDiffHelper(private val newList: List<YelpResult>,
                     private val oldList: List<YelpResult>) : DiffUtil.Callback() {
    override fun getOldListSize(): Int {
        return oldList.size
    }

    override fun getNewListSize(): Int {
        return newList.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].name == newList[newItemPosition].name
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}