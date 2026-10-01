package com.example.ble_app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Hardware-backed signing key that never leaves this phone. The server links the account to its public
 * key, so face-verification requests can only come from the student's own phone.
 * Must match DeviceSignatures.java: EC P-256, SHA256withECDSA, DER signature, base64 encoding.
 */
object DeviceKey {
    private const val ALIAS = "ble_attendance_device_key"
    private const val KEYSTORE = "AndroidKeyStore"

    fun publicKeyBase64(): String {
        ensureKey()
        val publicKey = keyStore().getCertificate(ALIAS).publicKey
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun sign(payload: String): String {
        ensureKey()
        val privateKey = keyStore().getKey(ALIAS, null) as PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(payload.toByteArray(Charsets.UTF_8))
            sign()
        }
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    @Synchronized
    private fun ensureKey() {
        if (keyStore().containsAlias(ALIAS)) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).run {
            initialize(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            generateKeyPair()
        }
    }
}
