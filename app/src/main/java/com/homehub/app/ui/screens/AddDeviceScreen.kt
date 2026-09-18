package com.homehub.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homehub.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val isBusy by viewModel.isBusy.collectAsState()
    val status by viewModel.statusMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add device") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Tuya") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("CozyLife") })
            }

            Spacer(Modifier.height(16.dp))

            if (isBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            status?.let {
                AssistChip(onClick = { viewModel.clearStatus() }, label = { Text(it) })
                Spacer(Modifier.height(8.dp))
            }

            when (tab) {
                0 -> TuyaAddSection(viewModel)
                1 -> CozyLifeAddSection(viewModel)
            }
        }
    }
}

@Composable
private fun TuyaAddSection(viewModel: AppViewModel) {
    val config by viewModel.tuyaConfig.collectAsState()
    var deviceId by remember { mutableStateOf("") }

    Column {
        if (config == null) {
            Text("Set up your Tuya Access ID / Secret in Settings first.")
            return@Column
        }

        Text("Option A: sync everything from your linked account", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pulls in every device linked to your Tuya/Smart Life account under the UID you set in Settings.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { viewModel.syncTuyaDevicesFromLinkedAccount() }) {
            Text("Sync from linked account")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        Text("Option B: add one device by ID", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Copy a device_id from the Tuya IoT Platform's device list page.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = deviceId,
            onValueChange = { deviceId = it },
            label = { Text("Tuya device ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { viewModel.addTuyaDeviceById(deviceId) }, enabled = deviceId.isNotBlank()) {
            Text("Add device")
        }
    }
}

@Composable
private fun CozyLifeAddSection(viewModel: AppViewModel) {
    Column {
        Text(
            "Make sure your phone is on the same WiFi network as your CozyLife devices, then scan for them.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { viewModel.discoverCozyLifeDevices() }) {
            Text("Scan network")
        }
    }
}
