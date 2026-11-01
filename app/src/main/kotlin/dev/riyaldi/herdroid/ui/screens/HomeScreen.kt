package dev.riyaldi.herdroid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.riyaldi.herdroid.domain.model.Agent
import dev.riyaldi.herdroid.domain.model.ConnectionStatus
import dev.riyaldi.herdroid.domain.model.MachineState
import dev.riyaldi.herdroid.domain.model.Workspace
import dev.riyaldi.herdroid.ui.MainUiState
import dev.riyaldi.herdroid.ui.components.AgentCard
import dev.riyaldi.herdroid.ui.components.StatusDot
import dev.riyaldi.herdroid.ui.components.StatusText

@Composable
fun HomeScreen(
  state: MainUiState,
  modifier: Modifier = Modifier,
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
  var showFinished by rememberSaveable { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (state.machines.isNotEmpty()) {
      item(key = "machines") {
        MachineFilterRow(
          machines = state.machines,
          selectedMachineId = state.selectedMachineId,
          onSelectMachine = onSelectMachine,
          onAddMachine = onOpenAddMachine,
        )
      }
    }

    if (state.loading) {
      item(key = "loading") {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
    }

    if (activeMachine != null && !activeMachine.isOnline) {
      item(key = "connection") {
        ConnectionCard(
          machine = activeMachine,
          onConnect = { onConnect(activeMachine.machine.id) },
          onDisconnect = { onDisconnect(activeMachine.machine.id) },
          onReconnect = { onReconnect(activeMachine.machine.id) },
          onOpenHosts = onOpenHosts,
        )
      }
    }

    if (state.machines.isEmpty() && !state.loading) {
      item(key = "empty-hosts") {
        EmptyHostsCard(onAddMachine = onOpenHosts, onLoadDemo = onLoadDemo)
      }
    }

    if (state.inbox.total == 0 && activeMachine?.isOnline == true) {
      item(key = "empty-inbox") {
        EmptyInboxCard(
          machineName = activeMachine.machine.name,
          onNewAgent = onNewAgent,
        )
      }
    }

    if (state.inbox.needsYou.isNotEmpty()) {
      item(key = "needs-header") {
        SectionHeader("Needs you", state.inbox.needsYou.size, MaterialTheme.colorScheme.error)
      }
      items(state.inbox.needsYou, key = { "agent-${it.machineId}-${it.address}" }) { agent ->
        AgentCard(agent = agent, onClick = { onOpenTerminal(agent.machineId, agent.address, agent.displayName) })
      }
    }

    if (state.inbox.working.isNotEmpty()) {
      item(key = "working-header") {
        SectionHeader("Working", state.inbox.working.size, MaterialTheme.colorScheme.primary)
      }
      items(state.inbox.working, key = { "agent-${it.machineId}-${it.address}" }) { agent ->
        AgentCard(agent = agent, onClick = { onOpenTerminal(agent.machineId, agent.address, agent.displayName) })
      }
    }

    if (state.inbox.idle.isNotEmpty()) {
      item(key = "idle-header") {
        SectionHeader("Ready", state.inbox.idle.size, MaterialTheme.colorScheme.onSurfaceVariant)
      }
      items(state.inbox.idle, key = { "agent-${it.machineId}-${it.address}" }) { agent ->
        AgentCard(agent = agent, onClick = { onOpenTerminal(agent.machineId, agent.address, agent.displayName) })
      }
    }

    if (state.inbox.finished.isNotEmpty()) {
      item(key = "finished-header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
          SectionHeader(
            "Finished",
            state.inbox.finished.size,
            MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = { showFinished = !showFinished }) {
            Text(if (showFinished) "Hide" else "Show")
          }
        }
      }
      if (showFinished) {
        items(state.inbox.finished, key = { "agent-${it.machineId}-${it.address}" }) { agent ->
          AgentCard(agent = agent, onClick = {})
        }
      }
    }

    if (activeMachine?.isOnline == true && activeMachine.workspaces.isNotEmpty()) {
      item(key = "desktop-layout") {
        DesktopLayoutCard(
          machine = activeMachine,
          onOpenTerminal = onOpenTerminal,
        )
      }
    }

    item(key = "bottom-space") {
      Spacer(Modifier.height(76.dp))
    }
  }
}

