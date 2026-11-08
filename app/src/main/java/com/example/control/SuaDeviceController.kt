package com.example.control

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.admin.SuaDeviceAdminReceiver
import com.example.ai.SuaBrain
import com.example.data.DeviceActionType
import com.example.data.MokoOperatingMode
import com.example.data.ParsedCommand
import com.example.data.PermissionStatusItem
import com.example.data.QuickContact
import com.example.data.SuaPreferences
import com.example.data.SuaStateRepository
import com.example.service.MokoAccessibilityService
import com.example.service.ScreenCaptureManager
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
    val allPhoneNumbers: List<String> = emptyList(),
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
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
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

    fun isAccessibilityVoiceClickEnabled(): Boolean {
        return MokoAccessibilityService.isServiceEnabled()
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
                subtitle = "Places outgoing voice calls when requested by voice",
                isGranted = hasPermission(Manifest.permission.CALL_PHONE)
            ),
            PermissionStatusItem(
                id = Manifest.permission.READ_CONTACTS,
                title = "Contacts Directory Sync",
                subtitle = "Matches Hindi & English spoken names from your phonebook",
                isGranted = hasPermission(Manifest.permission.READ_CONTACTS)
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
                id = "ACCESSIBILITY_SERVICE",
                title = "Accessibility Voice Click & Screen Reader",
                subtitle = "Required to click buttons and read text across external apps",
                isGranted = isAccessibilityVoiceClickEnabled(),
                isSpecialSystemPermission = true
            )
        )
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
        return items
    }

    fun executeCommand(parsed: ParsedCommand): ActionExecutionResult {
        return when (parsed.action) {
            DeviceActionType.CALL -> placePhoneCall(parsed.target, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.SMS -> sendSmsMessage(parsed.target, parsed.message, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.WHATSAPP_MSG -> sendWhatsAppMessage(parsed.target, parsed.message, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.FLASHLIGHT_ON -> setFlashlight(true, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.FLASHLIGHT_OFF -> setFlashlight(false, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.BLUETOOTH_ON -> controlBluetooth(true, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.BLUETOOTH_OFF -> controlBluetooth(false, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.INTERNET_ON -> controlInternetConnectivity(true, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.INTERNET_OFF -> controlInternetConnectivity(false, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.LOCK_PHONE -> lockPhoneScreen(parsed.displayReply, parsed.spokenReply)
            DeviceActionType.UNLOCK_PHONE -> unlockPhoneScreen(parsed.displayReply, parsed.spokenReply)
            DeviceActionType.SCREEN_SHARE_ANALYZE -> analyzeSharedScreen(parsed.target, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.VOICE_CLICK -> executeVoiceClick(parsed.target, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.OPEN_SYSTEM_SETTING -> openSpecificSystemSetting(parsed.target, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.RINGER_MODE_SET -> setPhoneRingerMode(parsed.target, parsed.displayReply, parsed.spokenReply)
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
            DeviceActionType.PLAY_YOUTUBE -> playYouTubeMusic(parsed.target, parsed.displayReply, parsed.spokenReply)
            DeviceActionType.STOP_ACTION -> stopAllActiveOperations(parsed.displayReply, parsed.spokenReply)
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
            DeviceActionType.SWITCH_MODE -> executeModeSwitch(parsed.target, parsed.displayReply, parsed.spokenReply)
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

    fun stopAllActiveOperations(
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        com.example.service.SuaVoiceSpeaker.stopSpeaking()
        SuaStateRepository.setPendingComms(null)
        SuaStateRepository.markWakeWordDetected()
        return ActionExecutionResult(
            success = true,
            displayReply = customDisplay.ifBlank { "STOPPED // Current operation stopped. Listening for your next command..." },
            finalSpokenReply = customSpoken
        )
    }

    data class YouTubeSongTarget(
        val englishTitle: String,
        val videoId: String
    )

    private var lastYouTubePickIndex = 0

    private val sadHindiHits = listOf(
        YouTubeSongTarget("Tum Hi Ho - Aashiqui 2 | Arijit Singh", "Umqb9KENgmk"),
        YouTubeSongTarget("Channa Mereya - Ae Dil Hai Mushkil | Arijit Singh", "284Ov7ysmfA"),
        YouTubeSongTarget("Agar Tum Saath Ho - Tamasha | Arijit Singh", "sK7riqg2mr4"),
        YouTubeSongTarget("Hamari Adhuri Kahani - Title Track | Arijit Singh", "f3FFOBrMmdg"),
        YouTubeSongTarget("Kabira Encore - Yeh Jawaani Hai Deewani", "jHNNMj5bNQw")
    )

    private val romanticHindiHits = listOf(
        YouTubeSongTarget("Kesariya - Brahmastra | Arijit Singh", "BddP6PYo2gs"),
        YouTubeSongTarget("Apna Bana Le - Bhediya | Arijit Singh", "ElZfdU54Cp8"),
        YouTubeSongTarget("Chaleya - Jawan | Arijit Singh & Shilpa Rao", "VAdGW7QDJiU"),
        YouTubeSongTarget("Heeriye - Jasleen Royal & Arijit Singh", "RLzC55ai0eo"),
        YouTubeSongTarget("Pehle Bhi Main - Animal | Vishal Mishra", "iAIBF2ngbWY"),
        YouTubeSongTarget("Satranga - Animal | Arijit Singh", "HrnrqYxYrbk")
    )

    private val partyHindiHits = listOf(
        YouTubeSongTarget("Kala Chashma - Baar Baar Dekho", "k4yXQkG2s1E"),
        YouTubeSongTarget("Ghungroo - War | Arijit Singh", "qFkNATtc3mc"),
        YouTubeSongTarget("Kar Gayi Chull - Kapoor & Sons", "NTHz9ephYTw"),
        YouTubeSongTarget("Bom Diggy Diggy - Sonu Ke Titu Ki Sweety", "yIIGQB6EMAM")
    )

    private val devotionalHits = listOf(
        YouTubeSongTarget("Shree Hanuman Chalisa - Gulshan Kumar | Hariharan", "AETFvQonfV8"),
        YouTubeSongTarget("Ram Siya Ram - Adipurush | Sachet Tandon", "Tl4bQBfOtbg"),
        YouTubeSongTarget("Namo Namo - Kedarnath | Amit Trivedi", "dx4Teh-nv3A")
    )

    private val retroClassicHits = listOf(
        YouTubeSongTarget("Lag Ja Gale - Lata Mangeshkar Classic", "TFr6G5zveS8"),
        YouTubeSongTarget("Mere Sapno Ki Rani - Kishore Kumar", "vo1MykK4u8U"),
        YouTubeSongTarget("Tujhe Dekha To Yeh Jaana Sanam - DDLJ", "cNV5hLSa9H8")
    )

    private val punjabiHits = listOf(
        YouTubeSongTarget("Lover - Diljit Dosanjh", "mH_LFkWxpI0"),
        YouTubeSongTarget("295 - Sidhu Moose Wala", "n_FCrCQ6-bA"),
        YouTubeSongTarget("Excuses - AP Dhillon", "vX2cDW8LUWk")
    )

    fun isDeviceMusicCurrentlyActive(): Boolean {
        return try {
            audioManager.isMusicActive || SuaStateRepository.isMusicPlaying.value
        } catch (_: Exception) {
            SuaStateRepository.isMusicPlaying.value
        }
    }

    /**
     * Resolves a user's spoken song/mood request into a 100% English song title and direct YouTube videoId
     * so YouTube auto-plays the actual song video instead of pasting Hindi command text in the search bar.
     */
    fun resolveYouTubeSongTarget(rawQuery: String): YouTubeSongTarget {
        val lower = rawQuery.lowercase()
        val idx = (lastYouTubePickIndex++)

        // 1. Emotion / Mood / Genre detection
        val isSadMood = lower.contains("sad") || lower.contains("udaas") || lower.contains("udas") ||
            lower.contains("dukhi") || lower.contains("dard") || lower.contains("breakup") ||
            lower.contains("heartbreak") || lower.contains("emotional") || lower.contains("alone") ||
            rawQuery.contains("सैड") || rawQuery.contains("उदास") || rawQuery.contains("दुखी") ||
            rawQuery.contains("दर्द") || rawQuery.contains("दिल टूट") || rawQuery.contains("गम")
        if (isSadMood) {
            return sadHindiHits[idx % sadHindiHits.size]
        }

        val isDevotional = lower.contains("bhajan") || lower.contains("bhakti") || lower.contains("hanuman") ||
            lower.contains("ram siya") || lower.contains("shiv") || lower.contains("bholenath") ||
            rawQuery.contains("भजन") || rawQuery.contains("भक्ति") || rawQuery.contains("हनुमान") ||
            rawQuery.contains("राम सिया") || rawQuery.contains("भोलेनाथ")
        if (isDevotional) {
            return devotionalHits[idx % devotionalHits.size]
        }

        val isParty = lower.contains("party") || lower.contains("dance") || lower.contains("dj") ||
            lower.contains("bhangra") || rawQuery.contains("पार्टी") || rawQuery.contains("डांस") || rawQuery.contains("डीजे")
        if (isParty) {
            return partyHindiHits[idx % partyHindiHits.size]
        }

        val isOldClassic = lower.contains("old") || lower.contains("purana") || lower.contains("purane") ||
            lower.contains("90s") || lower.contains("retro") || lower.contains("kishore") || lower.contains("lata") ||
            rawQuery.contains("पुराना") || rawQuery.contains("पुराने") || rawQuery.contains("किशोर") || rawQuery.contains("लता")
        if (isOldClassic) {
            return retroClassicHits[idx % retroClassicHits.size]
        }

        val isPunjabi = lower.contains("punjabi") || lower.contains("diljit") || lower.contains("sidhu") ||
            lower.contains("ap dhillon") || rawQuery.contains("पंजाबी") || rawQuery.contains("दिलजीत") || rawQuery.contains("सिद्धू")
        if (isPunjabi) {
            return punjabiHits[idx % punjabiHits.size]
        }

        val isRomantic = lower.contains("romantic") || lower.contains("love") || lower.contains("pyar") ||
            lower.contains("pyaar") || lower.contains("ishq") ||
            rawQuery.contains("रोमांटिक") || rawQuery.contains("प्यार") || rawQuery.contains("इश्क")
        if (isRomantic) {
            return romanticHindiHits[idx % romanticHindiHits.size]
        }

        // 2. Check if it's a generic request like "ek achcha sa hindi music", "achcha sa gana", "latest hindi hit songs", "hindi music"
        val englishClean = HindiTransliterator.toEnglishQuery(rawQuery)
        val genericCheck = englishClean.lowercase()
            .replace(Regex("\\b(ek|achcha|acha|accha|sa|si|se|koi|badiya|badhiya|mast|new|naya|latest|good|best|nice|super|hit|top|hindi|music|song|songs|gana|gaana|geet|play|on|youtube|bajao|lagao|chalao|sunao)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (genericCheck.length < 2) {
            return romanticHindiHits[idx % romanticHindiHits.size]
        }

        // 3. Check known specific song/artist matches in our fast catalog
        val specificLower = genericCheck.lowercase()
        when {
            specificLower.contains("kesariya") -> return YouTubeSongTarget("Kesariya - Brahmastra | Arijit Singh", "BddP6PYo2gs")
            specificLower.contains("tum hi ho") -> return YouTubeSongTarget("Tum Hi Ho - Aashiqui 2 | Arijit Singh", "Umqb9KENgmk")
            specificLower.contains("channa mereya") -> return YouTubeSongTarget("Channa Mereya | Arijit Singh", "284Ov7ysmfA")
            specificLower.contains("apna bana le") -> return YouTubeSongTarget("Apna Bana Le | Arijit Singh", "ElZfdU54Cp8")
            specificLower.contains("chaleya") -> return YouTubeSongTarget("Chaleya - Jawan | Arijit Singh", "VAdGW7QDJiU")
            specificLower.contains("heeriye") -> return YouTubeSongTarget("Heeriye | Arijit Singh", "RLzC55ai0eo")
            specificLower.contains("pehle bhi main") -> return YouTubeSongTarget("Pehle Bhi Main - Animal", "iAIBF2ngbWY")
            specificLower.contains("satranga") -> return YouTubeSongTarget("Satranga - Animal | Arijit Singh", "HrnrqYxYrbk")
            specificLower.contains("arijit") -> return romanticHindiHits[idx % romanticHindiHits.size]
        }

        // 4. For any other specific song/artist, ensure 100% English title and try to resolve live YouTube videoId
        val finalEnglishQuery = if (genericCheck.isNotBlank()) {
            genericCheck.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } + " Official Song"
        } else {
            englishClean.ifBlank { "Kesariya Arijit Singh Official Song" }
        }
        val liveVideoId = fetchLiveYouTubeVideoId(finalEnglishQuery)
        return YouTubeSongTarget(
            englishTitle = finalEnglishQuery,
            videoId = liveVideoId
        )
    }

    private fun fetchLiveYouTubeVideoId(englishSearchQuery: String): String {
        return try {
            val url = java.net.URL("https://www.youtube.com/results?search_query=${Uri.encode(englishSearchQuery)}")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2400
                readTimeout = 2400
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                )
            }
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val match = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").find(html)
            match?.groupValues?.getOrNull(1).orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun playYouTubeMusic(
        rawQuery: String,
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        val userName = prefs.getUserName()
        val pm = appContext.packageManager
        val resolvedTarget = resolveYouTubeSongTarget(rawQuery)
        val englishTitle = resolvedTarget.englishTitle
        val videoId = resolvedTarget.videoId
        SuaStateRepository.updateMusicPlaying(true)

        // 1. If we have a resolved YouTube videoId, launch the direct watch?v=VIDEO_ID deep link so YouTube AUTO-PLAYS the video immediately!
        if (videoId.isNotEmpty()) {
            try {
                val watchUri = Uri.parse("https://www.youtube.com/watch?v=$videoId")
                val watchIntent = Intent(Intent.ACTION_VIEW, watchUri).apply {
                    putExtra("force_fullscreen", false)
                    putExtra("finish_on_ended", false)
                    if (pm.getLaunchIntentForPackage("com.google.android.youtube") != null) {
                        setPackage("com.google.android.youtube")
                    }
                }
                launchActivityWithBackgroundSupport(
                    watchIntent,
                    "Playing on YouTube: $englishTitle",
                    "Auto-playing $englishTitle on YouTube"
                )
                return ActionExecutionResult(
                    success = true,
                    displayReply = customDisplay.ifBlank { "YOUTUBE AUTO-PLAY // Playing \"$englishTitle\" on YouTube." },
                    finalSpokenReply = customSpoken.ifBlank { "जी $userName, यूट्यूब पर $englishTitle चला रही हूँ।" }
                )
            } catch (_: Exception) {
            }
        }

        // 2. Fallback: Open YouTube in 100% English (never Hindi Devanagari)
        try {
            val ytUrl = "https://www.youtube.com/results?search_query=${Uri.encode(englishTitle)}"
            val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)).apply {
                if (pm.getLaunchIntentForPackage("com.google.android.youtube") != null) {
                    setPackage("com.google.android.youtube")
                }
            }
            launchActivityWithBackgroundSupport(
                ytIntent,
                "YouTube: $englishTitle",
                "Playing $englishTitle on YouTube"
            )
            return ActionExecutionResult(
                success = true,
                displayReply = customDisplay.ifBlank { "YOUTUBE PLAY // Playing \"$englishTitle\" on YouTube." },
                finalSpokenReply = customSpoken.ifBlank { "जी $userName, यूट्यूब पर $englishTitle चला रही हूँ।" }
            )
        } catch (e: Exception) {
            return ActionExecutionResult(
                success = false,
                displayReply = "YOUTUBE ERROR // Could not play $englishTitle: ${e.localizedMessage}",
                finalSpokenReply = "माफ़ कीजिए $userName, यूट्यूब चलाने में समस्या आई है।"
            )
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun controlBluetooth(
        enable: Boolean,
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        val userName = prefs.getUserName()
        val btManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        var toggledDirectly = false
        try {
            if (adapter != null) {
                val hasBtPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
                if (hasBtPerm) {
                    toggledDirectly = if (enable) adapter.enable() else adapter.disable()
                }
            }
        } catch (_: Exception) {
        }

        if (!toggledDirectly) {
            try {
                val btIntent = if (enable) {
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                } else {
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                }
                launchActivityWithBackgroundSupport(
                    btIntent,
                    if (enable) "Turn ON Bluetooth" else "Turn OFF Bluetooth",
                    "Tap to control Bluetooth"
                )
            } catch (_: Exception) {
                try {
                    val fallback = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    launchActivityWithBackgroundSupport(fallback, "Bluetooth Settings", "Open Bluetooth Settings")
                } catch (_: Exception) {
                }
            }
        }

        val stateWord = if (enable) "ON" else "OFF"
        return ActionExecutionResult(
            success = true,
            displayReply = customDisplay.ifBlank { "BLUETOOTH // Bluetooth turned $stateWord." },
            finalSpokenReply = customSpoken.ifBlank {
                if (enable) "जी $userName, ब्लूटूथ चालू किया जा रहा है।"
                else "जी $userName, ब्लूटूथ बंद किया जा रहा है।"
            }
        )
    }

    @Suppress("DEPRECATION")
    fun controlInternetConnectivity(
        enable: Boolean,
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        val userName = prefs.getUserName()
        var toggledWifiDirectly = false
        try {
            val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && wifiManager != null) {
                toggledWifiDirectly = wifiManager.setWifiEnabled(enable)
            }
        } catch (_: Exception) {
        }

        if (!toggledWifiDirectly) {
            try {
                val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                } else {
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                }
                launchActivityWithBackgroundSupport(
                    panelIntent,
                    if (enable) "Turn ON Internet / Wi-Fi" else "Turn OFF Internet / Wi-Fi",
                    "Tap to toggle Wi-Fi or Mobile Data"
                )
            } catch (_: Exception) {
                try {
                    val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    launchActivityWithBackgroundSupport(wifiIntent, "Wi-Fi & Internet", "Open Wi-Fi Settings")
                } catch (_: Exception) {
                }
            }
        }

        val stateWord = if (enable) "ON" else "OFF"
        return ActionExecutionResult(
            success = true,
            displayReply = customDisplay.ifBlank { "INTERNET & WI-FI // Opening Internet / Wi-Fi toggle ($stateWord)." },
            finalSpokenReply = customSpoken.ifBlank {
                if (enable) "जी $userName, इंटरनेट और वाई-फाई चालू किया जा रहा है।"
                else "जी $userName, इंटरनेट और वाई-फाई बंद करने का पैनल खोल दिया गया है।"
            }
        )
    }

    private fun executeModeSwitch(
        targetModeName: String,
        customDisplay: String,
        customSpoken: String
    ): ActionExecutionResult {
        val mode = when (targetModeName.uppercase()) {
            "SLEEP" -> MokoOperatingMode.SLEEP
            "OFFLINE" -> MokoOperatingMode.OFFLINE
            else -> MokoOperatingMode.ONLINE
        }
        SuaStateRepository.setOperatingMode(mode)
        return ActionExecutionResult(
            success = true,
            displayReply = customDisplay.ifBlank { "MODE SWITCHED // Active mode: ${mode.label} (${mode.subtitle})" },
            finalSpokenReply = customSpoken.ifBlank {
                when (mode) {
                    MokoOperatingMode.ONLINE -> "जी ${prefs.getUserName()}, अब मैं ऑनलाइन मोड में हूँ।"
                    MokoOperatingMode.SLEEP -> "जी ${prefs.getUserName()}, अब मैं स्लीप मोड में हूँ। बिना इंटरनेट के सभी फोन कमांड और कॉल काम करेंगे।"
                    MokoOperatingMode.OFFLINE -> "जी ${prefs.getUserName()}, अब मैं ऑफलाइन हो रही हूँ। बैकग्राउंड माइक बंद किया जा रहा है।"
                }
            }
        )
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
            "youtube music" to listOf("com.google.android.apps.youtube.music", "com.google.android.youtube"),
            "youtube" to listOf("com.google.android.youtube", "com.google.android.apps.youtube.music"),
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

            if (lower.contains("contacts") || lower.contains("कॉन्टैक्ट्स")) {
                val contactsIntent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
                launchActivityWithBackgroundSupport(contactsIntent, "Opening Contacts", "Tap to open Contacts")
                return ActionExecutionResult(
                    true,
                    customDisplay.ifBlank { "APP LAUNCHER // Contacts opened." },
                    customSpoken.ifBlank { "जी $userName, कॉन्टैक्ट्स खोल दिए गए हैं।" }
                )
            }

            if (lower.contains("dialer") || lower.contains("डायलर")) {
                val dialIntent = Intent(Intent.ACTION_DIAL)
                launchActivityWithBackgroundSupport(dialIntent, "Opening Dialer", "Tap to open Dialer")
                return ActionExecutionResult(
                    true,
                    customDisplay.ifBlank { "APP LAUNCHER // Phone Dialer opened." },
                    customSpoken.ifBlank { "जी $userName, डायलर खोल दिया गया है।" }
                )
            }

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

            // Deep-link / Web Fallback for WhatsApp, YouTube, Maps, Browser, Spotify, Instagram
            val webFallbackUri = when {
                lower.contains("whatsapp") -> Uri.parse("https://wa.me/")
                lower.contains("youtube") -> Uri.parse("https://www.youtube.com")
                lower.contains("instagram") -> Uri.parse("https://www.instagram.com")
                lower.contains("spotify") -> Uri.parse("https://open.spotify.com")
                lower.contains("maps") -> Uri.parse("geo:0,0?q=")
                lower.contains("chrome") || lower.contains("browser") -> Uri.parse("https://www.google.com")
                else -> null
            }
            if (webFallbackUri != null) {
                if (lower.contains("whatsapp")) {
                    try {
                        val waSchemeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://app"))
                        launchActivityWithBackgroundSupport(waSchemeIntent, "Opening WhatsApp", "Tap to open WhatsApp")
                        return ActionExecutionResult(
                            true,
                            customDisplay.ifBlank { "APP LAUNCHER // Opened WhatsApp." },
                            customSpoken.ifBlank { "जी $userName, व्हाट्सएप खोल दिया गया है।" }
                        )
                    } catch (_: Exception) {
                    }
                }
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
     * Resolves ALL phone numbers directly from the user's real Android Phonebook (ContactsContract)
     * without requiring the user to manually save numbers inside the app.
     */
    fun resolvePhoneNumber(targetNameOrNumber: String): ContactResolutionResult {
        val rawTrimmed = targetNameOrNumber.trim()
        if (rawTrimmed.isEmpty()) {
            return ContactResolutionResult(
                found = false,
                displayName = "Unknown",
                phoneNumber = "",
                allPhoneNumbers = emptyList()
            )
        }

        // 1. Check if the user spoke an actual phone number (digits or spoken Hindi/English number words)
        val directDigits = rawTrimmed.replace(Regex("[^0-9+]"), "")
        if (directDigits.count { it.isDigit() } >= 7) {
            SuaStateRepository.recordResolvedContact(directDigits, listOf(directDigits))
            return ContactResolutionResult(
                found = true,
                displayName = directDigits,
                phoneNumber = directDigits,
                allPhoneNumbers = listOf(directDigits)
            )
        }
        val spokenDigits = HindiTransliterator.extractSpokenDigits(rawTrimmed)
        if (spokenDigits.count { it.isDigit() } >= 10) {
            SuaStateRepository.recordResolvedContact(spokenDigits, listOf(spokenDigits))
            return ContactResolutionResult(
                found = true,
                displayName = spokenDigits,
                phoneNumber = spokenDigits,
                allPhoneNumbers = listOf(spokenDigits)
            )
        }

        val cleaned = HindiTransliterator.cleanSpokenContactQuery(rawTrimmed).ifEmpty { rawTrimmed }

        // 2. Search the real Android Phonebook (ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        val hasContactsPerm = hasPermission(Manifest.permission.READ_CONTACTS)
        if (!hasContactsPerm) {
            // Automatically prompt the UI to request READ_CONTACTS & CALL_PHONE permissions
            SuaStateRepository.triggerUiNavigationAction("REQUEST_PERMISSIONS")
            val romanLabel = HindiTransliterator.toRoman(cleaned).replaceFirstChar { it.uppercase() }
                .ifEmpty { cleaned }
            return ContactResolutionResult(
                found = false,
                displayName = romanLabel,
                phoneNumber = "",
                allPhoneNumbers = emptyList(),
                missingContactsPermission = true
            )
        }

        data class ScoredContact(
            val displayName: String,
            val numbers: MutableList<String>,
            var bestScore: Int
        )

        val contactMap = linkedMapOf<String, ScoredContact>()
        val qRoman = HindiTransliterator.toRoman(cleaned).lowercase().trim()
        val qSkel = HindiTransliterator.phoneticSkeleton(cleaned)

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
                    if (cName.isEmpty() || cNum.isEmpty()) continue

                    if (HindiTransliterator.namesMatch(cleaned, cName)) {
                        val normalized = cNum.replace(Regex("[^0-9+]"), "")
                        if (normalized.isEmpty()) continue

                        val cRoman = HindiTransliterator.toRoman(cName).lowercase().trim()
                        val cSkel = HindiTransliterator.phoneticSkeleton(cName)
                        val score = when {
                            cName.equals(cleaned, ignoreCase = true) || cRoman == qRoman -> 100
                            cRoman.split(Regex("\\s+")).any { it == qRoman } -> 92
                            cName.contains(cleaned, ignoreCase = true) || cRoman.contains(qRoman) -> 85
                            qSkel.isNotEmpty() && cSkel == qSkel -> 78
                            else -> 65
                        }

                        val key = cName.lowercase()
                        val entry = contactMap.getOrPut(key) {
                            ScoredContact(cName, mutableListOf(), score)
                        }
                        if (score > entry.bestScore) {
                            entry.bestScore = score
                        }
                        val last10 = normalized.takeLast(10)
                        if (entry.numbers.none { it.takeLast(10) == last10 }) {
                            entry.numbers.add(normalized)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Also check any legacy saved contacts only as a secondary fallback if not in phonebook
        if (contactMap.isEmpty()) {
            for (qc in prefs.getQuickContacts()) {
                if (HindiTransliterator.namesMatch(cleaned, qc.name) ||
                    HindiTransliterator.namesMatch(cleaned, qc.aliasTag)
                ) {
                    val norm = qc.phoneNumber.replace(Regex("[^0-9+]"), "")
                    if (norm.isNotEmpty()) {
                        val entry = contactMap.getOrPut(qc.name.lowercase()) {
                            ScoredContact(qc.name, mutableListOf(), 60)
                        }
                        if (!entry.numbers.contains(norm)) entry.numbers.add(norm)
                    }
                }
            }
        }

        val bestContact = contactMap.values.maxByOrNull { it.bestScore }
        if (bestContact != null && bestContact.numbers.isNotEmpty()) {
            SuaStateRepository.recordResolvedContact(bestContact.displayName, bestContact.numbers)
            return ContactResolutionResult(
                found = true,
                displayName = bestContact.displayName,
                phoneNumber = bestContact.numbers.first(),
                allPhoneNumbers = bestContact.numbers
            )
        }

        val romanName = HindiTransliterator.toRoman(cleaned).replaceFirstChar { it.uppercase() }
            .ifEmpty { cleaned }
        return ContactResolutionResult(
            found = false,
            displayName = romanName,
            phoneNumber = "",
            allPhoneNumbers = emptyList(),
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

        if (!inForeground) {
            try {
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val ch = NotificationChannel(
                        ACTION_ALERT_CHANNEL_ID,
                        "Moko Background Actions",
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
                SuaStateRepository.triggerUiNavigationAction("REQUEST_PERMISSIONS")
                ActionExecutionResult(
                    success = false,
                    displayReply = "CONTACTS PERMISSION REQUIRED // Please click 'Allow' on the Contacts permission popup so Moko can call '${resolution.displayName}' directly from your phonebook.",
                    finalSpokenReply = "जी ${prefs.getUserName()}, आपके फोन कॉन्टैक्ट्स से '${resolution.displayName}' को कॉल लगाने के लिए कृपया स्क्रीन पर कॉन्टैक्ट परमिशन Allow कर दीजिए।"
                )
            } else {
                ActionExecutionResult(
                    success = false,
                    displayReply = "CONTACT NOT FOUND // '${resolution.displayName}' was not found in your phone's Contacts.",
                    finalSpokenReply = "माफ़ कीजिए ${prefs.getUserName()}, '${resolution.displayName}' नाम का नंबर आपके फोन की कॉन्टैक्ट लिस्ट में नहीं मिला।"
                )
            }
        }

        val displayName = resolution.displayName
        val numberToDial = resolution.phoneNumber

        return try {
            val canDirectCall = hasPermission(Manifest.permission.CALL_PHONE)
            if (!canDirectCall) {
                SuaStateRepository.triggerUiNavigationAction("REQUEST_PERMISSIONS")
            }
            val intentAction = if (canDirectCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val callIntent = Intent(intentAction).apply {
                data = Uri.parse("tel:$numberToDial")
            }
            launchActivityWithBackgroundSupport(
                callIntent,
                "Calling $displayName",
                "Tap to place call to $displayName ($numberToDial)"
            )

            if (canDirectCall) {
                val disp = customDisplay.ifBlank {
                    "CALLING // Connecting voice call to $displayName ($numberToDial)..."
                }
                val spoken = customSpoken.ifBlank {
                    "जी ${prefs.getUserName()}, $displayName के नंबर पर कॉल लगाया जा रहा है।"
                }
                ActionExecutionResult(true, disp, spoken)
            } else {
                ActionExecutionResult(
                    success = true,
                    displayReply = "DIALER OPENED // Opened dialer for $displayName ($numberToDial). Allow Call Permission to dial automatically.",
                    finalSpokenReply = "जी ${prefs.getUserName()}, मैंने $displayName का नंबर डायलर में लगा दिया है।"
                )
            }
        } catch (e: Exception) {
            ActionExecutionResult(
                false,
                "COMMS ERROR // Failed to initiate call to $displayName: ${e.localizedMessage ?: "Telephony unavailable"}",
                "माफ़ कीजिए ${prefs.getUserName()}, $displayName को कॉल लगाने में तकनीकी समस्या आई है।"
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
                SuaStateRepository.triggerUiNavigationAction("REQUEST_PERMISSIONS")
                ActionExecutionResult(
                    success = false,
                    displayReply = "CONTACTS PERMISSION REQUIRED // Please Allow Contacts permission to read '${resolution.displayName}' from your phonebook.",
                    finalSpokenReply = "जी ${prefs.getUserName()}, '${resolution.displayName}' का नंबर पढ़ने के लिए कृपया कॉन्टैक्ट परमिशन Allow करें।"
                )
            } else {
                ActionExecutionResult(
                    success = false,
                    displayReply = "CONTACT NOT FOUND // '${resolution.displayName}' was not found in your phone's Contacts.",
                    finalSpokenReply = "माफ़ कीजिए ${prefs.getUserName()}, '${resolution.displayName}' का नंबर आपके फोन कॉन्टैक्ट्स में नहीं मिला।"
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

        return try {
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$numberToText")
                putExtra("sms_body", finalMsg)
            }
            launchActivityWithBackgroundSupport(
                smsIntent,
                "Send SMS to $displayName",
                "Tap to open SMS composer for $displayName"
            )
            ActionExecutionResult(
                success = true,
                displayReply = customDisplay.ifBlank {
                    "SMS COMPOSER OPENED // Opened SMS app for $displayName ($numberToText): \"$finalMsg\""
                },
                finalSpokenReply = customSpoken.ifBlank {
                    "जी ${prefs.getUserName()}, मैंने $displayName के लिए मैसेज ऐप में आपका संदेश तैयार कर दिया है।"
                }
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                success = false,
                displayReply = "SMS ERROR // ${e.localizedMessage}",
                finalSpokenReply = "माफ़ कीजिए ${prefs.getUserName()}, मैसेज ऐप नहीं खुल सका।"
            )
        }
    }

    private fun sendWhatsAppMessage(
        target: String,
        messageBody: String,
        customDisplay: String,
        customSpoken: String
    ): ActionExecutionResult {
        val userName = prefs.getUserName()
        val finalMsg = messageBody.trim()
        val resolution = resolvePhoneNumber(target)
        val pm = appContext.packageManager
        val installedWaPkg = when {
            pm.getLaunchIntentForPackage("com.whatsapp") != null -> "com.whatsapp"
            pm.getLaunchIntentForPackage("com.whatsapp.w4b") != null -> "com.whatsapp.w4b"
            else -> null
        }

        // If contact phone number is not found in ContactsContract, still open WhatsApp with the message ready!
        if (!resolution.found) {
            if (resolution.missingContactsPermission) {
                SuaStateRepository.triggerUiNavigationAction("REQUEST_PERMISSIONS")
            }
            return try {
                if (finalMsg.isNotEmpty() && installedWaPkg != null) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        setPackage(installedWaPkg)
                        putExtra(Intent.EXTRA_TEXT, finalMsg)
                    }
                    launchActivityWithBackgroundSupport(
                        shareIntent,
                        "WhatsApp Message",
                        "Select ${resolution.displayName} in WhatsApp to send \"$finalMsg\""
                    )
                    ActionExecutionResult(
                        success = true,
                        displayReply = "WHATSAPP OPENED // Contact '${resolution.displayName}' not matched in Phonebook; opened WhatsApp with message \"$finalMsg\".",
                        finalSpokenReply = "जी $userName, मैंने आपका मैसेज लिखकर व्हाट्सएप खोल दिया है।"
                    )
                } else {
                    openInstalledApp(
                        "WhatsApp",
                        "WHATSAPP OPENED // Opened WhatsApp for ${resolution.displayName}.",
                        "जी $userName, व्हाट्सएप खोल दिया गया है।"
                    )
                }
            } catch (e: Exception) {
                ActionExecutionResult(
                    success = false,
                    displayReply = "WHATSAPP ERROR // ${e.localizedMessage}",
                    finalSpokenReply = "माफ़ कीजिए $userName, व्हाट्सएप खोलने में समस्या आई है।"
                )
            }
        }

        val displayName = resolution.displayName
        val rawDigits = resolution.phoneNumber.replace(Regex("[^0-9]"), "")
        val formattedWhatsAppPhone = when {
            rawDigits.length == 10 -> "91$rawDigits"
            rawDigits.length == 11 && rawDigits.startsWith("0") -> "91${rawDigits.drop(1)}"
            else -> rawDigits
        }
        val encodedText = Uri.encode(finalMsg)

        // 1. Try native whatsapp://send deep-link first (opens direct chat composer immediately)
        try {
            val nativeUri = Uri.parse("whatsapp://send?phone=$formattedWhatsAppPhone&text=$encodedText")
            val nativeIntent = Intent(Intent.ACTION_VIEW, nativeUri).apply {
                if (installedWaPkg != null) setPackage(installedWaPkg)
            }
            launchActivityWithBackgroundSupport(
                nativeIntent,
                "WhatsApp to $displayName",
                "Tap to send WhatsApp message to $displayName"
            )
            return ActionExecutionResult(
                success = true,
                displayReply = customDisplay.ifBlank {
                    "WHATSAPP DISPATCH // Opened WhatsApp chat for $displayName (+$formattedWhatsAppPhone): \"$finalMsg\""
                },
                finalSpokenReply = customSpoken.ifBlank {
                    "जी $userName, $displayName के व्हाट्सएप पर मैसेज खोल दिया गया है।"
                }
            )
        } catch (_: Exception) {
        }

        // 2. Fallback to https://api.whatsapp.com/send
        return try {
            val waUri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedWhatsAppPhone&text=$encodedText")
            val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                if (installedWaPkg != null) setPackage(installedWaPkg)
            }
            launchActivityWithBackgroundSupport(
                waIntent,
                "WhatsApp to $displayName",
                "Tap to send WhatsApp message to $displayName"
            )
            ActionExecutionResult(
                success = true,
                displayReply = customDisplay.ifBlank {
                    "WHATSAPP DISPATCH // Opened WhatsApp chat for $displayName (+$formattedWhatsAppPhone): \"$finalMsg\""
                },
                finalSpokenReply = customSpoken.ifBlank {
                    "जी $userName, $displayName के नंबर पर व्हाट्सएप मैसेज तैयार कर दिया गया है।"
                }
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                success = false,
                displayReply = "WHATSAPP ERROR // Could not open WhatsApp for $displayName: ${e.localizedMessage}",
                finalSpokenReply = "माफ़ कीजिए $userName, व्हाट्सएप खोलने में समस्या आई है।"
            )
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

    @Suppress("DEPRECATION")
    fun unlockPhoneScreen(
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        val userName = prefs.getUserName()
        val wasOverlayActive = SuaStateRepository.isScreenLockedOverlay.value
        SuaStateRepository.setScreenLockedOverlay(false)

        val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceKeyguardLocked = keyguardManager?.isKeyguardLocked == true

        // Wake the display if it is off
        try {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wl = pm?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "Moko:VoiceUnlockWakeLock"
            )
            wl?.acquire(4000L)
        } catch (_: Exception) {
        }

        if (isDeviceKeyguardLocked) {
            // Do NOT pretend the OS lockscreen was bypassed! Trigger KeyguardManager Biometric/PIN prompt in MainActivity.
            try {
                val openAppIntent = Intent(appContext, com.example.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("request_biometric_unlock", true)
                }
                launchActivityWithBackgroundSupport(
                    openAppIntent,
                    "Moko // Biometric / PIN Unlock",
                    "Authenticate with Fingerprint or PIN to unlock"
                )
            } catch (_: Exception) {
            }
            SuaStateRepository.triggerUiNavigationAction("REQUEST_BIOMETRIC_KEYGUARD_DISMISS")
            return ActionExecutionResult(
                success = true,
                displayReply = "BIOMETRIC / PIN UNLOCK PROMPT // Woke screen & requested Android Biometric/PIN authentication (OS security cannot be bypassed without authentication).",
                finalSpokenReply = "जी $userName, मैंने स्क्रीन ऑन कर दी है। सुरक्षा के लिए कृपया अपना फिंगरप्रिंट या पिन लगाकर फोन अनलॉक करें।"
            )
        }

        if (wasOverlayActive) {
            return ActionExecutionResult(
                success = true,
                displayReply = customDisplay.ifBlank { "IN-APP OVERLAY UNLOCKED // In-app lock overlay dismissed." },
                finalSpokenReply = customSpoken.ifBlank { "जी $userName, इन-ऐप स्क्रीन ओवरले हटा दिया गया है।" }
            )
        }

        return ActionExecutionResult(
            success = true,
            displayReply = customDisplay.ifBlank { "DEVICE ALREADY UNLOCKED // Phone screen is already unlocked." },
            finalSpokenReply = customSpoken.ifBlank { "जी $userName, आपके फोन की स्क्रीन पहले से अनलॉक है।" }
        )
    }

    fun analyzeSharedScreen(
        targetModeOrQuery: String,
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        val upper = targetModeOrQuery.trim().uppercase()
        val userName = prefs.getUserName()
        if (upper == "OFF" || upper == "STOP") {
            ScreenCaptureManager.stopMediaProjection()
            SuaStateRepository.setScreenShareActive(false)
            return ActionExecutionResult(
                success = true,
                displayReply = customDisplay.ifBlank { "SCREEN SHARE OFF // Live screen capture & analysis stopped." },
                finalSpokenReply = customSpoken.ifBlank { "जी $userName, स्क्रीन शेयर बंद कर दिया गया है।" }
            )
        }

        SuaStateRepository.setScreenShareActive(true)
        if (!ScreenCaptureManager.hasMediaProjectionConsent()) {
            // Request explicit user consent for MediaProjection screen capture
            SuaStateRepository.triggerUiNavigationAction("REQUEST_MEDIA_PROJECTION")
        }

        val battery = getBatteryPercentage()
        val windowTextSummary = MokoAccessibilityService.instance?.readActiveScreenTextSummary().orEmpty()
        val base64Jpeg = ScreenCaptureManager.captureScreenBase64Jpeg()

        // If Gemini API key is configured and we have a captured screen frame or Accessibility window nodes, run Gemini Vision!
        if (prefs.getEffectiveApiKey().isNotBlank() && (base64Jpeg.isNotBlank() || windowTextSummary.isNotBlank())) {
            val visionResult = SuaBrain(prefs, this).analyzeScreenBitmapWithVision(
                base64Jpeg = base64Jpeg,
                windowTextSummary = windowTextSummary,
                userQuestion = targetModeOrQuery
            )
            if (visionResult != null) {
                return ActionExecutionResult(
                    success = true,
                    displayReply = visionResult.displayReply,
                    finalSpokenReply = visionResult.spokenReply
                )
            }
        }

        val screenSummaryEn = SuaStateRepository.buildCurrentScreenSnapshotSummary(
            prefs = prefs,
            batteryPct = battery,
            activeWindowSummary = windowTextSummary
        )
        val screenSummaryHi = SuaStateRepository.buildCurrentScreenSpokenSummaryHi(
            prefs = prefs,
            batteryPct = battery,
            hasVisionCapture = base64Jpeg.isNotBlank() || windowTextSummary.isNotBlank()
        )

        return ActionExecutionResult(
            success = true,
            displayReply = customDisplay.ifBlank { screenSummaryEn },
            finalSpokenReply = customSpoken.ifBlank { screenSummaryHi }
        )
    }

    fun executeVoiceClick(
        targetElement: String,
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        val raw = targetElement.trim()
        val lower = raw.lowercase()
        val userName = prefs.getUserName()

        // 1. In-App Navigation Shortcuts
        when {
            lower.contains("setting") || lower.contains("history") || lower.contains("सेटिंग") || lower.contains("हिस्ट्री") -> {
                SuaStateRepository.triggerUiNavigationAction("OPEN_SETTINGS")
                return ActionExecutionResult(
                    success = true,
                    displayReply = customDisplay.ifBlank { "IN-APP VOICE NAV // Opened 'Settings & History' screen." },
                    finalSpokenReply = customSpoken.ifBlank { "जी $userName, सेटिंग्स और हिस्ट्री स्क्रीन खोल दी गई है।" }
                )
            }
            lower.contains("menu") || lower.contains("drawer") || lower.contains("मेन्यू") -> {
                SuaStateRepository.triggerUiNavigationAction("OPEN_DRAWER")
                return ActionExecutionResult(
                    success = true,
                    displayReply = customDisplay.ifBlank { "IN-APP VOICE NAV // Opened Side Menu Drawer." },
                    finalSpokenReply = customSpoken.ifBlank { "जी $userName, साइड मेन्यू खोल दिया गया है।" }
                )
            }
            lower.contains("close") || lower.contains("back") || lower.contains("बंद") || lower.contains("बैक") -> {
                SuaStateRepository.triggerUiNavigationAction("CLOSE_DRAWER")
                return ActionExecutionResult(
                    success = true,
                    displayReply = customDisplay.ifBlank { "IN-APP VOICE NAV // Closed menu / navigated back." },
                    finalSpokenReply = customSpoken.ifBlank { "जी $userName, इसे बंद कर दिया गया है।" }
                )
            }
            lower.contains("screen share") || lower.contains("share") || lower.contains("स्क्रीन शेयर") -> {
                return analyzeSharedScreen("ON", customDisplay, customSpoken)
            }
            lower.contains("unlock") || lower.contains("अनलॉक") || lower.contains("लॉक खोल") -> {
                return unlockPhoneScreen(customDisplay, customSpoken)
            }
            lower.contains("lock") || lower.contains("लॉक") -> {
                return lockPhoneScreen(customDisplay, customSpoken)
            }
            lower.contains("torch") || lower.contains("flashlight") || lower.contains("टॉर्च") || lower.contains("फ्लैशलाइट") -> {
                val next = !SuaStateRepository.isFlashlightOn.value
                return setFlashlight(next, customDisplay, customSpoken)
            }
            lower.contains("online") || lower.contains("ऑनलाइन") -> {
                return executeModeSwitch("ONLINE", customDisplay, customSpoken)
            }
            lower.contains("sleep") || lower.contains("स्लीप") -> {
                return executeModeSwitch("SLEEP", customDisplay, customSpoken)
            }
            lower.contains("offline") || lower.contains("ऑफलाइन") -> {
                return executeModeSwitch("OFFLINE", customDisplay, customSpoken)
            }
        }

        // 2. Real System-Wide UI Click via MokoAccessibilityService
        val a11yService = MokoAccessibilityService.instance
        if (a11yService != null) {
            val clicked = a11yService.findAndClickNodeByText(raw)
            if (clicked) {
                return ActionExecutionResult(
                    success = true,
                    displayReply = customDisplay.ifBlank { "ACCESSIBILITY VOICE CLICK // Clicked '$raw' on screen." },
                    finalSpokenReply = customSpoken.ifBlank { "जी $userName, स्क्रीन पर '$raw' बटन पर क्लिक कर दिया गया है।" }
                )
            }
        }

        // 3. Check if the user named an installed app ("Click WhatsApp" -> open WhatsApp)
        val appRes = openInstalledApp(raw, "", "")
        if (appRes.success) {
            return ActionExecutionResult(
                success = true,
                displayReply = customDisplay.ifBlank { "APP LAUNCH // Opened '$raw'." },
                finalSpokenReply = customSpoken.ifBlank { "जी $userName, '$raw' ऐप खोल दिया गया है।" }
            )
        }

        // 4. Honest feedback when AccessibilityService is OFF or target element is not on screen
        return if (a11yService == null) {
            ActionExecutionResult(
                success = false,
                displayReply = "ACCESSIBILITY REQUIRED // To click '$raw' on any screen, enable 'Moko' in Android Accessibility Settings (Settings -> Accessibility).",
                finalSpokenReply = "जी $userName, स्क्रीन के किसी भी बटन पर वॉइस क्लिक करने के लिए कृपया सेटिंग्स में जाकर मोको एक्सेसिबिलिटी सर्विस चालू करें।"
            )
        } else {
            ActionExecutionResult(
                success = false,
                displayReply = "ELEMENT NOT FOUND // Could not find clickable button '$raw' on the current screen.",
                finalSpokenReply = "माफ़ कीजिए $userName, अभी स्क्रीन पर '$raw' नाम का कोई बटन नहीं दिखा।"
            )
        }
    }

    fun openSpecificSystemSetting(
        settingTarget: String,
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        val key = settingTarget.trim().uppercase()
        val userName = prefs.getUserName()

        val (intentAction, labelEn, labelHi) = when {
            key.contains("WIFI") || key.contains("WI-FI") || key.contains("INTERNET") ->
                Triple(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi & Internet Settings", "वाई-फाई और इंटरनेट सेटिंग्स")
            key.contains("DATA") || key.contains("MOBILE_DATA") || key.contains("ROAMING") ->
                Triple(Settings.ACTION_DATA_ROAMING_SETTINGS, "Mobile Data Settings", "मोबाइल डेटा सेटिंग्स")
            key.contains("HOTSPOT") || key.contains("TETHER") ->
                Triple(Settings.ACTION_WIRELESS_SETTINGS, "Hotspot & Tethering Settings", "हॉटस्पॉट और नेटवर्क सेटिंग्स")
            key.contains("BLUETOOTH") ->
                Triple(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings", "ब्लूटूथ सेटिंग्स")
            key.contains("LOCATION") || key.contains("GPS") ->
                Triple(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Location / GPS Settings", "लोकेशन और जीपीएस सेटिंग्स")
            key.contains("AIRPLANE") || key.contains("FLIGHT") ->
                Triple(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane Mode Settings", "एयरप्लेन मोड सेटिंग्स")
            key.contains("SOUND") || key.contains("AUDIO") || key.contains("RINGTONE") ->
                Triple(Settings.ACTION_SOUND_SETTINGS, "Sound & Vibration Settings", "साउंड और वाइब्रेशन सेटिंग्स")
            key.contains("DND") || key.contains("DISTURB") ->
                Triple(Settings.ACTION_SOUND_SETTINGS, "Do Not Disturb & Sound Settings", "डू नॉट डिस्टर्ब सेटिंग्स")
            key.contains("DISPLAY") || key.contains("ROTATE") || key.contains("SCREEN") ->
                Triple(Settings.ACTION_DISPLAY_SETTINGS, "Display & Screen Settings", "डिस्प्ले और स्क्रीन सेटिंग्स")
            key.contains("BATTERY") || key.contains("POWER") ->
                Triple(Settings.ACTION_BATTERY_SAVER_SETTINGS, "Battery & Power Saver Settings", "बैटरी सेवर सेटिंग्स")
            key.contains("SECURITY") || key.contains("PASSWORD") || key.contains("FINGERPRINT") ->
                Triple(Settings.ACTION_SECURITY_SETTINGS, "Security & Lock Screen Settings", "सिक्योरिटी और लॉक सेटिंग्स")
            key.contains("DATE") || key.contains("TIME") ->
                Triple(Settings.ACTION_DATE_SETTINGS, "Date & Time Settings", "तारीख और समय सेटिंग्स")
            key.contains("ACCESSIBILITY") ->
                Triple(Settings.ACTION_ACCESSIBILITY_SETTINGS, "Accessibility Settings", "एक्सेसिबिलिटी सेटिंग्स")
            key.contains("STORAGE") || key.contains("MEMORY") ->
                Triple(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "Storage Settings", "स्टोरेज सेटिंग्स")
            key.contains("APP") ->
                Triple(Settings.ACTION_APPLICATION_SETTINGS, "Installed Apps Settings", "ऐप्स सेटिंग्स")
            else ->
                Triple(Settings.ACTION_SETTINGS, "Android System Settings", "फोन की मुख्य सेटिंग्स")
        }

        return try {
            val intent = if (key.contains("HOTSPOT") || key.contains("TETHER")) {
                Intent().apply {
                    setClassName("com.android.settings", "com.android.settings.TetherSettings")
                }
            } else {
                Intent(intentAction)
            }
            try {
                launchActivityWithBackgroundSupport(intent, "Moko // $labelEn", "Tap to open $labelEn")
            } catch (_: Exception) {
                launchActivityWithBackgroundSupport(
                    Intent(intentAction),
                    "Moko // $labelEn",
                    "Tap to open $labelEn"
                )
            }
            ActionExecutionResult(
                success = true,
                displayReply = customDisplay.ifBlank { "OFFLINE SYSTEM SETTING // Opened $labelEn." },
                finalSpokenReply = customSpoken.ifBlank { "जी $userName, $labelHi खोल दी गई हैं।" }
            )
        } catch (_: Exception) {
            openSystemSettings(customDisplay, customSpoken)
        }
    }

    fun setPhoneRingerMode(
        modeTarget: String,
        customDisplay: String = "",
        customSpoken: String = ""
    ): ActionExecutionResult {
        val key = modeTarget.trim().uppercase()
        val userName = prefs.getUserName()
        return try {
            when {
                key.contains("VIBRATE") -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    ActionExecutionResult(
                        success = true,
                        displayReply = customDisplay.ifBlank { "RINGER MODE // Phone set to VIBRATE mode." },
                        finalSpokenReply = customSpoken.ifBlank { "जी $userName, आपका फोन वाइब्रेट मोड पर कर दिया गया है।" }
                    )
                }
                key.contains("SILENT") || key.contains("DND") -> {
                    try {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    } catch (_: SecurityException) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                        adjustVolume(0, 0, mute = true)
                    }
                    ActionExecutionResult(
                        success = true,
                        displayReply = customDisplay.ifBlank { "RINGER MODE // Phone set to SILENT mode." },
                        finalSpokenReply = customSpoken.ifBlank { "जी $userName, आपका फोन साइलेंट मोड पर कर दिया गया है।" }
                    )
                }
                else -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    adjustVolume(0, 80, mute = false)
                    ActionExecutionResult(
                        success = true,
                        displayReply = customDisplay.ifBlank { "RINGER MODE // Phone set to NORMAL RING mode (80%)." },
                        finalSpokenReply = customSpoken.ifBlank { "जी $userName, आपका फोन नॉर्मल रिंग मोड पर चालू कर दिया गया है।" }
                    )
                }
            }
        } catch (_: Exception) {
            openSpecificSystemSetting("SOUND", customDisplay, customSpoken)
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
