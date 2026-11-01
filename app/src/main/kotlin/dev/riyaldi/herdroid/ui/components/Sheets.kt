package dev.riyaldi.herdroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.riyaldi.herdroid.domain.model.ConnectionStatus
import dev.riyaldi.herdroid.domain.model.MachineState
import dev.riyaldi.herdroid.ui.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsSheet(
  state: MainUiState,
  onDismiss: () -> Unit,
  onAddMachine: () -> Unit,
  onConnect: (String) -> Unit,
  onDisconnect: (String) -> Unit,
  onForgetHostKey: (String) -> Unit,
  onRemoveMachine: (String) -> Unit,
  onResetDeviceKey: () -> Unit,
  onDeleteAllData: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Hosts", style = MaterialTheme.typography.headlineSmall)
            Text(
              "${state.connectedCount} connected · ${state.machines.size} saved",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Button(onClick = onAddMachine) { Text("Add host") }
        }
      }

      item { HostKeySummary(state.publicKey) }

      items(state.machines, key = { it.machine.id }) { machine ->
        HostRow(
          machine = machine,
          onConnect = { onConnect(machine.machine.id) },
          onDisconnect = { onDisconnect(machine.machine.id) },
          onForgetHostKey = { onForgetHostKey(machine.machine.id) },
          onRemove = { onRemoveMachine(machine.machine.id) },
        )
        HorizontalDivider()
      }

      if (state.machines.isEmpty()) {
        item {
          Text(
            "No hosts saved. Add one to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Privacy", style = MaterialTheme.typography.labelLarge)
          Text(
            "Hosts, keys, and terminal data stay on this phone. There is no analytics or cloud account, and notifications never include terminal content.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      item {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          TextButton(onClick = onResetDeviceKey) { Text("Reset device key") }
          TextButton(onClick = onDeleteAllData) {
            Text("Delete all data", color = MaterialTheme.colorScheme.error)
          }
        }
      }
    }
  }
}

@Composable
private fun HostKeySummary(publicKey: String) {
  val clipboard = LocalClipboardManager.current
  var copied by rememberSaveable { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("This phone's public key", style = MaterialTheme.typography.labelLarge)
    Text(
      publicKey.ifBlank { "Generating key…" },
      style = MaterialTheme.typography.labelSmall,
      fontFamily = FontFamily.Monospace,
      maxLines = 3,
    )
    TextButton(
      enabled = publicKey.isNotBlank(),
      onClick = {
        clipboard.setText(AnnotatedString(publicKey))
        copied = true
      },
    ) {
      Text(if (copied) "Copied" else "Copy public key")
    }
  }
}

@Composable
private fun HostRow(
  machine: MachineState,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
  onForgetHostKey: () -> Unit,
  onRemove: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          machine.machine.name,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          machine.machine.sshAddress,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      StatusText(machine.connection)
    }
    if (machine.coreName.isNotBlank()) {
      Text(machine.coreName, style = MaterialTheme.typography.labelSmall)
    }
    machine.error?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Text(
      machine.machine.fingerprint ?: "Host key not pinned",
      style = MaterialTheme.typography.labelSmall,
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      when (machine.connection) {
        ConnectionStatus.ONLINE -> OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        ConnectionStatus.CONNECTING -> OutlinedButton(onClick = onDisconnect) { Text("Cancel") }
        ConnectionStatus.ERROR, ConnectionStatus.OFFLINE -> Button(onClick = onConnect) { Text("Connect") }
      }
      if (machine.machine.knownHostKey != null && machine.machine.id != "demo") {
        TextButton(onClick = onForgetHostKey) { Text("Reset trust") }
      }
      if (machine.machine.id != "demo") {
        TextButton(onClick = onRemove) { Text("Remove") }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAgentSheet(
  machineName: String,
  onDismiss: () -> Unit,
  onStart: (command: String, cwd: String, spaceId: String, tabId: String) -> Unit,
) {
  var command by rememberSaveable { mutableStateOf("") }
  var cwd by rememberSaveable { mutableStateOf("") }
  var spaceId by rememberSaveable { mutableStateOf("") }
  var tabId by rememberSaveable { mutableStateOf("") }
  var advanced by rememberSaveable { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("New agent", style = MaterialTheme.typography.headlineSmall)
      Text(
        if (machineName.isNotBlank()) "Starts on $machineName" else "Starts on the connected host",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedTextField(
        value = command,
        onValueChange = { command = it },
        label = { Text("Command or agent") },
        placeholder = { Text("claude") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = cwd,
        onValueChange = { cwd = it },
        label = { Text("Working folder (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      TextButton(onClick = { advanced = !advanced }) {
        Text(if (advanced) "Hide desktop adoption" else "Adopt into desktop layout")
      }
      if (advanced) {
        OutlinedTextField(
          value = spaceId,
          onValueChange = { spaceId = it },
          label = { Text("Desktop space id") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = tabId,
          onValueChange = { tabId = it },
          label = { Text("Desktop tab id") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          "Optional. With space/tab labels the desktop app adopts the terminal; unknown ids create a new space or tab. HerdrOid never writes the layout.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Button(
        onClick = { onStart(command, cwd, spaceId, tabId) },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Start agent")
      }
    }
  }
}
