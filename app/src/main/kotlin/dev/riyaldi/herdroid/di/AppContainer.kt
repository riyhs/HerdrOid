package dev.riyaldi.herdroid.di

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.riyaldi.herdroid.data.MachineRepositoryImpl
import dev.riyaldi.herdroid.domain.model.AgentState
import dev.riyaldi.herdroid.domain.repository.MachineRepository
import dev.riyaldi.herdroid.service.HerdrWatchService
import dev.riyaldi.herdroid.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val notified = mutableSetOf<String>()

  val repository: MachineRepository = MachineRepositoryImpl(appContext)

  init {
    Notifications.ensureChannels(appContext)
    scope.launch {
      repository.state
        .map { snapshot ->
          snapshot.machines
            .filter { it.machine.id != "demo" }
            .flatMap { machine -> machine.agents.filter { it.state == AgentState.NEEDS_YOU } }
        }
        .distinctUntilChanged()
        .collect { blocked ->
          val current = blocked.map { "${it.machineId}/${it.address}" }.toSet()
          notified.retainAll(current)
          blocked.forEach { agent ->
            val key = "${agent.machineId}/${agent.address}"
            if (notified.add(key)) {
              Notifications.postNeedsYou(
                appContext,
                key.hashCode(),
                "${agent.displayName} needs you",
                "${agent.machineName} · ${agent.address} is blocked on a question or permission.",
              )
            }
          }
        }
    }
    scope.launch {
      repository.state
        .map { snapshot -> snapshot.machines.count { it.isOnline && it.machine.id != "demo" } }
        .distinctUntilChanged()
        .collect { count -> updateWatchService(count) }
    }
  }

  private fun updateWatchService(connected: Int) {
    val intent = Intent(appContext, HerdrWatchService::class.java)
    if (connected == 0) {
      runCatching { appContext.stopService(intent) }
      return
    }
    runCatching {
      ContextCompat.startForegroundService(
        appContext,
        intent.putExtra(HerdrWatchService.EXTRA_TEXT, "$connected machine(s) connected · SSH active"),
      )
    }
  }
}
