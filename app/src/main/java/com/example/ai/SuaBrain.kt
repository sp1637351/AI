package com.example.ai

import com.example.data.AiProviderType
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
        .connectTimeout(9, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(9, TimeUnit.SECONDS)
        .build()

    private sealed class AiCallOutcome {
        data class Success(val command: ParsedCommand) : AiCallOutcome()
        data class Failure(val providerLabel: String, val httpCode: Int, val errorDetail: String) : AiCallOutcome()
    }

    suspend fun interpretCommand(rawInput: String): ParsedCommand = withContext(Dispatchers.IO) {
        val text = rawInput.trim()
        val userName = prefs.getUserName()
        val assistantName = prefs.getAssistantName()

        if (text.isEmpty()) {
            return@withContext ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$assistantName is listening. Say 'Ok $assistantName' or 'Hi $assistantName' followed by your command.",
                spokenReply = "जी $userName, मैं $assistantName बड़े प्यार से सुन रही हूँ। बताइए क्या काम करना है?"
            )
        }

        // 0. Check User-Defined Custom Voice Commands first (exact or substring trigger match)
        val customCmdMatch = matchCustomVoiceCommand(text, userName, assistantName)
        if (customCmdMatch != null) {
            return@withContext customCmdMatch
        }

        // 1. Check Developer Attribution ("किसने बनाया", "कौन डेवलप किया", "who developed you", etc.)
        val devIdentityMatch = parseDeveloperAndIdentityIntent(text, userName, assistantName)
        if (devIdentityMatch != null) {
            return@withContext devIdentityMatch
        }

        // 2. Check if user is explicitly asking to remember a fact or asking what is remembered
        val localMemoryCmd = parseMemoryIntentLocally(text, userName, assistantName)
        if (localMemoryCmd != null) {
            return@withContext localMemoryCmd
        }

        // 3. Check local Hardware & App Launch intents
        val localMatch = parseHardwareIntentLocally(text, userName, assistantName)
        val apiKey = prefs.getEffectiveApiKey()
        val customUrl = prefs.getCustomApiUrl()

        // Fast-path unambiguous non-named hardware & app commands for instant <50ms response
        if (localMatch != null &&
            localMatch.action != DeviceActionType.CALL &&
            localMatch.action != DeviceActionType.SMS
        ) {
            return@withContext localMatch
        }

        // 4. For CALL, SMS, or open-ended queries: call the active AI Provider (Gemini / OpenAI / Claude / Custom Link)
        var lastApiFailure: AiCallOutcome.Failure? = null
        if (apiKey.isNotEmpty() || customUrl.isNotEmpty()) {
            when (val outcome = callActiveAiProvider(text, apiKey, customUrl, userName, assistantName)) {
                is AiCallOutcome.Success -> {
                    val aiCmd = outcome.command
                    if (aiCmd.memoryFact.isNotBlank()) {
                        SuaStateRepository.addMemoryFact(
                            factText = aiCmd.memoryFact,
                            isAuto = true,
                            prefs = prefs
                        )
                    }
                    if (aiCmd.action == DeviceActionType.AI_CHAT && localMatch != null) {
                        return@withContext localMatch
                    }
                    return@withContext aiCmd
                }
                is AiCallOutcome.Failure -> {
                    lastApiFailure = outcome
                }
            }
        }

        // If local parser matched CALL or SMS, execute it offline
        if (localMatch != null) {
            return@withContext localMatch
        }

        // Report AI error honestly if key/URL was configured
        if (lastApiFailure != null) {
            val code = lastApiFailure.httpCode
            val detail = lastApiFailure.errorDetail
            val prov = lastApiFailure.providerLabel
            return@withContext ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$prov API ERROR (HTTP $code) // $detail. Check your API Key or Endpoint Link in Settings.",
                spokenReply = when (code) {
                    400, 401, 403 -> "माफ़ कीजिए $userName, आपकी $prov एपीआई की या लिंक मान्य नहीं है। कृपया सेटिंग्स में जाकर अपनी एपीआई की चेक करें।"
                    429 -> "माफ़ कीजिए $userName, आपकी एपीआई की का कोटा पूरा हो गया है।"
                    else -> "माफ़ कीजिए $userName, एआई सर्वर से जुड़ने में समस्या आई है।"
                }
            )
        }

        // Conversational & Memory-aware fallback when offline / no API key
        val lower = text.lowercase()
        val memories = SuaStateRepository.memoryItems.value
        return@withContext when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("namaste") ||
                text.contains("नमस्ते") || text.contains("हेलो") || lower.contains("kaise ho") ||
                text.contains("कैसे हो") || text.contains("कैसी हो") -> {
                val memGreeting = if (memories.isNotEmpty()) {
                    " Remembering ${memories.size} facts about $userName."
                } else ""
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "$assistantName ONLINE // Hello $userName! Developed by ${SuaPreferences.DEVELOPER_NAME}.$memGreeting",
                    spokenReply = "नमस्ते $userName! मैं आपकी प्यारी $assistantName एकदम ठीक हूँ और आपकी सेवा में हाज़िर हूँ। बताइए क्या काम करूँ?"
                )
            }
            else -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "AI KEY REQUIRED // Hardware, App Launch & Memory work offline. For open AI chat (\"${sanitizeForHud(text)}\"), save your Gemini / ChatGPT / Claude / Custom API Key in Settings.",
                    spokenReply = "जी $userName, फोन कंट्रोल और मेमोरी कमांड ऑफलाइन काम कर रहे हैं। किसी भी सवाल के एआई जवाब के लिए सेटिंग्स में अपनी एपीआई की सेव करें।"
                )
            }
        }
    }

    private fun matchCustomVoiceCommand(
        input: String,
        userName: String,
        assistantName: String
    ): ParsedCommand? {
        val cleanInput = input.trim().lowercase()
        val romanInput = HindiTransliterator.toRoman(cleanInput)
        val customList = prefs.getCustomCommands()
        for (cmd in customList) {
            val triggerClean = cmd.triggerPhrase.trim().lowercase()
            if (triggerClean.isEmpty()) continue
            val triggerRoman = HindiTransliterator.toRoman(triggerClean)
            if (cleanInput == triggerClean ||
                cleanInput.contains(triggerClean) ||
                (triggerRoman.length >= 3 && romanInput.contains(triggerRoman))
            ) {
                val replyText = cmd.spokenResponse
                    .replace("{user}", userName, ignoreCase = true)
                    .replace("{assistant}", assistantName, ignoreCase = true)
                    .replace("{developer}", SuaPreferences.DEVELOPER_NAME, ignoreCase = true)
                return ParsedCommand(
                    action = DeviceActionType.CUSTOM_CMD,
                    target = cmd.triggerPhrase,
                    displayReply = "$assistantName CUSTOM CMD // Trigger: \"${cmd.triggerPhrase}\" -> $replyText",
                    spokenReply = replyText
                )
            }
        }
        return null
    }

    /**
     * Handles Developer Attribution ("किसने बनाया", "कौन डेवलप किया है", "MR. SHASHANK")
     * as well as User Name and Assistant Name queries.
     */
    private fun parseDeveloperAndIdentityIntent(
        input: String,
        userName: String,
        assistantName: String
    ): ParsedCommand? {
        val text = input.trim()
        val lower = text.lowercase()

        // 1. Developer query: "किसने बनाया", "कौन डेवलप किया", "who developed you", "who made you", "developer kaun hai"
        val isDeveloperQuery = lower.contains("kisne banaya") ||
            lower.contains("kisne develop") ||
            lower.contains("kaun develop") ||
            lower.contains("kon develop") ||
            lower.contains("who developed") ||
            lower.contains("who created") ||
            lower.contains("who made you") ||
            lower.contains("who built you") ||
            lower.contains("your developer") ||
            lower.contains("your creator") ||
            lower.contains("tumhara developer") ||
            lower.contains("tera developer") ||
            lower.contains("developer kaun") ||
            lower.contains("developer name") ||
            text.contains("किसने बनाया") ||
            text.contains("किसने डेवलप") ||
            text.contains("कौन डेवलप") ||
            text.contains("डेवलप कौन") ||
            text.contains("बनाने वाला कौन") ||
            text.contains("डेवलपर कौन") ||
            text.contains("मालिक कौन")

        if (isDeveloperQuery) {
            return ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "DEVELOPER // I was proudly developed & engineered by ${SuaPreferences.DEVELOPER_NAME}.",
                spokenReply = "मुझे मिस्टर शशांक (MR. SHASHANK) ने बहुत प्यार और स्मार्ट टेक्नोलॉजी से डेवलप किया है! मेरे डेवलपर मिस्टर शशांक हैं।"
            )
        }

        // 2. Assistant Name query ("तुम्हारा नाम क्या है?" / "What is your name?")
        val isAssistantNameQuery = lower.contains("tumhara naam") ||
            lower.contains("tera naam") ||
            lower.contains("your name") ||
            lower.contains("who are you") ||
            text.contains("तुम्हारा नाम") ||
            text.contains("तेरा नाम") ||
            text.contains("आपका नाम") ||
            text.contains("तुम कौन हो")
        if (isAssistantNameQuery) {
            return ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$assistantName AI // My name is $assistantName (Wake-word: 'Ok $assistantName' / 'Hi $assistantName'). Developed by ${SuaPreferences.DEVELOPER_NAME} for $userName.",
                spokenReply = "मेरा नाम $assistantName है $userName! मुझे मिस्टर शशांक ने डेवलप किया है, और आप कभी भी ओके $assistantName या हाय $assistantName बोलकर मुझसे प्यार से बात कर सकते हैं।"
            )
        }

        // 3. Changing Assistant Name by voice ("तुम्हारा नाम ... रख दो")
        val setAssistantNameMatch = Regex(
            "(?:तुम्हारा नाम|आज से तुम्हारा नाम|change your name to|set your name to|tumhara naam)\\s+([\\p{L}a-zA-Z0-9]{2,18})(?:\\s+रख दो|\\s+है|$)",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (setAssistantNameMatch != null && !lower.contains("kya") && !text.contains("क्या")) {
            val newBotName = setAssistantNameMatch.groupValues[1].trim()
            if (newBotName.isNotEmpty()) {
                prefs.setAssistantName(newBotName)
                return ParsedCommand(
                    action = DeviceActionType.MEMORY_SAVE,
                    displayReply = "WAKE-WORD UPDATED // Assistant name changed to '$newBotName'. Say 'Ok $newBotName' or 'Hi $newBotName'.",
                    spokenReply = "जी $userName, अब से मेरा नाम $newBotName है! अब आप ओके $newBotName या हाय $newBotName बोलकर मुझे बुला सकते हैं।"
                )
            }
        }

        return null
    }

    /**
     * Handles local Memory storage & recall ("याद रखना...", "Remember that...", "मेरा नाम ... है", "मेरा नाम क्या है?")
     */
    fun parseMemoryIntentLocally(
        input: String,
        userName: String = prefs.getUserName(),
        assistantName: String = prefs.getAssistantName()
    ): ParsedCommand? {
        val text = input.trim()
        val lower = text.lowercase()

        // 1. Check if user is sharing their name ("मेरा नाम शशांक है" / "My name is Rahul")
        val nameHiMatch = Regex(
            "(?:मेरा नाम|mera naam|my name is)\\s+([\\p{L}a-zA-Z0-9.\\s]{2,25}?)(?:\\s+है|\\s+hai|$)",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (nameHiMatch != null && !lower.contains("kya") && !text.contains("क्या")) {
            val newUserName = nameHiMatch.groupValues[1].trim()
            if (newUserName.isNotEmpty()) {
                prefs.setUserName(newUserName)
                val fact = "User's name is $newUserName"
                SuaStateRepository.addMemoryFact(fact, isAuto = true, prefs = prefs)
                return ParsedCommand(
                    action = DeviceActionType.MEMORY_SAVE,
                    memoryFact = fact,
                    displayReply = "USER PROFILE UPDATED // Active user set to '$newUserName' & saved in Memory.",
                    spokenReply = "जी $newUserName, मैंने बहुत प्यार से याद रख लिया है कि आपका नाम $newUserName है!"
                )
            }
        }

        // 2. Explicit "Remember that..." / "याद रखना कि..." / "याद रखो..."
        val rememberMatch = Regex(
            "^(?:moko\\s+|hi\\s+moko\\s+|ok\\s+moko\\s+|मोको\\s+|हाय\\s+मोको\\s+)?(?:याद रखना कि|याद रखना|याद रखो कि|याद रखो|ये बात याद रखना|remember that|remember|yaad rakhna ki|yaad rakhna|yaad rakho)\\s+(.+)$",
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
                    displayReply = "MEMORY SAVED // Stored in $assistantName Memory Vault: \"$cleanFact\"",
                    spokenReply = "जी $userName, मैंने यह बात अपनी मेमोरी में हमेशा के लिए याद रख ली है: $fact"
                )
            }
        }

        // 3. User Name Recall ("मेरा नाम क्या है?" / "कौन यूजर यूज कर रहा है?" / "What is my name?")
        val isAskingUserName = lower.contains("mera naam kya hai") ||
            text.contains("मेरा नाम क्या है") ||
            text.contains("कौन यूज कर रहा") ||
            text.contains("यूजर का नाम") ||
            lower.contains("what is my name") ||
            lower.contains("who am i")
        if (isAskingUserName) {
            return ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "USER IDENTITY // Current User: $userName | Developer: ${SuaPreferences.DEVELOPER_NAME}",
                spokenReply = "आपका नाम $userName है! और मुझे मिस्टर शशांक (MR. SHASHANK) ने डेवलप किया है।"
            )
        }

        // 4. Memory Vault summary query ("तुम्हें क्या याद है?" / "What do you remember?")
        val isAskingMemories = lower.contains("kya yaad hai") || text.contains("क्या याद है") ||
            lower.contains("what do you remember") || lower.contains("show my memory") ||
            text.contains("मेमोरी बताओ") || lower.contains("meri memory")
        if (isAskingMemories) {
            val memories = SuaStateRepository.memoryItems.value
            return if (memories.isEmpty()) {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "$assistantName MEMORY // User: $userName | Developer: ${SuaPreferences.DEVELOPER_NAME}. Say 'Remember that...' to store extra facts.",
                    spokenReply = "जी $userName, मुझे याद है कि आपका नाम $userName है और मुझे मिस्टर शशांक ने बनाया है। आप 'याद रखना कि...' बोलकर कोई भी नई बात सेव कर सकते हैं।"
                )
            } else {
                val summary = memories.take(3).joinToString(" | ") { it.factText }
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "$assistantName MEMORY (${memories.size} saved) // $summary",
                    spokenReply = "जी $userName, मुझे आपके बारे में ${memories.size} खास बातें याद हैं, जैसे कि: ${memories.first().factText}"
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

    fun parseHardwareIntentLocally(
        input: String,
        userName: String = prefs.getUserName(),
        assistantName: String = prefs.getAssistantName()
    ): ParsedCommand? {
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
                    displayReply = "$assistantName OPTICS // Flashlight turned OFF.",
                    spokenReply = "जी $userName, मैंने फ्लैशलाइट बंद कर दी है।"
                )
            } else {
                ParsedCommand(
                    action = DeviceActionType.FLASHLIGHT_ON,
                    displayReply = "$assistantName OPTICS // Flashlight turned ON.",
                    spokenReply = "जी $userName, मैंने फ्लैशलाइट चालू कर दी है।"
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
                displayReply = "$assistantName SECURITY // Locking phone screen now.",
                spokenReply = "जी $userName, आपका फोन लॉक किया जा रहा है।"
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
                    displayReply = "$assistantName DISPLAY // Brightness set to 100%.",
                    spokenReply = "जी $userName, ब्राइटनेस फुल 100 प्रतिशत कर दी गई है।"
                )
                extractedNum in 5..100 -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_SET,
                    numericValue = extractedNum,
                    displayReply = "$assistantName DISPLAY // Brightness set to $extractedNum%.",
                    spokenReply = "जी $userName, ब्राइटनेस $extractedNum प्रतिशत कर दी गई है।"
                )
                isDown -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_DOWN,
                    displayReply = "$assistantName DISPLAY // Decreasing brightness by 25%.",
                    spokenReply = "जी $userName, स्क्रीन की ब्राइटनेस कम कर दी गई है।"
                )
                isUp -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_UP,
                    displayReply = "$assistantName DISPLAY // Increasing brightness by 25%.",
                    spokenReply = "जी $userName, स्क्रीन की ब्राइटनेस बढ़ा दी गई है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_UP,
                    displayReply = "$assistantName DISPLAY // Adjusting brightness.",
                    spokenReply = "जी $userName, ब्राइटनेस एडजस्ट कर दी गई है।"
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
                    displayReply = "$assistantName AUDIO // System sound muted (0%).",
                    spokenReply = "जी $userName, फोन का साउंड म्यूट कर दिया गया है।"
                )
                lower.contains("full") || text.contains("फुल") -> ParsedCommand(
                    action = DeviceActionType.VOLUME_SET,
                    numericValue = 100,
                    displayReply = "$assistantName AUDIO // Volume set to 100%.",
                    spokenReply = "जी $userName, साउंड फुल सौ प्रतिशत कर दिया गया है।"
                )
                extractedNum in 0..100 -> ParsedCommand(
                    action = DeviceActionType.VOLUME_SET,
                    numericValue = extractedNum,
                    displayReply = "$assistantName AUDIO // Volume set to $extractedNum%.",
                    spokenReply = "जी $userName, साउंड $extractedNum प्रतिशत कर दिया गया है।"
                )
                isDown -> ParsedCommand(
                    action = DeviceActionType.VOLUME_DOWN,
                    displayReply = "$assistantName AUDIO // Decreasing volume by 20%.",
                    spokenReply = "जी $userName, फोन की आवाज़ कम कर दी गई है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.VOLUME_UP,
                    displayReply = "$assistantName AUDIO // Increasing volume by 20%.",
                    spokenReply = "जी $userName, फोन का साउंड बढ़ा दिया गया है।"
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
                    displayReply = "$assistantName MEDIA // Skipping to next track.",
                    spokenReply = "जी $userName, अगला गाना चलाया जा रहा है।"
                )
                isPrev -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PREV,
                    displayReply = "$assistantName MEDIA // Playing previous track.",
                    spokenReply = "जी $userName, पिछला गाना चलाया जा रहा है।"
                )
                isStop -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PAUSE,
                    displayReply = "$assistantName MEDIA // Music playback paused.",
                    spokenReply = "जी $userName, म्यूजिक रोक दिया गया है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PLAY,
                    displayReply = "$assistantName MEDIA // Starting music playback.",
                    spokenReply = "जी $userName, म्यूजिक चालू किया जा रहा है।"
                )
            }
        }

        // 6. OPEN SETTINGS
        if (lower.contains("open setting") || text.contains("सेटिंग खोल") || text.contains("सेटिंग्स खोल")) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_SETTINGS,
                displayReply = "$assistantName SYSTEM // Opening Android Settings.",
                spokenReply = "जी $userName, फोन की सेटिंग्स खोली जा रही हैं।"
            )
        }

        // 7. OPEN ANY APP BY VOICE ("WhatsApp खोलो", "Open YouTube", "Instagram चालू करो", "Open Camera", etc.)
        val appTarget = extractAppToOpen(text)
        if (appTarget != null) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_APP,
                target = appTarget,
                displayReply = "$assistantName LAUNCHER // Opening app: $appTarget",
                spokenReply = "जी $userName, $appTarget खोला जा रहा है।"
            )
        }

        // 8. SMS / MESSAGE
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
                displayReply = "$assistantName SMS // Target: $hudName",
                spokenReply = ""
            )
        }

        // 9. PHONE CALL
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
                displayReply = "$assistantName CALL // Target: $hudName",
                spokenReply = ""
            )
        }

        // 10. BATTERY
        if (Regex("\\bbattery\\b").containsMatchIn(lower) || text.contains("बैटरी") ||
            (text.contains("चार्ज") && (text.contains("कितना") || text.contains("कितनी")))
        ) {
            return ParsedCommand(
                action = DeviceActionType.BATTERY_STATUS,
                displayReply = "",
                spokenReply = ""
            )
        }

        // 11. ALARM
        if (Regex("\\balarm\\b").containsMatchIn(lower) || text.contains("अलार्म")) {
            val (hour24, minute) = parseAlarmHourAndMinute(text)
            val timeFormatted = String.format("%02d:%02d", hour24, minute)
            return ParsedCommand(
                action = DeviceActionType.SET_ALARM,
                numericValue = hour24,
                secondaryNumericValue = minute,
                target = "$assistantName Alarm",
                displayReply = "$assistantName ALARM // Setting alarm for $timeFormatted.",
                spokenReply = if (minute > 0) {
                    "जी $userName, $hour24 बजकर $minute मिनट का अलार्म सेट किया जा रहा है।"
                } else {
                    "जी $userName, $hour24 बजे का अलार्म सेट किया जा रहा है।"
                }
            )
        }

        // 12. TIME / DATE
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

        return null
    }

    private fun extractAppToOpen(text: String): String? {
        val lower = text.lowercase()
        val knownApps = listOf(
            "whatsapp" to listOf("whatsapp", "व्हाट्सएप", "वाट्सएप", "व्हाट्सऐप"),
            "youtube" to listOf("youtube", "यूट्यूब", "यू ट्यूब"),
            "instagram" to listOf("instagram", "इंस्टाग्राम", "इंस्टा", "insta"),
            "camera" to listOf("camera", "कैमरा", "फोटो खींचो"),
            "chrome" to listOf("chrome", "क्रोम", "browser", "ब्राउज़र"),
            "maps" to listOf("google maps", "maps", "मैप्स", "मैप", "लोकेशन ट्रैक"),
            "calculator" to listOf("calculator", "कैलकुलेटर"),
            "gmail" to listOf("gmail", "जीमेल", "ईमेल"),
            "telegram" to listOf("telegram", "टेलीग्राम"),
            "facebook" to listOf("facebook", "फेसबुक"),
            "spotify" to listOf("spotify", "स्पॉटिफाई"),
            "contacts" to listOf("contacts", "कॉन्टैक्ट्स"),
            "dialer" to listOf("dialer", "डायलर")
        )
        val hasOpenVerb = lower.contains("open") || lower.contains("launch") ||
            lower.contains("kholo") || lower.contains("khol") || lower.contains("chalu") ||
            lower.contains("start") || lower.contains("track") ||
            text.contains("खोलो") || text.contains("खोल") || text.contains("चालू") ||
            text.contains("ओपन") || text.contains("ट्रैक")

        if (hasOpenVerb) {
            for ((canonical, aliases) in knownApps) {
                if (aliases.any { lower.contains(it) || text.contains(it) }) {
                    return canonical.replaceFirstChar { it.uppercase() }
                }
            }

            val enMatch = Regex("(?i)\\b(?:open|launch|start)\\s+([a-zA-Z0-9\\s]{2,20}?)(?:\\s+app)?$").find(text)
            if (enMatch != null) {
                val candidate = enMatch.groupValues[1].trim()
                if (candidate.lowercase() !in listOf("settings", "setting")) {
                    return candidate.replaceFirstChar { it.uppercase() }
                }
            }

            val hiMatch = Regex("([\\p{L}a-zA-Z0-9]+)\\s+(?:ऐप\\s+|app\\s+)?(?:खोलो|खोल दो|चालू करो|kholo|khol do|open karo)", RegexOption.IGNORE_CASE).find(text)
            if (hiMatch != null) {
                val rawApp = hiMatch.groupValues[1].trim()
                val romanApp = HindiTransliterator.toRoman(rawApp).replaceFirstChar { it.uppercase() }
                return romanApp.ifEmpty { rawApp }
            }
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
            Regex("(?i)(?:mera\\s+|meri\\s+|moko\\s+|hi\\s+moko\\s+|hey\\s+moko\\s+|ok\\s+moko\\s+)?([a-zA-Z\\p{L}]+(?:\\s+[a-zA-Z\\p{L}]+)?)\\s+ko\\s+(?:call|phone|fone|message|sms|msg)").find(text)
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
                Regex("(?i)^(hey\\s+moko|hi\\s+moko|ok\\s+moko|hello\\s+moko|suno\\s+moko|moko|moco|हाय\\s+मोको|हे\\s+मोको|ओके\\s+मोको|सुनो\\s+मोको|मोको|मेरा|मेरी|मेरे|जरा|ज़रा|प्लीज|please)\\s+"),
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

    private fun buildSystemPrompt(userName: String, assistantName: String): String {
        val savedMemories = SuaStateRepository.memoryItems.value
            .take(15)
            .joinToString("\n") { "- ${it.factText}" }
            .ifBlank { "No additional saved memories yet." }

        val recentConversation = SuaStateRepository.commandLogs.value
            .take(6)
            .reversed()
            .joinToString("\n") { "User ($userName): ${it.userCommand} | $assistantName: ${it.suaResponse}" }
            .ifBlank { "No prior conversation in this session." }

        return """
            You are $assistantName, a loving, sweet, ultra-smart personal AI voice assistant for Android.
            CRITICAL IDENTITY & DEVELOPER FACTS:
            1. Your name is $assistantName. Never call yourself Jarvis or SUA.
            2. You were developed and created by ${SuaPreferences.DEVELOPER_NAME} (मिस्टर शशांक). Whenever anyone asks who developed you, who created you, or who your developer is, you MUST proudly say "${SuaPreferences.DEVELOPER_NAME}".
            3. The current user's name is "$userName". Speak to $userName warmly, affectionately, and respectfully like a caring, sweet friend ("प्यार से बात करें").
            4. Use the SAVED USER MEMORIES and RECENT CONVERSATION HISTORY below:
            [SAVED USER MEMORIES]:
            $savedMemories
            
            [RECENT CONVERSATION HISTORY]:
            $recentConversation
            
            5. Return ONLY a valid JSON object matching this exact schema:
            {
              "action": "CALL" | "SMS" | "FLASHLIGHT_ON" | "FLASHLIGHT_OFF" | "LOCK_PHONE" | "BRIGHTNESS_UP" | "BRIGHTNESS_DOWN" | "BRIGHTNESS_SET" | "VOLUME_UP" | "VOLUME_DOWN" | "VOLUME_SET" | "VOLUME_MUTE" | "MEDIA_PLAY" | "MEDIA_PAUSE" | "MEDIA_NEXT" | "MEDIA_PREV" | "BATTERY_STATUS" | "SET_ALARM" | "OPEN_SETTINGS" | "OPEN_APP" | "TIME_DATE" | "MEMORY_SAVE" | "AI_CHAT",
              "target": "contact name in English Roman script (e.g. 'Rahul') if CALL/SMS, or App name (e.g. 'WhatsApp', 'YouTube') if OPEN_APP, otherwise empty string",
              "message": "SMS text content if action is SMS, otherwise empty string",
              "numericValue": integer (0-100 for brightness/volume, 0-23 for alarm 24h hour, or -1 if not applicable),
              "secondaryNumericValue": integer (0-59 for alarm minutes, default 0),
              "memoryFact": "Short English summary if user shared a new personal fact/preference to remember, else empty string",
              "displayReply": "Concise, sharp response in clean English for the on-screen HUD card.",
              "spokenReply": "Sweet, affectionate, natural spoken response in Hindi (Devanagari) addressing $userName warmly."
            }
        """.trimIndent()
    }

    private fun parseAiJsonResponse(rawText: String, assistantName: String, userName: String): ParsedCommand {
        val cleanJson = rawText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonStart = cleanJson.indexOf('{')
        val jsonEnd = cleanJson.lastIndexOf('}')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            try {
                val parsedObj = JSONObject(cleanJson.substring(jsonStart, jsonEnd + 1))
                val actionStr = parsedObj.optString("action", "AI_CHAT").uppercase()
                val actionType = try {
                    DeviceActionType.valueOf(actionStr)
                } catch (_: Exception) {
                    DeviceActionType.AI_CHAT
                }
                val displayEn = parsedObj.optString("displayReply", "")
                    .ifBlank { "$assistantName // Command processed." }
                val spokenHi = parsedObj.optString("spokenReply", "")
                    .ifBlank { "जी $userName, आपका काम पूरा कर दिया गया है।" }
                val extractedMemory = parsedObj.optString("memoryFact", "").trim()

                return ParsedCommand(
                    action = actionType,
                    target = parsedObj.optString("target", ""),
                    message = parsedObj.optString("message", ""),
                    numericValue = parsedObj.optInt("numericValue", -1),
                    secondaryNumericValue = parsedObj.optInt("secondaryNumericValue", 0),
                    memoryFact = extractedMemory,
                    displayReply = displayEn,
                    spokenReply = spokenHi
                )
            } catch (_: Exception) {
            }
        }

        // Plain-text fallback if a custom API returned plain text instead of JSON
        return ParsedCommand(
            action = DeviceActionType.AI_CHAT,
            displayReply = rawText.take(280),
            spokenReply = rawText.take(240)
        )
    }

    private fun callActiveAiProvider(
        userCommand: String,
        apiKey: String,
        customUrl: String,
        userName: String,
        assistantName: String
    ): AiCallOutcome {
        val resolvedProvider = prefs.resolveActiveAiProvider()
        return when (resolvedProvider) {
            AiProviderType.CLAUDE -> {
                val endpoint = customUrl.ifBlank { "https://api.anthropic.com/v1/messages" }
                callClaudeEndpoint(userCommand, apiKey, endpoint, userName, assistantName)
            }
            AiProviderType.OPENAI -> {
                val defaultEndpoint = if (apiKey.startsWith("gsk_")) {
                    "https://api.groq.com/openai/v1/chat/completions"
                } else {
                    "https://api.openai.com/v1/chat/completions"
                }
                val endpoint = customUrl.ifBlank { defaultEndpoint }
                val defaultModel = if (apiKey.startsWith("gsk_")) "llama-3.3-70b-versatile" else "gpt-4o-mini"
                callOpenAiCompatibleEndpoint(
                    userCommand = userCommand,
                    apiKey = apiKey,
                    endpointUrl = endpoint,
                    modelId = defaultModel,
                    providerLabel = if (apiKey.startsWith("gsk_")) "GROQ AI" else "CHATGPT",
                    userName = userName,
                    assistantName = assistantName
                )
            }
            AiProviderType.CUSTOM -> {
                if (customUrl.contains("generativelanguage.googleapis.com")) {
                    callGeminiWithFallback(userCommand, apiKey, userName, assistantName)
                } else if (customUrl.contains("anthropic.com")) {
                    callClaudeEndpoint(userCommand, apiKey, customUrl, userName, assistantName)
                } else {
                    val endpoint = customUrl.ifBlank { "https://api.openai.com/v1/chat/completions" }
                    callOpenAiCompatibleEndpoint(
                        userCommand = userCommand,
                        apiKey = apiKey,
                        endpointUrl = endpoint,
                        modelId = prefs.getGeminiModel(),
                        providerLabel = "CUSTOM API",
                        userName = userName,
                        assistantName = assistantName
                    )
                }
            }
            AiProviderType.GEMINI, AiProviderType.AUTO -> {
                callGeminiWithFallback(userCommand, apiKey, userName, assistantName)
            }
        }
    }

    private fun callOpenAiCompatibleEndpoint(
        userCommand: String,
        apiKey: String,
        endpointUrl: String,
        modelId: String,
        providerLabel: String,
        userName: String,
        assistantName: String
    ): AiCallOutcome {
        return try {
            val systemPrompt = buildSystemPrompt(userName, assistantName)
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userCommand))

            val payload = JSONObject().apply {
                put("model", if (modelId.startsWith("gemini")) "gpt-4o-mini" else modelId)
                put("messages", messages)
                put("temperature", 0.3)
            }

            val reqBuilder = Request.Builder()
                .url(endpointUrl)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addHeader("Content-Type", "application/json")

            if (apiKey.isNotEmpty()) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = try {
                        JSONObject(bodyStr).optJSONObject("error")?.optString("message")
                            ?: "HTTP ${resp.code}"
                    } catch (_: Exception) {
                        "HTTP ${resp.code}"
                    }
                    return AiCallOutcome.Failure(providerLabel, resp.code, errMsg)
                }
                val root = JSONObject(bodyStr)
                val content = root.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "")
                    .orEmpty()

                if (content.isBlank()) {
                    return AiCallOutcome.Failure(providerLabel, 500, "Empty response from $endpointUrl")
                }
                AiCallOutcome.Success(parseAiJsonResponse(content, assistantName, userName))
            }
        } catch (e: Exception) {
            AiCallOutcome.Failure(providerLabel, 0, e.localizedMessage ?: "Connection failed")
        }
    }

    private fun callClaudeEndpoint(
        userCommand: String,
        apiKey: String,
        endpointUrl: String,
        userName: String,
        assistantName: String
    ): AiCallOutcome {
        return try {
            val systemPrompt = buildSystemPrompt(userName, assistantName)
            val payload = JSONObject().apply {
                put("model", "claude-3-5-haiku-latest")
                put("max_tokens", 600)
                put("system", systemPrompt)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user").put("content", userCommand)
                    )
                )
            }
            val request = Request.Builder()
                .url(endpointUrl)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = try {
                        JSONObject(bodyStr).optJSONObject("error")?.optString("message")
                            ?: "HTTP ${resp.code}"
                    } catch (_: Exception) {
                        "HTTP ${resp.code}"
                    }
                    return AiCallOutcome.Failure("CLAUDE AI", resp.code, errMsg)
                }
                val root = JSONObject(bodyStr)
                val textOut = root.optJSONArray("content")
                    ?.optJSONObject(0)
                    ?.optString("text", "")
                    .orEmpty()
                if (textOut.isBlank()) {
                    return AiCallOutcome.Failure("CLAUDE AI", 500, "Empty response from Claude API")
                }
                AiCallOutcome.Success(parseAiJsonResponse(textOut, assistantName, userName))
            }
        } catch (e: Exception) {
            AiCallOutcome.Failure("CLAUDE AI", 0, e.localizedMessage ?: "Connection error")
        }
    }

    private fun callGeminiWithFallback(
        userCommand: String,
        apiKey: String,
        userName: String,
        assistantName: String
    ): AiCallOutcome {
        val preferred = prefs.getGeminiModel()
        val candidateModels = (listOf(preferred) + SuaPreferences.SUPPORTED_GEMINI_MODELS).distinct().toMutableList()
        val triedModels = mutableSetOf<String>()

        var lastFailure = AiCallOutcome.Failure("GEMINI", 0, "Network error")
        var idx = 0
        while (idx < candidateModels.size) {
            val modelId = candidateModels[idx].removePrefix("models/")
            idx++
            if (!triedModels.add(modelId)) continue

            val outcome = callGeminiSingleModel(userCommand, apiKey, modelId, userName, assistantName)
            when (outcome) {
                is AiCallOutcome.Success -> {
                    if (modelId != preferred) {
                        prefs.setGeminiModel(modelId)
                    }
                    return outcome
                }
                is AiCallOutcome.Failure -> {
                    lastFailure = outcome
                    if (outcome.httpCode == 404) {
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

        val discoveredModel = discoverAvailableGeminiModel(apiKey, triedModels)
        if (!discoveredModel.isNullOrBlank()) {
            val outcome = callGeminiSingleModel(userCommand, apiKey, discoveredModel, userName, assistantName)
            if (outcome is AiCallOutcome.Success) {
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
        modelId: String,
        userName: String,
        assistantName: String
    ): AiCallOutcome {
        return try {
            val systemInstruction = buildSystemPrompt(userName, assistantName)

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

            val customUrl = prefs.getCustomApiUrl()
            val url = if (customUrl.isNotEmpty() && customUrl.contains("googleapis.com")) {
                if (customUrl.contains("key=")) customUrl else "$customUrl?key=$apiKey"
            } else {
                "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
            }

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
                    return AiCallOutcome.Failure("GEMINI", response.code, errMsg)
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
                    return AiCallOutcome.Failure("GEMINI", 500, "Empty response from Gemini model $modelId")
                }

                AiCallOutcome.Success(parseAiJsonResponse(textOut, assistantName, userName))
            }
        } catch (e: Exception) {
            AiCallOutcome.Failure("GEMINI", 0, e.localizedMessage ?: "Network failure")
        }
    }
}
