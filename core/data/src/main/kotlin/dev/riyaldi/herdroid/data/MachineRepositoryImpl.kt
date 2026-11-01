package dev.riyaldi.herdroid.data

import android.content.Context
import dev.riyaldi.herdroid.data.store.MachineStore
import dev.riyaldi.herdroid.data.store.MachineStoreException
import dev.riyaldi.herdroid.domain.model.Agent
import dev.riyaldi.herdroid.domain.model.AgentState
import dev.riyaldi.herdroid.domain.model.AppSnapshot
import dev.riyaldi.herdroid.domain.model.ConnectionStatus
import dev.riyaldi.herdroid.domain.model.Machine
import dev.riyaldi.herdroid.domain.model.MachineState
import dev.riyaldi.herdroid.domain.model.OpenTerminal
import dev.riyaldi.herdroid.domain.model.PendingTrust
import dev.riyaldi.herdroid.domain.model.Workspace
import dev.riyaldi.herdroid.domain.model.WorkspaceTab
import dev.riyaldi.herdroid.domain.repository.MachineRepository
import dev.riyaldi.herdroid.mirror.HerdrAppMirror
import dev.riyaldi.herdroid.protocol.HcClient
import dev.riyaldi.herdroid.ssh.HostKeyChangedException
import dev.riyaldi.herdroid.ssh.SshConnector
import dev.riyaldi.herdroid.ssh.SshConnection
import dev.riyaldi.herdroid.ssh.SshKeyManager
import dev.riyaldi.herdroid.ssh.SshTarget
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class MachineRepositoryImpl(context: Context) : MachineRepository {
  private val appContext = context.applicationContext
  private val store = MachineStore(appContext)
  private val keys = SshKeyManager(appContext)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val sessions = ConcurrentHashMap<String, MachineSession>()
  private val connectJobs = ConcurrentHashMap<String, Job>()
  private val connectAttempts = ConcurrentHashMap<String, Long>()
  private val persistMutex = Mutex()

  @Volatile
  private var savedBeforeDemo: List<MachineState> = emptyList()

  private val _state = MutableStateFlow(AppSnapshot())
  override val state = _state.asStateFlow()

  init {
    scope.launch {
      val result = runCatching { store.load() }
      val loaded = result.getOrDefault(emptyList()).map { MachineState(it) }
      val loadError = result.exceptionOrNull()?.let { error ->
        if (error is MachineStoreException) error.message else "Saved hosts could not be decrypted."
      }
      _state.update { current ->
        if (current.demo) {
          savedBeforeDemo = loaded
          current.copy(loading = false, error = current.error ?: loadError)
        } else {
          val merged = if (current.machines.isEmpty()) {
            loaded
          } else {
            current.machines + loaded.filterNot { savedMachine ->
              current.machines.any { it.machine.id == savedMachine.machine.id }
            }
          }
          current.copy(loading = false, machines = merged, error = current.error ?: loadError)
        }
      }
    }
    scope.launch {
      val key = runCatching { keys.publicKey() }.getOrDefault("")
      _state.update { it.copy(publicKey = key) }
    }
  }

  override fun addMachine(machine: Machine) {
    _state.update { current ->
      val base = if (current.demo) savedBeforeDemo else current.machines
      current.copy(
        loading = false,
        demo = false,
        machines = base.filterNot {
          it.machine.id == DEMO_ID || it.machine.id == machine.id
        } + MachineState(machine),
        terminal = current.terminal?.takeUnless { it.demo },
        error = null,
      )
    }
    savedBeforeDemo = emptyList()
    persist()
  }

  override fun removeMachine(machineId: String) {
    if (machineId == DEMO_ID) {
      _state.update {
        it.copy(machines = savedBeforeDemo, demo = false, terminal = null)
      }
      savedBeforeDemo = emptyList()
      return
    }
    disconnect(machineId)
    _state.update { current ->
      current.copy(machines = current.machines.filterNot { it.machine.id == machineId })
    }
    persist()
  }

  override fun connect(machineId: String) {
    val machine = _state.value.machines.firstOrNull { it.machine.id == machineId }?.machine ?: return
    if (machine.id == DEMO_ID) return
    if (machine.knownHostKey == null) probe(machine) else connectPinned(machine)
  }

  override fun disconnect(machineId: String) {
    connectAttempts.remove(machineId)
    connectJobs.remove(machineId)?.cancel()
    sessions.remove(machineId)?.let { session -> scope.launch { session.close() } }
    closeTerminalFor(machineId)
    updateMachine(machineId) { it.copy(connection = ConnectionStatus.OFFLINE, error = null) }
  }

  override fun trustHost() {
    val pending = _state.value.pendingTrust ?: return
    val machine = pending.machine.copy(knownHostKey = pending.hostKey)
    _state.update { current ->
      current.copy(
        pendingTrust = null,
        machines = current.machines.map {
          if (it.machine.id == machine.id) {
            it.copy(machine = machine, connection = ConnectionStatus.CONNECTING)
          } else {
            it
          }
        },
      )
    }
    persist()
    connectPinned(machine)
  }

  override fun rejectHost() {
    val pending = _state.value.pendingTrust ?: return
    _state.update { current ->
      current.copy(
        pendingTrust = null,
        machines = current.machines.map {
          if (it.machine.id == pending.machine.id) {
            it.copy(connection = ConnectionStatus.OFFLINE)
          } else {
            it
          }
        },
      )
    }
  }

  override fun forgetHostKey(machineId: String) {
    disconnect(machineId)
    _state.update { current ->
      current.copy(
        machines = current.machines.map {
          if (it.machine.id == machineId) {
            it.copy(machine = it.machine.copy(knownHostKey = null), connection = ConnectionStatus.OFFLINE)
          } else {
            it
          }
        },
      )
    }
    persist()
  }

  override fun openTerminal(machineId: String, address: String, title: String) {
    if (machineId == DEMO_ID) {
      _state.update {
        it.copy(terminal = OpenTerminal(machineId, address, title, handle = null, demo = true))
      }
      return
    }
    val session = sessions[machineId] ?: run {
      _state.update { it.copy(error = "Connect to this machine first.") }
      return
    }
    scope.launch {
      try {
        val handle = HcTerminalHandle(session.client.attach(address, cols = 100, rows = 30, history = 1_000))
        val previous = _state.value.terminal
        _state.update {
          it.copy(terminal = OpenTerminal(machineId, address, title, handle))
        }
        previous?.handle?.close()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        _state.update { current ->
          current.copy(
            error = if (e.message?.contains("already closed", ignoreCase = true) == true) {
              "This terminal has already finished. Its status was refreshed."
            } else {
              e.message ?: "Could not attach to the terminal."
            },
          )
        }
        runCatching { refresh(session) }
      }
    }
  }

  override fun closeTerminal() {
    val terminal = _state.value.terminal
    _state.update { it.copy(terminal = null) }
    terminal?.handle?.close()
  }

  override fun startTerminal(
    machineId: String,
    command: String,
    cwd: String,
    spaceId: String,
    tabId: String,
  ) {
    if (machineId == DEMO_ID) return
    val session = sessions[machineId] ?: run {
      _state.update { it.copy(error = "Connect to this machine first.") }
      return
    }
    val title = command.ifBlank { "shell" }
    scope.launch {
      try {
        val result = session.client.call(
          "terminal.run",
          buildJsonObject {
            put("keep", true)
            put("wait", false)
            if (command.isNotBlank()) put("line", command)
            if (cwd.isNotBlank()) put("cwd", cwd)
            if (spaceId.isNotBlank() || tabId.isNotBlank()) {
              put(
                "labels",
                buildJsonObject {
                  if (spaceId.isNotBlank()) put("herdr.space", spaceId.trim())
                  if (tabId.isNotBlank()) put("herdr.tab", tabId.trim())
                },
              )
            }
          },
        )
        refresh(session)
        result.jsonObject["address"]?.jsonPrimitive?.contentOrNull?.let { address ->
          openTerminal(machineId, address, title)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        _state.update { it.copy(error = e.message ?: "Could not start a terminal.") }
      }
    }
  }

  override fun loadDemo() {
    if (!_state.value.demo) {
      savedBeforeDemo = _state.value.machines.filterNot { it.machine.id == DEMO_ID }
    }
    disconnectAll()
    closeTerminal()
    val machine = Machine(
      id = DEMO_ID,
      name = "Demo host",
      host = "demo",
      user = "you",
      knownHostKey = "demo",
    )
    val agents = listOf(
      Agent(
        address = "t1",
        machineId = DEMO_ID,
        machineName = machine.name,
        name = "reviewer",
        title = "Review parser",
        cwd = "/work/herdroid",
        state = AgentState.NEEDS_YOU,
        message = "Allow the parser change?",
      ),
      Agent(
        address = "t2",
        machineId = DEMO_ID,
        machineName = machine.name,
        name = "builder",
        title = "Build release",
        cwd = "/work/herdroid",
        state = AgentState.WORKING,
        message = "Compiling",
        progress = 42,
      ),
      Agent(
        address = "t3",
        machineId = DEMO_ID,
        machineName = machine.name,
        name = "notes",
        title = "Release notes",
        cwd = "/work/herdroid/docs",
        state = AgentState.IDLE,
        message = "Waiting for input",
      ),
      Agent(
        address = "t4",
        machineId = DEMO_ID,
        machineName = machine.name,
        name = "old-build",
        title = "Previous build",
        cwd = "/work/herdroid",
        state = AgentState.FINISHED,
        message = "Finished",
      ),
    )
    val workspaces = listOf(
      Workspace(
        name = "herdroid",
        tabs = listOf(
          WorkspaceTab("Agents", listOf("t1", "t2")),
          WorkspaceTab("Notes", listOf("t3")),
        ),
      ),
    )
    _state.update {
      it.copy(
        loading = false,
        demo = true,
        machines = listOf(
          MachineState(
            machine = machine,
            connection = ConnectionStatus.ONLINE,
            coreName = "demo-core",
            agents = agents,
            workspaces = workspaces,
          ),
        ),
        terminal = null,
        error = null,
      )
    }
  }

  override fun resetDeviceKey() {
    disconnectAll()
    closeTerminal()
    _state.update { current ->
      current.copy(
        pendingTrust = null,
        machines = current.machines.map {
          it.copy(connection = ConnectionStatus.OFFLINE, error = null)
        },
      )
    }
    scope.launch {
      runCatching { keys.reset() }
      val key = runCatching { keys.publicKey() }.getOrDefault("")
      _state.update { it.copy(publicKey = key) }
    }
  }

  override fun deleteAllData() {
    disconnectAll()
    closeTerminal()
    scope.launch {
      persistMutex.withLock {
        runCatching { store.clear() }
        runCatching { keys.reset() }
        val key = runCatching { keys.publicKey() }.getOrDefault("")
        savedBeforeDemo = emptyList()
        _state.value = AppSnapshot(loading = false, publicKey = key)
      }
    }
  }

  override fun clearError() {
    _state.update { it.copy(error = null) }
  }

  override fun close() {
    sessions.values.forEach { it.close() }
    sessions.clear()
    connectJobs.values.forEach { it.cancel() }
    connectJobs.clear()
    closeTerminal()
    scope.cancel()
  }

  private fun probe(machine: Machine) {
    sessions.remove(machine.id)?.close()
    val attempt = beginAttempt(machine.id)
    updateMachine(machine.id) { it.copy(connection = ConnectionStatus.CONNECTING, error = null) }
    val job = scope.launch {
      try {
        val target = target(machine)
        val (hostKey, fingerprint) = keys.withPrivateKey { privateKey ->
          SshConnector.probeHostKey(target, privateKey)
        }
        if (!isCurrent(machine.id, attempt)) return@launch
        _state.update { it.copy(pendingTrust = PendingTrust(machine, hostKey, fingerprint)) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        if (isCurrent(machine.id, attempt)) {
          updateMachine(machine.id) {
            it.copy(connection = ConnectionStatus.ERROR, error = connectionError(e))
          }
        }
      } finally {
        finishAttempt(machine.id, attempt)
      }
    }
    trackJob(machine.id, job)
  }

  private fun connectPinned(machine: Machine) {
    sessions.remove(machine.id)?.close()
    val attempt = beginAttempt(machine.id)
    updateMachine(machine.id) { it.copy(connection = ConnectionStatus.CONNECTING, error = null) }
    val job = scope.launch {
      var ssh: SshConnection? = null
      var sessionScope: CoroutineScope? = null
      try {
        val target = target(machine)
        val connection = keys.withPrivateKey { privateKey ->
          SshConnector.connect(target, privateKey)
        }
        ssh = connection
        if (!isCurrent(machine.id, attempt)) {
          connection.close()
          return@launch
        }
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        sessionScope = newScope
        val client = HcClient(connection.input, connection.output, newScope)
        val hello = withTimeoutOrNull(SETUP_TIMEOUT_MS) { client.handshake() }
          ?: throw InterruptedIOException("timed out waiting for hc")
        val snapshot = client.subscribe()
        val terminals = snapshot.terminals.ifEmpty { client.listTerminals(all = true) }
        val mirror = HerdrAppMirror.parse(client.getDoc(HERDR_APP_DOC).value)
        if (!isCurrent(machine.id, attempt)) {
          connection.close()
          return@launch
        }
        val session = MachineSession(machine, connection, client, newScope)
        sessions[machine.id] = session
        updateMachine(machine.id) {
          it.copy(
            connection = ConnectionStatus.ONLINE,
            coreName = hello.core.name.ifBlank { hello.core.id },
            agents = terminals.mapNotNull { info -> info.toAgentOrNull(machine) },
            workspaces = mirror.toWorkspaces(),
            error = null,
          )
        }
        collectEvents(session)
      } catch (e: CancellationException) {
        throw e
      } catch (e: HostKeyChangedException) {
        sessionScope?.cancel()
        ssh?.close()
        if (isCurrent(machine.id, attempt)) {
          updateMachine(machine.id) {
            it.copy(
              connection = ConnectionStatus.ERROR,
              error = "Host key changed. Expected a different key, got ${e.fingerprint}.",
            )
          }
        }
      } catch (e: Throwable) {
        sessionScope?.cancel()
        ssh?.close()
        if (isCurrent(machine.id, attempt)) {
          updateMachine(machine.id) {
            it.copy(connection = ConnectionStatus.ERROR, error = connectionError(e))
          }
        }
      } finally {
        finishAttempt(machine.id, attempt)
      }
    }
    trackJob(machine.id, job)
  }

  private fun collectEvents(session: MachineSession) {
    val machineId = session.machine.id
    session.eventJob = scope.launch {
      session.client.events.collectLatest { event ->
        val type = event["type"]?.jsonPrimitive?.contentOrNull
        if (type == "closed") {
          sessions.remove(machineId)?.close()
          updateMachine(machineId) {
            it.copy(connection = ConnectionStatus.ERROR, error = "Connection closed.")
          }
          return@collectLatest
        }
        delay(180)
        refresh(session)
      }
    }
  }

  private suspend fun refresh(session: MachineSession) {
    try {
      val terminals = session.client.listTerminals(all = true)
      val doc = session.client.getDoc(HERDR_APP_DOC)
      val mirror = HerdrAppMirror.parse(doc.value)
      updateMachine(session.machine.id) {
        it.copy(
          connection = ConnectionStatus.ONLINE,
          agents = terminals.mapNotNull { info -> info.toAgentOrNull(session.machine) },
          workspaces = mirror.toWorkspaces(),
          error = null,
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Throwable) {
      // A single failed refresh must not tear down a working SSH session.
    }
  }

  private fun target(machine: Machine) = SshTarget(
    host = machine.host,
    port = machine.port,
    user = machine.user,
    command = SshConnector.command(machine.hcPath, machine.session),
    knownHostKey = machine.knownHostKey,
  )

  private fun beginAttempt(machineId: String): Long {
    connectAttempts.remove(machineId)
    connectJobs.remove(machineId)?.cancel()
    val attempt = System.nanoTime()
    connectAttempts[machineId] = attempt
    return attempt
  }

  private fun isCurrent(machineId: String, attempt: Long): Boolean =
    connectAttempts[machineId] == attempt

  private fun finishAttempt(machineId: String, attempt: Long) {
    if (isCurrent(machineId, attempt)) connectAttempts.remove(machineId)
  }

  private fun trackJob(machineId: String, job: Job) {
    connectJobs[machineId] = job
    job.invokeOnCompletion {
      if (connectJobs[machineId] === job) connectJobs.remove(machineId)
    }
  }

  private fun connectionError(error: Throwable): String {
    val message = error.message?.takeIf { it.isNotBlank() } ?: "Connection failed."
    return when {
      error is InterruptedIOException ||
        message.contains("timed out", true) ||
        message.contains("timeout", true) ->
        "Connection timed out after ${SETUP_TIMEOUT_MS / 1000}s. Check the host, network, and hc path."
      message.contains("connection closed during hello", true) ->
        "SSH connected, but hc closed before saying hello. Check that hc is on PATH and the forced command is allowed."
      message.contains("ConnectException", true) || message.contains("Connection refused", true) ->
        "The host refused the SSH connection. Check that sshd is running and the port is correct."
      message.contains("UnknownHostException", true) ->
        "The host name could not be resolved. Check the address or Tailscale connection."
      message.contains("Auth fail", true) || message.contains("authentication", true) ->
        "SSH authentication failed. Add this phone's public key to the host's authorized_keys."
      message.contains("Host key", true) ->
        "The host key does not match. Verify the host before resetting trust."
      message.contains("No route to host", true) ->
        "No route to the host. Check the network or VPN connection."
      else -> "Could not connect: ${message.substringBefore(':').take(120)}"
    }
  }

  private fun persist() {
    scope.launch {
      persistMutex.withLock {
        if (_state.value.demo) return@withLock
        val machines = _state.value.machines
          .map { it.machine }
          .filterNot { it.id == DEMO_ID }
        runCatching { store.save(machines) }
      }
    }
  }

  private fun updateMachine(machineId: String, transform: (MachineState) -> MachineState) {
    _state.update { current ->
      current.copy(
        machines = current.machines.map {
          if (it.machine.id == machineId) transform(it) else it
        },
      )
    }
  }

  private fun closeTerminalFor(machineId: String) {
    val terminal = _state.value.terminal
    if (terminal?.machineId == machineId) {
      _state.update { it.copy(terminal = null) }
      terminal.handle?.close()
    }
  }

  private fun disconnectAll() {
    val old = sessions.values.toList()
    sessions.clear()
    old.forEach { session -> scope.launch { session.close() } }
    connectJobs.values.forEach { it.cancel() }
    connectJobs.clear()
    connectAttempts.clear()
  }

  private class MachineSession(
    val machine: Machine,
    private val ssh: SshConnection,
    val client: HcClient,
    private val scope: CoroutineScope,
  ) {
    var eventJob: Job? = null

    fun close() {
      eventJob?.cancel()
      runCatching { client.close() }
      runCatching { ssh.close() }
      scope.cancel()
    }
  }

  companion object {
    private const val DEMO_ID = "demo"
    private const val HERDR_APP_DOC = "herdr-app"
    private const val SETUP_TIMEOUT_MS = 20_000L
  }
}
