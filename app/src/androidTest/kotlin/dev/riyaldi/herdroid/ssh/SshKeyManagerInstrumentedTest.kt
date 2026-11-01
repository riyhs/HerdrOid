package dev.riyaldi.herdroid.ssh

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jcraft.jsch.JSch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SshKeyManagerInstrumentedTest {
  @Test
  fun generatedKeyDecryptsAndLoadsInJsch() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val manager = SshKeyManager(context)
    val publicKey = manager.publicKey()
    assertTrue(publicKey.startsWith("ssh-ed25519 "))

    manager.withPrivateKey { privateKey ->
      JSch().addIdentity("herdroid", privateKey, null, null)
    }
  }

  @Test
  fun privateKeyIsNeverWrittenToCache() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val manager = SshKeyManager(context)
    manager.publicKey()

    val before = context.cacheDir
      .listFiles { file -> file.name.startsWith("herdroid-key-") }
      .orEmpty()
      .map { it.name }
      .toSet()

    manager.withPrivateKey { privateKey ->
      assertTrue(privateKey.isNotEmpty())
    }

    val after = context.cacheDir
      .listFiles { file -> file.name.startsWith("herdroid-key-") }
      .orEmpty()
      .map { it.name }
      .toSet()
    assertEquals(before, after)
  }
}
