package com.foliora.pos.ui.screens.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliora.pos.data.local.entity.CustomerEntity
import com.foliora.pos.data.repository.CustomerRepository
import com.foliora.pos.ui.viewmodel.launchCrudCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for managing customer state and actions in Foliora POS.
 * Connects to [CustomerRepository] to expose a reactive flow of customers
 * and handles adding, updating, and deleting customer profiles.
 */
@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val repository: CustomerRepository
) : ViewModel() {

    /**
     * Exposes all registered customers as a StateFlow sorted alphabetically by name.
     * Uses [SharingStarted.WhileSubscribed] to manage resource lifecycle efficiently.
     */
    val customers: StateFlow<List<CustomerEntity>> = repository.getAllCustomers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Adds a new customer record to the database.
     *
     * @param name Full name of the customer.
     * @param phoneNumber Contact phone number of the customer.
     * @param address Physical address of the customer.
     * @param notes Optional notes or remarks about the customer.
     */
    fun addCustomer(
        name: String,
        phoneNumber: String,
        address: String,
        notes: String? = null
    ) {
        launchCrudCatching("Unable to add customer") {
            val customer = CustomerEntity(
                name = name.trim(),
                phoneNumber = phoneNumber.trim(),
                address = address.trim(),
                notes = notes?.trim()?.ifEmpty { null }
            )
            repository.insertCustomer(customer)
        }
    }

    /**
     * Updates an existing customer profile in the database.
     *
     * @param customer Updated [CustomerEntity] instance.
     */
    fun updateCustomer(customer: CustomerEntity) {
        launchCrudCatching("Unable to update customer") {
            repository.updateCustomer(customer)
        }
    }

    /**
     * Removes a customer record from the database.
     *
     * @param customer [CustomerEntity] instance to delete.
     */
    fun deleteCustomer(customer: CustomerEntity) {
        launchCrudCatching("Unable to delete customer") {
            repository.deleteCustomer(customer)
        }
    }
}
