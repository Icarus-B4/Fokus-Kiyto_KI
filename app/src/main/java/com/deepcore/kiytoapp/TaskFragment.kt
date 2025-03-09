package com.deepcore.kiytoapp

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.deepcore.kiytoapp.data.Task
import com.deepcore.kiytoapp.data.TaskManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.deepcore.kiytoapp.MindMapView
import com.deepcore.kiytoapp.base.BaseFragment
import com.deepcore.kiytoapp.databinding.FragmentTasksBinding

class TaskFragment : BaseFragment() {
    private var taskRecyclerView: RecyclerView? = null
    private var fabMenu: View? = null
    private var mainFab: FloatingActionButton? = null
    private var mindMapButton: FloatingActionButton? = null
    private var videoSummaryButton: FloatingActionButton? = null
    private var addTaskButton: FloatingActionButton? = null
    private var searchView: SearchView? = null
    private var filterChipGroup: ChipGroup? = null
    private var _adapter: TaskAdapter? = null
    private var _taskManager: TaskManager? = null
    private var currentFilter: TaskFilter = TaskFilter.DUE_DATE
    private var showCompleted: Boolean = false
    private var isListView = true
    private var isFabMenuOpen = false
    private var savedSummariesButton: FloatingActionButton? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        _taskManager = TaskManager(requireContext())
        setupListView(view)
    }

    private fun setupListView(view: View) {
        try {
            initializeViews(view)
            setupRecyclerView()
            setupFabMenu()
            setupSearchView()
            setupFilterChips()
            setupSwipeToDelete()
            loadTasks()
        } catch (e: Exception) {
            Log.e("TaskFragment", "Fehler beim Initialisieren: ${e.message}", e)
            showErrorDialog()
        }
    }

    private fun initializeViews(view: View) {
        taskRecyclerView = view.findViewById(R.id.taskRecyclerView)
        fabMenu = view.findViewById(R.id.fabMenu)
        mainFab = view.findViewById(R.id.mainFab)
        mindMapButton = view.findViewById(R.id.mindMapButton)
        videoSummaryButton = view.findViewById(R.id.videoSummaryButton)
        addTaskButton = view.findViewById(R.id.addTaskButton)
        searchView = view.findViewById(R.id.searchView)
        filterChipGroup = view.findViewById(R.id.filterChipGroup)
        savedSummariesButton = view.findViewById(R.id.savedSummariesButton)
    }

    private fun setupFabMenu() {
        mainFab?.setOnClickListener {
            toggleFabMenu()
        }

        savedSummariesButton?.setOnClickListener {
            toggleFabMenu()
            startActivity(Intent(requireContext(), SavedSummariesActivity::class.java))
        }

        videoSummaryButton?.setOnClickListener {
            toggleFabMenu()
            startActivity(Intent(requireContext(), VideoSummaryActivity::class.java))
        }

        mindMapButton?.setOnClickListener {
            toggleFabMenu()
            startActivity(Intent(requireContext(), MindMapActivity::class.java))
        }

        addTaskButton?.setOnClickListener {
            toggleFabMenu()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AddTaskFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun toggleFabMenu() {
        isFabMenuOpen = !isFabMenuOpen
        fabMenu?.visibility = if (isFabMenuOpen) View.VISIBLE else View.GONE
        mainFab?.animate()?.rotation(if (isFabMenuOpen) 45f else 0f)
    }

    private fun setupRecyclerView() {
        _adapter = TaskAdapter(
            onTaskChecked = { task ->
                lifecycleScope.launch {
                    try {
                        _taskManager?.toggleTaskCompletion(task.id)
                        loadTasks()
                    } catch (e: Exception) {
                        Log.e("TaskFragment", "Fehler beim Togglen der Task: ${e.message}", e)
                    }
                }
            },
            onTaskClicked = { task ->
                try {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, TaskDetailsFragment.newInstance(task.id))
                        .addToBackStack(null)
                        .commit()
                } catch (e: Exception) {
                    Log.e("TaskFragment", "Fehler beim Öffnen der Task-Details: ${e.message}", e)
                }
            }
        )
        
        taskRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@TaskFragment._adapter
        }
    }

    private fun setupSearchView() {
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                _taskManager?.let { taskManager ->
                    lifecycleScope.launch {
                        try {
                            if (newText.isNullOrBlank()) {
                                loadTasks()
                            } else {
                                val tasks = taskManager.searchTasks(newText).first()
                                _adapter?.submitList(tasks)
                            }
                        } catch (e: Exception) {
                            Log.e("TaskFragment", "Fehler bei der Suche: ${e.message}", e)
                        }
                    }
                }
                return true
            }
        })
    }

    private fun setupFilterChips() {
        filterChipGroup?.let { chipGroup ->
            chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
                val checkedId = checkedIds.firstOrNull() ?: -1
                when (checkedId) {
                    R.id.chipPriority -> {
                        currentFilter = TaskFilter.PRIORITY
                        loadTasks()
                    }
                    R.id.chipDueDate -> {
                        currentFilter = TaskFilter.DUE_DATE
                        loadTasks()
                    }
                    R.id.chipCompleted -> {
                        currentFilter = TaskFilter.COMPLETED
                        loadTasks()
                    }
                }
            }
        }

        val chipCompleted = filterChipGroup?.findViewById<Chip>(R.id.chipCompleted)
        chipCompleted?.setOnCheckedChangeListener { _, isChecked ->
            showCompleted = isChecked
            loadTasks()
        }
    }

    private fun deleteCalendarEvent(task: Task) {
        task.calendarEventId?.let { eventId ->
            try {
                val deleteUri = ContentValues().apply {
                    put(CalendarContract.Events._ID, eventId)
                }
                requireContext().contentResolver.delete(
                    CalendarContract.Events.CONTENT_URI,
                    "${CalendarContract.Events._ID} = ?",
                    arrayOf(eventId.toString())
                )
            } catch (e: Exception) {
                Log.e("TaskFragment", "Fehler beim Löschen des Kalendereintrags: ${e.message}")
            }
        }
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                _adapter?.let { adapter ->
                    val task = adapter.currentList[position]
                    
                    lifecycleScope.launch {
                        try {
                            // Zuerst den Kalendereintrag löschen, falls vorhanden
                            deleteCalendarEvent(task)
                            
                            // Dann die Aufgabe löschen
                            _taskManager?.deleteTask(task.id)
                            loadTasks()
                            
                            view?.let { view ->
                                Snackbar.make(
                                    view,
                                    getString(R.string.task_deleted),
                                    Snackbar.LENGTH_LONG
                                )
                                .setBackgroundTint(resources.getColor(R.color.background_dark, null))
                                .setTextColor(resources.getColor(R.color.white, null))
                                .setActionTextColor(resources.getColor(R.color.primary, null))
                                .show()
                            }
                        } catch (e: Exception) {
                            Log.e("TaskFragment", "Fehler beim Löschen der Task: ${e.message}", e)
                        }
                    }
                }
            }
        }

        taskRecyclerView?.let { recyclerView ->
            ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
        }
    }

    private fun loadTasks() {
        _taskManager?.let { taskManager ->
            lifecycleScope.launch {
                try {
                    val tasks = when (currentFilter) {
                        TaskFilter.DUE_DATE -> taskManager.getTasksByDueDate(showCompleted).first()
                        TaskFilter.PRIORITY -> taskManager.getTasksSortedByPriority().first()
                        TaskFilter.COMPLETED -> taskManager.getCompletedTasks().first()
                        TaskFilter.TAGS -> emptyList()
                    }
                    
                    _adapter?.submitList(tasks)
                    updateEmptyView(tasks.isEmpty())
                } catch (e: Exception) {
                    Log.e("TaskFragment", "Fehler beim Laden der Tasks: ${e.message}", e)
                }
            }
        }
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        view?.findViewById<View>(R.id.emptyView)?.visibility = 
            if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun showErrorDialog() {
        context?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle("Fehler")
                .setMessage("Es ist ein Fehler aufgetreten. Bitte versuchen Sie es erneut.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    // Zurück zum Dashboard navigieren
                    (activity as? MainActivity)?.let { mainActivity ->
                        mainActivity.findViewById<BottomNavigationView>(R.id.bottomNavigation)?.selectedItemId = R.id.navigation_dashboard
                    }
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        taskRecyclerView = null
        fabMenu = null
        mainFab = null
        mindMapButton = null
        videoSummaryButton = null
        addTaskButton = null
        searchView = null
        filterChipGroup = null
        _adapter = null
    }

    override fun onDestroy() {
        super.onDestroy()
        _taskManager = null
    }

    enum class TaskFilter {
        PRIORITY, DUE_DATE, TAGS, COMPLETED
    }
} 
