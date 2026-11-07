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
        private const val KEY_AI_PROVIDER = "ai_provider_type_v1"
        private const val KEY_CUSTOM_API_URL = "custom_api_endpoint_url_v1"
        private const val KEY_CONTINUOUS_BG = "continuous_bg_listening"
        private const val KEY_LOW_BATTERY_WAKE = "low_battery_ok_google_wake"
        private const val KEY_REQUIRE_WAKE_WORD = "require_wake_word_bg"
        private const val KEY_GEMINI_MODEL = "gemini_model_id_v3"
        private const val KEY_VOICE_LOCALE = "voice_locale"
        private const val KEY_VOICE_PRESET = "voice_preset_v1"
        private const val KEY_VOICE_PITCH = "voice_pitch_v1"
        private const val KEY_VOICE_SPEED = "voice_speed_v1"
        private const val KEY_USER_NAME = "custom_user_name_v1"
        private const val KEY_ASSISTANT_NAME = "custom_assistant_name_v1"
        private const val KEY_QUICK_CONTACTS = "quick_contacts_real_v3_json"
        private const val KEY_COMMAND_LOGS = "command_logs_v3_json"
        private const val KEY_MOKO_MEMORIES = "moko_memories_v1_json"
        private const val KEY_CUSTOM_COMMANDS = "custom_voice_commands_v1_json"
        private const val KEY_KALPA_API_KEY_ENC = "kalpa_voice_api_key_enc_v1"
        private const val KEY_KALPA_VOICE_ID = "kalpa_voice_id_v1"
        private const val KEYSTORE_ALIAS = "SuaGeminiApiKeyMasterKey"

        const val DEVELOPER_NAME = "MR. SHASHANK"
        const val DEFAULT_KALPA_API_KEY = "sk-live-8d7644911c4cb82b45f6dff3126cb3ac903bc95307b5ba50"
        const val DEFAULT_KALPA_VOICE_ID = "5e0c5704-590f-483b-b291-00a2415cb67e" // Kiara (Feminine Hindi & English)

        val KALPA_VOICE_OPTIONS = listOf(
            KalpaVoiceOption(
                id = "5e0c5704-590f-483b-b291-00a2415cb67e",
                name = "Kiara (Sweet Female)",
                subtitle = "Kalpa Neural Hindi & English Voice"
            ),
            KalpaVoiceOption(
                id = "dda5319f-3b81-4124-b69e-9241eb510b77",
                name = "Wren (Human-Like)",
                subtitle = "Expressive Conversational Voice"
            )
        )

        val SUPPORTED_GEMINI_MODELS = listOf(
            "gemini-3-flash-preview",
            "gemini-3.1-pro-preview",
            "gemini-3.1-flash-lite-preview",
            "gpt-4o-mini",
            "claude-3-5-haiku-latest"
        )
    }

    fun getKalpaApiKey(): String {
        val raw = prefs.getString(KEY_KALPA_API_KEY_ENC, "") ?: ""
        if (raw.isNotBlank()) {
            val dec = decryptString(raw).trim()
            if (dec.isNotEmpty()) return dec
        }
        return DEFAULT_KALPA_API_KEY
    }

    fun saveKalpaApiKey(apiKey: String) {
        val clean = apiKey.trim()
        if (clean.isEmpty()) {
            prefs.edit().remove(KEY_KALPA_API_KEY_ENC).apply()
            return
        }
        val encrypted = encryptString(clean)
        prefs.edit().putString(KEY_KALPA_API_KEY_ENC, encrypted).apply()
    }

    fun getKalpaVoiceId(): String {
        return prefs.getString(KEY_KALPA_VOICE_ID, DEFAULT_KALPA_VOICE_ID)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_KALPA_VOICE_ID
    }

    fun setKalpaVoiceId(voiceId: String) {
        prefs.edit().putString(KEY_KALPA_VOICE_ID, voiceId.trim().ifEmpty { DEFAULT_KALPA_VOICE_ID }).apply()
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

    fun getAiProvider(): AiProviderType {
        val name = prefs.getString(KEY_AI_PROVIDER, AiProviderType.AUTO.name) ?: AiProviderType.AUTO.name
        return try {
            AiProviderType.valueOf(name)
        } catch (_: Exception) {
            AiProviderType.AUTO
        }
    }

    fun setAiProvider(provider: AiProviderType) {
        prefs.edit().putString(KEY_AI_PROVIDER, provider.name).apply()
    }

    fun getCustomApiUrl(): String {
        return prefs.getString(KEY_CUSTOM_API_URL, "")?.trim().orEmpty()
    }

    fun setCustomApiUrl(url: String) {
        prefs.edit().putString(KEY_CUSTOM_API_URL, url.trim()).apply()
    }

    /**
     * Auto-detects the AI provider from the saved API Key or custom URL if set to AUTO.
     */
    fun resolveActiveAiProvider(apiKey: String = getEffectiveApiKey()): AiProviderType {
        val explicit = getAiProvider()
        if (explicit != AiProviderType.AUTO) return explicit

        val customUrl = getCustomApiUrl().lowercase()
        val key = apiKey.trim()
        return when {
            customUrl.contains("anthropic.com") || key.startsWith("sk-ant-") -> AiProviderType.CLAUDE
            customUrl.contains("openai.com") || customUrl.contains("openrouter.ai") ||
                customUrl.contains("groq.com") || customUrl.contains("deepseek.com") -> AiProviderType.OPENAI
            customUrl.startsWith("http://") || customUrl.startsWith("https://") -> {
                if (customUrl.contains("generativelanguage.googleapis.com")) AiProviderType.GEMINI
                else AiProviderType.CUSTOM
            }
            key.startsWith("sk-") || key.startsWith("gsk_") -> AiProviderType.OPENAI
            else -> AiProviderType.GEMINI
        }
    }

    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "Shashank")?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "Shashank"
    }

    fun setUserName(name: String) {
        val clean = name.trim().ifEmpty { "Shashank" }
        prefs.edit().putString(KEY_USER_NAME, clean).apply()
    }

    fun getAssistantName(): String {
        return prefs.getString(KEY_ASSISTANT_NAME, "Moko")?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "Moko"
    }

    fun setAssistantName(name: String) {
        val clean = name.trim().ifEmpty { "Moko" }
        prefs.edit().putString(KEY_ASSISTANT_NAME, clean).apply()
    }

    fun getVoicePreset(): VoicePreset {
        val raw = prefs.getString(KEY_VOICE_PRESET, VoicePreset.SWEET.name) ?: VoicePreset.SWEET.name
        return try {
            VoicePreset.valueOf(raw)
        } catch (_: Exception) {
            VoicePreset.SWEET
        }
    }

    fun setVoicePreset(preset: VoicePreset) {
        prefs.edit()
            .putString(KEY_VOICE_PRESET, preset.name)
            .putFloat(KEY_VOICE_PITCH, preset.pitch)
            .putFloat(KEY_VOICE_SPEED, preset.speed)
            .apply()
    }

    fun getVoicePitch(): Float {
        return prefs.getFloat(KEY_VOICE_PITCH, VoicePreset.SWEET.pitch).coerceIn(0.6f, 1.6f)
    }

    fun setVoicePitch(pitch: Float) {
        prefs.edit().putFloat(KEY_VOICE_PITCH, pitch.coerceIn(0.6f, 1.6f)).apply()
    }

    fun getVoiceSpeed(): Float {
        return prefs.getFloat(KEY_VOICE_SPEED, VoicePreset.SWEET.speed).coerceIn(0.6f, 1.6f)
    }

    fun setVoiceSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_VOICE_SPEED, speed.coerceIn(0.6f, 1.6f)).apply()
    }

    fun isLowBatteryWakeMode(): Boolean {
        return prefs.getBoolean(KEY_LOW_BATTERY_WAKE, true)
    }

    fun setLowBatteryWakeMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_BATTERY_WAKE, enabled).apply()
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
        val saved = prefs.getString(KEY_GEMINI_MODEL, "gemini-3-flash-preview")?.trim().orEmpty()
        if (saved.isEmpty() || saved == "gemini-2.5-pro" || saved == "gemini-2.0-flash") {
            return "gemini-3-flash-preview"
        }
        return saved
    }

    fun setGeminiModel(modelId: String) {
        val clean = modelId.trim().removePrefix("models/").ifEmpty { "gemini-3-flash-preview" }
        prefs.edit().putString(KEY_GEMINI_MODEL, clean).apply()
    }

    fun getVoiceLocale(): String {
        return prefs.getString(KEY_VOICE_LOCALE, "hi-IN") ?: "hi-IN"
    }

    fun setVoiceLocale(locale: String) {
        prefs.edit().putString(KEY_VOICE_LOCALE, locale).apply()
    }

    fun getCustomCommands(): List<CustomVoiceCommand> {
        val raw = prefs.getString(KEY_CUSTOM_COMMANDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<CustomVoiceCommand>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val trig = obj.optString("triggerPhrase", "").trim()
                val resp = obj.optString("spokenResponse", "").trim()
                if (trig.isNotEmpty() && resp.isNotEmpty()) {
                    list.add(
                        CustomVoiceCommand(
                            id = obj.optLong("id", System.currentTimeMillis() + i),
                            triggerPhrase = trig,
                            spokenResponse = resp
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomCommands(commands: List<CustomVoiceCommand>) {
        val arr = JSONArray()
        commands.take(50).forEach { cmd ->
            val obj = JSONObject()
            obj.put("id", cmd.id)
            obj.put("triggerPhrase", cmd.triggerPhrase)
            obj.put("spokenResponse", cmd.spokenResponse)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_COMMANDS, arr.toString()).apply()
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
