package com.example.ai

import com.example.data.DeviceActionType
import com.example.data.ParsedCommand
import com.example.data.SuaPreferences
import com.example.util.HindiTransliterator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SuaBrain(private val prefs: SuaPreferences) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private sealed class GeminiCallOutcome {
        data class Success(val command: ParsedCommand) : GeminiCallOutcome()
        data class Failure(val httpCode: Int, val errorDetail: String) : GeminiCallOutcome()
    }

    suspend fun interpretCommand(rawInput: String): ParsedCommand = withContext(Dispatchers.IO) {
        val text = rawInput.trim()
        if (text.isEmpty()) {
            return@withContext ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "SUA READY // Listening for your voice command.",
                spokenReply = "जी सर, मैं सुआ सुन रही हूँ। कृपया अपना कमांड बोलें।"
            )
        }

        val localMatch = parseHardwareIntentLocally(text)
        val apiKey = prefs.getEffectiveApiKey()

        // Fast-path unambiguous non-named hardware commands (Flashlight, Lock, Brightness, Volume, Media, Battery, Time, Alarm)
        if (localMatch != null &&
            localMatch.action != DeviceActionType.CALL &&
            localMatch.action != DeviceActionType.SMS
        ) {
            return@withContext localMatch
        }

        // For CALL, SMS, or open-ended queries: if API key is present, let Gemini help transliterate names/messages accurately
        var lastApiFailure: GeminiCallOutcome.Failure? = null
        if (apiKey.isNotEmpty()) {
            when (val outcome = callGeminiWithFallback(text, apiKey)) {
                is GeminiCallOutcome.Success -> {
                    val gemCmd = outcome.command
                    if (gemCmd.action == DeviceActionType.AI_CHAT && localMatch != null) {
                        return@withContext localMatch
                    }
                    return@withContext gemCmd
                }
                is GeminiCallOutcome.Failure -> {
                    lastApiFailure = outcome
                }
            }
        }

        // If local parser matched CALL or SMS, execute it (HindiTransliterator handles Devanagari -> Roman contact matching)
        if (localMatch != null) {
            return@withContext localMatch
        }

        // If Gemini API was configured by user but failed with an error, report the exact API error honestly!
        if (lastApiFailure != null) {
            val code = lastApiFailure.httpCode
            val detail = lastApiFailure.errorDetail
            return@withContext ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "GEMINI API ERROR (HTTP $code) // $detail. Check your API Key or Model in Settings.",
                spokenReply = when (code) {
                    400, 401, 403 -> "माफ़ कीजिए सर, आपकी गूगल जेमिनी एपीआई की मान्य नहीं है। कृपया सेटिंग्स में जाकर अपनी एपीआई की चेक करें।"
                    429 -> "माफ़ कीजिए सर, आपकी जेमिनी एपीआई की का कोटा पूरा हो गया है।"
                    else -> "माफ़ कीजिए सर, जेमिनी एआई सर्वर से जुड़ने में समस्या आई है: $detail"
                }
            )
        }

        // Conversational fallback when no API key is configured
        val lower = text.lowercase()
        return@withContext when {
            lower.contains("tumhara naam") || lower.contains("your name") ||
                text.contains("तुम्हारा नाम") || text.contains("कौन हो") || lower.contains("who are you") -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "IDENTITY // I am SUA (Smart Universal Assistant), your personal tactical AI system.",
                    spokenReply = "मेरा नाम सुआ है सर। मैं आपकी पर्सनल वॉइस असिस्टेंट हूँ और आपके फोन के सारे काम कर सकती हूँ।"
                )
            }
            lower.contains("jarvis") || text.contains("जार्विस") -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "SYSTEM CORRECTION // My designation is SUA, not Jarvis. Ready for your command, Sir.",
                    spokenReply = "मेरा नाम जार्विस नहीं, सुआ है सर! बताइए मुझे क्या काम करना है?"
                )
            }
            lower.contains("hello") || lower.contains("hi") || lower.contains("namaste") ||
                text.contains("नमस्ते") || text.contains("हेलो") || lower.contains("hey sua") -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "SUA ONLINE // Greetings Sir. All systems nominal. Awaiting your directive.",
                    spokenReply = "नमस्ते सर! मैं सुआ ऑनलाइन हूँ। बताइए आज आपके फोन में क्या काम करूँ?"
                )
            }
            else -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "GEMINI KEY REQUIRED // Hardware controls work offline. To answer general questions (\"${sanitizeForHud(text)}\"), save your Google Gemini API Key in Settings.",
                    spokenReply = "जी सर, फोन कंट्रोल कमांड ऑफलाइन काम कर रहे हैं। किसी भी सवाल के एआई जवाब के लिए सेटिंग्स में अपनी गूगल जेमिनी एपीआई की सेव करें।"
                )
            }
        }
    }

    fun sanitizeForHud(input: String): String {
        val trim = input.trim()
        val hasDevanagari = trim.any { it.code in 0x0900..0x097F }
        if (!hasDevanagari) return trim

        val mapped = trim
            .replace("खुशी को कॉल लगाओ", "Call Khushi")
            .replace("राहुल को कॉल करो", "Call Rahul")
            .replace("फ्लैशलाइट बंद करो", "Turn Off Flashlight")
            .replace("फ्लैशलाइट चालू करो", "Turn On Flashlight")
            .replace("फोन को लॉक करो", "Lock Phone Screen")
            .replace("ब्राइटनेस बढ़ाओ", "Increase Brightness")
            .replace("ब्राइटनेस कम करो", "Decrease Brightness")
            .replace("साउंड फुल करो", "Set Volume 100%")
            .replace("म्यूजिक चलाओ", "Play Music")
            .replace("म्यूजिक बंद करो", "Pause Music")
            .replace("बैटरी कितनी है", "Check Battery Status")

        return if (mapped.any { it.code in 0x0900..0x097F }) {
            HindiTransliterator.toRoman(mapped).replaceFirstChar { it.uppercase() }
        } else {
            mapped
        }
    }

    fun parseHardwareIntentLocally(input: String): ParsedCommand? {
        val text = input.trim()
        val lower = text.lowercase()
        val numberRegex = Regex("(\\d{1,3})")
        val extractedNum = numberRegex.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1

        // 1. FLASHLIGHT OFF / ON (Strict: require explicit flashlight/torch OR light + explicit toggle verb)
        val hasExplicitTorch = lower.contains("flashlight") || lower.contains("flash light") ||
            lower.contains("torch") || text.contains("फ्लैशलाइट") || text.contains("टॉर्च")
        val hasLightWord = (Regex("\\blight\\b").containsMatchIn(lower) || text.contains("लाइट")) &&
            !text.contains("बिजली") && !text.contains("घर की लाइट") && !text.contains("लाइट नहीं")
        val hasToggleOnVerb = Regex("\\b(on|chalu|chaalu|jala|jalao|start|enable)\\b").containsMatchIn(lower) ||
            text.contains("चालू") || text.contains("जला") || text.contains("ऑन")
        val hasToggleOffVerb = Regex("\\b(off|band|bujha|bujhao|stop|disable)\\b").containsMatchIn(lower) ||
            text.contains("बंद") || text.contains("ऑफ") || text.contains("बुझा")

        if (hasExplicitTorch || (hasLightWord && (hasToggleOnVerb || hasToggleOffVerb))) {
            return if (hasToggleOffVerb) {
                ParsedCommand(
                    action = DeviceActionType.FLASHLIGHT_OFF,
                    displayReply = "OPTICS STANDBY // Flashlight beam disengaged.",
                    spokenReply = "जी सर, फ्लैशलाइट बंद कर दी गई है।"
                )
            } else {
                ParsedCommand(
                    action = DeviceActionType.FLASHLIGHT_ON,
                    displayReply = "OPTICS ONLINE // Flashlight beam engaged.",
                    spokenReply = "जी सर, फ्लैशलाइट चालू कर दी गई है।"
                )
            }
        }

        // 2. LOCK PHONE
        val hasLockWord = Regex("\\block\\b").containsMatchIn(lower) || text.contains("लॉक")
        val hasPhoneScreenTarget = lower.contains("phone") || lower.contains("screen") ||
            lower.contains("mobile") || text.contains("फोन") || text.contains("स्क्रीन") ||
            text.contains("मोबाइल") || lower.trim() == "lock" || text.contains("लॉक करो")
        if (hasLockWord && hasPhoneScreenTarget) {
            return ParsedCommand(
                action = DeviceActionType.LOCK_PHONE,
                displayReply = "SECURITY PROTOCOL // Engaging device screen lock.",
                spokenReply = "जी सर, आपका फोन लॉक किया जा रहा है।"
            )
        }

        // 3. BRIGHTNESS
        val hasBrightnessWord = lower.contains("brightness") || lower.contains("roshni") ||
            text.contains("ब्राइटनेस") || text.contains("रोशनी") || text.contains("चमक")
        if (hasBrightnessWord) {
            val isDown = lower.contains("kam") || lower.contains("down") || lower.contains("decrease") ||
                lower.contains("low") || lower.contains("ghata") || text.contains("कम") ||
                text.contains("घटा") || text.contains("धीमा")
            val isUp = lower.contains("badha") || lower.contains("up") || lower.contains("increase") ||
                lower.contains("high") || lower.contains("full") || lower.contains("zyada") ||
                text.contains("बढ़ा") || text.contains("ज्यादा") || text.contains("तेज") || text.contains("फुल")
            return when {
                lower.contains("full") || text.contains("फुल") -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_SET,
                    numericValue = 100,
                    displayReply = "LUMINANCE // Display brightness calibrated to 100%.",
                    spokenReply = "जी सर, ब्राइटनेस 100 प्रतिशत कर दी गई है।"
                )
                extractedNum in 5..100 -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_SET,
                    numericValue = extractedNum,
                    displayReply = "LUMINANCE // Display brightness calibrated to $extractedNum%.",
                    spokenReply = "जी सर, ब्राइटनेस $extractedNum प्रतिशत कर दी गई है।"
                )
                isDown -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_DOWN,
                    displayReply = "LUMINANCE // Decreasing display brightness by 25%.",
                    spokenReply = "जी सर, स्क्रीन की ब्राइटनेस कम कर दी गई है।"
                )
                isUp -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_UP,
                    displayReply = "LUMINANCE // Increasing display brightness by 25%.",
                    spokenReply = "जी सर, स्क्रीन की ब्राइटनेस बढ़ा दी गई है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_UP,
                    displayReply = "LUMINANCE // Adjusting display brightness.",
                    spokenReply = "जी सर, ब्राइटनेस एडजस्ट कर दी गई है।"
                )
            }
        }

        // 4. SOUND / VOLUME
        val hasVolumeWord = lower.contains("volume") || lower.contains("sound") ||
            lower.contains("awaz") || lower.contains("aawaz") || lower.contains("mute") ||
            text.contains("साउंड") || text.contains("आवाज़") || text.contains("आवाज") ||
            text.contains("वॉल्यूम") || text.contains("म्यूट")
        if (hasVolumeWord) {
            val isMute = lower.contains("mute") || lower.contains("silent") ||
                text.contains("म्यूट") || text.contains("साइलेंट") || text.contains("बंद")
            val isDown = lower.contains("kam") || lower.contains("down") || lower.contains("decrease") ||
                lower.contains("low") || text.contains("कम") || text.contains("घटा") || text.contains("धीमा")
            return when {
                isMute -> ParsedCommand(
                    action = DeviceActionType.VOLUME_MUTE,
                    displayReply = "AUDIO GAIN // System audio muted (0%).",
                    spokenReply = "जी सर, फोन का साउंड म्यूट कर दिया गया है।"
                )
                lower.contains("full") || text.contains("फुल") -> ParsedCommand(
                    action = DeviceActionType.VOLUME_SET,
                    numericValue = 100,
                    displayReply = "AUDIO GAIN // Master volume set to 100% maximum.",
                    spokenReply = "जी सर, साउंड फुल सौ प्रतिशत कर दिया गया है।"
                )
                extractedNum in 0..100 -> ParsedCommand(
                    action = DeviceActionType.VOLUME_SET,
                    numericValue = extractedNum,
                    displayReply = "AUDIO GAIN // Master volume set to $extractedNum%.",
                    spokenReply = "जी सर, साउंड $extractedNum प्रतिशत कर दिया गया है।"
                )
                isDown -> ParsedCommand(
                    action = DeviceActionType.VOLUME_DOWN,
                    displayReply = "AUDIO GAIN // Decreasing master volume by 20%.",
                    spokenReply = "जी सर, फोन की आवाज़ कम कर दी गई है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.VOLUME_UP,
                    displayReply = "AUDIO GAIN // Increasing master volume by 20%.",
                    spokenReply = "जी सर, फोन का साउंड बढ़ा दिया गया है।"
                )
            }
        }

        // 5. MUSIC / MEDIA
        val hasMusicWord = lower.contains("music") || lower.contains("song") || lower.contains("gana") ||
            lower.contains("gaana") || text.contains("म्यूजिक") || text.contains("गाना") ||
            text.contains("सॉन्ग") || lower.contains("next track") || lower.contains("previous track") ||
            lower.contains("play music") || lower.contains("pause music")
        if (hasMusicWord) {
            val isStop = lower.contains("stop") || lower.contains("pause") || lower.contains("band") ||
                lower.contains("roko") || text.contains("बंद") || text.contains("रोको") || text.contains("पॉज")
            val isNext = lower.contains("next") || lower.contains("agla") || text.contains("अगला") || text.contains("नेक्स्ट")
            val isPrev = lower.contains("prev") || lower.contains("pichla") || text.contains("पिछला")
            return when {
                isNext -> ParsedCommand(
                    action = DeviceActionType.MEDIA_NEXT,
                    displayReply = "MEDIA ENGINE // Skipping to next track.",
                    spokenReply = "जी सर, अगला गाना चलाया जा रहा है।"
                )
                isPrev -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PREV,
                    displayReply = "MEDIA ENGINE // Returning to previous track.",
                    spokenReply = "जी सर, पिछला गाना चलाया जा रहा है।"
                )
                isStop -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PAUSE,
                    displayReply = "MEDIA ENGINE // Playback paused.",
                    spokenReply = "जी सर, म्यूजिक रोक दिया गया है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PLAY,
                    displayReply = "MEDIA ENGINE // Engaging audio playback.",
                    spokenReply = "जी सर, म्यूजिक चालू किया जा रहा है।"
                )
            }
        }

        // 6. SMS / MESSAGE
        val hasSmsWord = Regex("\\b(message|sms|msg|text)\\b").containsMatchIn(lower) ||
            text.contains("मैसेज") || text.contains("संदेश") || text.contains("एसएमएस")
        if (hasSmsWord) {
            val targetName = extractTargetNameForCallOrSms(text)
            val msgContent = extractMessageBody(text)
            val hudName = HindiTransliterator.toRoman(targetName).replaceFirstChar { it.uppercase() }
                .ifEmpty { targetName }
            return ParsedCommand(
                action = DeviceActionType.SMS,
                target = targetName,
                message = msgContent,
                displayReply = "COMMS SMS // Target: $hudName",
                spokenReply = ""
            )
        }

        // 7. PHONE CALL
        val hasCallWord = Regex("\\b(call|dial)\\b").containsMatchIn(lower) ||
            lower.contains("phone laga") || lower.contains("fone laga") ||
            text.contains("कॉल") || text.contains("फ़ोन लगा") ||
            text.contains("फोन लगा") || text.contains("डायल")
        if (hasCallWord) {
            val targetName = extractTargetNameForCallOrSms(text)
            val hudName = HindiTransliterator.toRoman(targetName).replaceFirstChar { it.uppercase() }
                .ifEmpty { targetName }
            return ParsedCommand(
                action = DeviceActionType.CALL,
                target = targetName,
                displayReply = "COMMS VOICE // Target: $hudName",
                spokenReply = ""
            )
        }

        // 8. BATTERY
        if (Regex("\\bbattery\\b").containsMatchIn(lower) || text.contains("बैटरी") ||
            (text.contains("चार्ज") && (text.contains("कितना") || text.contains("कितनी")))
        ) {
            return ParsedCommand(
                action = DeviceActionType.BATTERY_STATUS,
                displayReply = "",
                spokenReply = ""
            )
        }

        // 9. ALARM (Supports AM/PM, शाम/रात/दोपहर/सुबह, and minutes like 5:30 or बजकर 30 मिनट)
        if (Regex("\\balarm\\b").containsMatchIn(lower) || text.contains("अलार्म")) {
            val (hour24, minute) = parseAlarmHourAndMinute(text)
            val timeFormatted = String.format("%02d:%02d", hour24, minute)
            return ParsedCommand(
                action = DeviceActionType.SET_ALARM,
                numericValue = hour24,
                secondaryNumericValue = minute,
                target = "SUA Tactical Alarm",
                displayReply = "CHRONO ALARM // Setting system alarm for $timeFormatted.",
                spokenReply = if (minute > 0) {
                    "जी सर, $hour24 बजकर $minute मिनट का अलार्म सेट किया जा रहा है।"
                } else {
                    "जी सर, $hour24 बजे का अलार्म सेट किया जा रहा है।"
                }
            )
        }

        // 10. TIME / DATE (Strict query matching so random sentences with 'time' don't trigger)
        val isTimeQuery = Regex("\\b(what time|current time|time kya|kya time|tell me the time|what is the date|aaj ki tarikh|samay kya|kitne baje)\\b")
            .containsMatchIn(lower) ||
            text.contains("समय क्या") || text.contains("कितने बजे") ||
            text.contains("टाइम क्या") || text.contains("क्या टाइम") ||
            text.contains("तारीख क्या") || text.contains("आज क्या तारीख") ||
            lower == "time" || lower == "date"
        if (isTimeQuery) {
            return ParsedCommand(
                action = DeviceActionType.TIME_DATE,
                displayReply = "",
                spokenReply = ""
            )
        }

        // 11. OPEN SETTINGS
        if (lower.contains("open setting") || text.contains("सेटिंग खोल") || text.contains("सेटिंग्स खोल")) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_SETTINGS,
                displayReply = "OS CONSOLE // Opening Android System Settings.",
                spokenReply = "जी सर, फोन की सेटिंग्स खोली जा रही हैं।"
            )
        }

        return null
    }

    /**
     * Parses alarm hour (0..23) and minute (0..59) including Hindi/English AM/PM indicators
     * ("शाम 5 बजे" -> 17:00, "रात 8:30 बजे" -> 20:30, "साढ़े 5 बजे शाम" -> 17:30).
     */
    fun parseAlarmHourAndMinute(text: String): Pair<Int, Int> {
        val lower = text.lowercase()
        var hour = 7
        var minute = 0

        // 1. Check HH:MM pattern first (e.g. "5:30", "17:45")
        val colonMatch = Regex("(\\d{1,2}):(\\d{2})").find(lower)
        if (colonMatch != null) {
            hour = colonMatch.groupValues[1].toIntOrNull() ?: 7
            minute = colonMatch.groupValues[2].toIntOrNull() ?: 0
        } else {
            // 2. Check "<H> बजकर <M> मिनट" or "<H> bajkar <M> minute"
            val bajkarMatch = Regex("(\\d{1,2})\\s*(?:बजकर|bajkar|baje)\\s*(\\d{1,2})").find(text)
            if (bajkarMatch != null) {
                hour = bajkarMatch.groupValues[1].toIntOrNull() ?: 7
                minute = bajkarMatch.groupValues[2].toIntOrNull() ?: 0
            } else {
                val firstNum = Regex("(\\d{1,2})").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (firstNum != null) {
                    hour = firstNum
                }
            }
        }

        // Handle Hindi fractional hours: साढ़े (+30m), सवा (+15m), पौने (-15m from hour)
        if (text.contains("साढ़े") || text.contains("साढे") || lower.contains("sadhe")) {
            minute = 30
        } else if (text.contains("सवा") || lower.contains("sawa")) {
            minute = 15
        } else if (text.contains("पौने") || lower.contains("paune")) {
            hour = if (hour > 1) hour - 1 else 12
            minute = 45
        }

        // Determine AM / PM
        val isPm = Regex("\\bpm\\b|\\bp\\.m\\.\\b").containsMatchIn(lower) ||
            text.contains("शाम") || text.contains("रात") || text.contains("दोपहर") ||
            lower.contains("shaam") || lower.contains("sham") || lower.contains("raat") ||
            lower.contains("dopahar") || lower.contains("evening") || lower.contains("night") ||
            lower.contains("afternoon")
        val isAm = Regex("\\bam\\b|\\ba\\.m\\.\\b").containsMatchIn(lower) ||
            text.contains("सुबह") || lower.contains("subah") || lower.contains("morning")

        if (isPm && hour in 1..11) {
            hour += 12
        } else if (isAm && hour == 12) {
            hour = 0
        }

        return Pair(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    /**
     * Extracts the spoken contact name in Hindi or English without ever defaulting to a fake name.
     */
    fun extractTargetNameForCallOrSms(text: String): String {
        // 1. Check if a phone number was spoken directly
        val phoneMatch = Regex("\\+?\\d[\\d\\s-]{5,13}\\d").find(text)
        if (phoneMatch != null) {
            return phoneMatch.value.replace(Regex("[^0-9+]"), "")
        }

        // 2. Hindi pattern: "<Name> को कॉल..." / "<Name> को फोन..." / "<Name> को मैसेज..."
        val koMatch = Regex("([\\p{L}\\s]+?)\\s+को\\s+(?:कॉल|फोन|फ़ोन|मैसेज|संदेश|एसएमएस|call|phone|message|sms)").find(text)
        if (koMatch != null) {
            val candidate = cleanExtractedContactName(koMatch.groupValues[1])
            if (candidate.isNotEmpty()) return candidate
        }

        val koIdx = text.indexOf(" को ")
        if (koIdx > 0) {
            val candidate = cleanExtractedContactName(text.substring(0, koIdx))
            if (candidate.isNotEmpty()) return candidate
        }

        // 3. Hinglish pattern: "<Name> ko call..." / "<Name> ko message..."
        val hinglishKo =
            Regex("(?i)(?:mera\\s+|meri\\s+|sua\\s+|hey\\s+sua\\s+)?([a-zA-Z\\p{L}]+(?:\\s+[a-zA-Z\\p{L}]+)?)\\s+ko\\s+(?:call|phone|fone|message|sms|msg)").find(text)
        if (hinglishKo != null) {
            val candidate = cleanExtractedContactName(hinglishKo.groupValues[1])
            if (candidate.isNotEmpty()) return candidate
        }

        // 4. English pattern: "Call <Name>" / "Dial <Name>" / "Send SMS to <Name>" / "Message <Name>"
        val englishCall =
            Regex("(?i)(?:call|dial|phone|message|sms|text)\\s+(?:to\\s+)?([a-zA-Z\\p{L}]+)").find(text)
        if (englishCall != null) {
            val candidate = cleanExtractedContactName(englishCall.groupValues[1])
            if (candidate.isNotEmpty()) return candidate
        }

        return ""
    }

    private fun cleanExtractedContactName(raw: String): String {
        return raw.trim()
            .replace(Regex("(?i)^(hey\\s+sua|ok\\s+sua|hello\\s+sua|suno\\s+sua|sua|हे\\s+सुआ|सुनो\\s+सुआ|सुआ|मेरा|मेरी|मेरे|जरा|ज़रा|प्लीज|please)\\s+"), "")
            .replace(Regex("(?i)\\s+(को|ko|पे|पर|par)$"), "")
            .trim()
    }

    private fun extractMessageBody(text: String): String {
        val kiIndex = text.indexOf(" कि ")
        if (kiIndex >= 0 && kiIndex + 4 < text.length) {
            return text.substring(kiIndex + 4).trim()
        }
        val hinglishKi = Regex("(?i)\\bki\\s+(.+)$").find(text)
        if (hinglishKi != null) {
            return hinglishKi.groupValues[1].trim()
        }
        val englishSaying = Regex("(?i)(?:saying|that)\\s+(.+)$").find(text)
        if (englishSaying != null) {
            return englishSaying.groupValues[1].trim()
        }
        return ""
    }

    private fun callGeminiWithFallback(userCommand: String, apiKey: String): GeminiCallOutcome {
        val preferred = prefs.getGeminiModel()
        val candidateModels = (listOf(preferred) + SuaPreferences.SUPPORTED_GEMINI_MODELS).distinct()

        var lastFailure = GeminiCallOutcome.Failure(0, "Network error")
        for (modelId in candidateModels) {
            val outcome = callGeminiSingleModel(userCommand, apiKey, modelId)
            when (outcome) {
                is GeminiCallOutcome.Success -> return outcome
                is GeminiCallOutcome.Failure -> {
                    lastFailure = outcome
                    // Only retry next model on 404 (model not found / deprecated); stop immediately on 400/401/403/429
                    if (outcome.httpCode != 404) {
                        return outcome
                    }
                }
            }
        }
        return lastFailure
    }

    private fun callGeminiSingleModel(
        userCommand: String,
        apiKey: String,
        modelId: String
    ): GeminiCallOutcome {
        return try {
            val systemInstruction = """
                You are SUA (Smart Universal Assistant), an Iron Man style tactical AI assistant for Android.
                IMPORTANT RULES:
                1. Your name is strictly SUA, NEVER Jarvis.
                2. Understand Hindi, Hinglish, and English voice commands.
                3. If the user asks to CALL or SMS someone (e.g. 'राहुल को कॉल करो'), always transliterate the contact's name into standard English script in the 'target' field (e.g. 'Rahul').
                4. Return ONLY a valid JSON object with this exact schema:
                {
                  "action": "CALL" | "SMS" | "FLASHLIGHT_ON" | "FLASHLIGHT_OFF" | "LOCK_PHONE" | "BRIGHTNESS_UP" | "BRIGHTNESS_DOWN" | "BRIGHTNESS_SET" | "VOLUME_UP" | "VOLUME_DOWN" | "VOLUME_SET" | "VOLUME_MUTE" | "MEDIA_PLAY" | "MEDIA_PAUSE" | "MEDIA_NEXT" | "MEDIA_PREV" | "BATTERY_STATUS" | "SET_ALARM" | "OPEN_SETTINGS" | "TIME_DATE" | "AI_CHAT",
                  "target": "contact name in English Roman script (e.g. 'Rahul', 'Khushi') or phone number if CALL/SMS, or alarm label, otherwise empty string",
                  "message": "SMS text content if action is SMS, otherwise empty string",
                  "numericValue": integer (0-100 for brightness/volume, 0-23 for alarm 24h hour, or -1 if not applicable),
                  "secondaryNumericValue": integer (0-59 for alarm minutes, default 0),
                  "displayReply": "Concise, futuristic Iron Man HUD response strictly in English (NO Devanagari/Hindi script here).",
                  "spokenReply": "Natural, polite spoken response in Hindi (Devanagari) as SUA so TextToSpeech speaks in fluent Hindi."
                }
            """.trimIndent()

            val payload = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("text", userCommand)))
                        }
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.2)
                        put("responseMimeType", "application/json")
                    }
                )
            }

            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = try {
                        JSONObject(bodyStr).optJSONObject("error")?.optString("message")
                            ?: "HTTP ${response.code}"
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                    return GeminiCallOutcome.Failure(response.code, errMsg)
                }

                val root = JSONObject(bodyStr)
                val textOut = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text", "")
                    .orEmpty()

                if (textOut.isBlank()) {
                    return GeminiCallOutcome.Failure(500, "Empty response from Gemini model $modelId")
                }

                val cleanJson = textOut
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val parsedObj = JSONObject(cleanJson)
                val actionStr = parsedObj.optString("action", "AI_CHAT").uppercase()
                val actionType = try {
                    DeviceActionType.valueOf(actionStr)
                } catch (_: Exception) {
                    DeviceActionType.AI_CHAT
                }

                val displayEn = parsedObj.optString("displayReply", "")
                    .ifBlank { "SUA CORE // Command processed." }
                val spokenHi = parsedObj.optString("spokenReply", "")
                    .ifBlank { "जी सर, आपका कमांड पूरा कर दिया गया है।" }

                GeminiCallOutcome.Success(
                    ParsedCommand(
                        action = actionType,
                        target = parsedObj.optString("target", ""),
                        message = parsedObj.optString("message", ""),
                        numericValue = parsedObj.optInt("numericValue", -1),
                        secondaryNumericValue = parsedObj.optInt("secondaryNumericValue", 0),
                        displayReply = displayEn,
                        spokenReply = spokenHi
                    )
                )
            }
        } catch (e: Exception) {
            GeminiCallOutcome.Failure(-1, e.localizedMessage ?: "Network connection failed")
        }
    }
}
