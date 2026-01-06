package pl.fithubapp

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class SearchDialogFragment<T> : DialogFragment() {

    protected var searchJob: Job? = null
    protected lateinit var llSearchResults: LinearLayout

    abstract fun getTitle(): String
    abstract fun getSearchHint(): String
    abstract suspend fun performSearch(query: String): List<T>
    abstract fun createResultView(item: T): View
    abstract fun onItemSelected(item: T)
    open fun onDialogCreated() {}
    open fun shouldShowSearchField(): Boolean = true

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val mainLayout = android.view.LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_search, null) as LinearLayout

        val etSearch = mainLayout.findViewById<android.widget.EditText>(R.id.etSearch)
        llSearchResults = mainLayout.findViewById(R.id.llSearchResults)
        val tilSearch = mainLayout.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilSearch)
        
        tilSearch.hint = getSearchHint()
        
        if (!shouldShowSearchField()) {
            tilSearch.visibility = View.GONE
        }

        etSearch.doAfterTextChanged { text ->
            val query = text.toString().trim()
            searchJob?.cancel()

            if (query.length >= 2) {
                searchJob = lifecycleScope.launch {
                    delay(500)
                    try {
                        val results = performSearch(query)
                        displayResults(results)
                    } catch (e: Exception) {
                        Log.e(this@SearchDialogFragment::class.simpleName, "Bład wyszukiwania", e)
                    }
                }
            } else {
                llSearchResults.removeAllViews()
            }
        }

        onDialogCreated()

        return AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_Dialog)
            .setTitle(getTitle())
            .setView(mainLayout)
            .setNegativeButton("Zamknij", null)
            .create()
    }

    protected fun displayResults(results: List<T>) {
        llSearchResults.removeAllViews()

        results.forEach { item ->
            val view = createResultView(item)
            view.setOnClickListener {
                onItemSelected(item)
            }
            llSearchResults.addView(view)
        }
    }
}