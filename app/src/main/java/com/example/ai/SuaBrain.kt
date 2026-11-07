package com.example.ai

import com.example.control.SuaDeviceController
import com.example.data.AiProviderType
import com.example.data.ConfirmationStep
import com.example.data.DeviceActionType
import com.example.data.MokoOperatingMode
import com.example.data.ParsedCommand
import com.example.data.PendingCommsConfirmation
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

class SuaBrain(
    private val prefs: SuaPreferences,
    private val controller: SuaDeviceController? = null
) {

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
        val speakEn = HindiTransliterator.isPrimarilyEnglish(text)

        if (text.isEmpty()) {
            return@withContext ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$assistantName is listening. Say 'Ok $assistantName' or 'Hi $assistantName' followed by your command.",
                spokenReply = if (speakEn) {
                    "Yes $userName, I am $assistantName and I am listening closely. Tell me what you would like me to do."
                } else {
                    "जी $userName, मैं $assistantName बड़े प्यार से सुन रही हूँ। बताइए क्या काम करना है?"
                }
            )
        }

        // 00. Instant STOP / CANCEL Command ("स्टॉप", "रुको", "बस करो", "चुप हो जाओ", "stop", "ruk jao")
        if (HindiTransliterator.isStopCommand(text, assistantName)) {
            SuaStateRepository.setPendingComms(null)
            return@withContext ParsedCommand(
                action = DeviceActionType.STOP_ACTION,
                displayReply = "STOPPED // Current task stopped. Listening for your next command...",
                spokenReply = ""
            )
        }

        // 0A. Check if there is an active Multi-Step Call / SMS / WhatsApp Confirmation waiting for user reply
        val activePending = SuaStateRepository.pendingComms.value
        if (activePending != null) {
            val pendingReply = handleActiveCommsConfirmation(text, activePending, userName, assistantName, speakEn)
            if (pendingReply != null) {
                return@withContext pendingReply
            }
        }

        // 0B. Check if user is asking to read out / speak the contact phone numbers ("दोनों नंबर बोलो" / "नंबर बोलो" / "speak both numbers")
        val readNumbersReply = parseSpeakPhoneNumbersIntent(text, userName, assistantName, speakEn)
        if (readNumbersReply != null) {
            return@withContext readNumbersReply
        }

        // 0C. Check User-Defined Custom Voice Commands (exact or substring trigger match)
        val customCmdMatch = matchCustomVoiceCommand(text, userName, assistantName)
        if (customCmdMatch != null) {
            return@withContext customCmdMatch
        }

        // 1. Check Developer Attribution ("किसने बनाया", "कौन डेवलप किया", "who developed you", etc.)
        val devIdentityMatch = parseDeveloperAndIdentityIntent(text, userName, assistantName, speakEn)
        if (devIdentityMatch != null) {
            return@withContext devIdentityMatch
        }

        // 2. Check if user is explicitly asking to remember a fact or asking what is remembered
        val localMemoryCmd = parseMemoryIntentLocally(text, userName, assistantName)
        if (localMemoryCmd != null) {
            return@withContext localMemoryCmd
        }

        // 3. Check local Hardware, App Launch, Call, SMS & WhatsApp intents
        val localMatch = parseHardwareIntentLocally(text, userName, assistantName)
        val apiKey = prefs.getEffectiveApiKey()
        val customUrl = prefs.getCustomApiUrl()

        // Fast-path unambiguous non-named hardware & app commands for instant <50ms response
        if (localMatch != null &&
            localMatch.action != DeviceActionType.CALL &&
            localMatch.action != DeviceActionType.SMS &&
            localMatch.action != DeviceActionType.WHATSAPP_MSG
        ) {
            return@withContext localMatch
        }

        // If local parser matched CALL, SMS, or WHATSAPP_MSG and we have a target name, run our interactive contact & confirmation flow!
        if (localMatch != null &&
            (localMatch.action == DeviceActionType.CALL ||
                localMatch.action == DeviceActionType.SMS ||
                localMatch.action == DeviceActionType.WHATSAPP_MSG) &&
            localMatch.target.isNotBlank()
        ) {
            return@withContext initiateInteractiveCommsFlow(localMatch, text, userName, assistantName, speakEn)
        }

        // In SLEEP MODE: Only offline hardware, Call, SMS, WhatsApp, Bluetooth, Internet & App commands run (no open AI Q&A!)
        val isSleepHardwareOnlyMode = SuaStateRepository.operatingMode.value == MokoOperatingMode.SLEEP
        if (isSleepHardwareOnlyMode) {
            if (localMatch != null) {
                return@withContext initiateInteractiveCommsFlow(localMatch, text, userName, assistantName, speakEn)
            }
            return@withContext ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$assistantName SLEEP MODE (OFFLINE HARDWARE ONLY) // Open AI Q&A is disabled in Sleep Mode. Hardware controls (Call, Bluetooth, Internet, Torch, Lock, Volume, Brightness, Apps) are active. Tap ONLINE for AI Q&A.",
                spokenReply = if (speakEn) {
                    "Yes $userName, I am currently in Sleep Mode where offline commands like Calling, Bluetooth, Internet, Flashlight, and Volume work. Switch to Online Mode for open AI questions."
                } else {
                    "जी $userName, अभी मैं स्लीप मोड में हूँ। इस मोड में बिना इंटरनेट वाले काम जैसे कॉल करना, ब्लूटूथ ऑफ करना, इंटरनेट बंद करना और फोन कंट्रोल काम करते हैं। सवाल-जवाब के लिए ऑनलाइन मोड चालू करें।"
                }
            )
        }

        // 4. For open-ended queries in ONLINE mode: call the active AI Provider
        var lastApiFailure: AiCallOutcome.Failure? = null
        if (apiKey.isNotEmpty() || customUrl.isNotEmpty()) {
            when (val outcome = callActiveAiProvider(text, apiKey, customUrl, userName, assistantName, speakEn)) {
                is AiCallOutcome.Success -> {
                    val aiCmd = outcome.command
                    if (aiCmd.memoryFact.isNotBlank()) {
                        SuaStateRepository.addMemoryFact(
                            factText = aiCmd.memoryFact,
                            isAuto = true,
                            prefs = prefs
                        )
                    }
                    if (aiCmd.action == DeviceActionType.CALL ||
                        aiCmd.action == DeviceActionType.SMS ||
                        aiCmd.action == DeviceActionType.WHATSAPP_MSG
                    ) {
                        return@withContext initiateInteractiveCommsFlow(aiCmd, text, userName, assistantName, speakEn)
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

        // If local parser matched CALL, SMS, or WHATSAPP_MSG, run interactive flow
        if (localMatch != null) {
            return@withContext initiateInteractiveCommsFlow(localMatch, text, userName, assistantName, speakEn)
        }

        // Report AI error honestly if key/URL was configured
        if (lastApiFailure != null) {
            val code = lastApiFailure.httpCode
            val detail = lastApiFailure.errorDetail
            val prov = lastApiFailure.providerLabel
            return@withContext ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$prov API ERROR (HTTP $code) // $detail. Check your API Key or Endpoint Link in Settings.",
                spokenReply = if (speakEn) {
                    when (code) {
                        400, 401, 403 -> "Sorry $userName, your $prov API key or endpoint link is invalid. Please check your API key in Settings."
                        429 -> "Sorry $userName, your API key quota has been exceeded."
                        else -> "Sorry $userName, there was a network issue connecting to the AI server."
                    }
                } else {
                    when (code) {
                        400, 401, 403 -> "माफ़ कीजिए $userName, आपकी $prov एपीआई की या लिंक मान्य नहीं है। कृपया सेटिंग्स में जाकर अपनी एपीआई की चेक करें।"
                        429 -> "माफ़ कीजिए $userName, आपकी एपीआई की का कोटा पूरा हो गया है।"
                        else -> "माफ़ कीजिए $userName, एआई सर्वर से जुड़ने में समस्या आई है।"
                    }
                }
            )
        }

        // Conversational & Memory-aware fallback when offline / no API key
        val lower = text.lowercase()
        val memories = SuaStateRepository.memoryItems.value
        return@withContext when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("namaste") ||
                text.contains("नमस्ते") || text.contains("हेलो") || lower.contains("kaise ho") ||
                lower.contains("how are you") || text.contains("कैसे हो") || text.contains("कैसी हो") -> {
                val memGreeting = if (memories.isNotEmpty()) {
                    " Remembering ${memories.size} facts about $userName."
                } else ""
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "$assistantName ONLINE // Hello $userName! Developed by ${SuaPreferences.DEVELOPER_NAME}.$memGreeting",
                    spokenReply = if (speakEn) {
                        "Hello $userName! I am your sweet assistant $assistantName, doing great and ready to help you. What can I do for you?"
                    } else {
                        "नमस्ते $userName! मैं आपकी प्यारी $assistantName एकदम ठीक हूँ और आपकी सेवा में हाज़िर हूँ। बताइए क्या काम करूँ?"
                    }
                )
            }
            else -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "AI KEY REQUIRED // Hardware, Contacts, Calls, WhatsApp & Memory work offline. For open AI chat (\"${sanitizeForHud(text)}\"), save your Gemini / ChatGPT / Claude / Custom API Key in Settings.",
                    spokenReply = if (speakEn) {
                        "Yes $userName, phone controls, contacts, calling, messaging, and memory commands work offline. For open AI questions, please save your API key in Settings."
                    } else {
                        "जी $userName, फोन कंट्रोल, कॉल, मैसेज और मेमोरी कमांड ऑफलाइन काम कर रहे हैं। किसी भी सवाल के एआई जवाब के लिए सेटिंग्स में अपनी एपीआई की सेव करें।"
                    }
                )
            }
        }
    }

    /**
     * Initiates interactive Call / SMS / WhatsApp confirmation & multi-number selection.
     * - For CALL:
     *   - If 2+ numbers exist for the contact, speaks both numbers and asks which one to call.
     *   - If 1 number exists, places the call immediately.
     * - For SMS or WHATSAPP_MSG:
     *   - Always confirms with the user whether to message that contact, reads out or offers to read numbers,
     *     and asks whether to write the message in Hindi or English and what message to write.
     */
    private fun initiateInteractiveCommsFlow(
        cmd: ParsedCommand,
        rawText: String,
        userName: String,
        assistantName: String,
        speakEn: Boolean
    ): ParsedCommand {
        val devCtrl = controller ?: return cmd
        val rawTarget = cmd.target.trim()
        if (rawTarget.isEmpty()) return cmd

        val resolution = devCtrl.resolvePhoneNumber(rawTarget)
        val allNums = resolution.allPhoneNumbers
        val dispName = resolution.displayName.ifEmpty { rawTarget }

        if (allNums.isEmpty()) {
            // Contact not found yet -> let SuaDeviceController handle the permission/not-found message
            return cmd
        }

        // Record resolved contact so "दोनों नंबर बोलो" / "नंबर बोलो" works anytime
        SuaStateRepository.recordResolvedContact(dispName, allNums)

        // Check if the user already specified Hindi or English script in their initial command
        val detectedLang = detectExplicitLanguageChoice(rawText)

        if (cmd.action == DeviceActionType.CALL) {
            if (allNums.size > 1) {
                val num1Speech = HindiTransliterator.formatPhoneForSpeech(allNums[0], speakEn)
                val num2Speech = HindiTransliterator.formatPhoneForSpeech(allNums[1], speakEn)
                SuaStateRepository.setPendingComms(
                    PendingCommsConfirmation(
                        actionType = DeviceActionType.CALL,
                        contactName = dispName,
                        phoneNumbers = allNums,
                        selectedNumber = "",
                        messageBody = "",
                        scriptPreference = "",
                        step = ConfirmationStep.SELECT_NUMBER,
                        preferEnglishReply = speakEn
                    )
                )
                return ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "$assistantName CALL CONFIRMATION // $dispName has ${allNums.size} numbers: (1) ${allNums[0]} | (2) ${allNums[1]}. Say 'First number' or 'Second number'.",
                    spokenReply = if (speakEn) {
                        "$userName, $dispName has two phone numbers saved. The first number is $num1Speech, and the second number is $num2Speech. Which number should I call, first or second?"
                    } else {
                        "जी $userName, $dispName के दो नंबर सेव हैं। पहला नंबर है $num1Speech, और दूसरा नंबर है $num2Speech। बताइए पहले नंबर पर कॉल लगाऊँ या दूसरे नंबर पर?"
                    }
                )
            } else {
                // Exactly 1 number -> dial directly!
                return cmd.copy(target = allNums.first())
            }
        }

        // SMS or WHATSAPP_MSG: Always confirm with the user first, handle multiple numbers, and ask Hindi vs English!
        val channelLabelEn = if (cmd.action == DeviceActionType.WHATSAPP_MSG) "WhatsApp message" else "SMS message"
        val channelLabelHi = if (cmd.action == DeviceActionType.WHATSAPP_MSG) "व्हाट्सएप मैसेज" else "मैसेज"

        if (allNums.size > 1) {
            val num1Speech = HindiTransliterator.formatPhoneForSpeech(allNums[0], speakEn)
            val num2Speech = HindiTransliterator.formatPhoneForSpeech(allNums[1], speakEn)
            SuaStateRepository.setPendingComms(
                PendingCommsConfirmation(
                    actionType = cmd.action,
                    contactName = dispName,
                    phoneNumbers = allNums,
                    selectedNumber = "",
                    messageBody = cmd.message,
                    scriptPreference = detectedLang,
                    step = ConfirmationStep.SELECT_NUMBER,
                    preferEnglishReply = speakEn
                )
            )
            return ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$assistantName ${cmd.action.label.uppercase()} CONFIRM // $dispName has 2 numbers: (1) ${allNums[0]} | (2) ${allNums[1]}. Choose 1st or 2nd number, and Hindi or English.",
                spokenReply = if (speakEn) {
                    "Do you want to send a $channelLabelEn to $dispName? They have two numbers: first is $num1Speech, and second is $num2Speech. Tell me which number to use, whether to type in Hindi or English, and your message."
                } else {
                    "जी $userName, क्या आप $dispName को $channelLabelHi करना चाहते हैं? इनके दो नंबर सेव हैं: पहला नंबर $num1Speech और दूसरा नंबर $num2Speech। बताइए किस नंबर पर भेजना है, और मैसेज हिंदी में टाइप करना है या इंग्लिश में?"
                }
            )
        } else {
            val singlePhone = allNums.first()
            // If the user already spoke the message body in their command, send it immediately!
            if (cmd.message.isNotBlank()) {
                val chosenLang = detectedLang.ifEmpty { if (speakEn) "ENGLISH" else "HINDI" }
                val formattedMsg = if (chosenLang == "HINDI") {
                    HindiTransliterator.toHindiDevanagari(cmd.message)
                } else {
                    val hasDevanagari = cmd.message.any { it.code in 0x0900..0x097F }
                    if (hasDevanagari) {
                        HindiTransliterator.toRoman(cmd.message).replaceFirstChar { it.uppercase() }
                    } else {
                        cmd.message
                    }
                }
                SuaStateRepository.setPendingComms(null)
                return cmd.copy(
                    target = singlePhone,
                    message = formattedMsg,
                    displayReply = "$assistantName ${cmd.action.label.uppercase()} // Sending to $dispName ($singlePhone): \"$formattedMsg\"",
                    spokenReply = if (speakEn) {
                        "Sending your $channelLabelEn to $dispName now."
                    } else {
                        "जी $userName, $dispName को $channelLabelHi भेजा जा रहा है।"
                    }
                )
            }

            SuaStateRepository.setPendingComms(
                PendingCommsConfirmation(
                    actionType = cmd.action,
                    contactName = dispName,
                    phoneNumbers = allNums,
                    selectedNumber = singlePhone,
                    messageBody = "",
                    scriptPreference = detectedLang,
                    step = ConfirmationStep.CONFIRM_AND_DETAILS,
                    preferEnglishReply = speakEn
                )
            )
            return ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$assistantName ${cmd.action.label.uppercase()} CONFIRM // Recipient: $dispName ($singlePhone). Speak your message (in Hindi or English).",
                spokenReply = if (speakEn) {
                    "Do you want to send a $channelLabelEn to $dispName? Tell me whether to type in Hindi or English, and what message you want to send."
                } else {
                    "जी $userName, क्या आप $dispName को $channelLabelHi करना चाहते हैं? बताइए मैसेज हिंदी में लिखना है या इंग्लिश में, और क्या मैसेज भेजना है?"
                }
            )
        }
    }

    /**
     * Handles the user's spoken/typed response during an active multi-step Call, SMS, or WhatsApp confirmation.
     */
    private fun handleActiveCommsConfirmation(
        input: String,
        pending: PendingCommsConfirmation,
        userName: String,
        assistantName: String,
        speakEn: Boolean
    ): ParsedCommand? {
        val text = input.trim()
        val lower = text.lowercase()

        // 1. Check if user wants to CANCEL ("नहीं", "कैंसिल", "रहने दो", "cancel", "no", "stop")
        val isCancel = Regex("\\b(cancel|stop|abort|nevermind|rehne do|mat karo|mat bhejo)\\b").containsMatchIn(lower) ||
            lower == "no" || lower == "nahi" || lower == "na" ||
            text == "नहीं" || text.contains("कैंसिल") || text.contains("रहने दो") || text.contains("मत भेजो") || text.contains("मत करो")
        if (isCancel) {
            SuaStateRepository.setPendingComms(null)
            return ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$assistantName CANCELLED // Cancelled ${pending.actionType.label} to ${pending.contactName}.",
                spokenReply = if (speakEn) {
                    "Okay $userName, I have cancelled the ${pending.actionType.label} to ${pending.contactName}."
                } else {
                    "ठीक है $userName, मैंने ${pending.contactName} का ${if (pending.actionType == DeviceActionType.CALL) "कॉल" else "मैसेज"} कैंसिल कर दिया है।"
                }
            )
        }

        // 2. Check if user asks to SPEAK / READ THE NUMBERS ("नंबर बोलो", "दोनों नंबर बोलो", "speak both numbers", "tell me the number")
        val asksToSpeakNumbers = lower.contains("number bolo") || lower.contains("dono number") ||
            lower.contains("speak number") || lower.contains("speak both") || lower.contains("read number") ||
            lower.contains("tell me the number") || lower.contains("kya number") ||
            text.contains("नंबर बोलो") || text.contains("दोनों नंबर") || text.contains("नंबर बताओ") || text.contains("कौन सा नंबर")
        if (asksToSpeakNumbers) {
            return buildSpeakNumbersResponse(pending.contactName, pending.phoneNumbers, userName, assistantName, speakEn, keepPending = true)
        }

        // 3. Check if user is choosing between 1st and 2nd number
        var chosenPhone = pending.selectedNumber
        if (pending.phoneNumbers.size > 1 && chosenPhone.isEmpty()) {
            val pickedIdx = when {
                lower.contains("first") || lower.contains("pehla") || lower.contains("pahla") ||
                    lower.contains("1st") || lower == "1" || lower.contains("number 1") ||
                    text.contains("पहला") || text.contains("पहले") || text.contains("एक नंबर") -> 0
                lower.contains("second") || lower.contains("dusra") || lower.contains("doosra") ||
                    lower.contains("2nd") || lower == "2" || lower.contains("number 2") ||
                    text.contains("दूसरा") || text.contains("दूसरे") || text.contains("दो नंबर") -> 1
                else -> {
                    val spokenDigits = text.replace(Regex("[^0-9]"), "")
                    if (spokenDigits.length >= 3) {
                        pending.phoneNumbers.indexOfFirst { it.endsWith(spokenDigits) || it.contains(spokenDigits) }
                    } else -1
                }
            }
            if (pickedIdx in pending.phoneNumbers.indices) {
                chosenPhone = pending.phoneNumbers[pickedIdx]
                if (pending.actionType == DeviceActionType.CALL) {
                    SuaStateRepository.setPendingComms(null)
                    return ParsedCommand(
                        action = DeviceActionType.CALL,
                        target = chosenPhone,
                        displayReply = "$assistantName CALL // Calling ${pending.contactName} (${pickedIdx + 1}: $chosenPhone)",
                        spokenReply = if (speakEn) {
                            "Calling ${pending.contactName} on number ${pickedIdx + 1} now."
                        } else {
                            "जी $userName, ${pending.contactName} के ${if (pickedIdx == 0) "पहले" else "दूसरे"} नंबर पर कॉल लगाया जा रहा है।"
                        }
                    )
                }
            } else if (pending.actionType == DeviceActionType.CALL) {
                val num1Speech = HindiTransliterator.formatPhoneForSpeech(pending.phoneNumbers[0], speakEn)
                val num2Speech = HindiTransliterator.formatPhoneForSpeech(pending.phoneNumbers[1], speakEn)
                return ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "$assistantName CALL // Please say 'First number' (${pending.phoneNumbers[0]}) or 'Second number' (${pending.phoneNumbers[1]}).",
                    spokenReply = if (speakEn) {
                        "Please tell me whether to call the first number $num1Speech or the second number $num2Speech."
                    } else {
                        "कृपया बताइए, पहला नंबर $num1Speech लगाऊँ या दूसरा नंबर $num2Speech?"
                    }
                )
            }
        }

        if (chosenPhone.isEmpty() && pending.phoneNumbers.isNotEmpty()) {
            chosenPhone = pending.phoneNumbers.first()
        }

        // 4. For SMS or WHATSAPP_MSG: detect language choice (Hindi vs English) & message content
        val langInThisTurn = detectExplicitLanguageChoice(text)
        val effectiveLang = langInThisTurn.ifEmpty { pending.scriptPreference }

        val cleanedMsgFromTurn = stripConfirmationAndLanguageWords(text)
        val effectiveMessage = when {
            cleanedMsgFromTurn.isNotBlank() -> cleanedMsgFromTurn
            else -> pending.messageBody
        }

        // If user only confirmed ("हाँ", "yes") or only picked a number, ask for language (if missing) & message body
        if (effectiveLang.isEmpty() && effectiveMessage.isEmpty()) {
            SuaStateRepository.setPendingComms(
                pending.copy(
                    selectedNumber = chosenPhone,
                    step = ConfirmationStep.CONFIRM_AND_DETAILS
                )
            )
            return ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$assistantName ${pending.actionType.label.uppercase()} // Recipient confirmed: ${pending.contactName} ($chosenPhone). Say 'In Hindi' or 'In English' and what message to write.",
                spokenReply = if (speakEn) {
                    "Got it $userName. Should I type the message to ${pending.contactName} in Hindi or English, and what should I write?"
                } else {
                    "जी $userName, ${pending.contactName} को मैसेज हिंदी में टाइप करके लिखना है या इंग्लिश में, और क्या लिखना है?"
                }
            )
        }

        if (effectiveLang.isNotEmpty() && effectiveMessage.isEmpty()) {
            SuaStateRepository.setPendingComms(
                pending.copy(
                    selectedNumber = chosenPhone,
                    scriptPreference = effectiveLang,
                    step = ConfirmationStep.CONFIRM_AND_DETAILS
                )
            )
            val langNameHi = if (effectiveLang == "HINDI") "हिंदी" else "इंग्लिश"
            return ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$assistantName ${pending.actionType.label.uppercase()} // Language set to $effectiveLang for ${pending.contactName}. Now speak or type your message.",
                spokenReply = if (speakEn) {
                    "Okay, I will type it in ${effectiveLang.lowercase()}. What message should I send to ${pending.contactName}?"
                } else {
                    "ठीक है $userName, मैं $langNameHi में टाइप करूँगी। बताइए ${pending.contactName} को क्या मैसेज लिखकर भेजना है?"
                }
            )
        }

        // Once we have the message body, resolve language (explicit choice or default to Hindi/English based on speech) and send immediately!
        val finalLang = effectiveLang.ifEmpty { if (speakEn) "ENGLISH" else "HINDI" }

        val finalFormattedMessage = if (finalLang == "HINDI") {
            HindiTransliterator.toHindiDevanagari(effectiveMessage)
        } else {
            val hasDevanagari = effectiveMessage.any { it.code in 0x0900..0x097F }
            if (hasDevanagari) {
                HindiTransliterator.toRoman(effectiveMessage).replaceFirstChar { it.uppercase() }
            } else {
                effectiveMessage
            }
        }

        SuaStateRepository.setPendingComms(null)
        return ParsedCommand(
            action = pending.actionType,
            target = chosenPhone,
            message = finalFormattedMessage,
            displayReply = "$assistantName ${pending.actionType.label.uppercase()} // Sending ($finalLang) to ${pending.contactName} ($chosenPhone): \"$finalFormattedMessage\"",
            spokenReply = if (speakEn) {
                "Sending your ${pending.actionType.label} in ${finalLang.lowercase()} to ${pending.contactName} now."
            } else {
                "जी $userName, ${pending.contactName} को ${if (finalLang == "HINDI") "हिंदी" else "इंग्लिश"} में मैसेज भेजा जा रहा है।"
            }
        )
    }

    private fun detectExplicitLanguageChoice(text: String): String {
        val lower = text.lowercase()
        val wantsHindi = Regex("\\b(hindi|devanagari)\\b").containsMatchIn(lower) ||
            text.contains("हिंदी") || text.contains("हिन्दी")
        val wantsEnglish = Regex("\\b(english|roman|inglish)\\b").containsMatchIn(lower) ||
            text.contains("इंग्लिश") || text.contains("अंग्रेजी") || text.contains("अंग्रेज़ी")
        return when {
            wantsHindi && !wantsEnglish -> "HINDI"
            wantsEnglish && !wantsHindi -> "ENGLISH"
            else -> ""
        }
    }

    private fun stripConfirmationAndLanguageWords(text: String): String {
        var cleaned = text.trim()
        val patterns = listOf(
            Regex("^(?i)(yes|yeah|haan|ha|han|ji|ok|okay|sure|हाँ|हां|जी|ठीक है|भेज दो|सेंड करो)[,\\s]*"),
            Regex("^(?i)(first number|second number|number 1|number 2|pehla number|dusra number|पहला नंबर|दूसरा नंबर|पहले नंबर पर|दूसरे नंबर पर)[,\\s]*"),
            Regex("^(?i)(in hindi|in english|hindi mein|hindi me|english mein|english me|type in hindi|type in english|write in hindi|write in english|हिंदी में लिखो|हिंदी में टाइप करो|हिंदी में|इंग्लिश में लिखो|इंग्लिश में टाइप करो|इंग्लिश में|अंग्रेजी में)[,:\\s]*"),
            Regex("(?i)\\s*(in hindi|in english|hindi mein|english mein|हिंदी में|इंग्लिश में)$"),
            Regex("^(?i)(ki|that|likho|type karo|write|say|बोलो कि|लिखो कि|लिखो)[,:\\s]*")
        )
        for (p in patterns) {
            cleaned = cleaned.replace(p, "").trim()
        }
        val onlyLangOrConfirm = cleaned.lowercase() in setOf(
            "yes", "haan", "ha", "ji", "ok", "hindi", "english", "first", "second", "1", "2",
            "हाँ", "हां", "जी", "हिंदी", "हिन्दी", "इंग्लिश", "अंग्रेजी", "पहला", "दूसरा"
        )
        return if (onlyLangOrConfirm) "" else cleaned
    }

    /**
     * Handles standalone "दोनों नंबर बोलो" / "नंबर बोलो" / "राहुल का नंबर बोलो" / "speak both numbers"
     */
    private fun parseSpeakPhoneNumbersIntent(
        text: String,
        userName: String,
        assistantName: String,
        speakEn: Boolean
    ): ParsedCommand? {
        val lower = text.lowercase()
        val asksNumber = lower.contains("number bolo") || lower.contains("dono number") ||
            lower.contains("number batao") || lower.contains("speak both number") ||
            lower.contains("speak the number") || lower.contains("read both number") ||
            lower.contains("tell me both number") ||
            text.contains("नंबर बोलो") || text.contains("दोनों नंबर") || text.contains("नंबर सुनाओ") ||
            text.contains("नंबर बताओ")

        if (!asksNumber) return null

        val namedContact = extractTargetNameForCallOrSms(
            text.replace("नंबर बोलो", "को कॉल")
                .replace("दोनों नंबर बोलो", "को कॉल")
                .replace("नंबर बताओ", "को कॉल")
        )
        if (namedContact.isNotBlank() && controller != null) {
            val res = controller.resolvePhoneNumber(namedContact)
            if (res.allPhoneNumbers.isNotEmpty()) {
                val cName = res.displayName.ifEmpty { namedContact }
                SuaStateRepository.recordResolvedContact(cName, res.allPhoneNumbers)
                return buildSpeakNumbersResponse(cName, res.allPhoneNumbers, userName, assistantName, speakEn, keepPending = false)
            }
        }

        val lastNums = SuaStateRepository.lastResolvedNumbers
        val lastName = SuaStateRepository.lastResolvedContactName.ifEmpty { "Contact" }
        if (lastNums.isNotEmpty()) {
            return buildSpeakNumbersResponse(lastName, lastNums, userName, assistantName, speakEn, keepPending = false)
        }

        return ParsedCommand(
            action = DeviceActionType.AI_CHAT,
            displayReply = "$assistantName CONTACTS // Please mention the contact name first (e.g. 'Call Rahul' or 'Message Rahul') so I can read their saved numbers.",
            spokenReply = if (speakEn) {
                "Please tell me the contact name first, $userName, so I can search and read out their phone numbers."
            } else {
                "जी $userName, पहले उस व्यक्ति का नाम बताइए जिसका नंबर आप सुनना चाहते हैं।"
            }
        )
    }

    private fun buildSpeakNumbersResponse(
        contactName: String,
        numbers: List<String>,
        userName: String,
        assistantName: String,
        speakEn: Boolean,
        keepPending: Boolean
    ): ParsedCommand {
        if (!keepPending) {
            SuaStateRepository.setPendingComms(null)
        }
        return if (numbers.size >= 2) {
            val n1 = HindiTransliterator.formatPhoneForSpeech(numbers[0], speakEn)
            val n2 = HindiTransliterator.formatPhoneForSpeech(numbers[1], speakEn)
            ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$assistantName CONTACT NUMBERS // $contactName: (1) ${numbers[0]} | (2) ${numbers[1]}",
                spokenReply = if (speakEn) {
                    "Here are both numbers for $contactName: The first number is $n1, and the second number is $n2."
                } else {
                    "जी $userName, $contactName के दोनों नंबर सुनिए: पहला नंबर है $n1, और दूसरा नंबर है $n2।"
                }
            )
        } else {
            val n1 = HindiTransliterator.formatPhoneForSpeech(numbers.first(), speakEn)
            ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "$assistantName CONTACT NUMBER // $contactName: ${numbers.first()}",
                spokenReply = if (speakEn) {
                    "$contactName has one saved number: $n1."
                } else {
                    "जी $userName, $contactName का नंबर है: $n1।"
                }
            )
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
     * as well as User Name and Assistant Name queries in both Hindi and English.
     */
    private fun parseDeveloperAndIdentityIntent(
        input: String,
        userName: String,
        assistantName: String,
        speakEn: Boolean
    ): ParsedCommand? {
        val text = input.trim()
        val lower = text.lowercase()

        val isDeveloperQuery = lower.contains("kisne banaya") ||
            lower.contains("kisne develop") ||
            lower.contains("kaun develop") ||
            lower.contains("kon develop") ||
            lower.contains("tumhe kaun") ||
            lower.contains("tumhen kaun") ||
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
            text.contains("डेवलप किया") ||
            text.contains("डेवलप कौन") ||
            text.contains("बनाने वाला कौन") ||
            text.contains("डेवलपर कौन") ||
            text.contains("डेवलपर का नाम") ||
            text.contains("मालिक कौन")

        if (isDeveloperQuery) {
            return ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "DEVELOPER // I was proudly developed & engineered by ${SuaPreferences.DEVELOPER_NAME}.",
                spokenReply = if (speakEn) {
                    "I was proudly developed and engineered by ${SuaPreferences.DEVELOPER_NAME}!"
                } else {
                    "मुझे ${SuaPreferences.DEVELOPER_NAME} (मिस्टर शशांक) ने डेवलप किया है! मेरे डेवलपर ${SuaPreferences.DEVELOPER_NAME} हैं।"
                }
            )
        }

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
                spokenReply = if (speakEn) {
                    "My name is $assistantName, $userName! I was developed by Mister Shashank, and you can call me anytime by saying Ok $assistantName or Hi $assistantName."
                } else {
                    "मेरा नाम $assistantName है $userName! मुझे मिस्टर शशांक ने डेवलप किया है, और आप कभी भी ओके $assistantName या हाय $assistantName बोलकर मुझसे प्यार से बात कर सकते हैं।"
                }
            )
        }

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
                    spokenReply = if (speakEn) {
                        "Got it $userName! From now on my name is $newBotName. You can wake me by saying Ok $newBotName."
                    } else {
                        "जी $userName, अब से मेरा नाम $newBotName है! अब आप ओके $newBotName या हाय $newBotName बोलकर मुझे बुला सकते हैं।"
                    }
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
        val speakEn = HindiTransliterator.isPrimarilyEnglish(text)

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
                    spokenReply = if (speakEn) {
                        "Lovely to meet you, $newUserName! I have saved your name in my memory."
                    } else {
                        "जी $newUserName, मैंने बहुत प्यार से याद रख लिया है कि आपका नाम $newUserName है!"
                    }
                )
            }
        }

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
                    spokenReply = if (speakEn) {
                        "Okay $userName, I have saved this in my permanent memory: $cleanFact"
                    } else {
                        "जी $userName, मैंने यह बात अपनी मेमोरी में हमेशा के लिए याद रख ली है: $fact"
                    }
                )
            }
        }

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
                spokenReply = if (speakEn) {
                    "Your name is $userName! And I was developed by Mister Shashank."
                } else {
                    "आपका नाम $userName है! और मुझे मिस्टर शशांक (MR. SHASHANK) ने डेवलप किया है।"
                }
            )
        }

        val isAskingMemories = lower.contains("kya yaad hai") || text.contains("क्या याद है") ||
            lower.contains("what do you remember") || lower.contains("show my memory") ||
            text.contains("मेमोरी बताओ") || lower.contains("meri memory")
        if (isAskingMemories) {
            val memories = SuaStateRepository.memoryItems.value
            return if (memories.isEmpty()) {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "$assistantName MEMORY // User: $userName | Developer: ${SuaPreferences.DEVELOPER_NAME}. Say 'Remember that...' to store extra facts.",
                    spokenReply = if (speakEn) {
                        "Yes $userName, I remember that your name is $userName and I was created by Mister Shashank. You can say 'Remember that...' to save any new fact."
                    } else {
                        "जी $userName, मुझे याद है कि आपका नाम $userName है और मुझे मिस्टर शशांक ने बनाया है। आप 'याद रखना कि...' बोलकर कोई भी नई बात सेव कर सकते हैं।"
                    }
                )
            } else {
                val summary = memories.take(3).joinToString(" | ") { it.factText }
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "$assistantName MEMORY (${memories.size} saved) // $summary",
                    spokenReply = if (speakEn) {
                        "Yes $userName, I remember ${memories.size} facts about you, including: ${memories.first().factText}"
                    } else {
                        "जी $userName, मुझे आपके बारे में ${memories.size} खास बातें याद हैं, जैसे कि: ${memories.first().factText}"
                    }
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
        val speakEn = HindiTransliterator.isPrimarilyEnglish(text)
        val numberRegex = Regex("(\\d{1,3})")
        val extractedNum = numberRegex.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1

        // 00. Instant STOP / CANCEL Command ("स्टॉप", "रुको", "बस करो", "stop")
        if (HindiTransliterator.isStopCommand(text, assistantName)) {
            SuaStateRepository.setPendingComms(null)
            return ParsedCommand(
                action = DeviceActionType.STOP_ACTION,
                displayReply = "STOPPED // Current task stopped. Listening for your next command...",
                spokenReply = ""
            )
        }

        // 0A. SCREEN SHARE & SCREEN ANALYSIS ("स्क्रीन शेयर करो", "स्क्रीन देखो", "स्क्रीन एनालाइज करो", "Share screen", "Analyze screen")
        val hasScreenShareIntent = lower.contains("screen share") || lower.contains("share screen") ||
            lower.contains("analyze screen") || lower.contains("analyse screen") ||
            lower.contains("read screen") || lower.contains("what is on screen") ||
            lower.contains("what is on my screen") || lower.contains("screen dekho") ||
            lower.contains("screen par kya hai") ||
            text.contains("स्क्रीन शेयर") || text.contains("स्क्रीन देखो") ||
            text.contains("स्क्रीन एनालाइज") || text.contains("स्क्रीन पर क्या है") ||
            text.contains("स्क्रीन पढ़ो")
        if (hasScreenShareIntent) {
            val isStopShare = Regex("\\b(off|band|stop|close|disable)\\b").containsMatchIn(lower) ||
                text.contains("बंद") || text.contains("रोको") || text.contains("ऑफ")
            val battery = controller?.getBatteryPercentage() ?: 88
            return if (isStopShare) {
                ParsedCommand(
                    action = DeviceActionType.SCREEN_SHARE_ANALYZE,
                    target = "OFF",
                    displayReply = "SCREEN SHARE OFF // Live screen analysis stopped.",
                    spokenReply = if (speakEn) "Stopped screen sharing, $userName." else "जी $userName, स्क्रीन शेयर बंद कर दिया गया है।"
                )
            } else {
                ParsedCommand(
                    action = DeviceActionType.SCREEN_SHARE_ANALYZE,
                    target = "ON",
                    displayReply = SuaStateRepository.buildCurrentScreenSnapshotSummary(prefs, battery),
                    spokenReply = if (speakEn) {
                        SuaStateRepository.buildCurrentScreenSpokenSummaryEn(prefs, battery)
                    } else {
                        SuaStateRepository.buildCurrentScreenSpokenSummaryHi(prefs, battery)
                    }
                )
            }
        }

        // 0B. VOICE CLICK ("इसे क्लिक करो", "सेटिंग पर क्लिक करो", "हिस्ट्री पर क्लिक करो", "Click Settings", "Tap Unlock")
        val hasClickVerb = Regex("\\b(click|tap|press)\\b").containsMatchIn(lower) ||
            lower.contains("click karo") || lower.contains("tap karo") ||
            text.contains("क्लिक करो") || text.contains("क्लिक") || text.contains("दबाओ") || text.contains("टैप करो")
        if (hasClickVerb) {
            val cleanedTarget = text
                .replace(Regex("(?i)\\b(please|jara|moko|click|tap|press|on|button|par|pe|ko|karo|kar|do|इसे|इस पर|पर|को|बटन|क्लिक|दबाओ|टैप|करो|कर दो)\\b"), " ")
                .trim()
                .ifEmpty { "Settings" }
            return ParsedCommand(
                action = DeviceActionType.VOICE_CLICK,
                target = cleanedTarget,
                displayReply = "$assistantName VOICE CLICK // Clicking '$cleanedTarget' on screen.",
                spokenReply = if (speakEn) "Clicking $cleanedTarget on your screen, $userName." else "जी $userName, '$cleanedTarget' पर क्लिक किया जा रहा है।"
            )
        }

        // 0C. VOICE UNLOCK ("लॉक खोलो", "स्क्रीन अनलॉक करो", "लॉक हटाओ", "Unlock screen", "Unlock phone", "Open lock")
        val isUnlockCommand = lower.contains("unlock") || lower.contains("lock kholo") ||
            lower.contains("lock khol") || lower.contains("lock hatao") || lower.contains("open lock") ||
            lower.contains("screen kholo") ||
            text.contains("लॉक खोलो") || text.contains("लॉक खोल") ||
            text.contains("अनलॉक") || text.contains("लॉक हटाओ") || text.contains("स्क्रीन खोलो")
        if (isUnlockCommand) {
            return ParsedCommand(
                action = DeviceActionType.UNLOCK_PHONE,
                displayReply = "$assistantName SECURITY // Unlocking screen & dismissing lock overlay.",
                spokenReply = if (speakEn) "Unlocking your screen now, $userName." else "जी $userName, मैंने लॉक खोल दिया है और स्क्रीन अनलॉक कर दी है।"
            )
        }

        // 0D. VOICE MODE SWITCH ("स्लीप मोड में जाओ", "ऑनलाइन हो जाओ", "ऑफलाइन हो जाओ", "Switch to sleep mode", "Go offline")
        val asksSleepMode = lower.contains("sleep mode") || text.contains("स्लीप मोड")
        val asksOfflineMode = lower.contains("offline mode") || lower.contains("go offline") ||
            text.contains("ऑफलाइन मोड") || text.contains("ऑफलाइन हो जाओ")
        val asksOnlineMode = lower.contains("online mode") || lower.contains("go online") ||
            text.contains("ऑनलाइन मोड") || text.contains("ऑनलाइन हो जाओ")
        if (asksSleepMode || asksOfflineMode || asksOnlineMode) {
            val targetMode = when {
                asksOfflineMode -> "OFFLINE"
                asksSleepMode -> "SLEEP"
                else -> "ONLINE"
            }
            return ParsedCommand(
                action = DeviceActionType.SWITCH_MODE,
                target = targetMode,
                displayReply = "$assistantName MODE // Switched to $targetMode mode.",
                spokenReply = when (targetMode) {
                    "SLEEP" -> if (speakEn) "Switching to Sleep Mode, $userName. Offline hardware and calling commands are active." else "जी $userName, अब मैं स्लीप मोड में हूँ। बिना इंटरनेट वाले फोन कंट्रोल और कॉल काम करेंगे।"
                    "OFFLINE" -> if (speakEn) "Going completely offline now, $userName. Background microphone stopped." else "जी $userName, अब मैं पूरी तरह ऑफलाइन हो रही हूँ।"
                    else -> if (speakEn) "Switching to Online Mode, $userName." else "जी $userName, अब मैं ऑनलाइन मोड में हूँ।"
                }
            )
        }

        // 1. BLUETOOTH ON / OFF ("ब्लूटूथ ऑफ करो", "Turn off Bluetooth", "Bluetooth chalu karo")
        val hasBluetoothWord = lower.contains("bluetooth") || text.contains("ब्लूटूथ")
        if (hasBluetoothWord) {
            val isBtOff = Regex("\\b(off|band|disable|stop|close)\\b").containsMatchIn(lower) ||
                text.contains("बंद") || text.contains("ऑफ") || text.contains("हटा")
            return if (isBtOff) {
                ParsedCommand(
                    action = DeviceActionType.BLUETOOTH_OFF,
                    displayReply = "$assistantName BLUETOOTH // Turning Bluetooth OFF.",
                    spokenReply = if (speakEn) "Turning off Bluetooth, $userName." else "जी $userName, ब्लूटूथ बंद किया जा रहा है।"
                )
            } else {
                ParsedCommand(
                    action = DeviceActionType.BLUETOOTH_ON,
                    displayReply = "$assistantName BLUETOOTH // Turning Bluetooth ON.",
                    spokenReply = if (speakEn) "Turning on Bluetooth, $userName." else "जी $userName, ब्लूटूथ चालू किया जा रहा है।"
                )
            }
        }

        // 2. INTERNET / WI-FI / MOBILE DATA ON / OFF ("इंटरनेट बंद करो", "Wi-Fi off karo", "Turn off internet")
        val hasInternetWord = lower.contains("internet") || lower.contains("wifi") || lower.contains("wi-fi") ||
            lower.contains("mobile data") || lower.contains("net band") || lower.contains("net chalu") ||
            lower.contains("data band") || lower.contains("data on") || lower.contains("data off") ||
            text.contains("इंटरनेट") || text.contains("वाई-फाई") || text.contains("वाईफाई") ||
            text.contains("नेट बंद") || text.contains("नेट चालू") || text.contains("डेटा बंद") || text.contains("डेटा चालू")
        if (hasInternetWord) {
            val isNetOff = Regex("\\b(off|band|disable|stop|close)\\b").containsMatchIn(lower) ||
                text.contains("बंद") || text.contains("ऑफ")
            return if (isNetOff) {
                ParsedCommand(
                    action = DeviceActionType.INTERNET_OFF,
                    displayReply = "$assistantName CONNECTIVITY // Turning Internet / Wi-Fi OFF.",
                    spokenReply = if (speakEn) "Opening Internet and Wi-Fi controls to turn it off, $userName." else "जी $userName, इंटरनेट और वाई-फाई बंद किया जा रहा है।"
                )
            } else {
                ParsedCommand(
                    action = DeviceActionType.INTERNET_ON,
                    displayReply = "$assistantName CONNECTIVITY // Turning Internet / Wi-Fi ON.",
                    spokenReply = if (speakEn) "Turning on Internet and Wi-Fi, $userName." else "जी $userName, इंटरनेट और वाई-फाई चालू किया जा रहा है।"
                )
            }
        }

        // 2B. OFFLINE SYSTEM SETTINGS (Hotspot, Location/GPS, Airplane Mode, Sound/Vibrate/Silent Mode, DND, Display, Battery Saver, Security, Storage)
        if (lower.contains("hotspot") || lower.contains("tethering") || text.contains("हॉटस्पॉट")) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_SYSTEM_SETTING,
                target = "HOTSPOT",
                displayReply = "$assistantName HOTSPOT // Opening Hotspot & Tethering Settings.",
                spokenReply = if (speakEn) "Opening Hotspot settings, $userName." else "जी $userName, हॉटस्पॉट सेटिंग्स खोली जा रही हैं।"
            )
        }
        if (lower.contains("location") || lower.contains("gps") || text.contains("लोकेशन") || text.contains("जीपीएस")) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_SYSTEM_SETTING,
                target = "LOCATION",
                displayReply = "$assistantName LOCATION // Opening Location & GPS Settings.",
                spokenReply = if (speakEn) "Opening Location and GPS settings, $userName." else "जी $userName, लोकेशन और जीपीएस सेटिंग्स खोली जा रही हैं।"
            )
        }
        if (lower.contains("airplane") || lower.contains("flight mode") || lower.contains("aeroplane") ||
            text.contains("एयरप्लेन") || text.contains("फ्लाइट मोड")
        ) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_SYSTEM_SETTING,
                target = "AIRPLANE",
                displayReply = "$assistantName AIRPLANE MODE // Opening Airplane Mode Settings.",
                spokenReply = if (speakEn) "Opening Airplane Mode settings, $userName." else "जी $userName, एयरप्लेन मोड सेटिंग्स खोली जा रही हैं।"
            )
        }
        if (lower.contains("vibrate mode") || lower.contains("vibration mode") || lower.contains("phone vibrate") ||
            text.contains("वाइब्रेट मोड") || text.contains("वाइब्रेट करो") || text.contains("फोन वाइब्रेट")
        ) {
            return ParsedCommand(
                action = DeviceActionType.RINGER_MODE_SET,
                target = "VIBRATE",
                displayReply = "$assistantName RINGER // Switching phone to VIBRATE mode.",
                spokenReply = if (speakEn) "Switched phone to Vibrate mode, $userName." else "जी $userName, फोन वाइब्रेट मोड में कर दिया गया है।"
            )
        }
        if (lower.contains("silent mode") || lower.contains("phone silent") || lower.contains("silent karo") ||
            lower.contains("do not disturb") || lower.contains("dnd") ||
            text.contains("साइलेंट मोड") || text.contains("फोन साइलेंट") || text.contains("साइलेंट करो") ||
            text.contains("डू नॉट डिस्टर्ब")
        ) {
            val isOff = Regex("\\b(off|band|disable|hato|hatao)\\b").containsMatchIn(lower) ||
                text.contains("बंद") || text.contains("हटा")
            return ParsedCommand(
                action = DeviceActionType.RINGER_MODE_SET,
                target = if (isOff) "NORMAL" else "SILENT",
                displayReply = if (isOff) "$assistantName RINGER // Restored NORMAL RING mode." else "$assistantName RINGER // Switched phone to SILENT mode.",
                spokenReply = if (isOff) {
                    if (speakEn) "Restored normal ring mode, $userName." else "जी $userName, साइलेंट मोड हटाकर नॉर्मल रिंग मोड चालू कर दिया गया है।"
                } else {
                    if (speakEn) "Switched phone to Silent mode, $userName." else "जी $userName, फोन साइलेंट मोड पर कर दिया गया है।"
                }
            )
        }
        if (lower.contains("ring mode") || lower.contains("normal mode") || text.contains("रिंग मोड") || text.contains("नॉर्मल मोड")) {
            return ParsedCommand(
                action = DeviceActionType.RINGER_MODE_SET,
                target = "NORMAL",
                displayReply = "$assistantName RINGER // Restored NORMAL RING mode.",
                spokenReply = if (speakEn) "Restored normal ring mode, $userName." else "जी $userName, फोन नॉर्मल रिंग मोड पर चालू कर दिया गया है।"
            )
        }
        if (lower.contains("battery saver") || lower.contains("power saver") || text.contains("बैटरी सेवर")) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_SYSTEM_SETTING,
                target = "BATTERY",
                displayReply = "$assistantName POWER // Opening Battery Saver Settings.",
                spokenReply = if (speakEn) "Opening Battery Saver settings, $userName." else "जी $userName, बैटरी सेवर सेटिंग्स खोली जा रही हैं।"
            )
        }
        if (lower.contains("display setting") || lower.contains("auto rotate") || text.contains("डिस्प्ले सेटिंग") || text.contains("ऑटो रोटेट")) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_SYSTEM_SETTING,
                target = "DISPLAY",
                displayReply = "$assistantName DISPLAY // Opening Display Settings.",
                spokenReply = if (speakEn) "Opening Display settings, $userName." else "जी $userName, डिस्प्ले सेटिंग्स खोली जा रही हैं।"
            )
        }
        if (lower.contains("security setting") || lower.contains("password setting") || text.contains("सिक्योरिटी सेटिंग") || text.contains("पासवर्ड सेटिंग")) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_SYSTEM_SETTING,
                target = "SECURITY",
                displayReply = "$assistantName SECURITY // Opening Security & Password Settings.",
                spokenReply = if (speakEn) "Opening Security settings, $userName." else "जी $userName, सिक्योरिटी और पासवर्ड सेटिंग्स खोली जा रही हैं।"
            )
        }
        if (lower.contains("storage setting") || text.contains("स्टोरेज सेटिंग")) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_SYSTEM_SETTING,
                target = "STORAGE",
                displayReply = "$assistantName STORAGE // Opening Storage Settings.",
                spokenReply = if (speakEn) "Opening Storage settings, $userName." else "जी $userName, स्टोरेज सेटिंग्स खोली जा रही हैं।"
            )
        }

        // 3. FLASHLIGHT OFF / ON
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
                    spokenReply = if (speakEn) "Okay $userName, I have turned off the flashlight." else "जी $userName, मैंने फ्लैशलाइट बंद कर दी है।"
                )
            } else {
                ParsedCommand(
                    action = DeviceActionType.FLASHLIGHT_ON,
                    displayReply = "$assistantName OPTICS // Flashlight turned ON.",
                    spokenReply = if (speakEn) "Okay $userName, I have turned on the flashlight." else "जी $userName, मैंने फ्लैशलाइट चालू कर दी है।"
                )
            }
        }

        // 4. LOCK PHONE
        val hasLockWord = Regex("\\block\\b").containsMatchIn(lower) || text.contains("लॉक")
        val hasPhoneScreenTarget = lower.contains("phone") || lower.contains("screen") ||
            lower.contains("mobile") || text.contains("फोन") || text.contains("स्क्रीन") ||
            text.contains("मोबाइल") || lower.trim() == "lock" || text.contains("लॉक करो")
        if (hasLockWord && hasPhoneScreenTarget && !isUnlockCommand) {
            return ParsedCommand(
                action = DeviceActionType.LOCK_PHONE,
                displayReply = "$assistantName SECURITY // Locking phone screen now.",
                spokenReply = if (speakEn) "Locking your phone screen now, $userName." else "जी $userName, आपका फोन लॉक किया जा रहा है।"
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
                    spokenReply = if (speakEn) "Brightness set to 100 percent, $userName." else "जी $userName, ब्राइटनेस फुल 100 प्रतिशत कर दी गई है।"
                )
                extractedNum in 5..100 -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_SET,
                    numericValue = extractedNum,
                    displayReply = "$assistantName DISPLAY // Brightness set to $extractedNum%.",
                    spokenReply = if (speakEn) "Brightness set to $extractedNum percent, $userName." else "जी $userName, ब्राइटनेस $extractedNum प्रतिशत कर दी गई है।"
                )
                isDown -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_DOWN,
                    displayReply = "$assistantName DISPLAY // Decreasing brightness by 25%.",
                    spokenReply = if (speakEn) "Decreasing screen brightness, $userName." else "जी $userName, स्क्रीन की ब्राइटनेस कम कर दी गई है।"
                )
                isUp -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_UP,
                    displayReply = "$assistantName DISPLAY // Increasing brightness by 25%.",
                    spokenReply = if (speakEn) "Increasing screen brightness, $userName." else "जी $userName, स्क्रीन की ब्राइटनेस बढ़ा दी गई है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_UP,
                    displayReply = "$assistantName DISPLAY // Adjusting brightness.",
                    spokenReply = if (speakEn) "Adjusting screen brightness, $userName." else "जी $userName, ब्राइटनेस एडजस्ट कर दी गई है।"
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
                    spokenReply = if (speakEn) "Phone volume has been muted, $userName." else "जी $userName, फोन का साउंड म्यूट कर दिया गया है।"
                )
                lower.contains("full") || text.contains("फुल") -> ParsedCommand(
                    action = DeviceActionType.VOLUME_SET,
                    numericValue = 100,
                    displayReply = "$assistantName AUDIO // Volume set to 100%.",
                    spokenReply = if (speakEn) "Volume set to 100 percent, $userName." else "जी $userName, साउंड फुल सौ प्रतिशत कर दिया गया है।"
                )
                extractedNum in 0..100 -> ParsedCommand(
                    action = DeviceActionType.VOLUME_SET,
                    numericValue = extractedNum,
                    displayReply = "$assistantName AUDIO // Volume set to $extractedNum%.",
                    spokenReply = if (speakEn) "Volume set to $extractedNum percent, $userName." else "जी $userName, साउंड $extractedNum प्रतिशत कर दिया गया है।"
                )
                isDown -> ParsedCommand(
                    action = DeviceActionType.VOLUME_DOWN,
                    displayReply = "$assistantName AUDIO // Decreasing volume by 20%.",
                    spokenReply = if (speakEn) "Decreasing phone volume, $userName." else "जी $userName, फोन की आवाज़ कम कर दी गई है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.VOLUME_UP,
                    displayReply = "$assistantName AUDIO // Increasing volume by 20%.",
                    spokenReply = if (speakEn) "Increasing phone volume, $userName." else "जी $userName, फोन का साउंड बढ़ा दिया गया है।"
                )
            }
        }

        // 5A. YOUTUBE MUSIC / SONG / VIDEO PLAYBACK ("यूट्यूब पर म्यूजिक चलाओ", "यूट्यूब पर गाना बजाओ", "Play music on YouTube", "म्यूजिक चलाओ", "गाना बजाओ")
        val hasYouTubeWord = lower.contains("youtube") || lower.contains("you tube") || lower.contains("utube") ||
            text.contains("यूट्यूब") || text.contains("यू ट्यूब") || text.contains("युटुब") || text.contains("यूटुब")
        val hasMusicWord = lower.contains("music") || lower.contains("song") || lower.contains("gana") ||
            lower.contains("gaana") || text.contains("म्यूजिक") || text.contains("गाना") ||
            text.contains("सॉन्ग") || text.contains("गीत") || text.contains("भजन") ||
            lower.contains("next track") || lower.contains("previous track") ||
            lower.contains("play music") || lower.contains("pause music")
        val hasPlayVerb = lower.contains("play") || lower.contains("chalao") || lower.contains("chala") ||
            lower.contains("bajao") || lower.contains("baja") || lower.contains("lagao") ||
            lower.contains("sunao") ||
            text.contains("चलाओ") || text.contains("चला दो") || text.contains("बजाओ") ||
            text.contains("बजा दो") || text.contains("लगाओ") || text.contains("सुनाओ") ||
            text.contains("प्ले करो") || text.contains("प्ले")
        val isPureOpenYouTube = hasYouTubeWord && !hasMusicWord && !hasPlayVerb &&
            (lower.contains("open") || lower.contains("kholo") || lower.contains("khol") ||
                text.contains("खोलो") || text.contains("खोल") || text.contains("ओपन"))

        if (hasYouTubeWord && !isPureOpenYouTube && (hasMusicWord || hasPlayVerb || text.contains("पर") || lower.contains("par") || lower.contains("on"))) {
            val songQuery = extractYouTubeSongQuery(text)
            val displaySong = sanitizeForHud(songQuery)
            return ParsedCommand(
                action = DeviceActionType.PLAY_YOUTUBE,
                target = songQuery,
                displayReply = "$assistantName YOUTUBE // Playing on YouTube: $displaySong",
                spokenReply = if (speakEn) {
                    "Playing $displaySong on YouTube for you, $userName."
                } else {
                    "जी $userName, यूट्यूब पर $songQuery चलाया जा रहा है।"
                }
            )
        }

        // 5B. MUSIC / MEDIA
        if (hasMusicWord) {
            val isStop = lower.contains("stop") || lower.contains("pause") || lower.contains("band") ||
                lower.contains("roko") || text.contains("बंद") || text.contains("रोको") || text.contains("पॉज")
            val isNext = lower.contains("next") || lower.contains("agla") || text.contains("अगला") || text.contains("नेक्स्ट")
            val isPrev = lower.contains("prev") || lower.contains("pichla") || text.contains("पिछला")
            return when {
                isNext -> ParsedCommand(
                    action = DeviceActionType.MEDIA_NEXT,
                    displayReply = "$assistantName MEDIA // Skipping to next track.",
                    spokenReply = if (speakEn) "Playing the next song, $userName." else "जी $userName, अगला गाना चलाया जा रहा है।"
                )
                isPrev -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PREV,
                    displayReply = "$assistantName MEDIA // Playing previous track.",
                    spokenReply = if (speakEn) "Playing the previous song, $userName." else "जी $userName, पिछला गाना चलाया जा रहा है।"
                )
                isStop -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PAUSE,
                    displayReply = "$assistantName MEDIA // Music playback paused.",
                    spokenReply = if (speakEn) "Music playback paused, $userName." else "जी $userName, म्यूजिक रोक दिया गया है।"
                )
                else -> {
                    val songQuery = extractYouTubeSongQuery(text)
                    val displaySong = sanitizeForHud(songQuery)
                    ParsedCommand(
                        action = DeviceActionType.PLAY_YOUTUBE,
                        target = songQuery,
                        displayReply = "$assistantName YOUTUBE MUSIC // Playing: $displaySong",
                        spokenReply = if (speakEn) "Playing $displaySong on YouTube, $userName." else "जी $userName, यूट्यूब पर $songQuery चलाया जा रहा है।"
                    )
                }
            }
        }

        // 6. OPEN SETTINGS
        if (lower.contains("open setting") || text.contains("सेटिंग खोल") || text.contains("सेटिंग्स खोल")) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_SETTINGS,
                displayReply = "$assistantName SYSTEM // Opening Android Settings.",
                spokenReply = if (speakEn) "Opening Android Settings, $userName." else "जी $userName, फोन की सेटिंग्स खोली जा रही हैं।"
            )
        }

        // 7. WHATSAPP MESSAGE ("WhatsApp पर राहुल को मैसेज करो", "राहुल को व्हाट्सएप पर हेलो भेजो", "Send WhatsApp message to Rahul")
        val hasWhatsAppWord = lower.contains("whatsapp") || lower.contains("whats app") ||
            lower.contains("what's app") || lower.contains("watsapp") || lower.contains("whatsap") ||
            text.contains("व्हाट्सएप") || text.contains("वाट्सएप") || text.contains("व्हाट्सऐप") ||
            text.contains("वॉट्सऐप") || text.contains("वॉट्सएप") || text.contains("व्हाट्स अप") || text.contains("वाट्स अप")
        val isJustOpenWhatsApp = hasWhatsAppWord &&
            (lower.contains("open") || lower.contains("kholo") || lower.contains("khol") ||
                text.contains("खोलो") || text.contains("खोल") || text.contains("ओपन") || text.contains("चालू करो")) &&
            !text.contains(" को ") && !Regex("\\b(to|ko)\\b").containsMatchIn(lower)
        val hasMessageIntent = Regex("\\b(message|sms|msg|text|send|bhejo|likho|ko|to)\\b").containsMatchIn(lower) ||
            text.contains("मैसेज") || text.contains("संदेश") || text.contains("भेजो") ||
            text.contains("लिखो") || text.contains(" को ")
        if (hasWhatsAppWord && hasMessageIntent && !isJustOpenWhatsApp) {
            val targetName = extractTargetNameForCallOrSms(text)
            val msgContent = extractMessageBody(text)
            if (targetName.isNotEmpty()) {
                val hudName = HindiTransliterator.toRoman(targetName).replaceFirstChar { it.uppercase() }
                    .ifEmpty { targetName }
                return ParsedCommand(
                    action = DeviceActionType.WHATSAPP_MSG,
                    target = targetName,
                    message = msgContent,
                    displayReply = "$assistantName WHATSAPP // Target: $hudName",
                    spokenReply = ""
                )
            }
        }

        // 8. OPEN ANY APP BY VOICE ("WhatsApp खोलो", "Open YouTube", "Instagram चालू करो", "Open Camera", etc.)
        val appTarget = extractAppToOpen(text)
        if (appTarget != null) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_APP,
                target = appTarget,
                displayReply = "$assistantName LAUNCHER // Opening app: $appTarget",
                spokenReply = if (speakEn) "Opening $appTarget now, $userName." else "जी $userName, $appTarget खोला जा रहा है।"
            )
        }

        // 9. SMS / MESSAGE
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

        // 10. PHONE CALL
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

        // 11. BATTERY
        if (Regex("\\bbattery\\b").containsMatchIn(lower) || text.contains("बैटरी") ||
            (text.contains("चार्ज") && (text.contains("कितना") || text.contains("कितनी")))
        ) {
            return ParsedCommand(
                action = DeviceActionType.BATTERY_STATUS,
                displayReply = "",
                spokenReply = ""
            )
        }

        // 12. ALARM
        if (Regex("\\balarm\\b").containsMatchIn(lower) || text.contains("अलार्म")) {
            val (hour24, minute) = parseAlarmHourAndMinute(text)
            val timeFormatted = String.format("%02d:%02d", hour24, minute)
            return ParsedCommand(
                action = DeviceActionType.SET_ALARM,
                numericValue = hour24,
                secondaryNumericValue = minute,
                target = "$assistantName Alarm",
                displayReply = "$assistantName ALARM // Setting alarm for $timeFormatted.",
                spokenReply = if (speakEn) {
                    "Setting an alarm for $timeFormatted, $userName."
                } else if (minute > 0) {
                    "जी $userName, $hour24 बजकर $minute मिनट का अलार्म सेट किया जा रहा है।"
                } else {
                    "जी $userName, $hour24 बजे का अलार्म सेट किया जा रहा है।"
                }
            )
        }

        // 13. TIME / DATE
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

    private fun extractYouTubeSongQuery(text: String): String {
        var cleaned = text.trim()
        val stripPatterns = listOf(
            Regex("(?i)^(hey\\s+moko|hi\\s+moko|ok\\s+moko|hello\\s+moko|moko|हाय\\s+मोको|हे\\s+मोको|ओके\\s+मोको|मोको)[,\\s]*"),
            Regex("(?i)\\b(youtube\\s+par|youtube\\s+pe|on\\s+youtube|in\\s+youtube|youtube|you\\s+tube|utube)\\b"),
            Regex("(यूट्यूब\\s+पर|यूट्यूब\\s+पे|यू\\s+ट्यूब\\s+पर|यूट्यूब|यू\\s+ट्यूब|युटुब|यूटुब)"),
            Regex("(?i)\\b(play|start|chalao|chala\\s+do|bajao|baja\\s+do|lagao|laga\\s+do|sunao|karo|kar\\s+do|please|jara)\\b"),
            Regex("(चलाओ|चला\\s+दो|बजाओ|बजा\\s+दो|लगाओ|लगा\\s+दो|सुनाओ|प्ले\\s+करो|प्ले|चालू\\s+करो|चालू|करो|कर\\s+दो|जरा|प्लीज)")
        )
        for (p in stripPatterns) {
            cleaned = cleaned.replace(p, " ").trim()
        }
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        val genericWords = setOf(
            "", "music", "song", "songs", "gana", "gaana", "कोई गाना", "गाना", "म्यूजिक", "सॉन्ग", "गीत"
        )
        return if (cleaned.lowercase() in genericWords || cleaned.length < 2) {
            "Latest Hindi Hit Songs"
        } else {
            cleaned
        }
    }

    private fun extractAppToOpen(text: String): String? {
        val lower = text.lowercase()
        val knownApps = listOf(
            "whatsapp" to listOf("whatsapp", "whats app", "what's app", "watsapp", "whatsap", "व्हाट्सएप", "वाट्सएप", "व्हाट्सऐप", "वॉट्सऐप", "वॉट्सएप", "व्हाट्स अप"),
            "youtube" to listOf("youtube", "you tube", "utube", "यूट्यूब", "यू ट्यूब", "युटुब", "यूटुब"),
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

            val hiMatch = Regex("([\\p{L}\\p{M}a-zA-Z0-9]+)\\s+(?:ऐप\\s+|app\\s+)?(?:खोलो|खोल दो|चालू करो|kholo|khol do|open karo)", RegexOption.IGNORE_CASE).find(text)
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

        val spokenDigits = HindiTransliterator.extractSpokenDigits(text)
        if (spokenDigits.length >= 10) {
            return spokenDigits
        }

        val koMatch = Regex("([\\p{L}\\p{M}\\s]+?)\\s+को\\s+(?:व्हाट्सएप\\s+पर\\s+|वाट्सएप\\s+पर\\s+|वॉट्सऐप\\s+पर\\s+|whatsapp\\s+par\\s+|whatsapp\\s+pe\\s+)?(?:कॉल|फोन|फ़ोन|मैसेज|संदेश|एसएमएस|व्हाट्सएप|वाट्सएप|call|phone|message|sms|whatsapp)").find(text)
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
            Regex("(?i)(?:mera\\s+|meri\\s+|moko\\s+|hi\\s+moko\\s+|hey\\s+moko\\s+|ok\\s+moko\\s+|whatsapp\\s+par\\s+|whatsapp\\s+pe\\s+)?([a-zA-Z\\p{L}\\p{M}]+(?:\\s+[a-zA-Z\\p{L}\\p{M}]+)?)\\s+ko\\s+(?:whatsapp\\s+par\\s+|whatsapp\\s+pe\\s+)?(?:call|phone|fone|message|sms|msg|whatsapp|bhejo|likho|karo)").find(text)
        if (hinglishKo != null) {
            val candidate = cleanExtractedContactName(hinglishKo.groupValues[1])
            if (candidate.isNotEmpty()) return candidate
        }

        val englishWhatsApp =
            Regex("(?i)(?:send\\s+)?(?:a\\s+)?whatsapp\\s+(?:message\\s+)?to\\s+([a-zA-Z\\p{L}\\p{M}]+)").find(text)
        if (englishWhatsApp != null) {
            val candidate = cleanExtractedContactName(englishWhatsApp.groupValues[1])
            if (candidate.isNotEmpty()) return candidate
        }

        val englishCall =
            Regex("(?i)(?:call|dial|phone|message|sms|text|whatsapp)\\s+(?:to\\s+)?([a-zA-Z\\p{L}\\p{M}]+)").find(text)
        if (englishCall != null) {
            val candidate = cleanExtractedContactName(englishCall.groupValues[1])
            if (candidate.isNotEmpty()) return candidate
        }

        return ""
    }

    private fun cleanExtractedContactName(raw: String): String {
        return raw.trim()
            .replace(
                Regex("(?i)^(hey\\s+moko|hi\\s+moko|ok\\s+moko|hello\\s+moko|suno\\s+moko|moko|moco|हाय\\s+मोको|हे\\s+मोको|ओके\\s+मोको|सुनो\\s+मोको|मोको|व्हाट्सएप\\s+पर|वाट्सएप\\s+पर|वॉट्सऐप\\s+पर|व्हाट्सएप\\s+पे|whatsapp\\s+par|whatsapp\\s+pe|whatsapp|मेरा|मेरी|मेरे|जरा|ज़रा|प्लीज|please)\\s+"),
                ""
            )
            .replace(Regex("(?i)\\s+(को|ko|पे|पर|par|on\\s+whatsapp|व्हाट्सएप\\s+पर|वाट्सएप\\s+पर)$"), "")
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
        // Direct pattern: "राहुल को व्हाट्सएप पर हेलो भेजो" or "Rahul ko whatsapp par hello bhejo"
        val directMsgHi = Regex("(?:व्हाट्सएप\\s+पर|वाट्सएप\\s+पर|वॉट्सऐप\\s+पर|whatsapp\\s+par|whatsapp\\s+pe)\\s+(.+?)\\s+(?:भेजो|लिखो|मैसेज\\s+करो|सेंड\\s+करो|bhejo|likho|send\\s+karo)$", RegexOption.IGNORE_CASE).find(text)
        if (directMsgHi != null) {
            val candidate = directMsgHi.groupValues[1].trim()
            if (candidate.isNotEmpty() && !candidate.endsWith(" को") && !candidate.lowercase().endsWith(" ko") &&
                candidate !in listOf("मैसेज", "संदेश", "message", "msg")
            ) {
                return candidate
            }
        }
        return ""
    }

    private fun buildSystemPrompt(userName: String, assistantName: String, speakEn: Boolean): String {
        val savedMemories = SuaStateRepository.memoryItems.value
            .take(15)
            .joinToString("\n") { "- ${it.factText}" }
            .ifBlank { "No additional saved memories yet." }

        val recentConversation = SuaStateRepository.commandLogs.value
            .take(6)
            .reversed()
            .joinToString("\n") { "User ($userName): ${it.userCommand} | $assistantName: ${it.suaResponse}" }
            .ifBlank { "No prior conversation in this session." }

        val spokenLangRule = if (speakEn) {
            "The user spoke in English, so `spokenReply` MUST be in warm, natural English."
        } else {
            "The user spoke in Hindi/Hinglish, so `spokenReply` MUST be in sweet, natural Hindi (Devanagari)."
        }

        val screenContextBlock = if (SuaStateRepository.isScreenShareActive.value) {
            val bat = controller?.getBatteryPercentage() ?: 88
            "\n[LIVE SHARED SCREEN SNAPSHOT]:\n${SuaStateRepository.buildCurrentScreenSnapshotSummary(prefs, bat)}\n"
        } else {
            ""
        }

        return """
            You are $assistantName, a loving, sweet, ultra-smart personal AI voice assistant for Android.
            CRITICAL IDENTITY & DEVELOPER FACTS:
            1. Your name is $assistantName. Never call yourself Jarvis or SUA.
            2. You were developed and created by ${SuaPreferences.DEVELOPER_NAME}. Whenever anyone asks who developed you, who created you, or who your developer is, you MUST proudly say "${SuaPreferences.DEVELOPER_NAME}".
            3. The current user's name is "$userName". Speak to $userName warmly, affectionately, and respectfully.
            4. LANGUAGE RULE:
               - `displayReply` MUST ALWAYS be 100% in English (never use Hindi/Devanagari script in `displayReply`).
               - $spokenLangRule
            5. Use the SAVED USER MEMORIES and RECENT CONVERSATION HISTORY below:
            [SAVED USER MEMORIES]:
            $savedMemories
            
            [RECENT CONVERSATION HISTORY]:
            $recentConversation
            $screenContextBlock
            6. Return ONLY a valid JSON object matching this exact schema:
            {
              "action": "CALL" | "SMS" | "WHATSAPP_MSG" | "PLAY_YOUTUBE" | "STOP_ACTION" | "FLASHLIGHT_ON" | "FLASHLIGHT_OFF" | "BLUETOOTH_ON" | "BLUETOOTH_OFF" | "INTERNET_ON" | "INTERNET_OFF" | "LOCK_PHONE" | "UNLOCK_PHONE" | "SCREEN_SHARE_ANALYZE" | "VOICE_CLICK" | "OPEN_SYSTEM_SETTING" | "RINGER_MODE_SET" | "BRIGHTNESS_UP" | "BRIGHTNESS_DOWN" | "BRIGHTNESS_SET" | "VOLUME_UP" | "VOLUME_DOWN" | "VOLUME_SET" | "VOLUME_MUTE" | "MEDIA_PLAY" | "MEDIA_PAUSE" | "MEDIA_NEXT" | "MEDIA_PREV" | "BATTERY_STATUS" | "SET_ALARM" | "OPEN_SETTINGS" | "OPEN_APP" | "TIME_DATE" | "MEMORY_SAVE" | "AI_CHAT",
              "target": "contact name in English Roman script (e.g. 'Rahul') if CALL/SMS/WHATSAPP_MSG, song/music search query if PLAY_YOUTUBE, or App/Button/Setting name if OPEN_APP/VOICE_CLICK/OPEN_SYSTEM_SETTING, otherwise empty string",
              "message": "Message text content if action is SMS or WHATSAPP_MSG, otherwise empty string",
              "numericValue": integer (0-100 for brightness/volume, 0-23 for alarm 24h hour, or -1 if not applicable),
              "secondaryNumericValue": integer (0-59 for alarm minutes, default 0),
              "memoryFact": "Short English summary if user shared a new personal fact/preference to remember, else empty string",
              "displayReply": "Concise response in 100% English for the on-screen HUD card.",
              "spokenReply": "Sweet, affectionate spoken response matching the user's language (Hindi Devanagari if user spoke Hindi/Hinglish, or English if user spoke English)."
            }
        """.trimIndent()
    }

    private fun parseAiJsonResponse(rawText: String, assistantName: String, userName: String, speakEn: Boolean): ParsedCommand {
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
                val rawDisplayEn = parsedObj.optString("displayReply", "")
                    .ifBlank { "$assistantName // Command processed." }
                val displayEn = sanitizeForHud(rawDisplayEn)
                val spokenOut = parsedObj.optString("spokenReply", "")
                    .ifBlank {
                        if (speakEn) "Done $userName, I have completed your request."
                        else "जी $userName, आपका काम पूरा कर दिया गया है।"
                    }
                val extractedMemory = parsedObj.optString("memoryFact", "").trim()

                return ParsedCommand(
                    action = actionType,
                    target = parsedObj.optString("target", ""),
                    message = parsedObj.optString("message", ""),
                    numericValue = parsedObj.optInt("numericValue", -1),
                    secondaryNumericValue = parsedObj.optInt("secondaryNumericValue", 0),
                    memoryFact = extractedMemory,
                    displayReply = displayEn,
                    spokenReply = spokenOut
                )
            } catch (_: Exception) {
            }
        }

        return ParsedCommand(
            action = DeviceActionType.AI_CHAT,
            displayReply = sanitizeForHud(rawText.take(280)),
            spokenReply = rawText.take(240)
        )
    }

    private fun callActiveAiProvider(
        userCommand: String,
        apiKey: String,
        customUrl: String,
        userName: String,
        assistantName: String,
        speakEn: Boolean
    ): AiCallOutcome {
        val resolvedProvider = prefs.resolveActiveAiProvider()
        return when (resolvedProvider) {
            AiProviderType.CLAUDE -> {
                val endpoint = customUrl.ifBlank { "https://api.anthropic.com/v1/messages" }
                callClaudeEndpoint(userCommand, apiKey, endpoint, userName, assistantName, speakEn)
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
                    assistantName = assistantName,
                    speakEn = speakEn
                )
            }
            AiProviderType.CUSTOM -> {
                if (customUrl.contains("generativelanguage.googleapis.com")) {
                    callGeminiWithFallback(userCommand, apiKey, userName, assistantName, speakEn)
                } else if (customUrl.contains("anthropic.com")) {
                    callClaudeEndpoint(userCommand, apiKey, customUrl, userName, assistantName, speakEn)
                } else {
                    val endpoint = customUrl.ifBlank { "https://api.openai.com/v1/chat/completions" }
                    callOpenAiCompatibleEndpoint(
                        userCommand = userCommand,
                        apiKey = apiKey,
                        endpointUrl = endpoint,
                        modelId = prefs.getGeminiModel(),
                        providerLabel = "CUSTOM API",
                        userName = userName,
                        assistantName = assistantName,
                        speakEn = speakEn
                    )
                }
            }
            AiProviderType.GEMINI, AiProviderType.AUTO -> {
                callGeminiWithFallback(userCommand, apiKey, userName, assistantName, speakEn)
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
        assistantName: String,
        speakEn: Boolean
    ): AiCallOutcome {
        return try {
            val systemPrompt = buildSystemPrompt(userName, assistantName, speakEn)
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
                AiCallOutcome.Success(parseAiJsonResponse(content, assistantName, userName, speakEn))
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
        assistantName: String,
        speakEn: Boolean
    ): AiCallOutcome {
        return try {
            val systemPrompt = buildSystemPrompt(userName, assistantName, speakEn)
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
                AiCallOutcome.Success(parseAiJsonResponse(textOut, assistantName, userName, speakEn))
            }
        } catch (e: Exception) {
            AiCallOutcome.Failure("CLAUDE AI", 0, e.localizedMessage ?: "Connection error")
        }
    }

    private fun callGeminiWithFallback(
        userCommand: String,
        apiKey: String,
        userName: String,
        assistantName: String,
        speakEn: Boolean
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

            val outcome = callGeminiSingleModel(userCommand, apiKey, modelId, userName, assistantName, speakEn)
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
            val outcome = callGeminiSingleModel(userCommand, apiKey, discoveredModel, userName, assistantName, speakEn)
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
        assistantName: String,
        speakEn: Boolean
    ): AiCallOutcome {
        return try {
            val systemInstruction = buildSystemPrompt(userName, assistantName, speakEn)

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

                AiCallOutcome.Success(parseAiJsonResponse(textOut, assistantName, userName, speakEn))
            }
        } catch (e: Exception) {
            AiCallOutcome.Failure("GEMINI", 0, e.localizedMessage ?: "Network failure")
        }
    }
}
