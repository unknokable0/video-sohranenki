package com.unknokable.sohrai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureKeyStore(context: Context) {
    private val prefs =
        context.getSharedPreferences(
            "sohr_ai_secure_v6",
            Context.MODE_PRIVATE
        )

    private fun alias(name: String) =
        "sohr_ai_v6_" +
            name.replace(
                Regex("[^a-zA-Z0-9_]"),
                "_"
            )

    private fun getOrCreateKey(name: String): SecretKey {
        val alias = alias(name)
        val store =
            KeyStore.getInstance("AndroidKeyStore")
                .apply { load(null) }

        val existing =
            store.getKey(alias, null) as? SecretKey

        if (existing != null) {
            return existing
        }

        val generator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )

        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return generator.generateKey()
    }

    fun save(name: String, value: String) {
        val cipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateKey(name)
        )

        val encrypted =
            cipher.doFinal(
                value.toByteArray(
                    Charsets.UTF_8
                )
            )

        prefs.edit()
            .putString(
                name + "_iv",
                Base64.encodeToString(
                    cipher.iv,
                    Base64.NO_WRAP
                )
            )
            .putString(
                name + "_payload",
                Base64.encodeToString(
                    encrypted,
                    Base64.NO_WRAP
                )
            )
            .apply()
    }

    fun load(name: String): String? =
        runCatching {
            val iv =
                prefs.getString(
                    name + "_iv",
                    null
                ) ?: return null

            val payload =
                prefs.getString(
                    name + "_payload",
                    null
                ) ?: return null

            val cipher =
                Cipher.getInstance(
                    "AES/GCM/NoPadding"
                )

            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(name),
                GCMParameterSpec(
                    128,
                    Base64.decode(
                        iv,
                        Base64.NO_WRAP
                    )
                )
            )

            String(
                cipher.doFinal(
                    Base64.decode(
                        payload,
                        Base64.NO_WRAP
                    )
                ),
                Charsets.UTF_8
            )
        }.getOrNull()

    fun has(name: String): Boolean =
        !load(name).isNullOrBlank()

    fun clear(name: String) {
        prefs.edit()
            .remove(name + "_iv")
            .remove(name + "_payload")
            .apply()

        runCatching {
            KeyStore.getInstance(
                "AndroidKeyStore"
            ).apply {
                load(null)
                val item = alias(name)

                if (containsAlias(item)) {
                    deleteEntry(item)
                }
            }
        }
    }

    fun clearAll() {
        listOf(
            "openai",
            "gemini",
            "groq"
        ).forEach { clear(it) }
    }
}
