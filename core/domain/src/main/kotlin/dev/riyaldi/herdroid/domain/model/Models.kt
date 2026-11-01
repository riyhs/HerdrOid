package dev.riyaldi.herdroid.domain.model

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class Machine(
  val id: String,
  val name: String,
  val host: String,
  val port: Int = 22,
  val user: String,
  val session: String = "default",
  val hcPath: String = "hc",
  val knownHostKey: String? = null,
) {
  val sshAddress: String get() = "$user@$host:$port"
  val fingerprint: String? get() = knownHostKey?.let(HostKeyFingerprint::fromOpenSsh)
}

data class NewMachine(
  val name: String,
  val host: String,
  val port: Int,
  val user: String,
  val session: String,
  val hcPath: String,
)

enum class ConnectionStatus { OFFLINE, CONNECTING, ONLINE, ERROR }

enum class AgentState { NEEDS_YOU, WORKING, IDLE, FINISHED }

data class Agent(
  val address: String,
  val machineId: String,
  val machineName: String,
  val name: String,
  val title: String,
  val cwd: String,
  val state: AgentState,
  val message: String = "",
  val progress: Int? = null,
) {
  val displayName: String
    get() = name.takeIf { it.isNotBlank() }
      ?: title.takeIf { it.isNotBlank() }
      ?: address

  val folder: String
    get() = java.io.File(cwd).name.ifBlank { cwd }

  val isLive: Boolean get() = state != AgentState.FINISHED
}

data class WorkspaceTab(
  val name: String,
  val agentAddresses: List<String>,
)

data class Workspace(
  val name: String,
  val tabs: List<WorkspaceTab>,
)

data class MachineState(
  val machine: Machine,
  val connection: ConnectionStatus = ConnectionStatus.OFFLINE,
  val coreName: String = "",
  val agents: List<Agent> = emptyList(),
  val workspaces: List<Workspace> = emptyList(),
  val error: String? = null,
) {
  val isOnline: Boolean get() = connection == ConnectionStatus.ONLINE
}

data class PendingTrust(
  val machine: Machine,
  val hostKey: String,
  val fingerprint: String,
)

sealed interface TerminalEvent {
  data class Data(val bytes: ByteArray) : TerminalEvent
  data class Closed(val reason: String?) : TerminalEvent
}

interface TerminalHandle : AutoCloseable {
  val events: Flow<TerminalEvent>

  fun write(bytes: ByteArray)

  fun acknowledge(bytes: Int)

  fun resize(cols: Int, rows: Int)
}

data class OpenTerminal(
  val machineId: String,
  val address: String,
  val title: String,
  val handle: TerminalHandle?,
  val demo: Boolean = false,
)

data class AppSnapshot(
  val loading: Boolean = true,
  val machines: List<MachineState> = emptyList(),
  val publicKey: String = "",
  val pendingTrust: PendingTrust? = null,
  val terminal: OpenTerminal? = null,
  val error: String? = null,
  val demo: Boolean = false,
)

data class Inbox(
  val needsYou: List<Agent> = emptyList(),
  val working: List<Agent> = emptyList(),
  val idle: List<Agent> = emptyList(),
  val finished: List<Agent> = emptyList(),
) {
  val total: Int get() = needsYou.size + working.size + idle.size + finished.size
  val needsYouCount: Int get() = needsYou.size
}
