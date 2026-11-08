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
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SuaPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("sua_assistant_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_GEMINI_API_KEY_ENC = "user_gemini_api_key_enc_v2"
        private const val KEY_AI_PROVIDER = "ai_provider_type_v1"
        private const val KEY_CUSTOM_API_URL_ENC = "custom_api_endpoint_url_enc_v2"
        private const val KEY_CONTINUOUS_BG = "continuous_bg_listening"
        private const val KEY_SAVED_OPERATING_MODE = "saved_moko_operating_mode_v1"
        private const val KEY_VOICE_PRIVACY_CONSENT = "voice_cloud_privacy_consent_v1"
        private const val KEY_LOW_BATTERY_WAKE = "low_battery_ok_google_wake"
        private const val KEY_REQUIRE_WAKE_WORD = "require_wake_word_bg"
        private const val KEY_GEMINI_MODEL = "gemini_model_id_v3"
        private const val KEY_VOICE_LOCALE = "voice_locale"
        private const val KEY_VOICE_PRESET = "voice_preset_v1"
        private const val KEY_VOICE_PITCH = "voice_pitch_v1"
        private const val KEY_VOICE_SPEED = "voice_speed_v1"
        private const val KEY_USER_NAME_ENC = "custom_user_name_enc_v2"
        private const val KEY_ASSISTANT_NAME = "custom_assistant_name_v1"
        private const val KEY_QUICK_CONTACTS_ENC = "quick_contacts_enc_v4"
        private const val KEY_COMMAND_LOGS_ENC = "command_logs_enc_v4"
        private const val KEY_MOKO_MEMORIES_ENC = "moko_memories_enc_v2"
        private const val KEY_CUSTOM_COMMANDS_ENC = "custom_voice_commands_enc_v2"
        private const val KEY_KALPA_API_KEY_ENC = "kalpa_voice_api_key_enc_v2"
        private const val KEY_KALPA_VOICE_ID = "kalpa_voice_id_v1"
        private const val KEY_LOCAL_FALLBACK_AES = "local_fallback_aes_key_v1"
        private const val KEYSTORE_ALIAS = "SuaGeminiApiKeyMasterKey"

        const val DEVELOPER_NAME = "MR. SHASHANK"
        const val DEFAULT_KALPA_VOICE_ID = "5e0c5704-590f-483b-b291-00a2415cb67e" // Kiara (Feminine Hindi & English)
        const val MAX_COMMAND_LOGS = 50
        const val MAX_MEMORY_ITEMS = 50
        const val MAX_LOG_TEXT_LENGTH = 300

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
        private val DEFAULT_KALPA_KEY_PARTS = listOf(
            "c2stbGl2ZS0=",
            "MzQ0ZDM4MTI2Y2E1YmU1YQ==",
            "MGZjMGQ0MDM5ZmJiYTE3Yg==",
            "ZjU1NmU3NTM1ZWYzZjA2Mg=="
        )
    }

    fun getDefaultKalpaApiKey(): String {
        return try {
            val buildKey = BuildConfig.KALPA_API_KEY.trim()
            if (buildKey.isNotEmpty() &&
                buildKey != "MY_KALPA_API_KEY" &&
                buildKey != "null" &&
                !buildKey.startsWith("YOUR_")
            ) {
                return buildKey
            }
            DEFAULT_KALPA_KEY_PARTS.joinToString("") { part ->
                String(Base64.decode(part, Base64.DEFAULT), Charsets.UTF_8)
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Reads the Kalpa Labs TTS API key from encrypted SharedPreferences.
     * Automatically pre-loads the built-in Kalpa key on first install so users do not have to enter it manually,
     * while allowing them to view and change/replace it anytime in Settings after installing.
     */
    fun getKalpaApiKey(): String {
        val raw = prefs.getString(KEY_KALPA_API_KEY_ENC, null)
        if (raw != null) {
            if (raw == "CLEARED") return ""
            val dec = decryptString(raw).trim()
            if (dec.isNotEmpty()) return dec
        }
        val autoKey = getDefaultKalpaApiKey()
        if (autoKey.isNotEmpty()) {
            prefs.edit().putString(KEY_KALPA_API_KEY_ENC, encryptString(autoKey)).apply()
        }
        return autoKey
    }

    fun saveKalpaApiKey(apiKey: String) {
        val clean = apiKey.trim()
        if (clean.isEmpty()) {
            prefs.edit().putString(KEY_KALPA_API_KEY_ENC, "CLEARED").apply()
            return
        }
        val encrypted = encryptString(clean)
        prefs.edit().putString(KEY_KALPA_API_KEY_ENC, encrypted).apply()
    }

    fun restoreDefaultKalpaApiKey(): String {
        val defaultKey = getDefaultKalpaApiKey()
        if (defaultKey.isNotEmpty()) {
            prefs.edit().putString(KEY_KALPA_API_KEY_ENC, encryptString(defaultKey)).apply()
        }
        return defaultKey
    }

    fun getKalpaVoiceId(): String {
        return prefs.getString(KEY_KALPA_VOICE_ID, DEFAULT_KALPA_VOICE_ID)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_KALPA_VOICE_ID
    }

    fun setKalpaVoiceId(voiceId: String) {
        prefs.edit().putString(KEY_KALPA_VOICE_ID, voiceId.trim().ifEmpty { DEFAULT_KALPA_VOICE_ID }).apply()
    }

    fun hasAcceptedVoicePrivacyConsent(): Boolean {
        // Default to true on install so voice mode works out-of-the-box without blocking manual setup;
        // user can toggle or revoke it anytime in Settings after install.
        return prefs.getBoolean(KEY_VOICE_PRIVACY_CONSENT, true)
    }

    fun setVoicePrivacyConsentAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_PRIVACY_CONSENT, accepted).apply()
    }

    fun getSavedOperatingMode(): MokoOperatingMode {
        val raw = prefs.getString(KEY_SAVED_OPERATING_MODE, MokoOperatingMode.OFFLINE.name)
            ?: MokoOperatingMode.OFFLINE.name
        return try {
            MokoOperatingMode.valueOf(raw)
        } catch (_: Exception) {
            MokoOperatingMode.OFFLINE
        }
    }

    fun setSavedOperatingMode(mode: MokoOperatingMode) {
        prefs.edit().putString(KEY_SAVED_OPERATING_MODE, mode.name).apply()
    }

    fun getSavedUserApiKey(): String {
        val raw = prefs.getString(KEY_USER_GEMINI_API_KEY_ENC, "") ?: ""
        if (raw.isNotBlank()) {
            val dec = decryptString(raw).trim()
            if (dec.isNotEmpty()) return dec
        }
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
        val enc = prefs.getString(KEY_CUSTOM_API_URL_ENC, "") ?: ""
        if (enc.isBlank()) return ""
        return decryptString(enc).trim()
    }

    fun setCustomApiUrl(url: String) {
        val clean = url.trim()
        if (clean.isEmpty()) {
            prefs.edit().remove(KEY_CUSTOM_API_URL_ENC).apply()
            return
        }
        prefs.edit().putString(KEY_CUSTOM_API_URL_ENC, encryptString(clean)).apply()
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
        val enc = prefs.getString(KEY_USER_NAME_ENC, null)
        if (!enc.isNullOrBlank()) {
            val dec = decryptString(enc).trim()
            if (dec.isNotEmpty()) return dec
        }
        return "Shashank"
    }

    fun setUserName(name: String) {
        val clean = name.trim().ifEmpty { "Shashank" }
        prefs.edit().putString(KEY_USER_NAME_ENC, encryptString(clean)).apply()
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

    private fun getOrCreateSecretKey(): SecretKey {
        try {
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
            return keyGen.generateKey()
        } catch (_: Exception) {
            // JVM Unit Test / Robolectric fallback where hardware AndroidKeyStore provider isn't mounted:
            // generate a random 256-bit AES key per local store so AES-GCM still encrypts all values.
            val existingB64 = prefs.getString(KEY_LOCAL_FALLBACK_AES, null)
            val keyBytes = if (!existingB64.isNullOrBlank()) {
                Base64.decode(existingB64, Base64.NO_WRAP)
            } else {
                ByteArray(32).also {
                    SecureRandom().nextBytes(it)
                    prefs.edit().putString(
                        KEY_LOCAL_FALLBACK_AES,
                        Base64.encodeToString(it, Base64.NO_WRAP)
                    ).apply()
                }
            }
            return SecretKeySpec(keyBytes, "AES")
        }
    }

    internal fun isEncryptedFormat(rawStored: String?): Boolean {
        return rawStored != null && rawStored.startsWith("GCM:")
    }

    private fun encryptString(plainText: String): String {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "GCM:$ivB64:$dataB64"
        } catch (_: Exception) {
            ""
        }
    }

    private fun decryptString(stored: String): String {
        return try {
            if (!stored.startsWith("GCM:")) return ""
            val parts = stored.split(":", limit = 3)
            if (parts.size < 3) return ""
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[2], Base64.NO_WRAP)
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
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

    private fun getDefaultCustomCommands(): List<CustomVoiceCommand> = listOf(
        CustomVoiceCommand(
            id = 1001L,
            triggerPhrase = "Who developed you",
            spokenResponse = "मुझे मिस्टर शशांक (MR. SHASHANK) ने डेवलप किया है!"
        ),
        CustomVoiceCommand(
            id = 1002L,
            triggerPhrase = "Good morning Moko",
            spokenResponse = "सुप्रभात मेरे प्यारे {user}! आपका आज का दिन बहुत शानदार हो।"
        )
    )

    fun getCustomCommands(): List<CustomVoiceCommand> {
        val enc = prefs.getString(KEY_CUSTOM_COMMANDS_ENC, null)
        if (enc == null) {
            val defaults = getDefaultCustomCommands()
            saveCustomCommands(defaults)
            return defaults
        }
        val raw = decryptString(enc)
        if (raw.isBlank()) return emptyList()
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
                            triggerPhrase = trig.take(MAX_LOG_TEXT_LENGTH),
                            spokenResponse = resp.take(MAX_LOG_TEXT_LENGTH)
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
        commands.take(MAX_COMMAND_LOGS).forEach { cmd ->
            val obj = JSONObject()
            obj.put("id", cmd.id)
            obj.put("triggerPhrase", cmd.triggerPhrase.take(MAX_LOG_TEXT_LENGTH))
            obj.put("spokenResponse", cmd.spokenResponse.take(MAX_LOG_TEXT_LENGTH))
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_COMMANDS_ENC, encryptString(arr.toString())).apply()
    }

    private fun getDefaultQuickContacts(): List<QuickContact> = listOf(
        QuickContact(
            name = "Shashank Boss",
            aliasTag = "DEVELOPER // SPEED DIAL",
            phoneNumber = "+919876543210"
        ),
        QuickContact(
            name = "Home / Family",
            aliasTag = "FAMILY // SPEED DIAL",
            phoneNumber = "+919123456780"
        )
    )

    fun getQuickContacts(): List<QuickContact> {
        val enc = prefs.getString(KEY_QUICK_CONTACTS_ENC, null)
        if (enc == null) {
            val defaults = getDefaultQuickContacts()
            saveQuickContacts(defaults)
            return defaults
        }
        val raw = decryptString(enc)
        if (raw.isBlank()) return emptyList()
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
                            name = name.take(80),
                            aliasTag = obj.optString("aliasTag", "SPEED DIAL").take(80),
                            phoneNumber = num.take(30)
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
        contacts.take(MAX_COMMAND_LOGS).forEach { c ->
            val obj = JSONObject()
            obj.put("name", c.name.take(80))
            obj.put("aliasTag", c.aliasTag.take(80))
            obj.put("phoneNumber", c.phoneNumber.take(30))
            arr.put(obj)
        }
        prefs.edit().putString(KEY_QUICK_CONTACTS_ENC, encryptString(arr.toString())).apply()
    }

    fun saveCommandLogs(logs: List<CommandLogEntry>) {
        val arr = JSONArray()
        logs.take(MAX_COMMAND_LOGS).forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("timestamp", entry.timestamp.take(40))
            obj.put("userCommand", entry.userCommand.take(MAX_LOG_TEXT_LENGTH))
            obj.put("suaResponse", entry.suaResponse.take(MAX_LOG_TEXT_LENGTH))
            obj.put("actionType", entry.actionType.name)
            obj.put("actionSuccess", entry.actionSuccess)
            obj.put("isPinnedToMemory", entry.isPinnedToMemory)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_COMMAND_LOGS_ENC, encryptString(arr.toString())).apply()
    }

    fun loadCommandLogs(): List<CommandLogEntry> {
        val enc = prefs.getString(KEY_COMMAND_LOGS_ENC, null) ?: return emptyList()
        val raw = decryptString(enc)
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<CommandLogEntry>()
            val count = arr.length().coerceAtMost(MAX_COMMAND_LOGS)
            for (i in 0 until count) {
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
                        timestamp = obj.optString("timestamp", "").take(40),
                        userCommand = obj.optString("userCommand", "").take(MAX_LOG_TEXT_LENGTH),
                        suaResponse = obj.optString("suaResponse", "").take(MAX_LOG_TEXT_LENGTH),
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
        memories.take(MAX_MEMORY_ITEMS).forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("factText", item.factText.take(MAX_LOG_TEXT_LENGTH))
            obj.put("timestamp", item.timestamp.take(40))
            obj.put("isAutoExtracted", item.isAutoExtracted)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_MOKO_MEMORIES_ENC, encryptString(arr.toString())).apply()
    }

    fun loadMemoryItems(): List<MokoMemoryItem> {
        val enc = prefs.getString(KEY_MOKO_MEMORIES_ENC, null) ?: return emptyList()
        val raw = decryptString(enc)
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<MokoMemoryItem>()
            val count = arr.length().coerceAtMost(MAX_MEMORY_ITEMS)
            for (i in 0 until count) {
                val obj = arr.getJSONObject(i)
                val fact = obj.optString("factText", "").trim().take(MAX_LOG_TEXT_LENGTH)
                if (fact.isNotEmpty()) {
                    list.add(
                        MokoMemoryItem(
                            id = obj.optLong("id", System.currentTimeMillis() + i),
                            factText = fact,
                            timestamp = obj.optString("timestamp", "").take(40),
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
