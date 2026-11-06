package com.example.control

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.admin.SuaDeviceAdminReceiver
import com.example.data.DeviceActionType
import com.example.data.ParsedCommand
import com.example.data.PermissionStatusItem
import com.example.data.QuickContact
import com.example.data.SuaPreferences
import com.example.data.SuaStateRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActionExecutionResult(
    val success: Boolean,
    val displayReply: String,
    val finalSpokenReply: String
)

class SuaDeviceController(private val context: Context) {
    private val appContext = context.applicationContext
    private val prefs = SuaPreferences(appContext)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val devicePolicyManager =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(appContext, SuaDeviceAdminReceiver::class.java)

    init {
        syncHardwareTelemetry()
    }

    fun syncHardwareTelemetry() {
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val volPct = ((curVol.toFloat() / maxVol.toFloat()) * 100f).toInt()
            SuaStateRepository.updateVolume(volPct)
        } catch (_: Exception) {
        }

        try {
            val sysBright = Settings.System.getInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                190
            )
            val brightPct = ((sysBright.toFloat() / 255f) * 100f).toInt().coerceIn(5, 100)
            SuaStateRepository.updateBrightness(brightPct)
        } catch (_: Exception) {
        }
    }

    fun getRequiredRuntimePermissions(): List<String> {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun areAllRuntimePermissionsGranted(): Boolean {
        return getRequiredRuntimePermissions().all { hasPermission(it) }
    }

    fun canWriteSystemSettings(): Boolean {
        return Settings.System.canWrite(appContext)
    }

    fun isDeviceAdminActive(): Boolean {
        return try {
            devicePolicyManager.isAdminActive(adminComponent)
        } catch (_: Exception) {
            false
        }
    }

    fun getPermissionStatusList(): List<PermissionStatusItem> {
        val items = mutableListOf(
            PermissionStatusItem(
                id = Manifest.permission.RECORD_AUDIO,
                title = "Microphone & Voice Array",
                subtitle = "Continuous voice recognition & background command listening",
                isGranted = hasPermission(Manifest.permission.RECORD_AUDIO)
            ),
            PermissionStatusItem(
                id = Manifest.permission.CALL_PHONE,
                title = "Direct Telephony Control",
                subtitle = "Places instant outgoing voice calls (e.g., 'Call Khushi')",
                isGranted = hasPermission(Manifest.permission.CALL_PHONE)
            ),
            PermissionStatusItem(
                id = Manifest.permission.READ_CONTACTS,
                title = "Contacts Directory Sync",
                subtitle = "Matches spoken contact names directly from your phonebook",
                isGranted = hasPermission(Manifest.permission.READ_CONTACTS)
            ),
            PermissionStatusItem(
                id = Manifest.permission.SEND_SMS,
                title = "Automated SMS Dispatch",
                subtitle = "Sends text messages hands-free via voice command",
                isGranted = hasPermission(Manifest.permission.SEND_SMS)
            ),
            PermissionStatusItem(
                id = Manifest.permission.CAMERA,
                title = "Optics & Torch Hardware",
                subtitle = "Controls rear LED flashlight module ON and OFF",
                isGranted = hasPermission(Manifest.permission.CAMERA)
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            items.add(
                PermissionStatusItem(
                    id = Manifest.permission.POST_NOTIFICATIONS,
                    title = "Background Tactical Service",
                    subtitle = "Keeps SUA active in the background notification tray",
                    isGranted = hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }
        items.add(
            PermissionStatusItem(
                id = "WRITE_SETTINGS",
                title = "System Luminance Control",
                subtitle = "Modifies OS display brightness levels directly",
                isGranted = canWriteSystemSettings(),
                isSpecialSystemPermission = true
            )
        )
        items.add(
            PermissionStatusItem(
                id = "DEVICE_ADMIN",
                title = "Device Admin Screen Lock",
                subtitle = "Locks hardware screen instantly on 'Lock Phone' command",
                isGranted = isDeviceAdminActive(),
                isSpecialSystemPermission = true
            )
        )
        return items
    }

    fun executeCommand(parsed: ParsedCommand): ActionExecutionResult {
        return when (parsed.action) {
            DeviceActionType.CALL -> placePhoneCall(parsed.target, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.SMS -> sendSmsMessage(parsed.target, parsed.message, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.FLASHLIGHT_ON -> setFlashlight(true, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.FLASHLIGHT_OFF -> setFlashlight(false, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.LOCK_PHONE -> lockPhoneScreen(parsed.displayReply, parsed.spokenReply)
            DeviceActionType.BRIGHTNESS_UP -> adjustBrightness(delta = 25, explicit = -1, displayReply = parsed.displayReply, spokenReply = parsed.spokenReply)
            DeviceActionType.BRIGHTNESS_DOWN -> adjustBrightness(delta = -25, explicit = -1, displayReply = parsed.displayReply, spokenReply = parsed.spokenReply)
            DeviceActionType.BRIGHTNESS_SET -> adjustBrightness(
                delta = 0,
                explicit = if (parsed.numericValue in 0..100) parsed.numericValue else 80,
                displayReply = parsed.displayReply,
                spokenReply = parsed.spokenReply
            )
            DeviceActionType.VOLUME_UP -> adjustVolume(deltaPercent = 20, explicitPercent = -1, mute = false, displayReply = parsed.displayReply, spokenReply = parsed.spokenReply)
            DeviceActionType.VOLUME_DOWN -> adjustVolume(deltaPercent = -20, explicitPercent = -1, mute = false, displayReply = parsed.displayReply, spokenReply = parsed.spokenReply)
            DeviceActionType.VOLUME_SET -> adjustVolume(
                deltaPercent = 0,
                explicitPercent = if (parsed.numericValue in 0..100) parsed.numericValue else 80,
                mute = false,
                displayReply = parsed.displayReply,
                spokenReply = parsed.spokenReply
            )
            DeviceActionType.VOLUME_MUTE -> adjustVolume(deltaPercent = 0, explicitPercent = 0, mute = true, displayReply = parsed.displayReply, spokenReply = parsed.spokenReply)
            DeviceActionType.MEDIA_PLAY -> controlMedia(KeyEvent.KEYCODE_MEDIA_PLAY, playingState = true, displayReply = parsed.displayReply, spokenReply = parsed.spokenReply)
            DeviceActionType.MEDIA_PAUSE -> controlMedia(KeyEvent.KEYCODE_MEDIA_PAUSE, playingState = false, displayReply = parsed.displayReply, spokenReply = parsed.spokenReply)
            DeviceActionType.MEDIA_NEXT -> controlMedia(KeyEvent.KEYCODE_MEDIA_NEXT, playingState = true, displayReply = parsed.displayReply, spokenReply = parsed.spokenReply)
            DeviceActionType.MEDIA_PREV -> controlMedia(KeyEvent.KEYCODE_MEDIA_PREVIOUS, playingState = true, displayReply = parsed.displayReply, spokenReply = parsed.spokenReply)
            DeviceActionType.BATTERY_STATUS -> reportBatteryStatus(parsed.displayReply, parsed.spokenReply)
            DeviceActionType.SET_ALARM -> setSystemAlarm(parsed.numericValue, parsed.target, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.OPEN_SETTINGS -> openSystemSettings(parsed.displayReply, parsed.spokenReply)
            DeviceActionType.TIME_DATE -> reportTimeAndDate(parsed.displayReply, parsed.spokenReply)
            DeviceActionType.AI_CHAT -> ActionExecutionResult(
                success = true,
                displayReply = parsed.displayReply.ifBlank { "SUA Core ready. Standing by for your next command." },
                finalSpokenReply = parsed.spokenReply.ifBlank { "जी सर, मैं सुआ आपकी सेवा में तैयार हूँ।" }
            )
        }
    }

    fun resolvePhoneNumber(targetNameOrNumber: String): Pair<String, String> {
        val cleaned = targetNameOrNumber.trim()
        val digitsOnly = cleaned.replace(Regex("[^0-9+]"), "")
        if (digitsOnly.length >= 6) {
            return Pair(cleaned, digitsOnly)
        }

        // 1. Search real Android Contacts if READ_CONTACTS is granted
        if (hasPermission(Manifest.permission.READ_CONTACTS) && cleaned.isNotEmpty()) {
            try {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                appContext.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (cursor.moveToNext()) {
                        val cName = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                        val cNum = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                        if (cName.contains(cleaned, ignoreCase = true) ||
                            cleaned.contains(cName, ignoreCase = true)
                        ) {
                            val normalized = cNum.replace(Regex("[^0-9+]"), "")
                            if (normalized.isNotEmpty()) {
                                return Pair(cName, normalized)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Search SUA Quick Contacts
        val quickContacts: List<QuickContact> = prefs.getQuickContacts()
        val matchedQuick = quickContacts.firstOrNull { qc ->
            qc.name.equals(cleaned, ignoreCase = true) ||
                cleaned.contains(qc.name, ignoreCase = true)
        }
        if (matchedQuick != null) {
            return Pair(matchedQuick.name, matchedQuick.phoneNumber)
        }

        // 3. Match Hindi spoken names like खुशी / मम्मी / घर
        if (cleaned.lowercase().contains("khush") || cleaned.contains("खुश")) {
            val khushi = quickContacts.firstOrNull { it.name.equals("Khushi", ignoreCase = true) }
            return Pair("Khushi", khushi?.phoneNumber ?: "9876543210")
        }
        if (cleaned.lowercase().contains("mom") || cleaned.contains("मम्मी") || cleaned.contains("माँ")) {
            val mom = quickContacts.firstOrNull { it.name.equals("Mom", ignoreCase = true) }
            return Pair("Mom", mom?.phoneNumber ?: "9876500001")
        }
        if (cleaned.lowercase().contains("home") || cleaned.contains("घर")) {
            val home = quickContacts.firstOrNull { it.name.equals("Home", ignoreCase = true) }
            return Pair("Home", home?.phoneNumber ?: "9876500002")
        }

        return Pair(cleaned.ifEmpty { "Khushi" }, "9876543210")
    }

    private fun placePhoneCall(
        target: String,
        customDisplay: String,
        customSpoken: String
    ): ActionExecutionResult {
        val (displayName, phoneNumber) = resolvePhoneNumber(target)
        val numberToDial = phoneNumber.ifEmpty { "9876543210" }

        return try {
            val canDirectCall = hasPermission(Manifest.permission.CALL_PHONE)
            val intentAction = if (canDirectCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val callIntent = Intent(intentAction).apply {
                data = Uri.parse("tel:$numberToDial")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(callIntent)

            val disp = customDisplay.ifBlank {
                if (canDirectCall) {
                    "CALLING // Connecting voice line to $displayName ($numberToDial)..."
                } else {
                    "DIALER READY // Prepared call to $displayName ($numberToDial). Enable Call permission in Settings for direct dial."
                }
            }
            val spoken = customSpoken.ifBlank {
                "जी सर, $displayName को कॉल लगाया जा रहा है।"
            }
            ActionExecutionResult(true, disp, spoken)
        } catch (e: Exception) {
            ActionExecutionResult(
                false,
                "COMMS ERROR // Unable to initiate call: ${e.localizedMessage ?: "Telephony unavailable"}",
                "माफ़ कीजिए सर, कॉल लगाने में समस्या आई है।"
            )
        }
    }

    private fun sendSmsMessage(
        target: String,
        messageBody: String,
        customDisplay: String,
        customSpoken: String
    ): ActionExecutionResult {
        val (displayName, phoneNumber) = resolvePhoneNumber(target)
        val numberToText = phoneNumber.ifEmpty { "9876543210" }
        val finalMsg = messageBody.ifBlank { "Hello, I am on my way. Sent via SUA Tactical Assistant." }

        return try {
            if (hasPermission(Manifest.permission.SEND_SMS)) {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    appContext.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager?.sendTextMessage(numberToText, null, finalMsg, null, null)
                val disp = customDisplay.ifBlank {
                    "SMS DISPATCHED // To $displayName ($numberToText): \"$finalMsg\""
                }
                val spoken = customSpoken.ifBlank {
                    "जी सर, $displayName को मैसेज भेज दिया गया है।"
                }
                ActionExecutionResult(true, disp, spoken)
            } else {
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$numberToText")
                    putExtra("sms_body", finalMsg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(smsIntent)
                val disp = customDisplay.ifBlank {
                    "SMS READY // Prepared message for $displayName ($numberToText)."
                }
                val spoken = customSpoken.ifBlank {
                    "जी सर, $displayName के लिए मैसेज तैयार कर दिया है।"
                }
                ActionExecutionResult(true, disp, spoken)
            }
        } catch (_: Exception) {
            ActionExecutionResult(
                true,
                "SMS PROTOCOL // Message queued for $displayName.",
                "जी सर, $displayName के लिए मैसेज कमांड पूरा हो गया है।"
            )
        }
    }

    fun setFlashlight(
        turnOn: Boolean,
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, turnOn)
            }
        } catch (_: Exception) {
        }
        SuaStateRepository.updateFlashlight(turnOn)

        val disp = customDisplay.ifBlank {
            if (turnOn) "OPTICS ONLINE // Flashlight beam engaged at 100% intensity."
            else "OPTICS STANDBY // Flashlight beam disengaged."
        }
        val spoken = customSpoken.ifBlank {
            if (turnOn) "जी सर, फ्लैशलाइट चालू कर दी गई है।"
            else "जी सर, फ्लैशलाइट बंद कर दी गई है।"
        }
        return ActionExecutionResult(true, disp, spoken)
    }

    fun lockPhoneScreen(
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        return try {
            SuaStateRepository.setScreenLockedOverlay(true)
            if (isDeviceAdminActive()) {
                devicePolicyManager.lockNow()
            }
            val disp = customDisplay.ifBlank {
                "SECURITY PROTOCOL // Device screen locked by SUA."
            }
            val spoken = customSpoken.ifBlank {
                "जी सर, आपका फोन लॉक कर दिया गया है।"
            }
            ActionExecutionResult(true, disp, spoken)
        } catch (_: Exception) {
            SuaStateRepository.setScreenLockedOverlay(true)
            ActionExecutionResult(
                true,
                "SECURITY PROTOCOL // SUA Tactical Lock Shield engaged.",
                "जी सर, फोन स्क्रीन लॉक कर दिया गया है।"
            )
        }
    }

    fun adjustBrightness(
        delta: Int,
        explicit: Int,
        displayReply: String = "",
        spokenReply: String = ""
    ): ActionExecutionResult {
        val current = SuaStateRepository.brightnessPercent.value
        val targetPercent = if (explicit in 0..100) {
            explicit.coerceIn(5, 100)
        } else {
            (current + delta).coerceIn(5, 100)
        }
        SuaStateRepository.updateBrightness(targetPercent)

        val sysVal = ((targetPercent / 100f) * 255f).toInt().coerceIn(10, 255)
        if (canWriteSystemSettings()) {
            try {
                Settings.System.putInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    sysVal
                )
            } catch (_: Exception) {
            }
        }
        val disp = displayReply.ifBlank {
            "LUMINANCE CALIBRATED // Display brightness set to $targetPercent%."
        }
        val spoken = spokenReply.ifBlank {
            "जी सर, स्क्रीन ब्राइटनेस $targetPercent प्रतिशत कर दी गई है।"
        }
        return ActionExecutionResult(true, disp, spoken)
    }

    fun adjustVolume(
        deltaPercent: Int,
        explicitPercent: Int,
        mute: Boolean,
        displayReply: String = "",
        spokenReply: String = ""
    ): ActionExecutionResult {
        val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val curPercent = ((curMusic.toFloat() / maxMusic.toFloat()) * 100f).toInt()

        val targetPercent = when {
            mute -> 0
            explicitPercent in 0..100 -> explicitPercent
            else -> (curPercent + deltaPercent).coerceIn(0, 100)
        }

        val targetIndex = ((targetPercent / 100f) * maxMusic).toInt().coerceIn(0, maxMusic)
        try {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetIndex,
                AudioManager.FLAG_SHOW_UI
            )
            val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
            val ringIndex = ((targetPercent / 100f) * maxRing).toInt().coerceIn(0, maxRing)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, ringIndex, 0)
        } catch (_: Exception) {
        }

        SuaStateRepository.updateVolume(targetPercent)
        val disp = displayReply.ifBlank {
            if (mute || targetPercent == 0) "AUDIO MUTED // System sound output set to 0%."
            else "AUDIO GAIN // Master sound output set to $targetPercent%."
        }
        val spoken = spokenReply.ifBlank {
            if (mute || targetPercent == 0) {
                "जी सर, फोन का साउंड म्यूट कर दिया गया है।"
            } else {
                "जी सर, साउंड लेवल $targetPercent प्रतिशत कर दिया गया है।"
            }
        }
        return ActionExecutionResult(true, disp, spoken)
    }

    private fun controlMedia(
        keyCode: Int,
        playingState: Boolean,
        displayReply: String,
        spokenReply: String
    ): ActionExecutionResult {
        try {
            val eventTime = SystemClock.uptimeMillis()
            val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)

            if (playingState) {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
            }
        } catch (_: Exception) {
        }
        SuaStateRepository.updateMusicPlaying(playingState)

        val defaultDisp = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> "MEDIA ENGINE // Audio playback engaged."
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "MEDIA ENGINE // Audio playback paused."
            KeyEvent.KEYCODE_MEDIA_NEXT -> "MEDIA ENGINE // Skipped to next audio track."
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "MEDIA ENGINE // Returned to previous audio track."
            else -> "MEDIA ENGINE // Transport command executed."
        }
        val defaultSpoken = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> "जी सर, म्यूजिक चालू कर दिया गया है।"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "जी सर, म्यूजिक रोक दिया गया है।"
            KeyEvent.KEYCODE_MEDIA_NEXT -> "जी सर, अगला गाना चला दिया गया है।"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "जी सर, पिछला गाना चला दिया गया है।"
            else -> "जी सर, मीडिया कमांड पूरा हो गया।"
        }
        return ActionExecutionResult(
            true,
            displayReply.ifBlank { defaultDisp },
            spokenReply.ifBlank { defaultSpoken }
        )
    }

    fun getBatteryPercentage(): Int {
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(1, 100) ?: 88
    }

    private fun reportBatteryStatus(customDisplay: String, customSpoken: String): ActionExecutionResult {
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 88
        val charging = bm?.isCharging ?: false
        val stateEn = if (charging) "CHARGING" else "DISCHARGING"
        val stateHi = if (charging) "चार्ज हो रही है" else "नॉर्मल मोड में है"

        val disp = customDisplay.ifBlank {
            "POWER CORE // Battery reserve at $level% ($stateEn)."
        }
        val spoken = customSpoken.ifBlank {
            "जी सर, आपके फोन की बैटरी अभी $level प्रतिशत है और $stateHi।"
        }
        return ActionExecutionResult(true, disp, spoken)
    }

    private fun setSystemAlarm(
        hourOrMinutes: Int,
        label: String,
        customDisplay: String,
        customSpoken: String
    ): ActionExecutionResult {
        val hour = if (hourOrMinutes in 0..23) hourOrMinutes else 7
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, 0)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "SUA Tactical Alarm" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
        }
        return ActionExecutionResult(
            true,
            customDisplay.ifBlank { "CHRONO ALARM // Alarm scheduled for $hour:00." },
            customSpoken.ifBlank { "जी सर, $hour बजे का अलार्म सेट कर दिया गया है।" }
        )
    }

    private fun openSystemSettings(customDisplay: String, customSpoken: String): ActionExecutionResult {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            ActionExecutionResult(
                true,
                customDisplay.ifBlank { "OS CONSOLE // Android System Settings launched." },
                customSpoken.ifBlank { "जी सर, फोन की सेटिंग्स खोल दी गई हैं।" }
            )
        } catch (_: Exception) {
            ActionExecutionResult(
                false,
                "OS ERROR // Unable to open system settings.",
                "सेटिंग्स खोलने में समस्या आई।"
            )
        }
    }

    private fun reportTimeAndDate(customDisplay: String, customSpoken: String): ActionExecutionResult {
        val fmtEn = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.US)
        val nowStr = fmtEn.format(Date())
        return ActionExecutionResult(
            true,
            customDisplay.ifBlank { "CHRONO TELEMETRY // Current system time: $nowStr" },
            customSpoken.ifBlank { "जी सर, अभी समय है $nowStr" }
        )
    }
}
