package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.SuaBrain
import com.example.control.SuaDeviceController
import com.example.data.DeviceActionType
import com.example.data.MokoOperatingMode
import com.example.data.SuaPreferences
import com.example.data.SuaStateRepository
import com.example.data.SuaStatus
import com.example.util.HindiTransliterator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SuaVoiceService : Service() {

    companion object {
        const val CHANNEL_ID = "moko_background_voice_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_LISTENING = "com.example.service.ACTION_START_LISTENING"
        const val ACTION_START_SLEEP_MODE = "com.example.service.ACTION_START_SLEEP_MODE"
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP_SERVICE"
        const val ACTION_EXECUTE_COMMAND = "com.example.service.ACTION_EXECUTE_COMMAND"
        const val EXTRA_COMMAND_TEXT = "extra_command_text"

        fun startService(context: Context) {
            startServiceInMode(context, MokoOperatingMode.ONLINE)
        }

        fun startServiceInMode(context: Context, mode: MokoOperatingMode) {
            if (mode == MokoOperatingMode.OFFLINE) {
                stopService(context)
                return
            }

            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasMic) {
                SuaStateRepository.setOperatingMode(MokoOperatingMode.OFFLINE)
                SuaStateRepository.updateStatus(SuaStatus.ERROR)
                SuaStateRepository.updateLiveTranscript("MIC PERMISSION REQUIRED // Grant Microphone access")
                SuaStateRepository.updateLastReply("PERMISSION ERROR // Microphone permission is required before starting ${mode.label} mode.")
                SuaVoiceSpeaker.speak("माफ़ कीजिए बॉस, माइक परमिशन के बिना मोको ${mode.label} मोड चालू नहीं हो सकता।")
                return
            }

            val intent = Intent(context, SuaVoiceService::class.java).apply {
                action = if (mode == MokoOperatingMode.SLEEP) {
                    ACTION_START_SLEEP_MODE
                } else {
                    ACTION_START_LISTENING
                }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                try {
                    context.startService(intent)
                } catch (_: Exception) {
                }
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SuaVoiceService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.stopService(intent)
            } catch (_: Exception) {
            }
            SuaVoiceSpeaker.stopSpeaking()
            SuaStateRepository.setOperatingMode(MokoOperatingMode.OFFLINE)
            SuaStateRepository.updateStatus(SuaStatus.IDLE)
            SuaStateRepository.updateLiveTranscript("OFFLINE MODE // Background voice service & microphone are completely OFF.")
        }

        fun sendCommandToService(context: Context, commandText: String) {
            val intent = Intent(context, SuaVoiceService::class.java).apply {
                action = ACTION_EXECUTE_COMMAND
                putExtra(EXTRA_COMMAND_TEXT, commandText)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var countdownTickerJob: Job? = null
    private var activeCommandJob: Job? = null

    private lateinit var prefs: SuaPreferences
    private lateinit var brain: SuaBrain
    private lateinit var controller: SuaDeviceController

    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var shouldKeepListening = false
    private var isCurrentlyListening = false
    private var lastListenStartMs = 0L
    private var lastMusicLaunchTimeMs = 0L

    private fun isMusicActiveNow(): Boolean {
        val recentLaunch = (System.currentTimeMillis() - lastMusicLaunchTimeMs) in 1..20_000L
        return recentLaunch || controller.isDeviceMusicCurrentlyActive()
    }

    // Keeps Moko's voice recognizer alive when the user locks the phone screen in ONLINE or SLEEP mode
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!shouldKeepListening) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_SCREEN_ON -> {
                    acquireWakeLock()
                    if (!isCurrentlyListening) {
                        scheduleNextListen(if (isMusicActiveNow()) 2500L else 350L)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = SuaPreferences(this)
        controller = SuaDeviceController(this)
        brain = SuaBrain(prefs, controller)
        SuaVoiceSpeaker.init(this)
        createNotificationChannel()
        initSpeechRecognizer()

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, filter)
        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                // OFFLINE MODE: Completely stop background listening & release WakeLock
                shouldKeepListening = false
                activeCommandJob?.cancel()
                countdownTickerJob?.cancel()
                releaseWakeLock()
                stopListeningInternal()
                SuaVoiceSpeaker.stopSpeaking()
                SuaStateRepository.setOperatingMode(MokoOperatingMode.OFFLINE)
                SuaStateRepository.updateStatus(SuaStatus.IDLE)
                SuaStateRepository.updateLiveTranscript("OFFLINE MODE // Background listening is completely OFF.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_EXECUTE_COMMAND -> {
                val cmdText = intent.getStringExtra(EXTRA_COMMAND_TEXT).orEmpty().trim()
                if (cmdText.isNotEmpty()) {
                    processRecognizedCommand(cmdText, fromMic = false)
                }
            }

            ACTION_START_SLEEP_MODE -> {
                startBackgroundListeningInMode(MokoOperatingMode.SLEEP)
            }

            else -> {
                startBackgroundListeningInMode(MokoOperatingMode.ONLINE)
            }
        }
        return START_STICKY
    }

    private fun startBackgroundListeningInMode(mode: MokoOperatingMode) {
        val hasMicPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPerm) {
            shouldKeepListening = false
            SuaStateRepository.setOperatingMode(MokoOperatingMode.OFFLINE)
            SuaStateRepository.updateStatus(SuaStatus.ERROR)
            SuaStateRepository.updateLastReply("MIC PERMISSION REQUIRED // Grant Microphone permission in Side Menu -> Settings.")
            stopSelf()
            return
        }

        shouldKeepListening = true
        acquireWakeLock()
        SuaStateRepository.setOperatingMode(mode)
        SuaStateRepository.markWakeWordDetected()
        startWakeCountdownTicker()

        val botName = prefs.getAssistantName()
        val userName = prefs.getUserName()

        // Start permanent microphone listening immediately so the mic is ON right away
        scheduleNextListen(200L)

        if (mode == MokoOperatingMode.SLEEP) {
            val sleepDesc = "Offline Hardware & Call Mode • 60s Active Window (Say 'Hi $botName')"
            SuaStateRepository.updateLiveTranscript("SLEEP MODE // $sleepDesc")
            SuaStateRepository.updateLastReply("$botName SLEEP MODE // AI Q&A is OFF. Direct Call, Bluetooth, Internet, Torch & Hardware commands are ACTIVE via 'Hi $botName'.")
            startForegroundWithNotification("SLEEP MODE // $sleepDesc")

            SuaVoiceSpeaker.speak("जी $userName, मैं $botName अब स्लीप मोड में हूँ। इसमें सवाल-जवाब बंद रहेंगे, लेकिन आप हाय $botName बोलकर बिना इंटरनेट के कॉल, ब्लूटूथ ऑफ़, इंटरनेट ऑफ़ और फोन के सारे काम करवा सकते हैं।") {
                if (shouldKeepListening) {
                    SuaStateRepository.markWakeWordDetected()
                    scheduleNextListen(200L)
                }
            }
        } else {
            val onlineDesc = "Permanent Active Mic • 60s Talk Window (Say 'Stop' anytime to interrupt)"
            SuaStateRepository.updateLiveTranscript("ONLINE MODE // $onlineDesc")
            SuaStateRepository.updateLastReply("$botName ONLINE // Permanent Mic ON. Speak continuously, say 'Stop' anytime to halt, or 'Hi $botName' after 60s silence.")
            startForegroundWithNotification("ONLINE // $onlineDesc")

            SuaVoiceSpeaker.speak("नमस्ते $userName! मैं $botName ऑनलाइन हो गई हूँ। माइक ऑन है, आप लगातार बात कर सकते हैं, बीच में स्टॉप बोलकर कोई भी काम रोक सकते हैं, और 1 मिनट तक बात न होने पर हाय $botName बोलकर फिर से एक्टिवेट कर सकते हैं।") {
                if (shouldKeepListening) {
                    SuaStateRepository.markWakeWordDetected()
                    scheduleNextListen(200L)
                }
            }
        }
    }

    private fun startWakeCountdownTicker() {
        countdownTickerJob?.cancel()
        countdownTickerJob = serviceScope.launch {
            var wasInActiveSession = true
            while (isActive && shouldKeepListening) {
                // Keep the 60-second timer paused/full while Moko is speaking, processing, or waiting on Call/SMS confirmation
                if (SuaVoiceSpeaker.isSpeakingNow ||
                    activeCommandJob?.isActive == true ||
                    SuaStateRepository.pendingComms.value != null
                ) {
                    SuaStateRepository.markWakeWordDetected()
                }
                val remainingSec = SuaStateRepository.refreshWakeCountdownSeconds()
                if (remainingSec > 0) {
                    wasInActiveSession = true
                } else if (wasInActiveSession) {
                    wasInActiveSession = false
                    val botName = prefs.getAssistantName()
                    val modeLabel = SuaStateRepository.operatingMode.value.label
                    SuaStateRepository.updateLiveTranscript(
                        "STANDBY ($modeLabel) // 60s silence reached. Waiting for 'Hi $botName' or 'Ok $botName'..."
                    )
                    updateNotification("STANDBY ($modeLabel) // Say 'Hi $botName' to wake")
                }

                // Permanent Mic Watchdog: if music is playing, use a gentler 6.5s interval so SpeechRecognizer doesn't interrupt music playback
                val now = System.currentTimeMillis()
                val watchdogThreshold = if (isMusicActiveNow()) 6500L else 2400L
                if (shouldKeepListening && !isCurrentlyListening && (now - lastListenStartMs > watchdogThreshold)) {
                    scheduleNextListen(if (isMusicActiveNow()) 1800L else 150L)
                }

                delay(1000L)
            }
        }
    }

    private fun handleInstantStopInterruption() {
        activeCommandJob?.cancel()
        activeCommandJob = null
        lastMusicLaunchTimeMs = 0L
        controller.stopAllActiveOperations()
        SuaStateRepository.markWakeWordDetected()
        val botName = prefs.getAssistantName()
        SuaStateRepository.updateStatus(SuaStatus.LISTENING)
        SuaStateRepository.updateLiveTranscript("STOPPED // Stopped by voice. Listening for your next command...")
        SuaStateRepository.updateLastReply("$botName STOPPED // Task & speech stopped immediately. Listening (60s active)...")
        updateNotification("$botName STOPPED // Listening for next command")
        if (shouldKeepListening) {
            scheduleNextListen(220L)
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Moko:OnlineVoiceWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                // Hold wake lock for up to 12 hours per Online/Sleep session so screen-lock doesn't suspend CPU
                wakeLock?.acquire(12 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun startForegroundWithNotification(contentText: String) {
        val notification = buildNotification(contentText)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            shouldKeepListening = false
            SuaStateRepository.setOperatingMode(MokoOperatingMode.OFFLINE)
            stopSelf()
        }
    }

    private fun updateNotification(contentText: String) {
        if (!shouldKeepListening) return
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(contentText))
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sleepIntent = Intent(this, SuaVoiceService::class.java).apply {
            action = ACTION_START_SLEEP_MODE
        }
        val pendingSleep = PendingIntent.getService(
            this,
            1,
            sleepIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val offlineIntent = Intent(this, SuaVoiceService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingOffline = PendingIntent.getService(
            this,
            2,
            offlineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val botName = prefs.getAssistantName()
        val modeLabel = SuaStateRepository.operatingMode.value.label
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("$botName $modeLabel // Say 'Hi $botName'")
            .setContentText(contentText)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingOpen)
            .addAction(
                android.R.drawable.ic_lock_idle_low_battery,
                "SLEEP MODE",
                pendingSleep
            )
            .addAction(
                android.R.drawable.ic_lock_power_off,
                "OFFLINE",
                pendingOffline
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun initSpeechRecognizer() {
        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(this)) {
                    speechRecognizer?.destroy()
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                        setRecognitionListener(recognitionListener)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private var lastRmsUpdateMs = 0L
    private var lastPartialUpdateMs = 0L

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isCurrentlyListening = true
            lastListenStartMs = System.currentTimeMillis()
            if (!SuaVoiceSpeaker.isSpeakingNow && activeCommandJob?.isActive != true) {
                SuaStateRepository.updateStatus(SuaStatus.LISTENING)
            }
            val botName = prefs.getAssistantName()
            val modeLabel = SuaStateRepository.operatingMode.value.label
            val remainingSec = SuaStateRepository.wakeSessionRemainingSeconds.value
            val wakeActive = remainingSec > 0 || SuaStateRepository.pendingComms.value != null
            if (!SuaVoiceSpeaker.isSpeakingNow && activeCommandJob?.isActive != true) {
                SuaStateRepository.updateLiveTranscript(
                    if (wakeActive) {
                        "$botName $modeLabel (${remainingSec}s) // Mic ON — Speak command or say 'Stop'..."
                    } else {
                        "STANDBY ($modeLabel) // Waiting for 'Hi $botName' or 'Ok $botName'..."
                    }
                )
            }
        }

        override fun onBeginningOfSpeech() {
            if (!SuaVoiceSpeaker.isSpeakingNow) {
                SuaStateRepository.updateStatus(SuaStatus.LISTENING)
            }
        }

        override fun onRmsChanged(rmsdB: Float) {
            val now = System.currentTimeMillis()
            if (now - lastRmsUpdateMs >= 260L) {
                lastRmsUpdateMs = now
                SuaStateRepository.updateAudioRms(rmsdB)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isCurrentlyListening = false
            SuaStateRepository.updateAudioRms(0f)
        }

        override fun onError(error: Int) {
            isCurrentlyListening = false
            SuaStateRepository.updateAudioRms(0f)
            if (shouldKeepListening) {
                acquireWakeLock()
                val musicActive = isMusicActiveNow()
                val inActiveSession = SuaStateRepository.isWithinWakeConversationWindow()
                val baseDelay = when {
                    musicActive -> 3800L
                    inActiveSession -> 280L
                    prefs.isLowBatteryWakeMode() -> 800L
                    else -> 480L
                }
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT
                ) {
                    initSpeechRecognizer()
                    scheduleNextListen(baseDelay + 250L)
                } else {
                    scheduleNextListen(baseDelay)
                }
            } else {
                SuaStateRepository.updateStatus(SuaStatus.IDLE)
            }
        }

        override fun onResults(results: Bundle?) {
            isCurrentlyListening = false
            SuaStateRepository.updateAudioRms(0f)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spokenText = matches?.firstOrNull()?.trim().orEmpty()
            if (spokenText.isNotEmpty()) {
                processRecognizedCommand(spokenText, fromMic = true)
            } else if (shouldKeepListening) {
                val nextDelay = when {
                    isMusicActiveNow() -> 3500L
                    SuaStateRepository.isWithinWakeConversationWindow() -> 250L
                    else -> 550L
                }
                scheduleNextListen(nextDelay)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val now = System.currentTimeMillis()
            if (now - lastPartialUpdateMs < 200L) return
            lastPartialUpdateMs = now
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (partial.isEmpty()) return

            val botName = prefs.getAssistantName()
            // 1. Instant "STOP" / "स्टॉप" / "रुको" command: halt everything immediately
            if (HindiTransliterator.isStopCommand(partial, botName)) {
                handleInstantStopInterruption()
                return
            }

            // 2. Acoustic Self-Echo Guard: ignore Moko's own voice from the phone speaker
            if (SuaVoiceSpeaker.isLikelySelfEcho(partial)) {
                return
            }

            // 3. Natural Mid-Sentence Barge-In: if Moko is speaking and the user speaks a new question or correction
            // (e.g. "नहीं नहीं प्राइम मिनिस्टर कौन है" or "म्यूजिक का साउंड कम करो"), immediately silence Moko's speaker!
            val wordCount = partial.split(Regex("\\s+")).count { it.length >= 2 }
            if (SuaVoiceSpeaker.isSpeakingNow &&
                (HindiTransliterator.isUserBargeInPhrase(partial, botName) || wordCount >= 2)
            ) {
                SuaVoiceSpeaker.stopSpeaking()
                SuaStateRepository.updateStatus(SuaStatus.LISTENING)
            }

            if (SuaStateRepository.isWithinWakeConversationWindow()) {
                // Actively speaking keeps the 60s conversation session alive
                SuaStateRepository.markWakeWordDetected()
            }
            SuaStateRepository.updateLiveTranscript(brain.sanitizeForHud(partial))
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startListeningInternal() {
        mainHandler.post {
            if (!shouldKeepListening) return@post

            val hasMic = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                SuaStateRepository.updateLiveTranscript("MIC PERMISSION REQUIRED // Open Side Menu -> Settings")
                return@post
            }

            acquireWakeLock()
            if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(this)) {
                initSpeechRecognizer()
            }

            try {
                lastListenStartMs = System.currentTimeMillis()
                val isSleepOffline = SuaStateRepository.operatingMode.value == MokoOperatingMode.SLEEP
                val musicActive = isMusicActiveNow()
                val completeSilenceMs = if (musicActive) 4500L else 1800L
                val possibleSilenceMs = if (musicActive) 3500L else 1400L
                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.getVoiceLocale())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, isSleepOffline)
                    }
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        completeSilenceMs
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        possibleSilenceMs
                    )
                }
                speechRecognizer?.startListening(recognizerIntent)
                isCurrentlyListening = true
                if (!SuaVoiceSpeaker.isSpeakingNow && activeCommandJob?.isActive != true) {
                    SuaStateRepository.updateStatus(SuaStatus.LISTENING)
                }
            } catch (_: Exception) {
                isCurrentlyListening = false
                initSpeechRecognizer()
                scheduleNextListen(if (isMusicActiveNow()) 3500L else 700L)
            }
        }
    }

    private fun stopListeningInternal() {
        mainHandler.post {
            isCurrentlyListening = false
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
            SuaStateRepository.updateAudioRms(0f)
        }
    }

    private fun scheduleNextListen(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (shouldKeepListening) {
                startListeningInternal()
            }
        }, delayMs)
    }

    private fun processRecognizedCommand(rawSpokenText: String, fromMic: Boolean) {
        val botName = prefs.getAssistantName()
        val userName = prefs.getUserName()

        // 0. Instant "STOP" / "स्टॉप" / "रुको" command: always processed immediately, even while Moko is speaking or working!
        if (HindiTransliterator.isStopCommand(rawSpokenText, botName)) {
            handleInstantStopInterruption()
            return
        }

        // 1. Acoustic Self-Echo Guard: reject Moko's own speaker output, while allowing genuine user barge-in!
        if (fromMic && SuaVoiceSpeaker.isLikelySelfEcho(rawSpokenText)) {
            if (shouldKeepListening) {
                scheduleNextListen(if (isMusicActiveNow()) 3200L else 280L)
            }
            return
        }

        // 2. If the user spoke a genuine command/correction while Moko was speaking, cut off Moko's current speech immediately!
        if (SuaVoiceSpeaker.isSpeakingNow) {
            SuaVoiceSpeaker.stopSpeaking()
        }

        val (hasWakeWord, strippedAfterWake) = HindiTransliterator.checkAndStripWakeWord(rawSpokenText, botName)
        val inActiveWindow = SuaStateRepository.isWithinWakeConversationWindow() ||
            SuaStateRepository.pendingComms.value != null

        // When 60 seconds of silence have elapsed, Moko is in Hands-Free Standby and requires "Hi Moko" / "Ok Moko"
        if (fromMic && !hasWakeWord && !inActiveWindow) {
            val ignoredHud = brain.sanitizeForHud(rawSpokenText)
            val modeLabel = SuaStateRepository.operatingMode.value.label
            SuaStateRepository.updateLiveTranscript(
                "STANDBY ($modeLabel) // Waiting for 'Hi $botName' (Heard: \"$ignoredHud\")"
            )
            if (shouldKeepListening) {
                val standbyDelay = when {
                    isMusicActiveNow() -> 3800L
                    prefs.isLowBatteryWakeMode() -> 650L
                    else -> 300L
                }
                scheduleNextListen(standbyDelay)
            }
            return
        }

        // Reset the 1-minute (60s) continuous conversation timer!
        SuaStateRepository.markWakeWordDetected()

        val effectiveCommand = if (hasWakeWord) strippedAfterWake else rawSpokenText.trim()

        // User said "Ok Moko" / "Hi Moko" with no command yet -> wake up & open 60-second continuous conversation!
        if (effectiveCommand.isEmpty()) {
            val mode = SuaStateRepository.operatingMode.value
            SuaStateRepository.markWakeWordDetected()
            SuaStateRepository.updateLiveTranscript("$botName AWAKE (${mode.label} • 60s) // Yes $userName, speak continuously...")
            SuaStateRepository.updateLastReply("WAKE-WORD DETECTED // 60s continuous conversation active in ${mode.label} mode.")
            val wakeGreeting = if (mode == MokoOperatingMode.SLEEP) {
                "जी $userName, मैं स्लीप मोड में सुन रही हूँ। कॉल, ब्लूटूथ या इंटरनेट का कोई भी काम बोलिए।"
            } else {
                "हाँ मेरे प्यारे $userName, बोलिए क्या काम करना है? आप लगातार 1 मिनट तक बात कर सकते हैं।"
            }
            SuaVoiceSpeaker.speak(wakeGreeting) {
                SuaStateRepository.markWakeWordDetected()
                if (shouldKeepListening) {
                    scheduleNextListen(200L)
                }
            }
            return
        }

        val hudTranscript = brain.sanitizeForHud(effectiveCommand)
        SuaStateRepository.updateLiveTranscript(hudTranscript)
        SuaStateRepository.updateStatus(SuaStatus.PROCESSING)

        activeCommandJob?.cancel()
        activeCommandJob = serviceScope.launch {
            val parsed = brain.interpretCommand(effectiveCommand)
            if (!isActive) return@launch

            if (parsed.action == DeviceActionType.STOP_ACTION) {
                handleInstantStopInterruption()
                return@launch
            }

            // Special handling for PLAY_YOUTUBE: speak confirmation BEFORE launching YouTube so Moko's TTS
            // never grabs AudioFocus after YouTube starts playing!
            if (parsed.action == DeviceActionType.PLAY_YOUTUBE) {
                SuaStateRepository.markWakeWordDetected()
                val preSpeech = parsed.spokenReply.ifBlank {
                    "जी $userName, यूट्यूब पर गाना चला रही हूँ।"
                }
                SuaVoiceSpeaker.speak(preSpeech) {
                    lastMusicLaunchTimeMs = System.currentTimeMillis()
                    val execResult = controller.executeCommand(parsed)
                    SuaStateRepository.updateLastReply(execResult.displayReply)
                    SuaStateRepository.addCommandLog(
                        userCommand = hudTranscript,
                        suaResponse = execResult.displayReply,
                        actionType = parsed.action,
                        actionSuccess = execResult.success,
                        prefs = prefs
                    )
                    updateNotification(execResult.displayReply)
                    SuaStateRepository.updateStatus(SuaStatus.LISTENING)
                    if (shouldKeepListening) {
                        // Give YouTube 4.5s to load and start playing before re-arming the microphone
                        scheduleNextListen(4500L)
                    }
                }
                return@launch
            }

            val execResult = controller.executeCommand(parsed)
            if (!isActive) return@launch

            // Keep 60-second window open unless switching to OFFLINE mode
            val isSwitchingOffline = parsed.action == DeviceActionType.SWITCH_MODE &&
                parsed.target.equals("OFFLINE", ignoreCase = true)

            if (!isSwitchingOffline) {
                SuaStateRepository.markWakeWordDetected()
            }

            SuaStateRepository.updateLastReply(execResult.displayReply)
            SuaStateRepository.addCommandLog(
                userCommand = hudTranscript,
                suaResponse = execResult.displayReply,
                actionType = parsed.action,
                actionSuccess = execResult.success,
                prefs = prefs
            )
            updateNotification(execResult.displayReply)

            // If finalSpokenReply is blank (e.g. adjusting music volume while a song is playing),
            // do NOT speak so the user's music continues uninterrupted!
            if (execResult.finalSpokenReply.isBlank()) {
                SuaStateRepository.updateStatus(SuaStatus.LISTENING)
                if (shouldKeepListening && !isSwitchingOffline) {
                    scheduleNextListen(if (isMusicActiveNow()) 3200L else 280L)
                }
                return@launch
            }

            // Ensure mic is actively listening while Moko speaks so the user can interrupt or say "Stop" anytime!
            if (shouldKeepListening && !isSwitchingOffline) {
                scheduleNextListen(280L)
            }

            SuaVoiceSpeaker.speak(execResult.finalSpokenReply) {
                if (isSwitchingOffline || SuaStateRepository.operatingMode.value == MokoOperatingMode.OFFLINE) {
                    stopService(this@SuaVoiceService)
                } else if (shouldKeepListening) {
                    // Refresh 60s timer right after Moko finishes speaking so the user has a full 1 minute to reply!
                    SuaStateRepository.markWakeWordDetected()
                    scheduleNextListen(if (isMusicActiveNow()) 3200L else 220L)
                } else {
                    SuaStateRepository.updateStatus(SuaStatus.IDLE)
                }
            }
        }
    }

    override fun onDestroy() {
        shouldKeepListening = false
        countdownTickerJob?.cancel()
        releaseWakeLock()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {
        }
        SuaStateRepository.setOperatingMode(MokoOperatingMode.OFFLINE)
        SuaStateRepository.updateStatus(SuaStatus.IDLE)
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
