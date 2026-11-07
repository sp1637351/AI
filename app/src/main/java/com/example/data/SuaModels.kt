package com.example.data

enum class SuaStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

enum class DeviceActionType(val label: String, val codeTag: String) {
    CALL("VOICE CALL", "MOKO.CALL"),
    SMS("SEND SMS", "MOKO.SMS"),
    WHATSAPP_MSG("WHATSAPP MSG", "MOKO.WHATSAPP"),
    FLASHLIGHT_ON("TORCH ON", "MOKO.TORCH.ON"),
    FLASHLIGHT_OFF("TORCH OFF", "MOKO.TORCH.OFF"),
    LOCK_PHONE("SCREEN LOCK", "MOKO.LOCK"),
    BRIGHTNESS_UP("BRIGHTNESS UP", "MOKO.BRIGHT.UP"),
    BRIGHTNESS_DOWN("BRIGHTNESS DOWN", "MOKO.BRIGHT.DOWN"),
    BRIGHTNESS_SET("BRIGHTNESS SET", "MOKO.BRIGHT.SET"),
    VOLUME_UP("VOLUME UP", "MOKO.VOL.UP"),
    VOLUME_DOWN("VOLUME DOWN", "MOKO.VOL.DOWN"),
    VOLUME_SET("VOLUME SET", "MOKO.VOL.SET"),
    VOLUME_MUTE("AUDIO MUTED", "MOKO.VOL.MUTE"),
    MEDIA_PLAY("PLAY MUSIC", "MOKO.MEDIA.PLAY"),
    MEDIA_PAUSE("PAUSE MUSIC", "MOKO.MEDIA.PAUSE"),
    MEDIA_NEXT("NEXT TRACK", "MOKO.MEDIA.NEXT"),
    MEDIA_PREV("PREV TRACK", "MOKO.MEDIA.PREV"),
    BATTERY_STATUS("BATTERY STATUS", "MOKO.BATTERY"),
    SET_ALARM("SET ALARM", "MOKO.ALARM"),
    OPEN_SETTINGS("SYSTEM SETTINGS", "MOKO.SETTINGS"),
    OPEN_APP("LAUNCH APP", "MOKO.APP.OPEN"),
    TIME_DATE("TIME & DATE", "MOKO.TIME"),
    MEMORY_SAVE("MEMORY SAVED", "MOKO.MEMORY"),
    CUSTOM_CMD("CUSTOM COMMAND", "MOKO.CUSTOM"),
    AI_CHAT("AI RESPONSE", "MOKO.CHAT")
}

enum class AiProviderType(val displayName: String, val defaultBaseUrl: String) {
    AUTO("Auto-Detect from Key", ""),
    GEMINI("Google Gemini AI", "https://generativelanguage.googleapis.com/v1beta/models"),
    OPENAI("ChatGPT (OpenAI)", "https://api.openai.com/v1/chat/completions"),
    CLAUDE("Claude AI (Anthropic)", "https://api.anthropic.com/v1/messages"),
    CUSTOM("Custom API Website / Link", "https://api.openai.com/v1/chat/completions")
}

enum class VoicePreset(val label: String, val pitch: Float, val speed: Float) {
    SWEET("Sweet & Loving (Kalpa Neural)", 1.15f, 1.0f),
    NATURAL("Warm & Natural", 1.04f, 1.03f),
    ENERGETIC("Fast & Energetic", 1.10f, 1.14f),
    CALM("Calm & Smooth", 0.92f, 0.96f)
}

data class KalpaVoiceOption(
    val id: String,
    val name: String,
    val subtitle: String
)

enum class ConfirmationStep {
    SELECT_NUMBER,
    CONFIRM_AND_DETAILS
}

data class PendingCommsConfirmation(
    val actionType: DeviceActionType, // CALL, SMS, or WHATSAPP_MSG
    val contactName: String,
    val phoneNumbers: List<String>,
    val selectedNumber: String = "",
    val messageBody: String = "",
    val scriptPreference: String = "", // "HINDI" or "ENGLISH"
    val step: ConfirmationStep = ConfirmationStep.CONFIRM_AND_DETAILS,
    val preferEnglishReply: Boolean = false
)

data class ParsedCommand(
    val action: DeviceActionType,
    val target: String = "",
    val message: String = "",
    val numericValue: Int = -1,
    val secondaryNumericValue: Int = 0,
    val displayReply: String = "",
    val spokenReply: String = "",
    val memoryFact: String = ""
)

data class CommandLogEntry(
    val id: Long,
    val timestamp: String,
    val userCommand: String,
    val suaResponse: String,
    val actionType: DeviceActionType,
    val actionSuccess: Boolean = true,
    val isPinnedToMemory: Boolean = false
)

data class MokoMemoryItem(
    val id: Long,
    val factText: String,
    val timestamp: String,
    val isAutoExtracted: Boolean = true
)

data class CustomVoiceCommand(
    val id: Long,
    val triggerPhrase: String,
    val spokenResponse: String
)

data class QuickContact(
    val name: String,
    val aliasTag: String,
    val phoneNumber: String
)

data class PermissionStatusItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val isGranted: Boolean,
    val isSpecialSystemPermission: Boolean = false
)
