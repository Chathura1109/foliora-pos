package com.foliora.pos.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliora.pos.data.local.entity.UserEntity
import com.foliora.pos.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * ViewModel for managing Firebase authentication state and user profile synchronization in Foliora POS.
 * Interacts with [UserRepository] to maintain local Room data consistency with Firebase Auth & Firestore.
 *
 * @property userRepository Repository managing local SQLite user records.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // State properties for authentication UI
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoginSuccess = MutableStateFlow(false)
    val isLoginSuccess: StateFlow<Boolean> = _isLoginSuccess.asStateFlow()

    /**
     * Updates the current email state.
     *
     * @param value New email string entered by user.
     */
    fun onEmailChange(value: String) {
        _email.value = value
        _errorMessage.value = null
    }

    /**
     * Updates the current password state.
     *
     * @param value New password string entered by user.
     */
    fun onPasswordChange(value: String) {
        _password.value = value
        _errorMessage.value = null
    }

    /**
     * Clears active authentication error message state.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Authenticates an existing user via Firebase Auth.
     * On successful login, checks if user exists in local Room database by UID.
     * If missing locally, retrieves user profile from Firestore ("users" collection) and saves to Room.
     */
    fun login() {
        val currentEmail = _email.value.trim()
        val currentPassword = _password.value.trim()

        if (currentEmail.isBlank() || currentPassword.isBlank()) {
            _errorMessage.value = "Please enter both email and password."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Call Firebase Auth to sign in with email and password
                val authResult = firebaseAuth.signInWithEmailAndPassword(currentEmail, currentPassword).await()
                val firebaseUser = authResult.user

                if (firebaseUser != null) {
                    val uid = firebaseUser.uid
                    val existingUser = userRepository.getUserByFirebaseUid(uid)
                    val docSnapshot = firestore.collection("users").document(uid).get().await()
                    check(docSnapshot.exists()) {
                        "No user profile exists for this account"
                    }

                    val name = docSnapshot.getString("name")
                        ?: firebaseUser.displayName
                        ?: firebaseUser.email?.substringBefore("@")
                        ?: "POS User"
                    val role = docSnapshot.getString("role")
                        ?.trim()
                        ?.uppercase()
                    check(role == "OWNER" || role == "CASHIER") {
                        "User role must be OWNER or CASHIER"
                    }

                    val localUser = existingUser?.copy(
                        name = name,
                        role = role,
                        firebaseAuthUid = uid,
                        firebaseId = uid,
                        isSynced = true,
                        isActive = true,
                        updatedAt = System.currentTimeMillis()
                    ) ?: UserEntity(
                        name = name,
                        role = role,
                        firebaseAuthUid = uid,
                        firebaseId = uid,
                        isSynced = true,
                        isActive = true
                    )
                    userRepository.insertUser(localUser)

                    _isLoginSuccess.value = true
                } else {
                    _errorMessage.value = "Authentication failed. User record not found."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Login failed. Please check your credentials."
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Registers a new user via Firebase Auth.
     * On successful account creation, creates a user record in Firestore
     * and persists the record to the local Room database with the given role.
     * 
     * @param role The role to assign to the new user (e.g. "OWNER" or "CASHIER").
     */
    fun register(role: String) {
        val currentEmail = _email.value.trim()
        val currentPassword = _password.value.trim()

        if (currentEmail.isBlank() || currentPassword.isBlank()) {
            _errorMessage.value = "Please enter both email and password."
            return
        }

        if (currentPassword.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters long."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Call Firebase Auth to create user account
                val authResult = firebaseAuth.createUserWithEmailAndPassword(currentEmail, currentPassword).await()
                val firebaseUser = authResult.user

                if (firebaseUser != null) {
                    val uid = firebaseUser.uid
                    val defaultName = currentEmail.substringBefore("@")
                    
                    // Create user record document in Firestore "users" collection
                    val userProfile = mapOf(
                        "name" to defaultName,
                        "role" to role,
                        "email" to currentEmail,
                        "createdAt" to System.currentTimeMillis()
                    )
                    firestore.collection("users").document(uid).set(userProfile).await()

                    // Save user record to local Room database
                    val newUser = UserEntity(
                        name = defaultName,
                        role = role,
                        firebaseAuthUid = uid,
                        firebaseId = uid,
                        isSynced = true,
                        isActive = true
                    )
                    userRepository.insertUser(newUser)

                    _isLoginSuccess.value = true
                } else {
                    _errorMessage.value = "Registration failed. User could not be created."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Registration failed. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }
}
