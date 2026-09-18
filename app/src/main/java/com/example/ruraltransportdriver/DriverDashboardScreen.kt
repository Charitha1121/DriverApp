package com.example.ruraltransportdriver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ruraltransportdriver.voice.VoiceSeatManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DriverDashboardScreen(
    initialProfile: DriverProfile,
    onLogout: () -> Unit,
    dashboardViewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val locationTracker = remember { LocationTracker(context) }
    val voiceSeatManager = remember { VoiceSeatManager(context.applicationContext) }

    val requiredPermissions = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (locGranted) {
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

    DisposableEffect(voiceSeatManager) {
        dashboardViewModel.attachVoiceManager(voiceSeatManager, context)
        onDispose {
            voiceSeatManager.destroy()
        }
    }

    LaunchedEffect(Unit) {
        val anyMissing = requiredPermissions.any { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
        if (anyMissing) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(initialProfile.uid) {
        dashboardViewModel.initialize(initialProfile)
    }

    val profile by dashboardViewModel.currentProfile.collectAsState()
    val passengersWaitingAhead by dashboardViewModel.passengersWaitingAhead.collectAsState()
    val demandAheadBreakdown by dashboardViewModel.demandAheadBreakdown.collectAsState()
    val activeRide by dashboardViewModel.activeRide.collectAsState()
    val statusMessage by dashboardViewModel.statusMessage.collectAsState()
    val errorMessage by dashboardViewModel.errorMessage.collectAsState()
    val isOperating by dashboardViewModel.isOperating.collectAsState()
    val isSharingLocation by dashboardViewModel.isSharingLocation.collectAsState()
    val lastKnownLocation by dashboardViewModel.lastKnownLocation.collectAsState()
    val isVoiceActive by dashboardViewModel.isVoiceActive.collectAsState()
    val showManualSeatPromptFallback by dashboardViewModel.showManualSeatPromptFallback.collectAsState()

    var showDirectionDialog by remember { mutableStateOf(false) }
    var selectedDirectionOption by remember { mutableStateOf(RouteDirection.FORWARD) }
    var showDemoSimulator by remember { mutableStateOf(false) }

    if (showDemoSimulator) {
        DemoSimulatorScreen(onBack = { showDemoSimulator = false })
        return
    }

    val scrollState = rememberScrollState()

    // ---------------------------------------------------------
    // DIRECTION SELECTION DIALOG (ON GO ONLINE)
    // ---------------------------------------------------------
    if (showDirectionDialog) {
        AlertDialog(
            onDismissRequest = { showDirectionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Direction",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select Trip Direction",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Choose your trip direction along this corridor. Demand matching and passenger pickups will strictly follow this direction.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val fwdTitle = RouteData.getDirectionTitle(profile.routeId, RouteDirection.FORWARD)
                    Surface(
                        onClick = { selectedDirectionOption = RouteDirection.FORWARD },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selectedDirectionOption == RouteDirection.FORWARD)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = if (selectedDirectionOption == RouteDirection.FORWARD) 4.dp else 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDirectionOption == RouteDirection.FORWARD,
                                onClick = { selectedDirectionOption = RouteDirection.FORWARD }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = fwdTitle,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "FORWARD DIRECTION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    val revTitle = RouteData.getDirectionTitle(profile.routeId, RouteDirection.REVERSE)
                    Surface(
                        onClick = { selectedDirectionOption = RouteDirection.REVERSE },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selectedDirectionOption == RouteDirection.REVERSE)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = if (selectedDirectionOption == RouteDirection.REVERSE) 4.dp else 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDirectionOption == RouteDirection.REVERSE,
                                onClick = { selectedDirectionOption = RouteDirection.REVERSE }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = revTitle,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "RETURN / REVERSE DIRECTION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDirectionDialog = false
                        dashboardViewModel.toggleAvailability(
                            isOnline = true,
                            direction = selectedDirectionOption
                        )
                    }
                ) {
                    Text("Confirm & Go Online")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
            Column(
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { showDemoSimulator = true }
                )
            ) {
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
                Icon(
                    Icons.Default.ExitToApp,
                    contentDescription = "Log Out"
                )
            }
        }

        // Status / Error Banners
        statusMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
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

                    IconButton(
                        onClick = {
                            dashboardViewModel.clearFeedback()
                        }
                    ) {
                        Text(
                            "✕",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        errorMessage?.let { err ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
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

                    IconButton(
                        onClick = {
                            dashboardViewModel.clearFeedback()
                        }
                    ) {
                        Text(
                            "✕",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
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
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp
                )
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
                            containerColor =
                                if (ride.status == RideRequest.STATUS_IN_PROGRESS)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.tertiary
                        ) {
                            Text(
                                text = ride.status,
                                modifier = Modifier.padding(
                                    horizontal = 6.dp,
                                    vertical = 2.dp
                                )
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                            alpha = 0.2f
                        )
                    )

                    Text(
                        text = "Passenger: ${
                            ride.passengerName.ifBlank { "Passenger" }
                        }",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Pickup",
                                style = MaterialTheme.typography.labelSmall
                            )

                            Text(
                                ride.pickupStop,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "to"
                        )

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                "Destination",
                                style = MaterialTheme.typography.labelSmall
                            )

                            Text(
                                ride.destinationStop.ifBlank {
                                    "Next Stop"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Reserved Seats: ${ride.effectiveSeats()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    if (ride.status == RideRequest.STATUS_ACCEPTED) {
                        Button(
                            onClick = {
                                dashboardViewModel.startRide()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = !isOperating
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Start"
                            )

                            Spacer(
                                modifier = Modifier.width(6.dp)
                            )

                            Text(
                                "Start Ride (Passenger Picked Up)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (
                        ride.status == RideRequest.STATUS_IN_PROGRESS
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Hands-free Active",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Hands-Free Active: Voice asks for seats automatically at vehicle stops",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (showManualSeatPromptFallback) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.MicOff,
                                            contentDescription = "Voice Fallback",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Didn't catch that. Tap available seats:",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        (0..profile.totalSeats).forEach { seats ->
                                            Button(
                                                onClick = {
                                                    dashboardViewModel.updateAvailableSeats(seats)
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (profile.availableSeats == seats)
                                                        MaterialTheme.colorScheme.error
                                                    else
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = if (profile.availableSeats == seats)
                                                        MaterialTheme.colorScheme.onError
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            ) {
                                                Text(text = seats.toString(), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                dashboardViewModel.completeRide()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            enabled = !isOperating
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Complete"
                            )

                            Spacer(
                                modifier = Modifier.width(6.dp)
                            )

                            Text(
                                "Complete Ride & Free Seats",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            dashboardViewModel.cancelRide(
                                "Cancelled by driver"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isOperating
                    ) {
                        Text("Cancel Ride")
                    }
                }
            }
        }

        // Availability Toggle Card
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
                        text = "Driver Availability",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Badge(
                        containerColor = if (profile.isAvailable)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            text = if (profile.isAvailable) "ONLINE" else "OFFLINE",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text =
                        if (profile.isAvailable)
                            "🟢 ONLINE & ACCEPTING RIDES"
                        else
                            "🔴 OFFLINE",
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (profile.isAvailable)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.ExtraBold
                )

                Button(
                    onClick = {
                        if (profile.isAvailable) {
                            // Driver going offline
                            dashboardViewModel.toggleAvailability(false)
                        } else {
                            // Driver going online
                            dashboardViewModel.toggleAvailability(true, RouteDirection.FORWARD)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (profile.isAvailable)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                    ),
                    enabled = !isOperating
                ) {
                    Text(
                        text =
                            if (profile.isAvailable)
                                "Go Offline"
                            else
                                "Go Online (Select Direction)"
                    )
                }
            }
        }



        // PASSENGERS WAITING AHEAD CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (profile.isAvailable && passengersWaitingAhead > 0)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        text = "PASSENGERS WAITING AHEAD",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (profile.isAvailable && passengersWaitingAhead > 0)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Badge(
                        containerColor = if (profile.isAvailable)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    ) {
                        Text(
                            text = if (profile.isAvailable) "LIVE DEMAND" else "OFFLINE",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (!profile.isAvailable) {
                    Text(
                        text = "🔒 Go Online to view passengers currently waiting ahead on your route corridor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$passengersWaitingAhead",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = if (passengersWaitingAhead > 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (passengersWaitingAhead == 1) "passenger waiting" else "passengers waiting",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Text(
                        text = "Travelling on Gurramguda Corridor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )

                    val stopsAhead = remember(profile.routeId, profile.currentStop) {
                        RouteData.getStopsAhead(profile.routeId, profile.currentStop, RouteDirection.FORWARD)
                    }

                    if (stopsAhead.isEmpty()) {
                        Text(
                            text = "End of route reached. Tap 'Reverse Direction' to start your return trip.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "Stops Ahead Breakdown:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        stopsAhead.forEach { stopName ->
                            val waitingAtStop = demandAheadBreakdown[stopName] ?: 0
                            val isCurrent = stopName.equals(profile.currentStop, ignoreCase = true)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isCurrent) "📍 $stopName (Current)" else "🚏 $stopName",
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                if (waitingAtStop > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text(
                                            text = "$waitingAtStop waiting",
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "0 waiting",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
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
                        containerColor =
                            if (isSharingLocation)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                    ) {
                        Text(
                            text =
                                if (isSharingLocation)
                                    "LIVE"
                                else
                                    "OFF",
                            modifier = Modifier.padding(
                                horizontal = 6.dp,
                                vertical = 2.dp
                            )
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
                            text = "Lat: %.4f • Lng: %.4f • Speed: %.1f km/h"
                                .format(
                                    loc.latitude,
                                    loc.longitude,
                                    loc.speed * 3.6f
                                ),
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

                            dashboardViewModel.stopLocationSharing(
                                locationTracker
                            )

                        } else {

                            if (locationTracker.isPermissionGranted()) {

                                dashboardViewModel.startLocationSharing(
                                    locationTracker
                                )

                            } else {

                                permissionLauncher.launch(
                                    requiredPermissions
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (isSharingLocation)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.secondary
                    )
                ) {

                    Icon(
                        imageVector =
                            if (isSharingLocation)
                                Icons.Default.LocationOff
                            else
                                Icons.Default.MyLocation,
                        contentDescription = "GPS"
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        text =
                            if (isSharingLocation)
                                "Stop Location Sharing"
                            else
                                "Start Location Sharing"
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )
    }
}