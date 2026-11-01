package dev.riyaldi.herdroid.ssh

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SshKeyManager(private val context: Context) {
  private val keyDir: File
    get() = File(context.filesDir, "ssh").apply { mkdirs() }

  private val encryptedKey: File get() = File(keyDir, "id_ed25519.enc")
  private val legacyKey: File get() = File(keyDir, "id_ed25519")
  private val publicKey: File get() = File(keyDir, "id_ed25519.pub")

  init {
    JschConfig.configure()
    deleteTempFiles()
  }

  @Synchronized
  fun publicKey(): String {
    ensureKey()
    return publicKey.readText().trim()
  }

  @Synchronized
  fun <T> withPrivateKey(block: (ByteArray) -> T): T {
    ensureKey()
    val stored = encryptedKey.readBytes()
    val plaintext = decrypt(stored)
    try {
      if (isLegacyFormat(stored)) {
        encryptedKey.writeBytes(encrypt(plaintext))
      }
      return block(plaintext)
    } finally {
      plaintext.fill(0)
    }
  }

  @Synchronized
  fun reset() {
    encryptedKey.delete()
    legacyKey.delete()
    publicKey.delete()
    deleteTempFiles()
    deleteSecretKey()
  }

  @Synchronized
  private fun ensureKey() {
    if (encryptedKey.isFile && publicKey.isFile) return

    if (legacyKey.isFile && publicKey.isFile) {
      val legacy = legacyKey.readBytes()
      try {
        encryptedKey.writeBytes(encrypt(legacy))
      } finally {
        legacy.fill(0)
        legacyKey.delete()
      }
      return
    }

    val pair = KeyPair.genKeyPair(JSch(), KeyPair.ED25519)
    try {
      val privateBytes = ByteArrayOutputStream().use { out ->
        // JSch's legacy PEM writer throws for Ed25519; OpenSSH v1 is supported.
        pair.writeOpenSSHv1PrivateKey(out, null)
        out.toByteArray()
      }
      try {
        val publicText = ByteArrayOutputStream().use { out ->
          pair.writePublicKey(out, "herdroid@phone")
          out.toString(Charsets.UTF_8.name())
        }
        encryptedKey.writeBytes(encrypt(privateBytes))
        publicKey.writeText(publicText.trim() + "\n")
        legacyKey.delete()
      } finally {
        privateBytes.fill(0)
      }
    } finally {
      pair.dispose()
    }
  }

  private fun secretKey(): SecretKey {
    val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
        ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build(),
    )
    return generator.generateKey()
  }

  private fun encrypt(plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
    cipher.updateAAD(KEY_AAD)
    val iv = cipher.iv
    require(iv.size <= 255)
    val encrypted = cipher.doFinal(plain)
    return byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + encrypted
  }

  private fun decrypt(stored: ByteArray): ByteArray {
    require(stored.isNotEmpty())
    if (stored[0] == FORMAT_VERSION) {
      require(stored.size > 3)
      val ivLength = stored[1].toInt() and 0xff
      require(ivLength in 12..16 && stored.size > 2 + ivLength)
      val iv = stored.copyOfRange(2, 2 + ivLength)
      val ciphertext = stored.copyOfRange(2 + ivLength, stored.size)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
      cipher.updateAAD(KEY_AAD)
      return cipher.doFinal(ciphertext)
    }

    require(stored.size > 2)
    val ivLength = stored[0].toInt() and 0xff
    require(ivLength in 12..16 && stored.size > 1 + ivLength)
    val iv = stored.copyOfRange(1, 1 + ivLength)
    val ciphertext = stored.copyOfRange(1 + ivLength, stored.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
    return cipher.doFinal(ciphertext)
  }

  private fun isLegacyFormat(stored: ByteArray): Boolean =
    stored.isNotEmpty() && stored[0] != FORMAT_VERSION

  private fun deleteTempFiles() {
    runCatching {
      context.cacheDir
        .listFiles { file -> file.name.startsWith(TEMP_PREFIX) }
        ?.forEach { it.delete() }
    }
  }

  private fun deleteSecretKey() {
    runCatching {
      val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
      if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
    }
  }

  private companion object {
    const val KEYSTORE = "AndroidKeyStore"
    const val ALIAS = "herdroid-ssh-key"
    const val FORMAT_VERSION: Byte = 1
    const val TEMP_PREFIX = "herdroid-key-"
    val KEY_AAD = "herdroid-ssh-key:v1".toByteArray(Charsets.UTF_8)
  }
}
