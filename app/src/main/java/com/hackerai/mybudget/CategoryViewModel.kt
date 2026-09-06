package com.hackerai.mybudget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerai.mybudget.data.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ExpenseRepository = (application as MyBudgetApplication).expenseRepository

    private val _categoriesMap = MutableStateFlow<Map<String, Map<String, List<String>>>>(emptyMap())
    val categoriesMap: StateFlow<Map<String, Map<String, List<String>>>> = _categoriesMap.asStateFlow()

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            val expenses = repository.loadExpenses()
            val map = expenses.groupBy { it.transactionType }
                .mapValues { (_, typeExpenses) ->
                    typeExpenses.groupBy { it.category }
                        .mapValues { (_, catExpenses) ->
                            catExpenses.map { it.subcategory }.filter { it.isNotBlank() }.distinct().sorted()
                        }
                }
            _categoriesMap.value = map
        }
    }

    fun addCategory(name: String, type: String) {
        viewModelScope.launch {
            repository.addDummyTransaction(name, "", type)
            loadCategories()
        }
    }

    fun renameCategory(oldName: String, newName: String, type: String) {
        viewModelScope.launch {
            repository.renameCategory(oldName, newName, type)
            loadCategories()
        }
    }

    fun addSubcategory(category: String, subName: String, type: String) {
        viewModelScope.launch {
            repository.addDummyTransaction(category, subName, type)
            loadCategories()
        }
    }

    fun renameSubcategory(category: String, oldName: String, newName: String, type: String) {
        viewModelScope.launch {
            repository.renameSubcategory(category, oldName, newName, type)
            loadCategories()
        }
    }
}
