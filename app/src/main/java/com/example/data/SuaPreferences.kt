package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SuaPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("sua_assistant_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_GEMINI_API_KEY_ENC = "user_gemini_api_key_enc_v2"
        private const val KEY_CONTINUOUS_BG = "continuous_bg_listening"
        private const val KEY_REQUIRE_WAKE_WORD = "require_wake_word_bg"
        private const val KEY_GEMINI_MODEL = "gemini_model_id"
        private const val KEY_VOICE_LOCALE = "voice_locale"
        private const val KEY_QUICK_CONTACTS = "quick_contacts_real_v3_json"
        private const val KEY_COMMAND_LOGS = "command_logs_v3_json"
        private const val KEY_MOKO_MEMORIES = "moko_memories_v1_json"
        private const val KEYSTORE_ALIAS = "SuaGeminiApiKeyMasterKey"

        val SUPPORTED_GEMINI_MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash"
        )
    }

    fun getSavedUserApiKey(): String {
        val raw = prefs.getString(KEY_USER_GEMINI_API_KEY_ENC, "") ?: ""
        if (raw.isBlank()) return ""
        return decryptString(raw).trim()
    }

    fun saveUserApiKey(apiKey: String) {
        val clean = apiKey.trim()
        if (clean.isEmpty()) {
            deleteUserApiKey()
            return
        }
        val encrypted = encryptString(clean)
        prefs.edit().putString(KEY_USER_GEMINI_API_KEY_ENC, encrypted).apply()
    }

    fun deleteUserApiKey() {
        prefs.edit().remove(KEY_USER_GEMINI_API_KEY_ENC).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) return existing.secretKey

            val keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun encryptString(plainText: String): String {
        return try {
            val secretKey = getOrCreateSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
                "GCM:$ivB64:$dataB64"
            } else {
                "B64:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            "B64:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decryptString(stored: String): String {
        return try {
            when {
                stored.startsWith("GCM:") -> {
                    val parts = stored.split(":", limit = 3)
                    if (parts.size < 3) return ""
                    val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                    val cipherBytes = Base64.decode(parts[2], Base64.NO_WRAP)
                    val secretKey = getOrCreateSecretKey() ?: return ""
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                    String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
                }
                stored.startsWith("B64:") -> {
                    val b64 = stored.removePrefix("B64:")
                    String(Base64.decode(b64, Base64.NO_WRAP), Charsets.UTF_8)
                }
                else -> stored
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun getEffectiveApiKey(): String {
        val userKey = getSavedUserApiKey()
        if (userKey.isNotEmpty()) return userKey

        return try {
            val buildKey = BuildConfig.GEMINI_API_KEY.trim()
            if (buildKey.isNotEmpty() &&
                buildKey != "MY_GEMINI_API_KEY" &&
                buildKey != "null" &&
                !buildKey.startsWith("YOUR_")
            ) {
                buildKey
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun isContinuousBackgroundEnabled(): Boolean {
        return prefs.getBoolean(KEY_CONTINUOUS_BG, true)
    }

    fun setContinuousBackgroundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTINUOUS_BG, enabled).apply()
    }

    fun isWakeWordRequiredInBackground(): Boolean {
        return prefs.getBoolean(KEY_REQUIRE_WAKE_WORD, true)
    }

    fun setWakeWordRequiredInBackground(required: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_WAKE_WORD, required).apply()
    }

    fun getGeminiModel(): String {
        return prefs.getString(KEY_GEMINI_MODEL, "gemini-2.5-flash")?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "gemini-2.5-flash"
    }

    fun setGeminiModel(modelId: String) {
        val clean = modelId.trim().ifEmpty { "gemini-2.5-flash" }
        prefs.edit().putString(KEY_GEMINI_MODEL, clean).apply()
    }

    fun getVoiceLocale(): String {
        return prefs.getString(KEY_VOICE_LOCALE, "hi-IN") ?: "hi-IN"
    }

    fun setVoiceLocale(locale: String) {
        prefs.edit().putString(KEY_VOICE_LOCALE, locale).apply()
    }

    fun getQuickContacts(): List<QuickContact> {
        val raw = prefs.getString(KEY_QUICK_CONTACTS, null)
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<QuickContact>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val num = obj.optString("phoneNumber", "").trim()
                val name = obj.optString("name", "").trim()
                if (name.isNotEmpty() && num.isNotEmpty()) {
                    list.add(
                        QuickContact(
                            name = name,
                            aliasTag = obj.optString("aliasTag", "SPEED DIAL"),
                            phoneNumber = num
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveQuickContacts(contacts: List<QuickContact>) {
        val arr = JSONArray()
        contacts.forEach { c ->
            val obj = JSONObject()
            obj.put("name", c.name)
            obj.put("aliasTag", c.aliasTag)
            obj.put("phoneNumber", c.phoneNumber)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_QUICK_CONTACTS, arr.toString()).apply()
    }

    fun saveCommandLogs(logs: List<CommandLogEntry>) {
        val arr = JSONArray()
        logs.take(60).forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("timestamp", entry.timestamp)
            obj.put("userCommand", entry.userCommand)
            obj.put("suaResponse", entry.suaResponse)
            obj.put("actionType", entry.actionType.name)
            obj.put("actionSuccess", entry.actionSuccess)
            obj.put("isPinnedToMemory", entry.isPinnedToMemory)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_COMMAND_LOGS, arr.toString()).apply()
    }

    fun loadCommandLogs(): List<CommandLogEntry> {
        val raw = prefs.getString(KEY_COMMAND_LOGS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<CommandLogEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val actionName = obj.optString("actionType", DeviceActionType.AI_CHAT.name)
                val actionType = try {
                    DeviceActionType.valueOf(actionName)
                } catch (_: Exception) {
                    DeviceActionType.AI_CHAT
                }
                list.add(
                    CommandLogEntry(
                        id = obj.optLong("id", System.currentTimeMillis() + i),
                        timestamp = obj.optString("timestamp", ""),
                        userCommand = obj.optString("userCommand", ""),
                        suaResponse = obj.optString("suaResponse", ""),
                        actionType = actionType,
                        actionSuccess = obj.optBoolean("actionSuccess", true),
                        isPinnedToMemory = obj.optBoolean("isPinnedToMemory", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMemoryItems(memories: List<MokoMemoryItem>) {
        val arr = JSONArray()
        memories.take(60).forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("factText", item.factText)
            obj.put("timestamp", item.timestamp)
            obj.put("isAutoExtracted", item.isAutoExtracted)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_MOKO_MEMORIES, arr.toString()).apply()
    }

    fun loadMemoryItems(): List<MokoMemoryItem> {
        val raw = prefs.getString(KEY_MOKO_MEMORIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<MokoMemoryItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val fact = obj.optString("factText", "").trim()
                if (fact.isNotEmpty()) {
                    list.add(
                        MokoMemoryItem(
                            id = obj.optLong("id", System.currentTimeMillis() + i),
                            factText = fact,
                            timestamp = obj.optString("timestamp", ""),
                            isAutoExtracted = obj.optBoolean("isAutoExtracted", true)
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
