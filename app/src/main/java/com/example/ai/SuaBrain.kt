package com.example.ai

import com.example.data.DeviceActionType
import com.example.data.ParsedCommand
import com.example.data.SuaPreferences
import com.example.data.SuaStateRepository
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
                displayReply = "Moko is listening. Say 'Hi Moko' followed by your command.",
                spokenReply = "जी बॉस, मैं मोको सुन रही हूँ। बताइए क्या काम करना है?"
            )
        }

        // 0. Check if user is explicitly asking Moko to remember a fact or asking what Moko remembers
        val localMemoryCmd = parseMemoryIntentLocally(text)
        if (localMemoryCmd != null) {
            return@withContext localMemoryCmd
        }

        val localMatch = parseHardwareIntentLocally(text)
        val apiKey = prefs.getEffectiveApiKey()

        // Fast-path unambiguous non-named hardware commands
        if (localMatch != null &&
            localMatch.action != DeviceActionType.CALL &&
            localMatch.action != DeviceActionType.SMS
        ) {
            return@withContext localMatch
        }

        // For CALL, SMS, or open-ended queries: if API key is present, call Gemini with Memory & Chat History
        var lastApiFailure: GeminiCallOutcome.Failure? = null
        if (apiKey.isNotEmpty()) {
            when (val outcome = callGeminiWithFallback(text, apiKey)) {
                is GeminiCallOutcome.Success -> {
                    val gemCmd = outcome.command
                    if (gemCmd.memoryFact.isNotBlank()) {
                        SuaStateRepository.addMemoryFact(
                            factText = gemCmd.memoryFact,
                            isAuto = true,
                            prefs = prefs
                        )
                    }
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

        // If local parser matched CALL or SMS, execute it
        if (localMatch != null) {
            return@withContext localMatch
        }

        // Report Gemini error honestly if key was set
        if (lastApiFailure != null) {
            val code = lastApiFailure.httpCode
            val detail = lastApiFailure.errorDetail
            return@withContext ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "GEMINI API ERROR (HTTP $code) // $detail. Check your API Key or Model in Side Menu -> Settings.",
                spokenReply = when (code) {
                    400, 401, 403 -> "माफ़ कीजिए बॉस, आपकी गूगल जेमिनी एपीआई की मान्य नहीं है। कृपया साइड मेनू की सेटिंग्स में जाकर अपनी एपीआई की चेक करें।"
                    429 -> "माफ़ कीजिए बॉस, आपकी जेमिनी एपीआई की का कोटा पूरा हो गया है।"
                    else -> "माफ़ कीजिए बॉस, जेमिनी एआई सर्वर से जुड़ने में समस्या आई है।"
                }
            )
        }

        // Conversational & Memory-aware fallback when offline / no API key
        val lower = text.lowercase()
        val memories = SuaStateRepository.memoryItems.value
        return@withContext when {
            lower.contains("tumhara naam") || lower.contains("your name") ||
                text.contains("तुम्हारा नाम") || text.contains("कौन हो") || lower.contains("who are you") -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "MOKO AI // I am Moko, your personal voice & memory assistant.",
                    spokenReply = "मेरा नाम मोको है बॉस! मैं आपकी पर्सनल वॉइस असिस्टेंट हूँ और आपकी बातें याद भी रखती हूँ।"
                )
            }
            lower.contains("jarvis") || text.contains("जार्विस") || lower.contains("sua") || text.contains("सुआ") -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "MOKO AI // My name is now Moko! Say 'Hi Moko' anytime to wake me.",
                    spokenReply = "बॉस, अब मेरा नाम मोको है! आप कभी भी हाय मोको बोलकर मुझे काम बता सकते हैं।"
                )
            }
            lower.contains("hello") || lower.contains("hi") || lower.contains("namaste") ||
                text.contains("नमस्ते") || text.contains("हेलो") || lower.contains("hey moko") || lower.contains("hi moko") -> {
                val memGreeting = if (memories.isNotEmpty()) {
                    " I also remember ${memories.size} saved facts about you."
                } else ""
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "MOKO ONLINE // Hello Boss! All systems ready.$memGreeting",
                    spokenReply = "नमस्ते बॉस! मैं मोको ऑनलाइन हूँ। बताइए आज आपके फोन में क्या काम करूँ?"
                )
            }
            else -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "GEMINI KEY REQUIRED // Phone controls & memory work offline. For open AI conversation (\"${sanitizeForHud(text)}\"), save your Gemini API Key in Side Menu -> Settings.",
                    spokenReply = "जी बॉस, फोन कंट्रोल और मेमोरी कमांड ऑफलाइन काम कर रहे हैं। किसी भी सवाल के एआई जवाब के लिए साइड मेनू की सेटिंग्स में अपनी गूगल जेमिनी एपीआई की सेव करें।"
                )
            }
        }
    }

    /**
     * Handles local Memory storage & recall ("याद रखना...", "Remember that...", "मेरा नाम ... है", "तुम्हें क्या याद है?")
     */
    fun parseMemoryIntentLocally(input: String): ParsedCommand? {
        val text = input.trim()
        val lower = text.lowercase()

        // 1. Check if user is sharing their name ("मेरा नाम राहुल है" / "My name is Rahul")
        val nameHiMatch = Regex("(?:मेरा नाम|mera naam|my name is)\\s+([\\p{L}a-zA-Z0-9\\s]{2,25}?)(?:\\s+है|\\s+hai|$)", RegexOption.IGNORE_CASE)
            .find(text)
        if (nameHiMatch != null && !lower.contains("kya") && !text.contains("क्या")) {
            val userName = nameHiMatch.groupValues[1].trim()
            if (userName.isNotEmpty()) {
                val fact = "User's name is $userName"
                SuaStateRepository.addMemoryFact(fact, isAuto = true, prefs = prefs)
                return ParsedCommand(
                    action = DeviceActionType.MEMORY_SAVE,
                    memoryFact = fact,
                    displayReply = "MEMORY SAVED // Remembered: \"$fact\"",
                    spokenReply = "जी $userName, मैंने अपनी मेमोरी में याद रख लिया है कि आपका नाम $userName है।"
                )
            }
        }

        // 2. Explicit "Remember that..." / "याद रखना कि..." / "याद रखो..."
        val rememberMatch = Regex(
            "^(?:moko\\s+|hi\\s+moko\\s+|hey\\s+moko\\s+|मोको\\s+|हाय\\s+मोको\\s+)?(?:याद रखना कि|याद रखना|याद रखो कि|याद रखो|ये बात याद रखना|remember that|remember|yaad rakhna ki|yaad rakhna|yaad rakho)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (rememberMatch != null) {
            val fact = rememberMatch.groupValues[1].trim()
            if (fact.length >= 2) {
                val cleanFact = sanitizeForHud(fact)
                SuaStateRepository.addMemoryFact(cleanFact, isAuto = true, prefs = prefs)
                return ParsedCommand(
                    action = DeviceActionType.MEMORY_SAVE,
                    memoryFact = cleanFact,
                    displayReply = "MEMORY SAVED // Stored in Moko Memory Vault: \"$cleanFact\"",
                    spokenReply = "जी बॉस, मैंने यह बात अपनी मेमोरी में सेव कर ली है: $fact"
                )
            }
        }

        // 3. Memory Recall query ("मेरा नाम क्या है?" / "तुम्हें क्या याद है?" / "What do you remember?")
        val isAskingName = lower.contains("mera naam kya hai") || text.contains("मेरा नाम क्या है") ||
            lower.contains("what is my name") || lower.contains("who am i")
        if (isAskingName) {
            val memories = SuaStateRepository.memoryItems.value
            val nameFact = memories.firstOrNull {
                it.factText.contains("name is", ignoreCase = true) ||
                    it.factText.contains("नाम", ignoreCase = true)
            }
            if (nameFact != null) {
                return ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "MOKO MEMORY // ${nameFact.factText}",
                    spokenReply = "जी बॉस, मेरी मेमोरी के अनुसार ${nameFact.factText}"
                )
            }
        }

        val isAskingMemories = lower.contains("kya yaad hai") || text.contains("क्या याद है") ||
            lower.contains("what do you remember") || lower.contains("show my memory") ||
            text.contains("मेमोरी बताओ") || lower.contains("meri memory")
        if (isAskingMemories) {
            val memories = SuaStateRepository.memoryItems.value
            return if (memories.isEmpty()) {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "MOKO MEMORY // No saved memories yet. Say 'Remember that...' or pin any chat to save a memory.",
                    spokenReply = "बॉस, अभी मेरी मेमोरी में कोई खास बात सेव नहीं है। आप 'याद रखना कि...' बोलकर कुछ भी सेव कर सकते हैं।"
                )
            } else {
                val summary = memories.take(3).joinToString(" | ") { it.factText }
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "MOKO MEMORY (${memories.size} saved) // $summary",
                    spokenReply = "जी बॉस, मुझे आपके बारे में ${memories.size} बातें याद हैं, जैसे कि: ${memories.first().factText}"
                )
            }
        }

        return null
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

        // 1. FLASHLIGHT OFF / ON
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
                    displayReply = "MOKO OPTICS // Flashlight turned OFF.",
                    spokenReply = "जी बॉस, फ्लैशलाइट बंद कर दी गई है।"
                )
            } else {
                ParsedCommand(
                    action = DeviceActionType.FLASHLIGHT_ON,
                    displayReply = "MOKO OPTICS // Flashlight turned ON.",
                    spokenReply = "जी बॉस, फ्लैशलाइट चालू कर दी गई है।"
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
                displayReply = "MOKO SECURITY // Locking phone screen now.",
                spokenReply = "जी बॉस, आपका फोन लॉक किया जा रहा है।"
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
                    displayReply = "MOKO DISPLAY // Brightness set to 100%.",
                    spokenReply = "जी बॉस, ब्राइटनेस 100 प्रतिशत कर दी गई है।"
                )
                extractedNum in 5..100 -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_SET,
                    numericValue = extractedNum,
                    displayReply = "MOKO DISPLAY // Brightness set to $extractedNum%.",
                    spokenReply = "जी बॉस, ब्राइटनेस $extractedNum प्रतिशत कर दी गई है।"
                )
                isDown -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_DOWN,
                    displayReply = "MOKO DISPLAY // Decreasing brightness by 25%.",
                    spokenReply = "जी बॉस, स्क्रीन की ब्राइटनेस कम कर दी गई है।"
                )
                isUp -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_UP,
                    displayReply = "MOKO DISPLAY // Increasing brightness by 25%.",
                    spokenReply = "जी बॉस, स्क्रीन की ब्राइटनेस बढ़ा दी गई है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_UP,
                    displayReply = "MOKO DISPLAY // Adjusting brightness.",
                    spokenReply = "जी बॉस, ब्राइटनेस एडजस्ट कर दी गई है।"
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
                    displayReply = "MOKO AUDIO // System sound muted (0%).",
                    spokenReply = "जी बॉस, फोन का साउंड म्यूट कर दिया गया है।"
                )
                lower.contains("full") || text.contains("फुल") -> ParsedCommand(
                    action = DeviceActionType.VOLUME_SET,
                    numericValue = 100,
                    displayReply = "MOKO AUDIO // Volume set to 100%.",
                    spokenReply = "जी बॉस, साउंड फुल सौ प्रतिशत कर दिया गया है।"
                )
                extractedNum in 0..100 -> ParsedCommand(
                    action = DeviceActionType.VOLUME_SET,
                    numericValue = extractedNum,
                    displayReply = "MOKO AUDIO // Volume set to $extractedNum%.",
                    spokenReply = "जी बॉस, साउंड $extractedNum प्रतिशत कर दिया गया है।"
                )
                isDown -> ParsedCommand(
                    action = DeviceActionType.VOLUME_DOWN,
                    displayReply = "MOKO AUDIO // Decreasing volume by 20%.",
                    spokenReply = "जी बॉस, फोन की आवाज़ कम कर दी गई है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.VOLUME_UP,
                    displayReply = "MOKO AUDIO // Increasing volume by 20%.",
                    spokenReply = "जी बॉस, फोन का साउंड बढ़ा दिया गया है।"
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
                    displayReply = "MOKO MEDIA // Skipping to next track.",
                    spokenReply = "जी बॉस, अगला गाना चलाया जा रहा है।"
                )
                isPrev -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PREV,
                    displayReply = "MOKO MEDIA // Playing previous track.",
                    spokenReply = "जी बॉस, पिछला गाना चलाया जा रहा है।"
                )
                isStop -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PAUSE,
                    displayReply = "MOKO MEDIA // Music playback paused.",
                    spokenReply = "जी बॉस, म्यूजिक रोक दिया गया है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PLAY,
                    displayReply = "MOKO MEDIA // Starting music playback.",
                    spokenReply = "जी बॉस, म्यूजिक चालू किया जा रहा है।"
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
                displayReply = "MOKO SMS // Target: $hudName",
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
                displayReply = "MOKO CALL // Target: $hudName",
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

        // 9. ALARM
        if (Regex("\\balarm\\b").containsMatchIn(lower) || text.contains("अलार्म")) {
            val (hour24, minute) = parseAlarmHourAndMinute(text)
            val timeFormatted = String.format("%02d:%02d", hour24, minute)
            return ParsedCommand(
                action = DeviceActionType.SET_ALARM,
                numericValue = hour24,
                secondaryNumericValue = minute,
                target = "Moko Alarm",
                displayReply = "MOKO ALARM // Setting alarm for $timeFormatted.",
                spokenReply = if (minute > 0) {
                    "जी बॉस, $hour24 बजकर $minute मिनट का अलार्म सेट किया जा रहा है।"
                } else {
                    "जी बॉस, $hour24 बजे का अलार्म सेट किया जा रहा है।"
                }
            )
        }

        // 10. TIME / DATE
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
                displayReply = "MOKO SYSTEM // Opening Android Settings.",
                spokenReply = "जी बॉस, फोन की सेटिंग्स खोली जा रही हैं।"
            )
        }

        return null
    }

    fun parseAlarmHourAndMinute(text: String): Pair<Int, Int> {
        val lower = text.lowercase()
        var hour = 7
        var minute = 0

        val colonMatch = Regex("(\\d{1,2}):(\\d{2})").find(lower)
        if (colonMatch != null) {
            hour = colonMatch.groupValues[1].toIntOrNull() ?: 7
            minute = colonMatch.groupValues[2].toIntOrNull() ?: 0
        } else {
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

        if (text.contains("साढ़े") || text.contains("साढे") || lower.contains("sadhe")) {
            minute = 30
        } else if (text.contains("सवा") || lower.contains("sawa")) {
            minute = 15
        } else if (text.contains("पौने") || lower.contains("paune")) {
            hour = if (hour > 1) hour - 1 else 12
            minute = 45
        }

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

    fun extractTargetNameForCallOrSms(text: String): String {
        val phoneMatch = Regex("\\+?\\d[\\d\\s-]{5,13}\\d").find(text)
        if (phoneMatch != null) {
            return phoneMatch.value.replace(Regex("[^0-9+]"), "")
        }

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

        val hinglishKo =
            Regex("(?i)(?:mera\\s+|meri\\s+|moko\\s+|hi\\s+moko\\s+|hey\\s+moko\\s+)?([a-zA-Z\\p{L}]+(?:\\s+[a-zA-Z\\p{L}]+)?)\\s+ko\\s+(?:call|phone|fone|message|sms|msg)").find(text)
        if (hinglishKo != null) {
            val candidate = cleanExtractedContactName(hinglishKo.groupValues[1])
            if (candidate.isNotEmpty()) return candidate
        }

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
            .replace(
                Regex("(?i)^(hey\\s+moko|hi\\s+moko|ok\\s+moko|hello\\s+moko|suno\\s+moko|moko|moco|हाय\\s+मोको|हे\\s+मोको|सुनो\\s+मोको|मोको|मेरा|मेरी|मेरे|जरा|ज़रा|प्लीज|please)\\s+"),
                ""
            )
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
        val candidateModels = (listOf(preferred) + SuaPreferences.SUPPORTED_GEMINI_MODELS).distinct().toMutableList()
        val triedModels = mutableSetOf<String>()

        var lastFailure = GeminiCallOutcome.Failure(0, "Network error")
        var idx = 0
        while (idx < candidateModels.size) {
            val modelId = candidateModels[idx].removePrefix("models/")
            idx++
            if (!triedModels.add(modelId)) continue

            val outcome = callGeminiSingleModel(userCommand, apiKey, modelId)
            when (outcome) {
                is GeminiCallOutcome.Success -> {
                    if (modelId != preferred) {
                        prefs.setGeminiModel(modelId)
                    }
                    return outcome
                }
                is GeminiCallOutcome.Failure -> {
                    lastFailure = outcome
                    if (outcome.httpCode == 404) {
                        // Check if Google's 404 error recommends a specific replacement model (e.g. "models/gemini-3.1-pro-preview")
                        val suggested = Regex("use\\s+models/([a-zA-Z0-9._-]+)")
                            .find(outcome.errorDetail)
                            ?.groupValues
                            ?.getOrNull(1)
                        if (!suggested.isNullOrBlank() && !triedModels.contains(suggested)) {
                            candidateModels.add(idx, suggested)
                        }
                    } else {
                        return outcome
                    }
                }
            }
        }

        // If all static models returned 404, dynamically query /v1beta/models to find an available Gemini model
        val discoveredModel = discoverAvailableGeminiModel(apiKey, triedModels)
        if (!discoveredModel.isNullOrBlank()) {
            val outcome = callGeminiSingleModel(userCommand, apiKey, discoveredModel)
            if (outcome is GeminiCallOutcome.Success) {
                prefs.setGeminiModel(discoveredModel)
                return outcome
            }
        }

        return lastFailure
    }

    private fun discoverAvailableGeminiModel(apiKey: String, alreadyTried: Set<String>): String? {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                val arr = JSONObject(body).optJSONArray("models") ?: return null
                val available = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val name = m.optString("name", "").removePrefix("models/")
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var supportsGen = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.optString(j) == "generateContent") {
                                supportsGen = true
                                break
                            }
                        }
                    }
                    if (supportsGen && name.startsWith("gemini") && !alreadyTried.contains(name)) {
                        available.add(name)
                    }
                }
                available.firstOrNull { it.contains("gemini-3") && it.contains("flash") }
                    ?: available.firstOrNull { it.contains("gemini-3") }
                    ?: available.firstOrNull { it.contains("flash") }
                    ?: available.firstOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun callGeminiSingleModel(
        userCommand: String,
        apiKey: String,
        modelId: String
    ): GeminiCallOutcome {
        return try {
            val savedMemories = SuaStateRepository.memoryItems.value
                .take(15)
                .joinToString("\n") { "- ${it.factText}" }
                .ifBlank { "No saved memories yet." }

            val recentConversation = SuaStateRepository.commandLogs.value
                .take(6)
                .reversed()
                .joinToString("\n") { "User: ${it.userCommand} | Moko: ${it.suaResponse}" }
                .ifBlank { "No prior conversation in this session." }

            val systemInstruction = """
                You are Moko ("मोको"), a smart, friendly, memory-aware personal AI assistant for Android.
                IMPORTANT RULES:
                1. Your name is strictly Moko (मोको), NEVER Jarvis or SUA.
                2. Use the SAVED USER MEMORIES and RECENT CONVERSATION HISTORY below to personalize your answers and remember facts the user told you earlier:
                [SAVED USER MEMORIES]:
                $savedMemories
                
                [RECENT CONVERSATION HISTORY]:
                $recentConversation
                
                3. If the user shares a new personal fact, preference, name, reminder, or asks you to remember something, put a short English summary of that fact in 'memoryFact' (otherwise "").
                4. If the user asks to CALL or SMS someone (e.g. 'राहुल को कॉल करो'), always transliterate the contact's name into standard English script in the 'target' field (e.g. 'Rahul').
                5. Return ONLY a valid JSON object with this exact schema:
                {
                  "action": "CALL" | "SMS" | "FLASHLIGHT_ON" | "FLASHLIGHT_OFF" | "LOCK_PHONE" | "BRIGHTNESS_UP" | "BRIGHTNESS_DOWN" | "BRIGHTNESS_SET" | "VOLUME_UP" | "VOLUME_DOWN" | "VOLUME_SET" | "VOLUME_MUTE" | "MEDIA_PLAY" | "MEDIA_PAUSE" | "MEDIA_NEXT" | "MEDIA_PREV" | "BATTERY_STATUS" | "SET_ALARM" | "OPEN_SETTINGS" | "TIME_DATE" | "MEMORY_SAVE" | "AI_CHAT",
                  "target": "contact name in English Roman script (e.g. 'Rahul', 'Khushi') or phone number if CALL/SMS, or alarm label, otherwise empty string",
                  "message": "SMS text content if action is SMS, otherwise empty string",
                  "numericValue": integer (0-100 for brightness/volume, 0-23 for alarm 24h hour, or -1 if not applicable),
                  "secondaryNumericValue": integer (0-59 for alarm minutes, default 0),
                  "memoryFact": "Short English summary if user shared a new personal fact/preference to remember, else empty string",
                  "displayReply": "Concise response in clean English for the on-screen chat card.",
                  "spokenReply": "Natural, warm spoken response in Hindi (Devanagari) as Moko so TextToSpeech speaks in fluent Hindi."
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
                        put("temperature", 0.3)
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
                    .ifBlank { "MOKO // Command processed." }
                val spokenHi = parsedObj.optString("spokenReply", "")
                    .ifBlank { "जी बॉस, आपका काम पूरा कर दिया गया है।" }
                val extractedMemory = parsedObj.optString("memoryFact", "").trim()

                GeminiCallOutcome.Success(
                    ParsedCommand(
                        action = actionType,
                        target = parsedObj.optString("target", ""),
                        message = parsedObj.optString("message", ""),
                        numericValue = parsedObj.optInt("numericValue", -1),
                        secondaryNumericValue = parsedObj.optInt("secondaryNumericValue", 0),
                        displayReply = displayEn,
                        spokenReply = spokenHi,
                        memoryFact = extractedMemory
                    )
                )
            }
        } catch (e: Exception) {
            GeminiCallOutcome.Failure(-1, e.localizedMessage ?: "Network connection failed")
        }
    }
}
