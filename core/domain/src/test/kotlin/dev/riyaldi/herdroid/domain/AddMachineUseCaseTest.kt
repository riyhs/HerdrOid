package dev.riyaldi.herdroid.domain

import dev.riyaldi.herdroid.domain.model.NewMachine
import dev.riyaldi.herdroid.domain.usecase.AddMachineResult
import dev.riyaldi.herdroid.domain.usecase.AddMachineUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddMachineUseCaseTest {
  private val useCase = AddMachineUseCase()

  @Test
  fun normalizesAndAddsMachine() {
    val result = useCase(
      NewMachine(
        name = " ",
        host = " host.example ",
        port = 2222,
        user = " riy ",
        session = " ",
        hcPath = " ",
      ),
    )
    val machine = (result as AddMachineResult.Added).machine
    assertEquals("host.example", machine.host)
    assertEquals("riy", machine.user)
    assertEquals("default", machine.session)
    assertEquals("hc", machine.hcPath)
    assertEquals(2222, machine.port)
    assertEquals("host.example", machine.name)
  }

  @Test
  fun rejectsUnsafeValues() {
    val base = NewMachine("", "host", 22, "user", "default", "hc")
    assertTrue(useCase(base.copy(host = "host name")) is AddMachineResult.Invalid)
    assertTrue(useCase(base.copy(user = "user;rm")) is AddMachineResult.Invalid)
    assertTrue(useCase(base.copy(port = 0)) is AddMachineResult.Invalid)
    assertTrue(useCase(base.copy(session = "one two")) is AddMachineResult.Invalid)
    assertTrue(useCase(base.copy(hcPath = "/tmp/hc;rm")) is AddMachineResult.Invalid)
  }
}
