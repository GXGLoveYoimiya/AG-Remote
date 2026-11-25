package com.local.agremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.local.agremote.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(vm.prefs.host) }
    var port by remember { mutableStateOf(vm.prefs.port.toString()) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Rounded.RocketLaunch, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(tr("连接电脑"), style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                name,
                { name = it },
                Modifier.fillMaxWidth(),
                label = { Text(tr("设备名称")) },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    host,
                    { host = it.trim() },
                    Modifier.weight(1f),
                    label = { Text("IP") },
                    placeholder = { Text("100.x.x.x") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(port, { port = it.filter(Char::isDigit) }, Modifier.weight(0.45f), label = { Text("Port") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(),
                label = { Text(tr("配对码")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        error = vm.pair(host, port.toIntOrNull() ?: 5100, code, name)
                        busy = false
                    }
                },
                enabled = !busy && host.isNotBlank() && code.length == 6,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(tr("配对"))
            }
        }
    }
}
