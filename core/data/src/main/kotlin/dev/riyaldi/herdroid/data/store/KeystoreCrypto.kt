package dev.riyaldi.herdroid.data.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class KeystoreCrypto(
  private val keyAlias: String,
) {
  fun encrypt(plain: ByteArray): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key())
    cipher.updateAAD(aad())
    val iv = cipher.iv
    val encrypted = cipher.doFinal(plain)
    val payload = ByteArray(2 + iv.size + encrypted.size)
    payload[0] = FORMAT_VERSION
    payload[1] = iv.size.toByte()
    iv.copyInto(payload, destinationOffset = 2)
    encrypted.copyInto(payload, destinationOffset = 2 + iv.size)
    return Base64.getEncoder().encodeToString(payload)
  }

  fun decrypt(encoded: String): ByteArray {
    val payload = Base64.getDecoder().decode(encoded)
    require(payload.isNotEmpty()) { "encrypted value is empty" }

    if (payload[0] == FORMAT_VERSION) {
      require(payload.size > 3) { "encrypted value is truncated" }
      val ivLength = payload[1].toInt() and 0xff
      require(ivLength in 12..16 && payload.size > 2 + ivLength) { "invalid encrypted value" }
      val iv = payload.copyOfRange(2, 2 + ivLength)
      val ciphertext = payload.copyOfRange(2 + ivLength, payload.size)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
      cipher.updateAAD(aad())
      return cipher.doFinal(ciphertext)
    }

    require(payload.size > 2) { "encrypted value is empty" }
    val ivLength = payload[0].toInt() and 0xff
    require(ivLength in 12..16 && payload.size > 1 + ivLength) { "invalid encrypted value" }
    val iv = payload.copyOfRange(1, 1 + ivLength)
    val ciphertext = payload.copyOfRange(1 + ivLength, payload.size)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
    return cipher.doFinal(ciphertext)
  }

  fun isLegacy(encoded: String): Boolean = runCatching {
    Base64.getDecoder().decode(encoded).firstOrNull()?.let { it != FORMAT_VERSION } ?: false
  }.getOrDefault(false)

  fun deleteKey() {
    val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    if (store.containsAlias(keyAlias)) store.deleteEntry(keyAlias)
  }

  private fun aad(): ByteArray = "herdroid:$keyAlias:v1".toByteArray(Charsets.UTF_8)

  private fun key(): SecretKey {
    val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (store.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
        keyAlias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build(),
    )
    return generator.generateKey()
  }

  private companion object {
    const val KEYSTORE = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val FORMAT_VERSION: Byte = 1
  }
}
