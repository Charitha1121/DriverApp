package com.example.ruraltransportdriver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ruraltransportdriver.ui.theme.RuralTransportDriverTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            RuralTransportDriverTheme {
                DriverApp()
            }
        }
    }
}

// ============================================================
// ROOT DRIVER APPLICATION
// ============================================================

@Composable
fun DriverApp(
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    var showRegistration by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = authState) {
                is AuthUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading driver session...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is AuthUiState.Authenticated -> {
                    DriverDashboardScreen(
                        initialProfile = state.profile,
                        onLogout = {
                            authViewModel.logout()
                        }
                    )
                }

                is AuthUiState.PendingApproval -> {
                    VerificationPendingScreen(
                        profile = state.profile,
                        isRejected = false,
                        onRefreshStatus = {
                            authViewModel.checkCurrentSession()
                        },
                        onLogout = {
                            authViewModel.logout()
                        }
                    )
                }

                is AuthUiState.Rejected -> {
                    VerificationPendingScreen(
                        profile = state.profile,
                        isRejected = true,
                        onRefreshStatus = {
                            authViewModel.checkCurrentSession()
                        },
                        onLogout = {
                            authViewModel.logout()
                        }
                    )
                }

                is AuthUiState.Unauthenticated, is AuthUiState.Error, is AuthUiState.Idle -> {
                    val errorMessage = (state as? AuthUiState.Error)?.message

                    if (showRegistration) {
                        RegistrationScreen(
                            isLoading = false,
                            errorMessage = errorMessage,
                            onRegisterSubmitted = { name, phone, email, pass, vehNo, vehType, seats, rId, rName ->
                                authViewModel.register(
                                    name = name,
                                    phone = phone,
                                    email = email,
                                    pass = pass,
                                    vehicleNumber = vehNo,
                                    vehicleType = vehType,
                                    totalSeats = seats,
                                    routeId = rId,
                                    routeName = rName
                                )
                            },
                            onNavigateToLogin = {
                                showRegistration = false
                                authViewModel.clearError()
                            },
                            onClearError = {
                                authViewModel.clearError()
                            }
                        )
                    } else {
                        LoginScreen(
                            isLoading = false,
                            errorMessage = errorMessage,
                            onLoginSubmitted = { email, pass ->
                                authViewModel.login(email, pass)
                            },
                            onNavigateToRegister = {
                                showRegistration = true
                                authViewModel.clearError()
                            },
                            onClearError = {
                                authViewModel.clearError()
                            }
                        )
                    }
                }
            }
        }
    }
}