package dev.riyaldi.herdroid.domain.usecase

import dev.riyaldi.herdroid.domain.model.Machine
import dev.riyaldi.herdroid.domain.model.NewMachine
import java.util.UUID

sealed interface AddMachineResult {
  data class Added(val machine: Machine) : AddMachineResult
  data class Invalid(val message: String) : AddMachineResult
}

class AddMachineUseCase {
  operator fun invoke(input: NewMachine): AddMachineResult {
    val host = input.host.trim()
    val user = input.user.trim()
    val name = input.name.trim().ifBlank { host }
    val session = input.session.trim().ifBlank { "default" }
    val hcPath = input.hcPath.trim().ifBlank { "hc" }

    if (host.isBlank()) return AddMachineResult.Invalid("Host is required.")
    if (host.length > 255 || host.any { it.isWhitespace() || it == '/' }) {
      return AddMachineResult.Invalid("Host must be a hostname or IP address.")
    }
    if (user.isBlank()) return AddMachineResult.Invalid("SSH user is required.")
    if (!user.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) {
      return AddMachineResult.Invalid("SSH user contains unsupported characters.")
    }
    if (input.port !in 1..65535) return AddMachineResult.Invalid("Port must be between 1 and 65535.")
    if (!session.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) {
      return AddMachineResult.Invalid("Session contains unsupported characters.")
    }
    if (!hcPath.matches(Regex("[A-Za-z0-9_./~+-]+"))) {
      return AddMachineResult.Invalid("hc path contains unsupported characters.")
    }
    if (name.length > 64) return AddMachineResult.Invalid("Name is too long.")

    return AddMachineResult.Added(
      Machine(
        id = UUID.randomUUID().toString(),
        name = name,
        host = host,
        port = input.port,
        user = user,
        session = session,
        hcPath = hcPath,
      ),
    )
  }
}
