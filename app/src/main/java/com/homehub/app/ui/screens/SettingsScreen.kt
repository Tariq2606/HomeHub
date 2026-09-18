package com.homehub.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.homehub.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val config by viewModel.tuyaConfig.collectAsState()
    val cost by viewModel.electricityCostPerKwh.collectAsState()

    var accessId by remember(config) { mutableStateOf(config?.accessId ?: "") }
    var accessSecret by remember(config) { mutableStateOf(config?.accessSecret ?: "") }
    var regionHost by remember(config) { mutableStateOf(config?.regionHost ?: "openapi.tuyaus.com") }
    var linkedUid by remember(config) { mutableStateOf(config?.linkedUid ?: "") }
    var costText by remember(cost) { mutableStateOf(if (cost > 0) cost.toString() else "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()).fillMaxSize()
        ) {
            Text("Tuya cloud account", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "From your free Cloud Project at iot.tuya.com (Development Method: Smart Home), " +
                    "after linking your Tuya/Smart Life app account under Devices.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(value = accessId, onValueChange = { accessId = it }, label = { Text("Access ID") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = accessSecret,
                onValueChange = { accessSecret = it },
                label = { Text("Access Secret") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = regionHost,
                onValueChange = { regionHost = it },
                label = { Text("Region host") },
                supportingText = { Text("e.g. openapi.tuyaus.com, openapi.tuyaeu.com, openapi.tuyacn.com, openapi.tuyain.com, match your app's region") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = linkedUid,
                onValueChange = { linkedUid = it },
                label = { Text("Linked account UID (optional)") },
                supportingText = { Text("Only needed for the \"sync from linked account\" option. Found on the IoT Platform's linked-accounts list.") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.saveTuyaConfig(accessId.trim(), accessSecret.trim(), regionHost.trim(), linkedUid.trim()) }) {
                Text("Save Tuya settings")
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(32.dp))

            Text("Electricity cost", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Used to estimate running cost on the dashboard. Check your utility bill for your rate.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = costText,
                onValueChange = { costText = it },
                label = { Text("Cost per kWh") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { costText.toDoubleOrNull()?.let { viewModel.saveElectricityCost(it) } }) {
                Text("Save cost")
            }
        }
    }
}
