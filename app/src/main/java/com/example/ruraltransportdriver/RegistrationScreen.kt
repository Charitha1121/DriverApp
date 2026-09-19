package com.example.ruraltransportdriver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onRegisterSubmitted: (
        name: String,
        phone: String,
        email: String,
        pass: String,
        vehicleNumber: String,
        vehicleType: String,
        totalSeats: Int,
        routeId: String,
        routeName: String
    ) -> Unit,
    onNavigateToLogin: () -> Unit,
    onClearError: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var vehicleNumber by remember { mutableStateOf("") }

    val vehicleTypes = listOf("Shared Auto", "Auto Rickshaw", "Minivan / Shuttle")
    var selectedVehicleType by remember { mutableStateOf(vehicleTypes.first()) }
    var vehicleTypeExpanded by remember { mutableStateOf(false) }

    var totalSeatsText by remember { mutableStateOf("4") }

    val scrollState = rememberScrollState()

    // Dynamic state for typed inputs
    var sourceText by remember { mutableStateOf("") }
    var destText by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    var currentGpsLocation by remember { mutableStateOf<android.location.Location?>(null) }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
                currentGpsLocation = loc
            }
        }
    }

    val allStops = RouteData.getAllCachedStops()

    val filteredSourceSuggestions = remember(sourceText, allStops) {
        if (sourceText.isBlank()) emptyList()
        else allStops.filter { it.name.contains(sourceText, ignoreCase = true) }
    }

    val filteredDestSuggestions = remember(destText, allStops) {
        if (destText.isBlank()) emptyList()
        else allStops.filter { it.name.contains(destText, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "Driver Registration",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Register your vehicle with Rural Transport",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Error message banner
        errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠ $error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearError) {
                        Text("✕", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // Full Name
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                if (errorMessage != null) onClearError()
            },
            label = { Text("Driver Full Name *") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Phone Number
        OutlinedTextField(
            value = phone,
            onValueChange = { input ->
                if (input.length <= 10 && input.all { it.isDigit() }) {
                    phone = input
                    if (errorMessage != null) onClearError()
                }
            },
            label = { Text("10-Digit Mobile Number *") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone") },
            prefix = { Text("+91 ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                if (errorMessage != null) onClearError()
            },
            label = { Text("Email Address *") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (errorMessage != null) onClearError()
            },
            label = { Text("Create Password (min 6 chars) *") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
            trailingIcon = {
                val icon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(icon, contentDescription = if (passwordVisible) "Hide" else "Show")
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Vehicle Number
        OutlinedTextField(
            value = vehicleNumber,
            onValueChange = {
                vehicleNumber = it.uppercase()
                if (errorMessage != null) onClearError()
            },
            label = { Text("Vehicle Registration Number * (e.g. TS 08 UA 1234)") },
            leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = "Vehicle") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Vehicle Type Dropdown
        ExposedDropdownMenuBox(
            expanded = vehicleTypeExpanded,
            onExpandedChange = { vehicleTypeExpanded = !vehicleTypeExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedVehicleType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Vehicle Type *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleTypeExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = vehicleTypeExpanded,
                onDismissRequest = { vehicleTypeExpanded = false }
            ) {
                vehicleTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = {
                            selectedVehicleType = type
                            vehicleTypeExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Total Passenger Seats
        OutlinedTextField(
            value = totalSeatsText,
            onValueChange = { input ->
                if (input.all { it.isDigit() } && input.length <= 2) {
                    totalSeatsText = input
                    if (errorMessage != null) onClearError()
                }
            },
            label = { Text("Total Passenger Seats * (1 to 15)") },
            leadingIcon = { Icon(Icons.Default.AirlineSeatReclineNormal, contentDescription = "Seats") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic Source Input Field with Autocomplete suggestions
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = sourceText,
                onValueChange = {
                    sourceText = it.uppercase()
                    if (errorMessage != null) onClearError()
                },
                label = { Text("Trip Source / Start Stop *") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Source") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (filteredSourceSuggestions.isNotEmpty()) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { /* keep active or dismiss by clearing state */ }
                ) {
                    filteredSourceSuggestions.forEach { stop ->
                        DropdownMenuItem(
                            text = { Text(stop.name) },
                            onClick = { sourceText = stop.name.uppercase() }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic Destination Input Field with Autocomplete suggestions
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = destText,
                onValueChange = {
                    destText = it.uppercase()
                    if (errorMessage != null) onClearError()
                },
                label = { Text("Trip Destination / End Stop *") },
                leadingIcon = { Icon(Icons.Default.Navigation, contentDescription = "Destination") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (filteredDestSuggestions.isNotEmpty()) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { }
                ) {
                    filteredDestSuggestions.forEach { stop ->
                        DropdownMenuItem(
                            text = { Text(stop.name) },
                            onClick = { destText = stop.name.uppercase() }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Submit Button Logic
        val seatsInt = totalSeatsText.toIntOrNull() ?: 0
        val isFormFilled = name.isNotBlank() &&
                phone.length == 10 &&
                email.isNotBlank() &&
                password.length >= 6 &&
                vehicleNumber.isNotBlank() &&
                seatsInt in 1..15 &&
                sourceText.isNotBlank() &&
                destText.isNotBlank() &&
                !sourceText.equals(destText, ignoreCase = true)

        Button(
            onClick = {
                val sClean = sourceText.trim().uppercase()
                val dClean = destText.trim().uppercase()

                // Final safety check: block registration if new stops are being created but GPS is unavailable
                val needsGps = allStops.none { it.name.equals(sClean, ignoreCase = true) } ||
                               allStops.none { it.name.equals(dClean, ignoreCase = true) }
                
                if (needsGps && currentGpsLocation == null) {
                    // This could be improved with a snackbar or toast
                    return@Button
                }

                // Generate sanitized alphanumeric ID strings for references from UPPERCASE names
                val sId = allStops.firstOrNull { it.name.equals(sClean, ignoreCase = true) }?.id
                    ?: "stop_${sClean.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9_]"), "_")}"
                val dId = allStops.firstOrNull { it.name.equals(dClean, ignoreCase = true) }?.id
                    ?: "stop_${dClean.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9_]"), "_")}"

                // Save new stops to 'stops/' database node if they don't exist
                if (allStops.none { it.name.equals(sClean, ignoreCase = true) }) {
                    val lat = currentGpsLocation?.latitude ?: RouteData.stops.firstOrNull()?.latitude ?: 0.0
                    val lng = currentGpsLocation?.longitude ?: RouteData.stops.firstOrNull()?.longitude ?: 0.0
                    FirebaseRepository().saveStop(DbStop(sId, sClean, lat, lng))
                }
                if (allStops.none { it.name.equals(dClean, ignoreCase = true) }) {
                    val lat = currentGpsLocation?.latitude ?: RouteData.stops.firstOrNull()?.latitude ?: 0.0
                    val lng = currentGpsLocation?.longitude ?: RouteData.stops.firstOrNull()?.longitude ?: 0.0
                    FirebaseRepository().saveStop(DbStop(dId, dClean, lat, lng))
                }

                // Establish deterministic routeId string
                val finalRouteId = "route_${sId.replace("stop_", "")}_${dId.replace("stop_", "")}"
                val finalRouteName = "$sClean - $dClean"

                // Save new route to 'routes/' database node
                val newDbRoute = DbRoute(
                    id = finalRouteId,
                    name = finalRouteName,
                    sourceStopId = sId,
                    destinationStopId = dId,
                    stopOrder = listOf(sId, dId),
                    status = "active" 
                )
                FirebaseRepository().saveRoute(newDbRoute)

                onRegisterSubmitted(
                    name,
                    phone,
                    email,
                    password,
                    vehicleNumber,
                    selectedVehicleType,
                    seatsInt,
                    finalRouteId,
                    finalRouteName
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isLoading && isFormFilled
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = "Submit Driver Registration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Back to Login
        TextButton(
            onClick = onNavigateToLogin,
            enabled = !isLoading
        ) {
            Text("Already registered? Sign In here")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
