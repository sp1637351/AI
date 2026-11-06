package com.example.data

enum class SuaStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

enum class DeviceActionType(val label: String, val codeTag: String) {
    CALL("VOICE CALL", "SYS.COMMS.CALL"),
    SMS("DISPATCH SMS", "SYS.COMMS.MSG"),
    FLASHLIGHT_ON("TORCH ENGAGED", "SYS.OPTICS.ON"),
    FLASHLIGHT_OFF("TORCH DISENGAGED", "SYS.OPTICS.OFF"),
    LOCK_PHONE("SECURITY LOCK", "SYS.SEC.LOCK"),
    BRIGHTNESS_UP("LUMINANCE UP", "SYS.DISP.HIGH"),
    BRIGHTNESS_DOWN("LUMINANCE DOWN", "SYS.DISP.LOW"),
    BRIGHTNESS_SET("LUMINANCE SET", "SYS.DISP.SET"),
    VOLUME_UP("AUDIO GAIN UP", "SYS.AUDIO.UP"),
    VOLUME_DOWN("AUDIO GAIN DOWN", "SYS.AUDIO.DOWN"),
    VOLUME_SET("AUDIO GAIN SET", "SYS.AUDIO.SET"),
    VOLUME_MUTE("AUDIO MUTED", "SYS.AUDIO.MUTE"),
    MEDIA_PLAY("MEDIA PLAYBACK", "SYS.MEDIA.PLAY"),
    MEDIA_PAUSE("MEDIA PAUSED", "SYS.MEDIA.PAUSE"),
    MEDIA_NEXT("NEXT TRACK", "SYS.MEDIA.NEXT"),
    MEDIA_PREV("PREV TRACK", "SYS.MEDIA.PREV"),
    BATTERY_STATUS("POWER DIAGNOSTICS", "SYS.PWR.SCAN"),
    SET_ALARM("CHRONO ALARM", "SYS.TIME.ALARM"),
    OPEN_SETTINGS("OS CONSOLE", "SYS.OS.CONFIG"),
    TIME_DATE("CHRONO SYNC", "SYS.TIME.SYNC"),
    AI_CHAT("NEURAL RESPONSE", "SUA.AI.CORE")
}

data class ParsedCommand(
    val action: DeviceActionType,
    val target: String = "",
    val message: String = "",
    val numericValue: Int = -1,
    val displayReply: String = "",
    val spokenReply: String = ""
)

data class CommandLogEntry(
    val id: Long,
    val timestamp: String,
    val userCommand: String,
    val suaResponse: String,
    val actionType: DeviceActionType,
    val actionSuccess: Boolean = true
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
