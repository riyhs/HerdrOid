package dev.riyaldi.herdroid.ui

import dev.riyaldi.herdroid.domain.model.Inbox
import dev.riyaldi.herdroid.domain.model.MachineState
import dev.riyaldi.herdroid.domain.model.OpenTerminal
import dev.riyaldi.herdroid.domain.model.PendingTrust

sealed interface Screen {
  data object Home : Screen
  data object AddMachine : Screen
  data object Terminal : Screen
}

data class MainUiState(
  val loading: Boolean = true,
  val screen: Screen = Screen.Home,
  val machines: List<MachineState> = emptyList(),
  val selectedMachineId: String? = null,
  val inbox: Inbox = Inbox(),
  val publicKey: String = "",
  val pendingTrust: PendingTrust? = null,
  val terminal: OpenTerminal? = null,
  val error: String? = null,
  val demo: Boolean = false,
  val openingTerminal: Boolean = false,
) {
  val selectedMachine: MachineState?
    get() = machines.firstOrNull { it.machine.id == selectedMachineId }

  val connectedCount: Int get() = machines.count { it.isOnline }
  val needsYouCount: Int get() = inbox.needsYouCount
}
