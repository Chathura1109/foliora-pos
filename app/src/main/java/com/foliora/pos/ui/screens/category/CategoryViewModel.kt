package com.foliora.pos.ui.screens.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliora.pos.data.local.entity.CategoryEntity
import com.foliora.pos.data.repository.CategoryRepository
import com.foliora.pos.ui.viewmodel.launchCrudCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for managing category state and operations in Foliora POS.
 * Interacts with [CategoryRepository] to provide reactive category lists and handle CRUD actions.
 */
@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: CategoryRepository
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Exposes the full list of categories ordered alphabetically as a StateFlow.
     * Uses [SharingStarted.WhileSubscribed] to save resources when UI is in background.
     */
    val categories: StateFlow<List<CategoryEntity>> = repository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Adds a new category with the specified name and optional description.
     *
     * @param name Name of the new category.
     * @param description Optional description for the category.
     */
    fun addCategory(name: String, description: String?) {
        launchCrudCatching("Unable to add category", onError = { _errorMessage.value = it }) {
            val category = CategoryEntity(
                name = name.trim(),
                description = description?.trim()?.ifEmpty { null }
            )
            repository.insertCategory(category)
        }
    }

    /**
     * Updates an existing category entity in the local database.
     *
     * @param category Updated category entity instance.
     */
    fun updateCategory(category: CategoryEntity) {
        launchCrudCatching("Unable to update category", onError = { _errorMessage.value = it }) {
            repository.updateCategory(category)
        }
    }

    /**
     * Deletes a category entity from the local database.
     *
     * @param category Category entity to be removed.
     */
    fun deleteCategory(category: CategoryEntity) {
        launchCrudCatching("Unable to delete category", onError = { _errorMessage.value = it }) {
            repository.deleteCategory(category)
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
