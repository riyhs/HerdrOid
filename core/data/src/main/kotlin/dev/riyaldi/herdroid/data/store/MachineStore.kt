package dev.riyaldi.herdroid.data.store

import android.content.Context
import dev.riyaldi.herdroid.domain.model.Machine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class MachineStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

class MachineStore(context: Context) {
  private val prefs = context.getSharedPreferences("herdroid-machines", Context.MODE_PRIVATE)
  private val crypto = KeystoreCrypto("herdroid-config-key")
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }
  private val serializer = ListSerializer(Machine.serializer())

  @Synchronized
  fun load(): List<Machine> {
    val encrypted = prefs.getString(KEY_ENCRYPTED, null)
    if (encrypted != null) {
      val decoded = runCatching {
        json.decodeFromString(serializer, crypto.decrypt(encrypted).toString(Charsets.UTF_8))
      }
      if (decoded.isSuccess) {
        val machines = decoded.getOrThrow()
        if (crypto.isLegacy(encrypted)) {
          save(machines)
        }
        removeLegacy()
        return machines
      }

      val legacy = prefs.getString(KEY_LEGACY, null)
      if (legacy != null) {
        val migrated = runCatching {
          json.decodeFromString(serializer, legacy)
        }.getOrNull()
        if (migrated != null) {
          save(migrated)
          removeLegacy()
          return migrated
        }
      }
      throw MachineStoreException(
        "Saved hosts could not be decrypted. Add the host again to replace the saved data.",
        decoded.exceptionOrNull(),
      )
    }

    val legacy = prefs.getString(KEY_LEGACY, null) ?: return emptyList()
    val migrated = runCatching {
      json.decodeFromString(serializer, legacy)
    }.getOrElse {
      throw MachineStoreException("Saved hosts could not be read. Add the host again to replace the saved data.", it)
    }
    if (migrated.isNotEmpty()) {
      save(migrated)
    }
    removeLegacy()
    return migrated
  }

  @Synchronized
  fun save(machines: List<Machine>) {
    val plain = json.encodeToString(serializer, machines).toByteArray(Charsets.UTF_8)
    try {
      prefs.edit().putString(KEY_ENCRYPTED, crypto.encrypt(plain)).apply()
    } finally {
      plain.fill(0)
    }
  }

  @Synchronized
  fun clear() {
    prefs.edit().remove(KEY_ENCRYPTED).remove(KEY_LEGACY).commit()
    crypto.deleteKey()
  }

  private fun removeLegacy() {
    if (prefs.contains(KEY_LEGACY)) {
      prefs.edit().remove(KEY_LEGACY).commit()
    }
  }

  private companion object {
    const val KEY_ENCRYPTED = "machines.v2"
    const val KEY_LEGACY = "machines"
  }
}
