package dev.riyaldi.herdroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.riyaldi.herdroid.ui.components.HostsSheet
import dev.riyaldi.herdroid.ui.components.NewAgentSheet
import dev.riyaldi.herdroid.ui.screens.AddMachineScreen
import dev.riyaldi.herdroid.ui.screens.HomeScreen
import dev.riyaldi.herdroid.ui.screens.TerminalScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerdrOidApp(vm: MainViewModel) {
  val state by vm.uiState.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  var showHosts by rememberSaveable { mutableStateOf(false) }
  var showNewAgent by rememberSaveable { mutableStateOf(false) }
  var confirmForget by rememberSaveable { mutableStateOf<String?>(null) }
  var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }
  var confirmResetKey by rememberSaveable { mutableStateOf(false) }
  var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(state.error) {
    val message = state.error ?: return@LaunchedEffect
    snackbar.showSnackbar(message)
    vm.clearError()
  }

  BackHandler(enabled = showHosts || showNewAgent || state.screen != Screen.Home) {
    when {
      showHosts -> showHosts = false
      showNewAgent -> showNewAgent = false
      state.screen == Screen.AddMachine && state.machines.isEmpty() -> Unit
      else -> vm.back()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    if (state.loading && state.machines.isEmpty()) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        CircularProgressIndicator()
        Text(
          "Opening HerdrOid",
          modifier = Modifier.padding(top = 12.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else when (state.screen) {
      Screen.Home -> HomeRoute(
        state = state,
        onSelectMachine = vm::selectMachine,
        onConnect = vm::connect,
        onDisconnect = vm::disconnect,
        onReconnect = vm::reconnect,
        onOpenTerminal = vm::openTerminal,
        onOpenHosts = { showHosts = true },
        onOpenAddMachine = vm::openAddMachine,
        onNewAgent = { showNewAgent = true },
        onLoadDemo = vm::loadDemo,
      )

      Screen.AddMachine -> AddMachineScreen(
        machines = state.machines,
        publicKey = state.publicKey,
        onBack = vm::back,
        onAdd = vm::addMachine,
        onDemo = vm::loadDemo,
      )

      Screen.Terminal -> {
        val terminal = state.terminal
        if (terminal == null) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        } else {
          TerminalScreen(
            terminal = terminal,
            machineName = state.machines
              .firstOrNull { it.machine.id == terminal.machineId }
              ?.machine
              ?.name
              .orEmpty(),
            onBack = vm::closeTerminal,
          )
        }
      }
    }

    if (state.openingTerminal) {
      LinearProgressIndicator(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .fillMaxWidth(),
      )
    }

    SnackbarHost(
      hostState = snackbar,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(16.dp),
    )
  }

  state.pendingTrust?.let { pending ->
    AlertDialog(
      onDismissRequest = vm::rejectHost,
      title = { Text("Trust this host?") },
      text = {
        Column {
          Text(
            "First connection to ${pending.machine.name}. Verify this fingerprint on the host before trusting it.",
            style = MaterialTheme.typography.bodyMedium,
          )
          SelectionContainer {
            Text(
              pending.fingerprint,
              modifier = Modifier.padding(top = 16.dp),
              fontFamily = FontFamily.Monospace,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = vm::trustHost) { Text("Trust and connect") }
      },
      dismissButton = {
        TextButton(onClick = vm::rejectHost) { Text("Cancel") }
      },
    )
  }

  if (showHosts) {
    HostsSheet(
      state = state,
      onDismiss = { showHosts = false },
      onAddMachine = {
        showHosts = false
        vm.openAddMachine()
      },
      onConnect = vm::connect,
      onDisconnect = vm::disconnect,
      onForgetHostKey = { confirmForget = it },
      onRemoveMachine = { confirmRemove = it },
      onResetDeviceKey = {
        showHosts = false
        confirmResetKey = true
      },
      onDeleteAllData = {
        showHosts = false
        confirmDeleteAll = true
      },
    )
  }

  if (showNewAgent) {
    val machine = state.selectedMachine
      ?: state.machines.firstOrNull { it.isOnline }
    NewAgentSheet(
      machineName = machine?.machine?.name.orEmpty(),
      onDismiss = { showNewAgent = false },
      onStart = { command, cwd, spaceId, tabId ->
        showNewAgent = false
        vm.startTerminal(command, cwd, spaceId, tabId)
      },
    )
  }

  confirmForget?.let { machineId ->
    val machine = state.machines.firstOrNull { it.machine.id == machineId }
    AlertDialog(
      onDismissRequest = { confirmForget = null },
      title = { Text("Reset host key?") },
      text = {
        Text(
          "Only do this after verifying the new fingerprint on ${machine?.machine?.name ?: "the host"}. The next connection will ask you to trust it again.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirmForget = null
            vm.forgetHostKey(machineId)
          },
        ) { Text("Reset") }
      },
      dismissButton = {
        TextButton(onClick = { confirmForget = null }) { Text("Cancel") }
      },
    )
  }

  confirmRemove?.let { machineId ->
    val machine = state.machines.firstOrNull { it.machine.id == machineId }
    AlertDialog(
      onDismissRequest = { confirmRemove = null },
      title = { Text("Remove host?") },
      text = { Text("Remove ${machine?.machine?.name ?: "this host"} and its saved trust information from this phone?") },
      confirmButton = {
        TextButton(
          onClick = {
            confirmRemove = null
            showHosts = false
            vm.removeMachine(machineId)
          },
        ) { Text("Remove") }
      },
      dismissButton = {
        TextButton(onClick = { confirmRemove = null }) { Text("Cancel") }
      },
    )
  }

  if (confirmResetKey) {
    AlertDialog(
      onDismissRequest = { confirmResetKey = false },
      title = { Text("Reset device key?") },
      text = {
        Text(
          "A new SSH key will be generated. Add the new public key to every host before it can connect again.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirmResetKey = false
            vm.resetDeviceKey()
          },
        ) { Text("Reset key") }
      },
      dismissButton = {
        TextButton(onClick = { confirmResetKey = false }) { Text("Cancel") }
      },
    )
  }

  if (confirmDeleteAll) {
    AlertDialog(
      onDismissRequest = { confirmDeleteAll = false },
      title = { Text("Delete all data?") },
      text = {
        Text(
          "This removes every saved host, pinned host key, and the phone's SSH key. This cannot be undone.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDeleteAll = false
            vm.deleteAllData()
          },
        ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
      },
      dismissButton = {
        TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeRoute(
  state: MainUiState,
  onSelectMachine: (String?) -> Unit,
  onConnect: (String) -> Unit,
  onDisconnect: (String) -> Unit,
  onReconnect: (String) -> Unit,
  onOpenTerminal: (String, String, String) -> Unit,
  onOpenHosts: () -> Unit,
  onOpenAddMachine: () -> Unit,
  onNewAgent: () -> Unit,
  onLoadDemo: () -> Unit,
) {
  val activeMachine = state.selectedMachine
    ?: state.machines.firstOrNull { it.isOnline }
    ?: state.machines.firstOrNull()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("HerdrOid", fontWeight = FontWeight.SemiBold)
            Text(
              when {
                state.demo -> "Demo data"
                state.connectedCount == 0 -> "No host connected"
                state.needsYouCount > 0 -> "${state.connectedCount} online · ${state.needsYouCount} need you"
                else -> "${state.connectedCount} online"
              },
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        actions = {
          TextButton(onClick = onOpenHosts) { Text("Hosts") }
          IconButton(onClick = onOpenAddMachine) {
            Icon(Icons.Outlined.Add, contentDescription = "Add host")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
      )
    },
    floatingActionButton = {
      if (activeMachine?.isOnline == true) {
        ExtendedFloatingActionButton(
          onClick = onNewAgent,
          icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
          text = { Text("New agent") },
        )
      }
    },
  ) { padding ->
    HomeScreen(
      state = state,
      modifier = Modifier.padding(padding),
      onSelectMachine = onSelectMachine,
      onConnect = onConnect,
      onDisconnect = onDisconnect,
      onReconnect = onReconnect,
      onOpenTerminal = onOpenTerminal,
      onOpenHosts = onOpenHosts,
      onOpenAddMachine = onOpenAddMachine,
      onNewAgent = onNewAgent,
      onLoadDemo = onLoadDemo,
    )
  }
}

