package com.example.ruraltransportdriver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoSimulatorScreen(
    onBack: () -> Unit,
    viewModel: DemoSimulatorViewModel = viewModel()
) {
    val auto1 by viewModel.auto1.collectAsState()
    val auto2 by viewModel.auto2.collectAsState()
    val auto3 by viewModel.auto3.collectAsState()
    val auto4 by viewModel.auto4.collectAsState()
    val auto5 by viewModel.auto5.collectAsState()
    val auto6 by viewModel.auto6.collectAsState()
    val auto7 by viewModel.auto7.collectAsState()
    val auto8 by viewModel.auto8.collectAsState()
    val auto9 by viewModel.auto9.collectAsState()
    val auto10 by viewModel.auto10.collectAsState()
    val auto11 by viewModel.auto11.collectAsState()
    val auto12 by viewModel.auto12.collectAsState()

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Demo Control Simulator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.resetDemo() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset Demo")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Use this screen to simulate fake driver position data to Firebase. The passenger app will display these autos live.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val autoList = listOf(auto1, auto2, auto3, auto4, auto5, auto6, auto7, auto8, auto9, auto10, auto11, auto12)
            autoList.forEach { auto ->
                AutoControlCard(
                    state = auto,
                    totalWaypoints = viewModel.getWaypointsForAuto(auto.uid).size,
                    distance = viewModel.calculateDistanceToDestination(auto),
                    eta = viewModel.calculateEtaMinutes(auto),
                    onToggle = { viewModel.toggleStartPause(auto.uid) },
                    onSpeedChange = { viewModel.updateSpeed(auto.uid, it) },
                    onJump = { viewModel.jumpToWaypoint(auto.uid, it) }
                )
            }
        }
    }
}

@Composable
fun AutoControlCard(
    state: AutoSimState,
    totalWaypoints: Int,
    distance: Double,
    eta: Int,
    onToggle: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onJump: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isRunning)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = state.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.vehicleNumber} • Nearest Stop: ${state.currentStop}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRunning)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (state.isRunning) "Pause" else "Start")
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Distance to Destination", style = MaterialTheme.typography.labelSmall)
                    Text("%.1f km".format(distance / 1000.0), fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("ETA", style = MaterialTheme.typography.labelSmall)
                    Text("$eta mins", fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "Lat: %.5f, Lng: %.5f • Heading: %.1f°".format(state.currentLat, state.currentLng, state.currentHeading),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Text("Speed Presets", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val speeds = listOf(
                    Triple("Stopped", 0f, "0m/s"),
                    Triple("Slow", 3f, "3m/s"),
                    Triple("Normal", 8f, "8m/s"),
                    Triple("Fast", 15f, "15m/s")
                )
                speeds.forEach { (label, value, hint) ->
                    val isSelected = state.speedMs == value
                    OutlinedButton(
                        onClick = { onSpeedChange(value) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(hint, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Text("Position / Waypoint (0 to ${totalWaypoints - 1})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Slider(
                value = state.currentWaypointIndex.toFloat(),
                onValueChange = { onJump(it.toInt()) },
                valueRange = 0f..(totalWaypoints - 1).toFloat(),
                steps = totalWaypoints - 2
            )
            Text(
                text = "Currently at Waypoint index: ${state.currentWaypointIndex}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
