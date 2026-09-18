package com.homehub.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homehub.app.data.Device
import com.homehub.app.data.DeviceBrand
import com.homehub.app.data.DeviceSchedule
import com.homehub.app.data.ScheduleAction
import com.homehub.app.data.ThresholdAlert
import com.homehub.app.ui.AppViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(viewModel: AppViewModel, deviceId: String, onBack: () -> Unit) {
    val devices by viewModel.devices.collectAsState()
    val device = devices.firstOrNull { it.id == deviceId }

    if (device == null) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Device") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            })
        }) { padding -> Box(Modifier.padding(padding)) { Text("Device not found") } }
        return
    }

    LaunchedEffect(device.id) { viewModel.refreshTuyaStatus(device) }

    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(device.name) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.removeDevice(device.id); onBack() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove device")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {

            item { PowerSection(device, viewModel) }
            item { Spacer(Modifier.height(24.dp)) }

            item {
                Text("Settings", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (showRename) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("Name") })
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            viewModel.renameDevice(device.id, renameText)
                            showRename = false
                        }) { Text("Save") }
                    }
                } else {
                    OutlinedButton(onClick = { showRename = true }) { Text("Rename device") }
                }
                Spacer(Modifier.height(24.dp))
            }

            item { ThresholdSection(device, viewModel) }
            item { Spacer(Modifier.height(24.dp)) }

            item { ScheduleSection(device, viewModel) }
            item { Spacer(Modifier.height(24.dp)) }

            item { RawStatusSection(device) }
        }
    }
}

@Composable
private fun PowerSection(device: Device, viewModel: AppViewModel) {
    ElevatedCard {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(if (device.isOn) "On" else "Off", style = MaterialTheme.typography.titleLarge)
                    Text(device.brand.name, style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = device.isOn, onCheckedChange = { viewModel.toggleDevice(device) })
            }

            if (device.wattage() != null || device.voltage() != null || device.current() != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    device.wattage()?.let { MetricTile("Power", "$it W") }
                    device.voltage()?.let { MetricTile("Voltage", "$it V") }
                    device.current()?.let { MetricTile("Current", "$it A") }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ThresholdSection(device: Device, viewModel: AppViewModel) {
    val thresholds by viewModel.thresholds.collectAsState()
    val deviceThresholds = thresholds.filter { it.deviceId == device.id }
    var showAdd by remember { mutableStateOf(false) }

    Text("Notifications", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Get alerted when this device's reading drops below a value you set. Checked periodically, not instantly.",
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(8.dp))

    deviceThresholds.forEach { t ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(t.label, style = MaterialTheme.typography.bodyMedium)
                Text("Alert when ${t.metric} < ${t.belowValue}", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { viewModel.removeThreshold(t.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
    }

    if (showAdd) {
        AddThresholdForm(
            onCancel = { showAdd = false },
            onSave = { label, metric, value ->
                viewModel.addThreshold(ThresholdAlert(UUID.randomUUID().toString(), device.id, label, metric, value))
                showAdd = false
            }
        )
    } else {
        OutlinedButton(onClick = { showAdd = true }) { Text("Add notification") }
    }
}

@Composable
private fun AddThresholdForm(onCancel: () -> Unit, onSave: (String, String, Double) -> Unit) {
    var label by remember { mutableStateOf("") }
    var metric by remember { mutableStateOf("watts") }
    var value by remember { mutableStateOf("") }

    Column(Modifier.padding(top = 8.dp)) {
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Notification name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            FilterChip(selected = metric == "watts", onClick = { metric = "watts" }, label = { Text("Watts") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = metric == "voltage", onClick = { metric = "voltage" }, label = { Text("Voltage") })
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("Alert when below") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { value.toDoubleOrNull()?.let { onSave(label.ifBlank { "Alert" }, metric, it) } }) { Text("Save") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun ScheduleSection(device: Device, viewModel: AppViewModel) {
    val schedules by viewModel.schedules.collectAsState()
    val deviceSchedules = schedules.filter { it.deviceId == device.id }
    var showAdd by remember { mutableStateOf(false) }

    Text("Schedules", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))

    deviceSchedules.forEach { s ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${s.label}: ${s.action} at %02d:%02d".format(s.hour, s.minute))
            IconButton(onClick = { viewModel.removeSchedule(s.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
    }

    if (showAdd) {
        AddScheduleForm(
            onCancel = { showAdd = false },
            onSave = { label, hour, minute, action ->
                viewModel.addSchedule(
                    DeviceSchedule(UUID.randomUUID().toString(), device.id, label, hour, minute, action, setOf(1,2,3,4,5,6,7))
                )
                showAdd = false
            }
        )
    } else {
        OutlinedButton(onClick = { showAdd = true }) { Text("Add schedule") }
    }
}

@Composable
private fun AddScheduleForm(onCancel: () -> Unit, onSave: (String, Int, Int, ScheduleAction) -> Unit) {
    var label by remember { mutableStateOf("") }
    var hourText by remember { mutableStateOf("22") }
    var minuteText by remember { mutableStateOf("0") }
    var action by remember { mutableStateOf(ScheduleAction.OFF) }

    Column(Modifier.padding(top = 8.dp)) {
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Schedule name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedTextField(value = hourText, onValueChange = { hourText = it }, label = { Text("Hour (0-23)") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = minuteText, onValueChange = { minuteText = it }, label = { Text("Minute") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row {
            FilterChip(selected = action == ScheduleAction.ON, onClick = { action = ScheduleAction.ON }, label = { Text("Turn on") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = action == ScheduleAction.OFF, onClick = { action = ScheduleAction.OFF }, label = { Text("Turn off") })
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = {
                val h = hourText.toIntOrNull(); val m = minuteText.toIntOrNull()
                if (h != null && m != null) onSave(label.ifBlank { "Schedule" }, h.coerceIn(0, 23), m.coerceIn(0, 59), action)
            }) { Text("Save") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun RawStatusSection(device: Device) {
    if (device.points.isEmpty()) return
    Text("All reported values", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    device.points.forEach { p ->
        Text("${p.code}: ${p.value}", style = MaterialTheme.typography.bodySmall)
    }
}
