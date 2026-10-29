package com.redpacketalert.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.redpacketalert.R
import com.redpacketalert.core.EventRecord
import com.redpacketalert.databinding.ItemEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventAdapter : RecyclerView.Adapter<EventAdapter.Holder>() {

    private val items = ArrayList<EventRecord>()
    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    fun submit(values: List<EventRecord>) {
        items.clear()
        items.addAll(values)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: EventRecord) {
            val context = binding.root.context
            binding.eventTime.text = formatter.format(Date(record.time))
            binding.eventBadge.setText(if (record.alerted) R.string.log_alerted else R.string.log_skipped)
            binding.eventBadge.setBackgroundResource(
                if (record.alerted) R.drawable.bg_badge_alert else R.drawable.bg_badge_skip
            )
            val who = listOfNotNull(record.conversation, record.sender)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
            binding.eventSource.text = if (who.isBlank()) record.packageName else who
            binding.eventText.text = record.text.ifBlank { context.getString(R.string.log_no_text) }
            binding.eventReason.text = if (record.keyword != null) {
                context.getString(R.string.log_reason_with_keyword, record.reason, record.keyword)
            } else {
                record.reason
            }
        }
    }
}
