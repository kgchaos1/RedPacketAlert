package com.redpacketalert.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.redpacketalert.databinding.ItemEditableTextBinding

class StringListAdapter(
    private val onEdit: (String) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<StringListAdapter.Holder>() {

    private val items = ArrayList<String>()

    fun submit(values: List<String>) {
        items.clear()
        items.addAll(values)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemEditableTextBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemEditableTextBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(value: String) {
            binding.itemText.text = value
            binding.itemText.setOnClickListener { onEdit(value) }
            binding.itemDelete.setOnClickListener { onDelete(value) }
        }
    }
}
