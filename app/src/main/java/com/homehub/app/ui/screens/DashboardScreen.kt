package com.homehub.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homehub.app.data.Device
import com.homehub.app.ui.AppViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onOpenDevice: (String) -> Unit,
    onAddDevice: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val costPerKwh by viewModel.electricityCostPerKwh.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HomeHub") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDevice) {
                Icon(Icons.Filled.Add, contentDescription = "Add device")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            EnergyOverviewCard(devices = devices, costPerKwh = costPerKwh)

            if (devices.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No devices yet, tap + to add one", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onOpenDevice(device.id) },
                            onToggle = { viewModel.toggleDevice(device) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnergyOverviewCard(devices: List<Device>, costPerKwh: Double) {
    val totalWatts = devices.mapNotNull { it.wattage() }.sum()
    val hasAnyMetering = devices.any { it.wattage() != null }
    val estimatedHourlyCost = (totalWatts / 1000.0) * costPerKwh

    ElevatedCard(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Current draw", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            if (hasAnyMetering) {
                Text("${totalWatts.roundToInt()} W across your devices", style = MaterialTheme.typography.headlineSmall)
                if (costPerKwh > 0) {
                    Text(
                        "~ ${"%.3f".format(estimatedHourlyCost)} per hour at current draw",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "Set your electricity cost in Settings to see running cost",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text(
                    "None of your devices are reporting power data yet. This needs a metering-capable plug and works once Tuya devices are synced.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(device: Device, onClick: () -> Unit, onToggle: () -> Unit) {
    ElevatedCard(onClick = onClick) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text(device.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                device.brand.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall
            )
            device.wattage()?.let {
                Spacer(Modifier.height(4.dp))
                Text("${it.roundToInt()} W", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (device.isOn) "On" else "Off", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = device.isOn, onCheckedChange = { onToggle() })
            }
        }
    }
}
