package com.redpacketalert.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.redpacketalert.R
import com.redpacketalert.core.AlertSettings
import com.redpacketalert.data.SettingsStore
import com.redpacketalert.databinding.ActivityListEditorBinding

/** 通用列表编辑器：关键词、排除词、白名单、免打扰名单都是同一套界面。 */
class ListEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListEditorBinding
    private lateinit var adapter: StringListAdapter
    private var listKey: String = SettingsStore.Keys.KEYWORDS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        listKey = intent.getStringExtra(EXTRA_KEY) ?: SettingsStore.Keys.KEYWORDS
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.inputEdit.hint = intent.getStringExtra(EXTRA_HINT) ?: getString(R.string.editor_hint_default)

        adapter = StringListAdapter(
            onEdit = { value ->
                binding.inputEdit.setText(value)
                binding.inputEdit.setSelection(binding.inputEdit.text?.length ?: 0)
                remove(value)
            },
            onDelete = { value -> remove(value) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnAdd.setOnClickListener { addCurrentInput() }
        binding.btnReset.setOnClickListener { resetToDefault() }
        binding.btnClear.setOnClickListener {
            SettingsStore.writeList(this, listKey, emptyList())
            reload()
        }

        reload()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun addCurrentInput() {
        val value = binding.inputEdit.text?.toString()?.trim().orEmpty()
        if (value.isEmpty()) {
            Toast.makeText(this, R.string.editor_empty_input, Toast.LENGTH_SHORT).show()
            return
        }
        val current = SettingsStore.readList(this, listKey, defaultFor(listKey)).toMutableList()
        if (current.any { it.equals(value, ignoreCase = true) }) {
            Toast.makeText(this, R.string.editor_duplicated, Toast.LENGTH_SHORT).show()
            return
        }
        current.add(value)
        SettingsStore.writeList(this, listKey, current)
        binding.inputEdit.setText("")
        reload()
        binding.recycler.scrollToPosition(current.size - 1)
    }

    private fun remove(value: String) {
        val current = SettingsStore.readList(this, listKey, defaultFor(listKey)).toMutableList()
        current.removeAll { it == value }
        SettingsStore.writeList(this, listKey, current)
        reload()
    }

    private fun resetToDefault() {
        SettingsStore.writeList(this, listKey, defaultFor(listKey))
        reload()
    }

    private fun reload() {
        val values = SettingsStore.readList(this, listKey, defaultFor(listKey))
        adapter.submit(values)
        binding.emptyView.visibility = if (values.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun defaultFor(key: String): List<String> = when (key) {
        SettingsStore.Keys.EXCLUDE_KEYWORDS -> AlertSettings.DEFAULT_EXCLUDES
        SettingsStore.Keys.MONITORED_PACKAGES -> AlertSettings.DEFAULT_PACKAGES
        SettingsStore.Keys.WHITELIST, SettingsStore.Keys.MUTE_LIST -> emptyList()
        else -> AlertSettings.DEFAULT_KEYWORDS
    }

    companion object {
        private const val EXTRA_KEY = "list_key"
        private const val EXTRA_TITLE = "list_title"
        private const val EXTRA_HINT = "list_hint"

        fun intent(context: Context, key: String, title: String, hint: String): Intent =
            Intent(context, ListEditorActivity::class.java)
                .putExtra(EXTRA_KEY, key)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_HINT, hint)
    }
}
