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
    TIME_DATE("TIME & DATE", "MOKO.TIME"),
    MEMORY_SAVE("MEMORY SAVED", "MOKO.MEMORY"),
    AI_CHAT("MOKO AI CHAT", "MOKO.CHAT")
}

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