@Composable
private fun MachineFilterRow(
  machines: List<MachineState>,
  selectedMachineId: String?,
  onSelectMachine: (String?) -> Unit,
  onAddMachine: () -> Unit,
) {
  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(vertical = 2.dp),
  ) {
    item(key = "all") {
      FilterChip(
        selected = selectedMachineId == null,
        onClick = { onSelectMachine(null) },
        label = { Text("All hosts") },
      )
    }
    items(machines, key = { it.machine.id }) { machine ->
      FilterChip(
        selected = selectedMachineId == machine.machine.id,
        onClick = { onSelectMachine(machine.machine.id) },
        label = {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusDot(machine.connection)
            Text(machine.machine.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        },
      )
    }
    item(key = "add") {
      AssistChip(
        onClick = onAddMachine,
        label = { Text("Add host") },
      )
    }
  }
}

@Composable
private fun SectionHeader(
  title: String,
  count: Int,
  color: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.padding(top = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = color,
    )
    Text(
      count.toString(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ConnectionCard(
  machine: MachineState,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
  onReconnect: () -> Unit,
  onOpenHosts: () -> Unit,
) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(machine.machine.name, style = MaterialTheme.typography.titleMedium)
          Text(
            machine.machine.sshAddress,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        StatusText(machine.connection)
      }
      machine.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (machine.connection) {
          ConnectionStatus.CONNECTING -> {
            OutlinedButton(onClick = onDisconnect) { Text("Cancel") }
          }
          ConnectionStatus.ERROR -> {
            Button(onClick = onReconnect) { Text("Retry") }
          }
          ConnectionStatus.OFFLINE -> {
            Button(onClick = onConnect) { Text("Connect") }
          }
          ConnectionStatus.ONLINE -> Unit
        }
        TextButton(onClick = onOpenHosts) { Text("Host details") }
      }
    }
  }
}

@Composable
private fun EmptyInboxCard(machineName: String, onNewAgent: () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("All clear on $machineName", style = MaterialTheme.typography.titleMedium)
      Text(
        "No agents are waiting, working, or idle. Start a new agent when you need one.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onNewAgent) { Text("New agent") }
    }
  }
}

@Composable
private fun EmptyHostsCard(onAddMachine: () -> Unit, onLoadDemo: () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("No hosts yet", style = MaterialTheme.typography.titleMedium)
      Text(
        "Add the SSH machine that runs hc. You will verify its host key once.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onAddMachine) { Text("Add host") }
      TextButton(onClick = onLoadDemo) { Text("Preview with demo data") }
    }
  }
}

@Composable
private fun DesktopLayoutCard(
  machine: MachineState,
  onOpenTerminal: (String, String, String) -> Unit,
) {
  var expanded by rememberSaveable(machine.machine.id) { mutableStateOf(false) }
  val agentsByAddress = machine.agents.associateBy { it.address }
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { expanded = !expanded }
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Desktop layout", style = MaterialTheme.typography.titleSmall)
          Text(
            "Read-only mirror of ${machine.machine.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.labelMedium)
      }
      if (expanded) {
        machine.workspaces.forEach { workspace ->
          WorkspaceRow(machine.machine.id, workspace, agentsByAddress, onOpenTerminal)
        }
      }
    }
  }
}

@Composable
private fun WorkspaceRow(
  machineId: String,
  workspace: Workspace,
  agentsByAddress: Map<String, Agent>,
  onOpenTerminal: (String, String, String) -> Unit,
) {
  Text(
    workspace.name,
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier.padding(top = 4.dp),
  )
  workspace.tabs.forEach { tab ->
    Text(
      tab.name,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      tab.agentAddresses.forEach { address ->
        val agent = agentsByAddress[address]
        AssistChip(
          enabled = agent?.isLive == true,
          onClick = {
            onOpenTerminal(
              machineId,
              address,
              agent?.displayName ?: address,
            )
          },
          label = { Text(agent?.displayName ?: address) },
        )
      }
    }
  }
}
