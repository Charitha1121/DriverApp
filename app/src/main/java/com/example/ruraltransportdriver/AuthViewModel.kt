package com.example.ruraltransportdriver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Authenticated(val profile: DriverProfile) : AuthUiState()
    data class PendingApproval(val profile: DriverProfile) : AuthUiState()
    data class Rejected(val profile: DriverProfile) : AuthUiState()
    object Unauthenticated : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var profileListener: ValueEventListener? = null
    private var activeObservedUid: String? = null

    init {
        checkCurrentSession()
    }

    fun checkCurrentSession() {
        val uid = repository.currentUid
        if (uid == null) {
            _uiState.value = AuthUiState.Unauthenticated
        } else {
            _uiState.value = AuthUiState.Loading
            attachProfileObserver(uid)
        }
    }

    fun login(email: String, pass: String) {
        val emailTrim = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrim).matches()) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        if (pass.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters.")
            return
        }

        _uiState.value = AuthUiState.Loading
        repository.login(
            email = emailTrim,
            pass = pass,
            onSuccess = { uid ->
                attachProfileObserver(uid)
            },
            onError = { error ->
                _uiState.value = AuthUiState.Error(error)
            }
        )
    }

    fun register(
        name: String,
        phone: String,
        email: String,
        pass: String,
        vehicleNumber: String,
        vehicleType: String,
        totalSeats: Int,
        routeId: String,
        routeName: String
    ) {
        // Strict Field Validation
        if (name.trim().length < 3) {
            _uiState.value = AuthUiState.Error("Driver name must be at least 3 characters long.")
            return
        }
        val cleanPhone = phone.trim().replace("+91", "").replace(" ", "").replace("-", "")
        if (cleanPhone.length != 10 || !cleanPhone.all { it.isDigit() }) {
            _uiState.value = AuthUiState.Error("Please enter a valid 10-digit mobile number.")
            return
        }
        val emailTrim = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrim).matches()) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        if (pass.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters.")
            return
        }
        if (vehicleNumber.trim().length < 4) {
            _uiState.value = AuthUiState.Error("Please enter a valid vehicle registration number.")
            return
        }
        if (totalSeats !in 1..15) {
            _uiState.value = AuthUiState.Error("Total seats must be between 1 and 15.")
            return
        }
        if (routeId.isBlank()) {
            _uiState.value = AuthUiState.Error("Please select a valid operating route.")
            return
        }

        val initialStop = RouteData.getStopsForRoute(routeId).firstOrNull() ?: "Start"

        val initialProfile = DriverProfile(
            uid = "",
            name = name.trim(),
            email = emailTrim,
            phone = cleanPhone,
            vehicleNumber = vehicleNumber.trim().uppercase(),
            vehicleType = vehicleType.trim(),
            totalSeats = totalSeats,
            availableSeats = totalSeats,
            routeId = routeId,
            routeName = routeName,
            currentStop = initialStop,
            isAvailable = false,
            approvalStatus = DriverProfile.APPROVAL_PENDING
        )

        _uiState.value = AuthUiState.Loading
        repository.register(
            email = emailTrim,
            pass = pass,
            profile = initialProfile,
            onSuccess = { createdProfile ->
                attachProfileObserver(createdProfile.uid)
            },
            onError = { error ->
                _uiState.value = AuthUiState.Error(error)
            }
        )
    }

    fun logout() {
        detachProfileObserver()
        repository.logout {
            _uiState.value = AuthUiState.Unauthenticated
        }
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = if (repository.isUserLoggedIn) {
                AuthUiState.Idle
            } else {
                AuthUiState.Unauthenticated
            }
        }
    }

    private fun attachProfileObserver(uid: String) {
        detachProfileObserver()
        activeObservedUid = uid

        profileListener = repository.observeDriverProfile(
            uid = uid,
            onProfileChanged = { profile ->
                when (profile.approvalStatus.uppercase()) {
                    DriverProfile.APPROVAL_APPROVED -> {
                        _uiState.value = AuthUiState.Authenticated(profile)
                    }
                    DriverProfile.APPROVAL_REJECTED -> {
                        _uiState.value = AuthUiState.Rejected(profile)
                    }
                    else -> {
                        _uiState.value = AuthUiState.PendingApproval(profile)
                    }
                }
            },
            onError = { errorMsg ->
                _uiState.value = AuthUiState.Error(errorMsg)
            }
        )
    }

    private fun detachProfileObserver() {
        val uid = activeObservedUid
        val listener = profileListener
        if (uid != null && listener != null) {
            repository.removeDriverProfileListener(uid, listener)
        }
        profileListener = null
        activeObservedUid = null
    }

    override fun onCleared() {
        super.onCleared()
        detachProfileObserver()
    }
}
