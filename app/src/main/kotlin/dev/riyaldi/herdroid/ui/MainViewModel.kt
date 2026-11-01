package dev.riyaldi.herdroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.riyaldi.herdroid.domain.model.NewMachine
import dev.riyaldi.herdroid.domain.repository.MachineRepository
import dev.riyaldi.herdroid.domain.usecase.AddMachineResult
import dev.riyaldi.herdroid.domain.usecase.AddMachineUseCase
import dev.riyaldi.herdroid.domain.usecase.BuildInboxUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainViewModel(
  private val repository: MachineRepository,
  private val addMachineUseCase: AddMachineUseCase,
  private val buildInboxUseCase: BuildInboxUseCase,
) : ViewModel() {
  private val requestedScreen = MutableStateFlow<Screen>(Screen.Home)
  private val machineFilter = MutableStateFlow<String?>(null)
  private val openingTerminal = MutableStateFlow(false)

  val uiState: StateFlow<MainUiState> = combine(
    repository.state,
    requestedScreen,
    machineFilter,
    openingTerminal,
  ) { snapshot, requested, filter, opening ->
    val validFilter = filter?.takeIf { id -> snapshot.machines.any { it.machine.id == id } }
    val screen = when {
      snapshot.terminal != null -> Screen.Terminal
      snapshot.loading -> Screen.Home
      snapshot.machines.isEmpty() && requested != Screen.AddMachine -> Screen.AddMachine
      else -> requested
    }
    MainUiState(
      loading = snapshot.loading,
      screen = screen,
      machines = snapshot.machines,
      selectedMachineId = validFilter,
      inbox = buildInboxUseCase(snapshot.machines, validFilter),
      publicKey = snapshot.publicKey,
      pendingTrust = snapshot.pendingTrust,
      terminal = snapshot.terminal,
      error = snapshot.error,
      demo = snapshot.demo,
      openingTerminal = opening,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = MainUiState(),
  )

  fun addMachine(
    name: String,
    host: String,
    port: Int,
    user: String,
    session: String,
    hcPath: String,
  ): String? {
    val result = addMachineUseCase(
      NewMachine(
        name = name,
        host = host,
        port = port,
        user = user,
        session = session,
        hcPath = hcPath,
      ),
    )
    return when (result) {
      is AddMachineResult.Invalid -> result.message
      is AddMachineResult.Added -> {
        repository.addMachine(result.machine)
        machineFilter.value = result.machine.id
        requestedScreen.value = Screen.Home
        repository.connect(result.machine.id)
        null
      }
    }
  }

  fun openAddMachine() {
    requestedScreen.value = Screen.AddMachine
  }

  fun back() {
    when (requestedScreen.value) {
      Screen.AddMachine -> requestedScreen.value = Screen.Home
      Screen.Terminal -> closeTerminal()
      Screen.Home -> Unit
    }
  }

  fun selectMachine(machineId: String?) {
    machineFilter.value = machineId
  }

  fun connect(machineId: String) {
    machineFilter.value = machineId
    repository.connect(machineId)
  }

  fun disconnect(machineId: String) {
    repository.disconnect(machineId)
  }

  fun reconnect(machineId: String) {
    repository.disconnect(machineId)
    repository.connect(machineId)
  }

  fun forgetHostKey(machineId: String) {
    repository.forgetHostKey(machineId)
  }

  fun removeMachine(machineId: String) {
    repository.removeMachine(machineId)
    if (machineFilter.value == machineId) machineFilter.value = null
  }

  fun trustHost() = repository.trustHost()

  fun rejectHost() = repository.rejectHost()

  fun openTerminal(machineId: String, address: String, title: String) {
    if (openingTerminal.value) return
    repository.clearError()
    openingTerminal.value = true
    repository.openTerminal(machineId, address, title)
    viewModelScope.launch {
      withTimeoutOrNull(ATTACH_TIMEOUT_MS) {
        repository.state.first { it.terminal != null || it.error != null }
      }
      openingTerminal.value = false
    }
  }

  fun closeTerminal() {
    repository.closeTerminal()
    requestedScreen.value = Screen.Home
  }

  fun startTerminal(command: String, cwd: String, spaceId: String, tabId: String) {
    val machineId = machineFilter.value
      ?: uiState.value.machines.firstOrNull { it.isOnline }?.machine?.id
      ?: return
    repository.startTerminal(machineId, command, cwd, spaceId, tabId)
  }

  fun clearError() = repository.clearError()

  fun loadDemo() {
    repository.loadDemo()
    requestedScreen.value = Screen.Home
    machineFilter.value = null
  }

  fun resetDeviceKey() = repository.resetDeviceKey()

  fun deleteAllData() {
    repository.deleteAllData()
    requestedScreen.value = Screen.Home
    machineFilter.value = null
  }

  class Factory(
    private val repository: MachineRepository,
    private val addMachine: AddMachineUseCase = AddMachineUseCase(),
    private val buildInbox: BuildInboxUseCase = BuildInboxUseCase(),
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MainViewModel(repository, addMachine, buildInbox) as T
    }
  }

  companion object {
    private const val ATTACH_TIMEOUT_MS = 25_000L
  }
}
