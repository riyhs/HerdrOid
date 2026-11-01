package dev.riyaldi.herdroid.ssh

import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SshConnectorTest {
  @Test
  fun buildsPortableCommand() {
    assertEquals("hc connect --session main", SshConnector.command("hc", "main"))
    assertEquals("/opt/hc connect --session work-1", SshConnector.command("/opt/hc", "work-1"))
  }

  @Test
  fun rejectsShellMetacharacters() {
    for (bad in listOf("hc;rm", "hc && id", "hc`id`", "/tmp/hc path", "hc|cat")) {
      try {
        SshConnector.command(bad, "main")
        fail("accepted unsafe path: $bad")
      } catch (_: IllegalArgumentException) {
      }
    }
  }

  @Test
  fun fingerprintIsStable() {
    val raw = byteArrayOf(1, 2, 3, 4, 5)
    val pin = Base64.getEncoder().encodeToString(raw)
    val expected = "SHA256:" + Base64.getEncoder().withoutPadding()
      .encodeToString(MessageDigest.getInstance("SHA-256").digest(raw))
    assertEquals(expected, SshConnector.fingerprint(pin))
    assertTrue(SshConnector.fingerprint("not-base64").startsWith("SHA256"))
  }
}

class Ed25519KeyTest {
  @Test
  fun writesLoadableOpenSshKey() {
    JschConfig.configure()
    val pair = com.jcraft.jsch.KeyPair.genKeyPair(com.jcraft.jsch.JSch(), com.jcraft.jsch.KeyPair.ED25519)
    val out = java.io.ByteArrayOutputStream()
    pair.writeOpenSSHv1PrivateKey(out, null)
    val temp = java.nio.file.Files.createTempFile("herdroid-key", ".tmp").toFile()
    try {
      temp.writeBytes(out.toByteArray())
      temp.setReadable(true, true)
      val loaded = com.jcraft.jsch.KeyPair.load(com.jcraft.jsch.JSch(), temp.absolutePath)
      assertEquals(com.jcraft.jsch.KeyPair.ED25519, loaded.keyType)
      assertEquals(pair.getFingerPrint(), loaded.getFingerPrint())
    } finally {
      temp.delete()
    }
  }

  @Test
  fun loadsOpenSshKeyFromMemory() {
    JschConfig.configure()
    val pair = com.jcraft.jsch.KeyPair.genKeyPair(com.jcraft.jsch.JSch(), com.jcraft.jsch.KeyPair.ED25519)
    val out = java.io.ByteArrayOutputStream()
    pair.writeOpenSSHv1PrivateKey(out, null)
    val privateBytes = out.toByteArray()
    try {
      val jsch = com.jcraft.jsch.JSch()
      jsch.addIdentity("herdroid", privateBytes, null, null)
      assertEquals(1, jsch.identityRepository.identities.size)
    } finally {
      privateBytes.fill(0)
      pair.dispose()
    }
  }
}
