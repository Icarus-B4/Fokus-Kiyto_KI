package com.deepcore.kiytoapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.deepcore.kiytoapp.data.SummaryDatabase
import com.deepcore.kiytoapp.data.entity.Summary
import com.deepcore.kiytoapp.databinding.ActivitySavedSummariesBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SavedSummariesActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySavedSummariesBinding
    private lateinit var toolbar: MaterialToolbar
    private lateinit var adapter: SummaryAdapter
    private lateinit var database: SummaryDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySavedSummariesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = SummaryDatabase.getDatabase(this)
        setupToolbar()
        setupRecyclerView()
        setupTabLayout()
        observeSummaries()
    }

    private fun setupToolbar() {
        toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.saved_summaries)
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = SummaryAdapter(
            onItemClick = { summary ->
                startActivity(ResultsActivity.createIntent(this, summary.content))
            },
            onCreateTask = { summary ->
                createTaskFromSummary(summary)
            },
            onItemLongClick = { summary ->
                showOptionsDialog(summary)
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@SavedSummariesActivity)
            adapter = this@SavedSummariesActivity.adapter
        }
    }

    private fun createTaskFromSummary(summary: Summary) {
        val intent = Intent(this, NewTaskActivity::class.java).apply {
            putExtra(NewTaskActivity.EXTRA_TITLE, summary.title)
            putExtra(NewTaskActivity.EXTRA_DESCRIPTION, summary.content)
        }
        startActivity(intent)
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> observeSummaries(showCompleted = false)
                    1 -> observeSummaries(showCompleted = true)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun observeSummaries(showCompleted: Boolean = false) {
        lifecycleScope.launch {
            val summaries = if (showCompleted) {
                database.summaryDao().getCompletedSummaries()
            } else {
                database.summaryDao().getActiveSummaries()
            }
            summaries.collectLatest { summaryList ->
                adapter.submitList(summaryList)
            }
        }
    }

    private fun showOptionsDialog(summary: Summary) {
        val options = arrayOf(
            getString(R.string.create_task),
            getString(R.string.delete_summary)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_action))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> createTaskFromSummary(summary)
                    1 -> deleteSummaryWithUndo(summary)
                }
            }
            .show()
    }

    private fun deleteSummaryWithUndo(summary: Summary) {
        lifecycleScope.launch {
            try {
                database.summaryDao().delete(summary)
                
                Snackbar.make(
                    binding.root,
                    getString(R.string.summary_deleted),
                    Snackbar.LENGTH_LONG
                ).setAction(getString(R.string.undo)) {
                    lifecycleScope.launch {
                        database.summaryDao().insert(summary)
                    }
                }.show()
            } catch (e: Exception) {
                Log.e("SavedSummariesActivity", "Error deleting summary", e)
                Snackbar.make(
                    binding.root,
                    "Fehler beim Löschen der Zusammenfassung",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, SavedSummariesActivity::class.java)
        }
    }
} 