package com.example.landmarkremark.screens.notes.components

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.landmarkremark.R
import com.example.landmarkremark.models.Note

class SearchResultAdapter(private var dataset: ArrayList<Note>) :
    RecyclerView.Adapter<SearchResultAdapter.ItemViewHolder>() {

    class ItemViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        private val titleTextView: TextView = view.findViewById(R.id.name)
        private val descriptionTextView: TextView = view.findViewById(R.id.description)
        fun bind(item: Note) {
            titleTextView.text = item.name
            descriptionTextView.text = item.description
        }
    }

    // Inflate item layout to create a new view holder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val adapterLayout =
            LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        return ItemViewHolder(adapterLayout)
    }

    // Bind the data to the view holder
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(dataset[position])
    }
    // function to remove item of recycle view
    fun removeItem(itemId: String) {
        val index = dataset.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            dataset.removeIf { it.id == itemId }
            notifyItemRemoved(index)
        }
    }

    // function to add item of recycle view
    fun addItem(item: Note) {
        dataset.add(item)
        notifyItemInserted(dataset.size - 1)
    }
    // function update new list data of recycle view
    fun setDataChange(newItems: ArrayList<Note>) {
        dataset = newItems
        notifyDataSetChanged()
    }

    // Return the size of your dataset
    override fun getItemCount() = dataset.size
}
