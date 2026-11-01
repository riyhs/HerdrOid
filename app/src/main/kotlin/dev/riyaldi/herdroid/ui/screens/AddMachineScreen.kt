package dev.riyaldi.herdroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.riyaldi.herdroid.domain.model.MachineState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMachineScreen(
  machines: List<MachineState>,
  publicKey: String,
  onBack: () -> Unit,
  onAdd: (name: String, host: String, port: Int, user: String, session: String, hcPath: String) -> String?,
  onDemo: () -> Unit,
) {
  var name by rememberSaveable { mutableStateOf("") }
  var host by rememberSaveable { mutableStateOf("") }
  var port by rememberSaveable { mutableStateOf("22") }
  var user by rememberSaveable { mutableStateOf("") }
  var session by rememberSaveable { mutableStateOf("default") }
  var hcPath by rememberSaveable { mutableStateOf("hc") }
  var advanced by rememberSaveable { mutableStateOf(false) }
  var formError by rememberSaveable { mutableStateOf<String?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Add host") },
        navigationIcon = {
          if (machines.isNotEmpty()) {
            IconButton(onClick = onBack) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxWidth().padding(padding),
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(
          "Connect a machine running hc",
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          "HerdrOid talks to herdr-core over SSH with a key stored on this phone. Nothing is sent to a cloud service.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 6.dp),
        )
      }

      item {
        Text("1 · Add this phone's key to the host", style = MaterialTheme.typography.titleSmall)
        Text(
          "Copy the key below into ~/.ssh/authorized_keys before connecting. The host key is verified separately on first connect.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
        )
        DeviceKeyCard(publicKey)
      }

      item {
        Text("2 · Add the host", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
          value = host,
          onValueChange = { host = it; formError = null },
          label = { Text("Host, IP, or Tailscale name") },
          placeholder = { Text("host.example.com") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = user,
            onValueChange = { user = it; formError = null },
            label = { Text("SSH user") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = port,
            onValueChange = { value ->
              port = value.filter(Char::isDigit).take(5)
              formError = null
            },
            label = { Text("Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(0.6f),
          )
        }
      }

      item {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Display name (optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      item {
        TextButton(onClick = { advanced = !advanced }) {
          Text(if (advanced) "Hide advanced settings" else "Advanced settings")
        }
        if (advanced) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = session,
              onValueChange = { session = it },
              label = { Text("hc session") },
              supportingText = { Text("Use the same name as the host's HC_SESSION; normally default.") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = hcPath,
              onValueChange = { hcPath = it },
              label = { Text("hc path") },
              supportingText = { Text("Use an absolute path if hc is in ~/.local/bin.") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }

      formError?.let { error ->
        item {
          Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }

      item {
        Button(
          enabled = host.isNotBlank() && user.isNotBlank(),
          onClick = {
            val result = onAdd(
              name,
              host,
              port.toIntOrNull() ?: 22,
              user,
              session,
              hcPath,
            )
            if (result != null) formError = result
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Add and connect")
        }
        Text(
          "The host key fingerprint is shown before the first command is sent.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 6.dp),
        )
      }

      if (machines.isEmpty()) {
        item {
          OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) {
            Text("Preview with demo data")
          }
        }
      }
    }
  }
}

@Composable
private fun DeviceKeyCard(publicKey: String) {
  val clipboard = LocalClipboardManager.current
  var copied by rememberSaveable { mutableStateOf(false) }
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("This phone's public key", style = MaterialTheme.typography.titleSmall)
      Text(
        publicKey.ifBlank { "Generating key…" },
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
          enabled = publicKey.isNotBlank(),
          onClick = {
            clipboard.setText(AnnotatedString(publicKey))
            copied = true
          },
        ) {
          Text(if (copied) "Copied" else "Copy key")
        }
      }
      Text(
        "Add it to the host's ~/.ssh/authorized_keys, preferably with a forced command. Use the absolute hc path if it is not on the non-interactive PATH.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (publicKey.isNotBlank()) {
        Text(
          "restrict,command=\"/absolute/path/to/hc connect --session default\" $publicKey",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = FontFamily.Monospace,
        )
      }
    }
  }
}
