package com.foliora.pos.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliora.pos.data.local.entity.UserEntity
import com.foliora.pos.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _currentUserRole = MutableStateFlow<String>("CASHIER") // Default to Cashier for safety
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    init {
        fetchCurrentUserRole()
    }

    private fun fetchCurrentUserRole() {
        viewModelScope.launch {
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser != null) {
                val dbUser = userRepository.getUserByFirebaseUid(firebaseUser.uid)
                if (dbUser != null) {
                    _currentUserRole.value = dbUser.role
                }
            }
        }
    }
}
