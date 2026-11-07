package com.example.control

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.os.PowerManager
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.admin.SuaDeviceAdminReceiver
import com.example.data.DeviceActionType
import com.example.data.ParsedCommand
import com.example.data.PermissionStatusItem
import com.example.data.QuickContact
import com.example.data.SuaPreferences
import com.example.data.SuaStateRepository
import com.example.util.HindiTransliterator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActionExecutionResult(
    val success: Boolean,
    val displayReply: String,
    val finalSpokenReply: String
)

data class ContactResolutionResult(
    val found: Boolean,
    val displayName: String,
    val phoneNumber: String,
    val missingContactsPermission: Boolean = false
)

class SuaDeviceController(private val context: Context) {
    private val appContext = context.applicationContext
    private val prefs = SuaPreferences(appContext)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val devicePolicyManager =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(appContext, SuaDeviceAdminReceiver::class.java)

    companion object {
        private const val ACTION_ALERT_CHANNEL_ID = "sua_action_handoff_channel"
        private const val ACTION_ALERT_NOTIF_ID = 2002
    }

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

    fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(appContext.packageName)
        } catch (_: Exception) {
            true
        }
    }

    fun getPermissionStatusList(): List<PermissionStatusItem> {
        val items = mutableListOf(
            PermissionStatusItem(
                id = Manifest.permission.RECORD_AUDIO,
                title = "Microphone & Voice Array",
                subtitle = "Required for voice recognition & background listening",
                isGranted = hasPermission(Manifest.permission.RECORD_AUDIO)
            ),
            PermissionStatusItem(
                id = Manifest.permission.CALL_PHONE,
                title = "Direct Telephony Control",
                subtitle = "Places instant outgoing voice calls without manual dialer tap",
                isGranted = hasPermission(Manifest.permission.CALL_PHONE)
            ),
            PermissionStatusItem(
                id = Manifest.permission.READ_CONTACTS,
                title = "Contacts Directory Sync",
                subtitle = "Matches Hindi & English spoken names from your phonebook",
                isGranted = hasPermission(Manifest.permission.READ_CONTACTS)
            ),
            PermissionStatusItem(
                id = Manifest.permission.SEND_SMS,
                title = "Automated SMS Dispatch",
                subtitle = "Sends SMS text messages directly via SmsManager",
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
                    subtitle = "Displays persistent status & background action alerts",
                    isGranted = hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }
        items.add(
            PermissionStatusItem(
                id = "WRITE_SETTINGS",
                title = "System Luminance Control",
                subtitle = "Required to modify real OS screen brightness levels",
                isGranted = canWriteSystemSettings(),
                isSpecialSystemPermission = true
            )
        )
        items.add(
            PermissionStatusItem(
                id = "DEVICE_ADMIN",
                title = "Device Admin Screen Lock",
                subtitle = "Required to physically lock the phone on 'Lock Phone' command",
                isGranted = isDeviceAdminActive(),
                isSpecialSystemPermission = true
            )
        )
        items.add(
            PermissionStatusItem(
                id = "BATTERY_OPT",
                title = "Unrestricted Background Battery",
                subtitle = "Prevents Android from killing SUA while running in background",
                isGranted = isIgnoringBatteryOptimizations(),
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
            DeviceActionType.SET_ALARM -> setSystemAlarm(
                hour24 = parsed.numericValue,
                minute = parsed.secondaryNumericValue,
                label = parsed.target,
                customDisplay = parsed.displayReply,
                customSpoken = parsed.spokenReply
            )
            DeviceActionType.OPEN_SETTINGS -> openSystemSettings(parsed.displayReply, parsed.spokenReply)
            DeviceActionType.OPEN_APP -> openInstalledApp(parsed.target, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.CUSTOM_CMD -> ActionExecutionResult(
                success = true,
                displayReply = parsed.displayReply.ifBlank { "CUSTOM CMD // Executed '${parsed.target}'" },
                finalSpokenReply = parsed.spokenReply.ifBlank { "जी ${prefs.getUserName()}, आपका कस्टम कमांड पूरा हो गया।" }
            )
            DeviceActionType.TIME_DATE -> reportTimeAndDate(parsed.displayReply, parsed.spokenReply)
            DeviceActionType.MEMORY_SAVE -> ActionExecutionResult(
                success = true,
                displayReply = parsed.displayReply.ifBlank { "MEMORY SAVED // ${parsed.memoryFact}" },
                finalSpokenReply = parsed.spokenReply.ifBlank { "जी ${prefs.getUserName()}, मैंने यह बात अपनी मेमोरी में सेव कर ली है।" }
            )
            DeviceActionType.AI_CHAT -> ActionExecutionResult(
                success = true,
                displayReply = parsed.displayReply.ifBlank { "${prefs.getAssistantName()} is ready for your next command." },
                finalSpokenReply = parsed.spokenReply.ifBlank { "जी ${prefs.getUserName()}, मैं ${prefs.getAssistantName()} आपकी सेवा में तैयार हूँ।" }
            )
        }
    }

    private fun openInstalledApp(
        appNameOrQuery: String,
        customDisplay: String,
        customSpoken: String
    ): ActionExecutionResult {
        val query = appNameOrQuery.trim()
        val lower = query.lowercase()
        val userName = prefs.getUserName()
        val packageManager = appContext.packageManager

        val knownPackages = mapOf(
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "youtube" to listOf("com.google.android.youtube"),
            "instagram" to listOf("com.instagram.android"),
            "chrome" to listOf("com.android.chrome"),
            "gmail" to listOf("com.google.android.gm"),
            "maps" to listOf("com.google.android.apps.maps"),
            "telegram" to listOf("org.telegram.messenger"),
            "facebook" to listOf("com.facebook.katana"),
            "spotify" to listOf("com.spotify.music"),
            "calculator" to listOf("com.google.android.calculator", "com.android.calculator2")
        )

        try {
            if (lower.contains("camera") || lower.contains("कैमरा")) {
                val camIntent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                launchActivityWithBackgroundSupport(camIntent, "Opening Camera", "Tap to open Camera")
                return ActionExecutionResult(
                    true,
                    customDisplay.ifBlank { "APP LAUNCHER // Camera launched." },
                    customSpoken.ifBlank { "जी $userName, कैमरा खोल दिया गया है।" }
                )
            }

            // 1. Check known popular packages first
            for ((key, pkgList) in knownPackages) {
                if (lower.contains(key)) {
                    for (pkg in pkgList) {
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchActivityWithBackgroundSupport(launchIntent, "Opening $query", "Tap to open $query")
                            return ActionExecutionResult(
                                true,
                                customDisplay.ifBlank { "APP LAUNCHER // Opened $query ($pkg)." },
                                customSpoken.ifBlank { "जी $userName, $query खोल दिया गया है।" }
                            )
                        }
                    }
                }
            }

            // 2. Search installed launcher apps by label
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = packageManager.queryIntentActivities(mainIntent, 0)
            val matchedApp = apps.firstOrNull { resolveInfo ->
                val label = resolveInfo.loadLabel(packageManager).toString()
                label.contains(query, ignoreCase = true) ||
                    HindiTransliterator.namesMatch(query, label)
            }
            if (matchedApp != null) {
                val pkg = matchedApp.activityInfo.packageName
                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    val label = matchedApp.loadLabel(packageManager).toString()
                    launchActivityWithBackgroundSupport(launchIntent, "Opening $label", "Tap to open $label")
                    return ActionExecutionResult(
                        true,
                        customDisplay.ifBlank { "APP LAUNCHER // Opened $label." },
                        customSpoken.ifBlank { "जी $userName, $label ऐप खोल दिया गया है।" }
                    )
                }
            }

            // 3. Fallback for web/deep-linkable apps (YouTube, Maps, Browser)
            val webFallbackUri = when {
                lower.contains("youtube") -> Uri.parse("https://www.youtube.com")
                lower.contains("maps") -> Uri.parse("geo:0,0?q=")
                lower.contains("chrome") || lower.contains("browser") -> Uri.parse("https://www.google.com")
                else -> null
            }
            if (webFallbackUri != null) {
                val webIntent = Intent(Intent.ACTION_VIEW, webFallbackUri)
                launchActivityWithBackgroundSupport(webIntent, "Opening $query", "Tap to open $query")
                return ActionExecutionResult(
                    true,
                    customDisplay.ifBlank { "APP LAUNCHER // Opened $query." },
                    customSpoken.ifBlank { "जी $userName, $query खोल दिया गया है।" }
                )
            }
        } catch (e: Exception) {
            return ActionExecutionResult(
                false,
                "APP LAUNCH ERROR // Could not open $query: ${e.localizedMessage}",
                "माफ़ कीजिए $userName, $query खोलने में समस्या आई है।"
            )
        }

        return ActionExecutionResult(
            false,
            "APP NOT INSTALLED // '$query' was not found on this device.",
            "माफ़ कीजिए $userName, '$query' नाम का ऐप आपके फोन में इंस्टॉल नहीं मिला।"
        )
    }

    /**
     * Resolves a contact name (spoken in Hindi Devanagari like "राहुल" or English like "Rahul")
     * or direct phone number. NEVER returns fake fallback phone numbers.
     */
    fun resolvePhoneNumber(targetNameOrNumber: String): ContactResolutionResult {
        val cleaned = targetNameOrNumber.trim()
        if (cleaned.isEmpty()) {
            return ContactResolutionResult(
                found = false,
                displayName = "Unknown",
                phoneNumber = ""
            )
        }

        // Check if the user spoke an actual phone number (at least 7 digits)
        val digitsOnly = cleaned.replace(Regex("[^0-9+]"), "")
        if (digitsOnly.count { it.isDigit() } >= 7) {
            return ContactResolutionResult(
                found = true,
                displayName = digitsOnly,
                phoneNumber = digitsOnly
            )
        }

        // 1. Check user-saved SUA Speed Dial Contacts first (matches both English & Hindi names phonetically)
        val quickContacts: List<QuickContact> = prefs.getQuickContacts()
        val matchedQuick = quickContacts.firstOrNull { qc ->
            HindiTransliterator.namesMatch(cleaned, qc.name) ||
                HindiTransliterator.namesMatch(cleaned, qc.aliasTag)
        }
        if (matchedQuick != null && matchedQuick.phoneNumber.isNotBlank()) {
            return ContactResolutionResult(
                found = true,
                displayName = matchedQuick.name,
                phoneNumber = matchedQuick.phoneNumber
            )
        }

        // 2. Search real Android Contacts using Devanagari-to-Roman phonetic matching
        val hasContactsPerm = hasPermission(Manifest.permission.READ_CONTACTS)
        if (!hasContactsPerm) {
            val romanLabel = HindiTransliterator.toRoman(cleaned).replaceFirstChar { it.uppercase() }
                .ifEmpty { cleaned }
            return ContactResolutionResult(
                found = false,
                displayName = romanLabel,
                phoneNumber = "",
                missingContactsPermission = true
            )
        }

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
                    val cName = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim() ?: "" else ""
                    val cNum = if (numIdx >= 0) cursor.getString(numIdx)?.trim() ?: "" else ""
                    if (cName.isNotEmpty() && cNum.isNotEmpty() &&
                        HindiTransliterator.namesMatch(cleaned, cName)
                    ) {
                        val normalized = cNum.replace(Regex("[^0-9+]"), "")
                        if (normalized.isNotEmpty()) {
                            return ContactResolutionResult(
                                found = true,
                                displayName = cName,
                                phoneNumber = normalized
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        val romanName = HindiTransliterator.toRoman(cleaned).replaceFirstChar { it.uppercase() }
            .ifEmpty { cleaned }
        return ContactResolutionResult(
            found = false,
            displayName = romanName,
            phoneNumber = "",
            missingContactsPermission = false
        )
    }

    private fun launchActivityWithBackgroundSupport(
        intent: Intent,
        notifTitle: String,
        notifBody: String
    ) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val inForeground = SuaStateRepository.isAppInForeground.value
        try {
            appContext.startActivity(intent)
        } catch (e: Exception) {
            throw e
        }

        // On Android 10+, if the service is in the background, also post a Full-Screen / Heads-Up
        // Action Notification so the user can tap it if the OS suppresses background activity starts.
        if (!inForeground) {
            try {
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val ch = NotificationChannel(
                        ACTION_ALERT_CHANNEL_ID,
                        "SUA Background Actions",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                    nm.createNotificationChannel(ch)
                }
                val pi = PendingIntent.getActivity(
                    appContext,
                    System.currentTimeMillis().toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notif = NotificationCompat.Builder(appContext, ACTION_ALERT_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.sym_action_call)
                    .setContentTitle(notifTitle)
                    .setContentText(notifBody)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setFullScreenIntent(pi, true)
                    .build()
                nm.notify(ACTION_ALERT_NOTIF_ID, notif)
            } catch (_: Exception) {
            }
        }
    }

    private fun placePhoneCall(
        target: String,
        customDisplay: String,
        customSpoken: String
    ): ActionExecutionResult {
        val resolution = resolvePhoneNumber(target)
        if (!resolution.found) {
            return if (resolution.missingContactsPermission) {
                ActionExecutionResult(
                    success = false,
                    displayReply = "CONTACTS PERMISSION REQUIRED // Cannot look up '${resolution.displayName}'. Grant Contacts permission in Settings or add their number to Speed Dial.",
                    finalSpokenReply = "माफ़ कीजिए सर, '${resolution.displayName}' का नंबर ढूँढने के लिए कॉन्टैक्ट परमिशन ऑन नहीं है। कृपया सेटिंग्स में परमिशन ऑन करें या स्पीड डायल में नंबर जोड़ें।"
                )
            } else {
                ActionExecutionResult(
                    success = false,
                    displayReply = "CONTACT NOT FOUND // No phone number found for '${resolution.displayName}' in Contacts or Speed Dial. Add their number in Settings -> Speed Dial first.",
                    finalSpokenReply = "माफ़ कीजिए सर, '${resolution.displayName}' नाम का कोई नंबर आपके फोन कॉन्टैक्ट्स या स्पीड डायल में नहीं मिला। कृपया पहले सेटिंग्स में उनका नंबर सेव करें।"
                )
            }
        }

        val displayName = resolution.displayName
        val numberToDial = resolution.phoneNumber

        return try {
            val canDirectCall = hasPermission(Manifest.permission.CALL_PHONE)
            val intentAction = if (canDirectCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val callIntent = Intent(intentAction).apply {
                data = Uri.parse("tel:$numberToDial")
            }
            launchActivityWithBackgroundSupport(
                callIntent,
                "SUA // Calling $displayName",
                "Tap to place call to $displayName ($numberToDial)"
            )

            if (canDirectCall) {
                val disp = customDisplay.ifBlank {
                    "CALLING // Connecting voice call to $displayName ($numberToDial)..."
                }
                val spoken = customSpoken.ifBlank {
                    "जी सर, $displayName को कॉल लगाया जा रहा है।"
                }
                ActionExecutionResult(true, disp, spoken)
            } else {
                ActionExecutionResult(
                    success = false,
                    displayReply = "CALL PERMISSION MISSING // Opened dialer for $displayName ($numberToDial). Grant 'Direct Telephony Control' in Settings for automatic calling.",
                    finalSpokenReply = "कॉल परमिशन ऑन नहीं है सर, इसलिए मैंने $displayName का नंबर डायलर में खोल दिया है।"
                )
            }
        } catch (e: Exception) {
            ActionExecutionResult(
                false,
                "COMMS ERROR // Failed to initiate call to $displayName: ${e.localizedMessage ?: "Telephony unavailable"}",
                "माफ़ कीजिए सर, $displayName को कॉल लगाने में तकनीकी समस्या आई है।"
            )
        }
    }

    private fun sendSmsMessage(
        target: String,
        messageBody: String,
        customDisplay: String,
        customSpoken: String
    ): ActionExecutionResult {
        val resolution = resolvePhoneNumber(target)
        if (!resolution.found) {
            return if (resolution.missingContactsPermission) {
                ActionExecutionResult(
                    success = false,
                    displayReply = "CONTACTS PERMISSION REQUIRED // Cannot send SMS to '${resolution.displayName}' because Contacts permission is denied.",
                    finalSpokenReply = "माफ़ कीजिए सर, कॉन्टैक्ट परमिशन के बिना '${resolution.displayName}' का नंबर नहीं मिल सका।"
                )
            } else {
                ActionExecutionResult(
                    success = false,
                    displayReply = "CONTACT NOT FOUND // Cannot send SMS: '${resolution.displayName}' was not found in Contacts or Speed Dial.",
                    finalSpokenReply = "माफ़ कीजिए सर, '${resolution.displayName}' का नंबर आपके कॉन्टैक्ट्स या स्पीड डायल में नहीं मिला, इसलिए मैसेज नहीं भेजा गया।"
                )
            }
        }

        val displayName = resolution.displayName
        val numberToText = resolution.phoneNumber
        val finalMsg = messageBody.trim()
        if (finalMsg.isEmpty()) {
            return ActionExecutionResult(
                success = false,
                displayReply = "MESSAGE EMPTY // Please specify what message to send to $displayName.",
                finalSpokenReply = "कृपया बताइए कि $displayName को मैसेज में क्या लिखकर भेजना है।"
            )
        }

        return if (hasPermission(Manifest.permission.SEND_SMS)) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    appContext.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                if (smsManager == null) {
                    return ActionExecutionResult(
                        success = false,
                        displayReply = "SMS ERROR // SmsManager unavailable on this device.",
                        finalSpokenReply = "माफ़ कीजिए सर, इस डिवाइस पर एसएमएस सर्विस उपलब्ध नहीं है।"
                    )
                }
                smsManager.sendTextMessage(numberToText, null, finalMsg, null, null)
                val disp = customDisplay.ifBlank {
                    "SMS SENT // Dispatched to $displayName ($numberToText): \"$finalMsg\""
                }
                val spoken = customSpoken.ifBlank {
                    "जी सर, $displayName को मैसेज भेज दिया गया है।"
                }
                ActionExecutionResult(true, disp, spoken)
            } catch (e: Exception) {
                ActionExecutionResult(
                    success = false,
                    displayReply = "SMS FAILED // Could not send SMS to $displayName: ${e.localizedMessage ?: "Telephony error"}",
                    finalSpokenReply = "माफ़ कीजिए सर, $displayName को मैसेज भेजने में एरर आ गया है।"
                )
            }
        } else {
            try {
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$numberToText")
                    putExtra("sms_body", finalMsg)
                }
                launchActivityWithBackgroundSupport(
                    smsIntent,
                    "SUA // Send SMS to $displayName",
                    "Tap to open SMS composer for $displayName"
                )
                ActionExecutionResult(
                    success = false,
                    displayReply = "SMS PERMISSION OFF // Opened SMS app for $displayName ($numberToText). Grant SMS permission in Settings for direct sending.",
                    finalSpokenReply = "एसएमएस परमिशन बंद है सर, इसलिए मैंने $displayName के लिए मैसेज ऐप खोल दिया है। सीधे भेजने के लिए सेटिंग्स में एसएमएस परमिशन ऑन करें।"
                )
            } catch (e: Exception) {
                ActionExecutionResult(
                    success = false,
                    displayReply = "SMS ERROR // Neither SEND_SMS permission nor an SMS app is available: ${e.localizedMessage}",
                    finalSpokenReply = "माफ़ कीजिए सर, एसएमएस परमिशन बंद है और मैसेज ऐप नहीं खुल सका।"
                )
            }
        }
    }

    fun setFlashlight(
        turnOn: Boolean,
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        return try {
            val flashCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (flashCameraId == null) {
                SuaStateRepository.updateFlashlight(false)
                return ActionExecutionResult(
                    success = false,
                    displayReply = "HARDWARE UNAVAILABLE // No physical camera LED flash unit detected on this device.",
                    finalSpokenReply = "माफ़ कीजिए सर, इस डिवाइस में फ्लैशलाइट हार्डवेयर उपलब्ध नहीं है।"
                )
            }

            cameraManager.setTorchMode(flashCameraId, turnOn)
            SuaStateRepository.updateFlashlight(turnOn)

            val disp = customDisplay.ifBlank {
                if (turnOn) "OPTICS ONLINE // Flashlight beam engaged."
                else "OPTICS STANDBY // Flashlight beam disengaged."
            }
            val spoken = customSpoken.ifBlank {
                if (turnOn) "जी सर, फ्लैशलाइट चालू कर दी गई है।"
                else "जी सर, फ्लैशलाइट बंद कर दी गई है।"
            }
            ActionExecutionResult(true, disp, spoken)
        } catch (e: Exception) {
            ActionExecutionResult(
                success = false,
                displayReply = "FLASHLIGHT ERROR // Failed to toggle torch: ${e.localizedMessage ?: "Camera in use or permission missing"}",
                finalSpokenReply = "माफ़ कीजिए सर, फ्लैशलाइट चालू या बंद करने में समस्या आई है।"
            )
        }
    }

    fun lockPhoneScreen(
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        if (!isDeviceAdminActive()) {
            // Do NOT pretend the phone locked! Prompt to enable Device Admin and report honestly.
            try {
                val adminIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Enable SUA Device Admin so voice command 'Lock Phone' can physically lock your screen."
                    )
                }
                launchActivityWithBackgroundSupport(
                    adminIntent,
                    "SUA // Enable Device Admin Lock",
                    "Tap to grant Device Admin permission for screen locking"
                )
            } catch (_: Exception) {
            }
            return ActionExecutionResult(
                success = false,
                displayReply = "DEVICE ADMIN REQUIRED // Hardware screen lock failed because Device Admin is OFF. Please activate 'Device Admin Screen Lock' on the prompt or in Settings.",
                finalSpokenReply = "माफ़ कीजिए सर, फोन लॉक नहीं हो सका क्योंकि डिवाइस एडमिन परमिशन बंद है। कृपया स्क्रीन पर आए पॉपअप या सेटिंग्स में डिवाइस एडमिन को एक्टिवेट करें।"
            )
        }

        return try {
            devicePolicyManager.lockNow()
            val disp = customDisplay.ifBlank {
                "SECURITY PROTOCOL // Hardware device screen locked."
            }
            val spoken = customSpoken.ifBlank {
                "जी सर, आपका फोन लॉक कर दिया गया है।"
            }
            ActionExecutionResult(true, disp, spoken)
        } catch (e: Exception) {
            ActionExecutionResult(
                success = false,
                displayReply = "LOCK FAILED // DevicePolicyManager error: ${e.localizedMessage}",
                finalSpokenReply = "माफ़ कीजिए सर, फोन स्क्रीन लॉक करने में त्रुटि आई है।"
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

        if (!canWriteSystemSettings()) {
            return ActionExecutionResult(
                success = false,
                displayReply = "WRITE_SETTINGS REQUIRED // Only in-app brightness changed to $targetPercent%. Enable 'System Luminance Control' in Settings to modify real phone brightness.",
                finalSpokenReply = "माफ़ कीजिए सर, सिस्टम ब्राइटनेस बदलने की परमिशन बंद है। अभी सिर्फ ऐप की ब्राइटनेस बदली गई है, कृपया सेटिंग्स में जाकर सिस्टम ब्राइटनेस परमिशन ऑन करें।"
            )
        }

        val sysVal = ((targetPercent / 100f) * 255f).toInt().coerceIn(10, 255)
        return try {
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val wrote = Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                sysVal
            )
            if (wrote) {
                val disp = displayReply.ifBlank {
                    "LUMINANCE CALIBRATED // System display brightness set to $targetPercent%."
                }
                val spoken = spokenReply.ifBlank {
                    "जी सर, स्क्रीन ब्राइटनेस $targetPercent प्रतिशत कर दी गई है।"
                }
                ActionExecutionResult(true, disp, spoken)
            } else {
                ActionExecutionResult(
                    success = false,
                    displayReply = "BRIGHTNESS ERROR // OS rejected system brightness update.",
                    finalSpokenReply = "माफ़ कीजिए सर, सिस्टम ब्राइटनेस अपडेट नहीं हो सकी।"
                )
            }
        } catch (e: Exception) {
            ActionExecutionResult(
                success = false,
                displayReply = "BRIGHTNESS ERROR // ${e.localizedMessage}",
                finalSpokenReply = "माफ़ कीजिए सर, ब्राइटनेस बदलने में त्रुटि आई है।"
            )
        }
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
        return try {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetIndex,
                AudioManager.FLAG_SHOW_UI
            )
            try {
                val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
                val ringIndex = ((targetPercent / 100f) * maxRing).toInt().coerceIn(0, maxRing)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, ringIndex, 0)
            } catch (_: SecurityException) {
                // DND policy may block ring stream volume change; music stream still succeeded
            }

            SuaStateRepository.updateVolume(targetPercent)
            val disp = displayReply.ifBlank {
                if (mute || targetPercent == 0) "AUDIO MUTED // System media volume set to 0%."
                else "AUDIO GAIN // Master media volume set to $targetPercent%."
            }
            val spoken = spokenReply.ifBlank {
                if (mute || targetPercent == 0) {
                    "जी सर, फोन का साउंड म्यूट कर दिया गया है।"
                } else {
                    "जी सर, साउंड लेवल $targetPercent प्रतिशत कर दिया गया है।"
                }
            }
            ActionExecutionResult(true, disp, spoken)
        } catch (e: Exception) {
            ActionExecutionResult(
                false,
                "VOLUME ERROR // Failed to change volume: ${e.localizedMessage}",
                "माफ़ कीजिए सर, साउंड लेवल बदलने में समस्या आई है।"
            )
        }
    }

    private fun controlMedia(
        keyCode: Int,
        playingState: Boolean,
        displayReply: String,
        spokenReply: String
    ): ActionExecutionResult {
        return try {
            val eventTime = SystemClock.uptimeMillis()
            val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)

            if (playingState) {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            }
            SuaStateRepository.updateMusicPlaying(playingState)

            val defaultDisp = when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> "MEDIA ENGINE // Dispatched Play command to active media session."
                KeyEvent.KEYCODE_MEDIA_PAUSE -> "MEDIA ENGINE // Dispatched Pause command to active media session."
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
            ActionExecutionResult(
                true,
                displayReply.ifBlank { defaultDisp },
                spokenReply.ifBlank { defaultSpoken }
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                false,
                "MEDIA ERROR // ${e.localizedMessage}",
                "माफ़ कीजिए सर, म्यूजिक कंट्रोल करने में समस्या आई है।"
            )
        }
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
        hour24: Int,
        minute: Int,
        label: String,
        customDisplay: String,
        customSpoken: String
    ): ActionExecutionResult {
        val validHour = if (hour24 in 0..23) hour24 else 7
        val validMin = minute.coerceIn(0, 59)
        val formattedTime = String.format(Locale.US, "%02d:%02d", validHour, validMin)

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, validHour)
                putExtra(AlarmClock.EXTRA_MINUTES, validMin)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "SUA Tactical Alarm" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            launchActivityWithBackgroundSupport(
                intent,
                "SUA // Set Alarm ($formattedTime)",
                "Tap to confirm alarm for $formattedTime"
            )
            ActionExecutionResult(
                true,
                customDisplay.ifBlank { "CHRONO ALARM // Alarm scheduled for $formattedTime (24h)." },
                customSpoken.ifBlank {
                    if (validMin > 0) {
                        "जी सर, $validHour बजकर $validMin मिनट का अलार्म सेट कर दिया गया है।"
                    } else {
                        "जी सर, $validHour बजे का अलार्म सेट कर दिया गया है।"
                    }
                }
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                false,
                "ALARM ERROR // Could not set alarm: ${e.localizedMessage ?: "No Clock app found"}",
                "माफ़ कीजिए सर, अलार्म सेट करने में समस्या आई है।"
            )
        }
    }

    private fun openSystemSettings(customDisplay: String, customSpoken: String): ActionExecutionResult {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            launchActivityWithBackgroundSupport(
                intent,
                "SUA // Open System Settings",
                "Tap to open Android Settings"
            )
            ActionExecutionResult(
                true,
                customDisplay.ifBlank { "OS CONSOLE // Android System Settings launched." },
                customSpoken.ifBlank { "जी सर, फोन की सेटिंग्स खोल दी गई हैं।" }
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                false,
                "OS ERROR // Unable to open system settings: ${e.localizedMessage}",
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
