package com.hackerai.mybudget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerai.mybudget.data.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TagViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ExpenseRepository = (application as MyBudgetApplication).expenseRepository

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    init {
        loadTags()
    }

    fun loadTags() {
        viewModelScope.launch {
            val expenses = repository.loadExpenses()
            val tags = expenses.map { it.tag }.filter { it.isNotBlank() }.distinct().sorted()
            _tags.value = tags
        }
    }

    fun addTag(name: String) {
        viewModelScope.launch {
            repository.addDummyTag(name)
            loadTags()
        }
    }

    fun renameTag(oldName: String, newName: String) {
        viewModelScope.launch {
            repository.renameTag(oldName, newName)
            loadTags()
        }
    }
}
