package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

class SuaPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("sua_assistant_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_GEMINI_API_KEY = "user_gemini_api_key"
        private const val KEY_CONTINUOUS_BG = "continuous_bg_listening"
        private const val KEY_VOICE_LOCALE = "voice_locale"
        private const val KEY_QUICK_CONTACTS = "quick_contacts_v2_json"
        private const val KEY_COMMAND_LOGS = "command_logs_v2_json"
    }

    fun getSavedUserApiKey(): String {
        return prefs.getString(KEY_USER_GEMINI_API_KEY, "")?.trim() ?: ""
    }

    fun saveUserApiKey(apiKey: String) {
        prefs.edit().putString(KEY_USER_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun deleteUserApiKey() {
        prefs.edit().remove(KEY_USER_GEMINI_API_KEY).apply()
    }

    /**
     * Returns the user-entered API key from Settings first, or falls back to BuildConfig.GEMINI_API_KEY
     * if configured in the AI Studio Secrets panel.
     */
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

    fun getVoiceLocale(): String {
        return prefs.getString(KEY_VOICE_LOCALE, "hi-IN") ?: "hi-IN"
    }

    fun setVoiceLocale(locale: String) {
        prefs.edit().putString(KEY_VOICE_LOCALE, locale).apply()
    }

    fun getQuickContacts(): List<QuickContact> {
        val raw = prefs.getString(KEY_QUICK_CONTACTS, null)
        if (raw.isNullOrBlank()) {
            val defaults = listOf(
                QuickContact(name = "Khushi", aliasTag = "PRIORITY // KHUSHI", phoneNumber = "9876543210"),
                QuickContact(name = "Mom", aliasTag = "FAMILY // MOM", phoneNumber = "9876500001"),
                QuickContact(name = "Home", aliasTag = "BASE // HOME", phoneNumber = "9876500002")
            )
            saveQuickContacts(defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<QuickContact>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    QuickContact(
                        name = obj.optString("name", ""),
                        aliasTag = obj.optString("aliasTag", "SPEED DIAL"),
                        phoneNumber = obj.optString("phoneNumber", "")
                    )
                )
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
        logs.take(40).forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("timestamp", entry.timestamp)
            obj.put("userCommand", entry.userCommand)
            obj.put("suaResponse", entry.suaResponse)
            obj.put("actionType", entry.actionType.name)
            obj.put("actionSuccess", entry.actionSuccess)
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
                        actionSuccess = obj.optBoolean("actionSuccess", true)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
