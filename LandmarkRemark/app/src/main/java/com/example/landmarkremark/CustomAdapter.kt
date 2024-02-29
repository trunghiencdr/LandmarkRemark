package com.example.landmarkremark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Define your data model
data class Item(val title: String, val description: String)

// Create your custom adapter
class CustomAdapter(private var dataset: List<String>) : RecyclerView.Adapter<CustomAdapter.ItemViewHolder>() {

    // Create your custom view holder
    class ItemViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.title)
        val descriptionTextView: TextView = view.findViewById(R.id.description)
    }

    // Inflate your custom item layout to create a new view holder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        return ItemViewHolder(adapterLayout)
    }

    // Bind the data to the view holder
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = dataset[position]
        holder.titleTextView.text = item
        holder.descriptionTextView.text = item
    }

    public fun setDataChange(newItems: List<String>){
        dataset = newItems
        notifyDataSetChanged()
    }

    // Return the size of your dataset
    override fun getItemCount() = dataset.size
}
