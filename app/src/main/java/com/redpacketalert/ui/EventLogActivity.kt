package com.redpacketalert.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.redpacketalert.R
import com.redpacketalert.data.EventStore
import com.redpacketalert.databinding.ActivityEventLogBinding

class EventLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventLogBinding
    private val adapter = EventAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.log_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnClear.setOnClickListener {
            EventStore.clear(this)
            reload()
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun reload() {
        val events = EventStore.load(this)
        adapter.submit(events)
        binding.emptyView.visibility =
            if (events.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }
}
