package com.example.ruraltransportdriver

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DriverDashboardScreen(
    initialProfile: DriverProfile,
    onLogout: () -> Unit,
    dashboardViewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val locationTracker = remember { LocationTracker(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            dashboardViewModel.startLocationSharing(locationTracker)
        } else {
            dashboardViewModel.clearFeedback()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationTracker.stopTracking()
        }
    }

    LaunchedEffect(initialProfile.uid) {
        dashboardViewModel.initialize(initialProfile)
    }

    val profile by dashboardViewModel.currentProfile.collectAsState()
    val activeRide by dashboardViewModel.activeRide.collectAsState()
    val pendingRequests by dashboardViewModel.pendingRequests.collectAsState()
    val statusMessage by dashboardViewModel.statusMessage.collectAsState()
    val errorMessage by dashboardViewModel.errorMessage.collectAsState()
    val isOperating by dashboardViewModel.isOperating.collectAsState()
    val isSharingLocation by dashboardViewModel.isSharingLocation.collectAsState()
    val lastKnownLocation by dashboardViewModel.lastKnownLocation.collectAsState()

    val routeStops = remember(profile.routeId) {
        RouteData.getStopsForRoute(profile.routeId)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Driver Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Welcome, ${profile.name.ifBlank { "Driver" }}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${profile.vehicleNumber} • ${profile.vehicleType}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Log Out")
            }
        }

        // Status / Error Banners
        statusMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✓ $msg",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { dashboardViewModel.clearFeedback() }) {
                        Text("✕", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        errorMessage?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠ $err",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { dashboardViewModel.clearFeedback() }) {
                        Text("✕", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // ACTIVE RIDE CARD (Highest Priority)
        activeRide?.let { ride ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚕 ACTIVE RIDE",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Badge(
                            containerColor = if (ride.status == RideRequest.STATUS_IN_PROGRESS)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.tertiary
                        ) {
                            Text(
                                text = ride.status,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

                    Text(
                        text = "Passenger: ${ride.passengerName.ifBlank { "Passenger" }}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Pickup", style = MaterialTheme.typography.labelSmall)
                            Text(ride.pickupStop, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = "to")
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Destination", style = MaterialTheme.typography.labelSmall)
                            Text(ride.destinationStop.ifBlank { "Next Stop" }, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Reserved Seats: ${ride.effectiveSeats()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (ride.status == RideRequest.STATUS_ACCEPTED) {
                        Button(
                            onClick = { dashboardViewModel.startRide() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = !isOperating
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Ride (Passenger Picked Up)", fontWeight = FontWeight.Bold)
                        }
                    } else if (ride.status == RideRequest.STATUS_IN_PROGRESS) {
                        Button(
                            onClick = { dashboardViewModel.completeRide() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            enabled = !isOperating
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Complete")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Complete Ride & Free Seats", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { dashboardViewModel.cancelRide("Cancelled by driver") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isOperating
                    ) {
                        Text("Cancel Ride")
                    }
                }
            }
        }

        // Availability Toggle
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Driver Availability",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = if (profile.isAvailable) "🟢 ONLINE & ACCEPTING RIDES" else "🔴 OFFLINE",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (profile.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.ExtraBold
                )

                Button(
                    onClick = {
                        dashboardViewModel.toggleAvailability(!profile.isAvailable)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (profile.isAvailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    enabled = !isOperating
                ) {
                    Text(text = if (profile.isAvailable) "Go Offline" else "Go Online")
                }
            }
        }

        // GPS LIVE LOCATION TRACKING CARD
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GPS Location Sharing",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Badge(
                        containerColor = if (isSharingLocation)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    ) {
                        Text(
                            text = if (isSharingLocation) "LIVE" else "OFF",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (isSharingLocation) {
                    Text(
                        text = "🟢 Broadcasting vehicle coordinates for passenger availability forecasting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    lastKnownLocation?.let { loc ->
                        Text(
                            text = "Lat: %.4f • Lng: %.4f • Speed: %.1f km/h".format(loc.latitude, loc.longitude, loc.speed * 3.6f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        text = "Location sharing is inactive. Start sharing so passengers and forecasting models can locate your auto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        if (isSharingLocation) {
                            dashboardViewModel.stopLocationSharing(locationTracker)
                        } else {
                            if (locationTracker.isPermissionGranted()) {
                                dashboardViewModel.startLocationSharing(locationTracker)
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSharingLocation)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = if (isSharingLocation) Icons.Default.LocationOff else Icons.Default.MyLocation,
                        contentDescription = "GPS"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isSharingLocation) "Stop Location Sharing" else "Start Location Sharing")
                }
            }
        }

        // Assigned Corridor Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Assigned Route Corridor",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = profile.routeName.ifBlank { "IBP → Gurramguda → Champapet → Issdan" },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Current Stop Selector
        Text(
            text = "Current Stop Location",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        routeStops.forEach { stop ->
            if (profile.currentStop == stop) {
                Button(
                    onClick = { /* already selected */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Current")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Current: $stop", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { dashboardViewModel.updateCurrentStop(stop) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isOperating
                ) {
                    Text(text = stop)
                }
            }
        }

        // Available Passenger Seats
        Text(
            text = "Available Seats (${profile.availableSeats} / ${profile.totalSeats})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            (0..profile.totalSeats).forEach { seats ->
                if (profile.availableSeats == seats) {
                    Button(
                        onClick = { /* already selected */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = seats.toString(), fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { dashboardViewModel.updateAvailableSeats(seats) },
                        modifier = Modifier.weight(1f),
                        enabled = !isOperating
                    ) {
                        Text(text = seats.toString())
                    }
                }
            }
        }

        // INCOMING RIDE REQUESTS SECTION
        Text(
            text = "Incoming Ride Requests (${pendingRequests.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (!profile.isAvailable) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "🔒 You are currently OFFLINE. Go ONLINE to receive passenger ride requests.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (pendingRequests.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "No pending ride requests on your route corridor right now.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            pendingRequests.forEach { request ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "👤 ${request.passengerName.ifBlank { "Passenger" }}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Badge {
                                Text("${request.effectiveSeats()} Seat(s)")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Pickup", style = MaterialTheme.typography.labelSmall)
                                Text(request.pickupStop, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Default.ArrowForward, contentDescription = "to")
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Destination", style = MaterialTheme.typography.labelSmall)
                                Text(request.destinationStop.ifBlank { "Next Stop" }, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { dashboardViewModel.rejectRequest(request.requestId) },
                                modifier = Modifier.weight(1f),
                                enabled = !isOperating
                            ) {
                                Text("Dismiss")
                            }

                            Button(
                                onClick = { dashboardViewModel.acceptRequest(request) },
                                modifier = Modifier.weight(1.5f),
                                enabled = !isOperating && profile.availableSeats >= request.effectiveSeats()
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Accept")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Accept Ride")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
