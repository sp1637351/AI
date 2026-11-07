package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SuaPreferences
import com.example.data.SuaStatus
import com.example.ui.theme.SuaAmber
import com.example.ui.theme.SuaBorder
import com.example.ui.theme.SuaCrimson
import com.example.ui.theme.SuaCyan
import com.example.ui.theme.SuaCyanDim
import com.example.ui.theme.SuaDeepNavy
import com.example.ui.theme.SuaElectricBlue
import com.example.ui.theme.SuaEmerald
import com.example.ui.theme.SuaObsidian
import com.example.ui.theme.SuaSurfaceCard
import com.example.ui.theme.SuaSurfaceElevated
import com.example.ui.theme.SuaTextPrimary
import com.example.ui.theme.SuaTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuaDashboardScreen(
    viewModel: SuaViewModel,
    onOpenSettings: () -> Unit
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isBgRunning by viewModel.isBackgroundServiceRunning.collectAsStateWithLifecycle()
    val liveTranscript by viewModel.liveTranscript.collectAsStateWithLifecycle()
    val lastSuaReply by viewModel.lastSuaReply.collectAsStateWithLifecycle()
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsStateWithLifecycle()
    val brightnessPercent by viewModel.brightnessPercent.collectAsStateWithLifecycle()
    val volumePercent by viewModel.volumePercent.collectAsStateWithLifecycle()
    val isMusicPlaying by viewModel.isMusicPlaying.collectAsStateWithLifecycle()
    val isScreenLockedOverlay by viewModel.isScreenLockedOverlay.collectAsStateWithLifecycle()
    val commandLogs by viewModel.commandLogs.collectAsStateWithLifecycle()
    val memoryItems by viewModel.memoryItems.collectAsStateWithLifecycle()
    val audioRms by viewModel.audioRmsDb.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasEffectiveApiKey.collectAsStateWithLifecycle()
    val batteryPercent by viewModel.batteryPercent.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val assistantName by viewModel.assistantName.collectAsStateWithLifecycle()

    var commandInput by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.refreshPermissionsAndHardware()
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true ||
            viewModel.controller.hasPermission(Manifest.permission.RECORD_AUDIO)
        if (micGranted) {
            viewModel.activateOnlineMode()
        } else {
            viewModel.onMicPermissionDenied()
        }
    }

    fun requestOnlineMode() {
        if (viewModel.controller.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            viewModel.activateOnlineMode()
        } else {
            micPermLauncher.launch(
                viewModel.controller.getRequiredRuntimePermissions().toTypedArray()
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SuaObsidian,
                drawerContentColor = SuaTextPrimary,
                modifier = Modifier
                    .widthIn(max = 335.dp)
                    .fillMaxHeight()
                    .border(1.5.dp, SuaCyan, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
            ) {
                MokoSideDrawerContent(
                    assistantName = assistantName,
                    userName = userName,
                    isOnline = isBgRunning,
                    hasApiKey = hasApiKey,
                    batteryPercent = batteryPercent,
                    isFlashlightOn = isFlashlightOn,
                    brightnessPercent = brightnessPercent,
                    volumePercent = volumePercent,
                    isMusicPlaying = isMusicPlaying,
                    memoryCount = memoryItems.size,
                    historyCount = commandLogs.size,
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    },
                    onOpenFullSettings = {
                        coroutineScope.launch {
                            drawerState.close()
                            onOpenSettings()
                        }
                    },
                    onSwitchToOnline = { requestOnlineMode() },
                    onSwitchToSleep = { viewModel.activateSleepMode() },
                    onToggleFlashlight = { viewModel.toggleFlashlightDirect() },
                    onLockPhone = { viewModel.lockScreenDirect() },
                    onBrightnessChange = { viewModel.adjustBrightnessDirect(it, speakFeedback = false) },
                    onBrightnessCommit = { viewModel.adjustBrightnessDirect(brightnessPercent, speakFeedback = true) },
                    onVolumeChange = { viewModel.adjustVolumeDirect(it, speakFeedback = false) },
                    onVolumeCommit = { viewModel.adjustVolumeDirect(volumePercent, speakFeedback = true) },
                    onExecuteCommand = { cmd ->
                        viewModel.executeCommandText(cmd)
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = SuaObsidian,
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .testTag("open_side_drawer_button")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SuaDeepNavy)
                                        .border(1.5.dp, SuaCyan, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Open Menu & Settings",
                                        tint = SuaCyan
                                    )
                                }
                            }
                        },
                        title = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = assistantName.uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = SuaTextPrimary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SuaDeepNavy)
                                            .border(1.dp, SuaCyan, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "BY ${SuaPreferences.DEVELOPER_NAME}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuaCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = if (isBgRunning) {
                                        "ONLINE • Say 'Ok $assistantName' ($userName)"
                                    } else {
                                        "SLEEP MODE • Background Mic Off"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBgRunning) SuaEmerald else SuaTextSecondary
                                )
                            }
                        },
                        actions = {
                            OnlineSleepSegmentedSwitch(
                                isOnline = isBgRunning,
                                onSelectOnline = { requestOnlineMode() },
                                onSelectSleep = { viewModel.activateSleepMode() }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("top_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Open Settings & History",
                                    tint = SuaTextPrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = SuaObsidian
                        )
                    )
                },
                bottomBar = {
                    // Ultra-Sharp Bottom Voice + Message Bar
                    Surface(
                        color = SuaObsidian,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.5.dp, SuaCyan, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .background(SuaDeepNavy)
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. VOICE BUTTON (On/Off right on the bottom bar)
                                Button(
                                    onClick = {
                                        if (isBgRunning) {
                                            viewModel.activateSleepMode()
                                        } else {
                                            requestOnlineMode()
                                        }
                                    },
                                    modifier = Modifier
                                        .height(52.dp)
                                        .border(
                                            1.5.dp,
                                            if (isBgRunning) SuaEmerald else SuaCyan,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .testTag("bottom_voice_toggle_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isBgRunning) SuaEmerald else SuaObsidian,
                                        contentColor = if (isBgRunning) SuaObsidian else SuaCyan
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isBgRunning) Icons.Default.Mic else Icons.Default.MicOff,
                                        contentDescription = "Toggle Voice Mode",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isBgRunning) "ON" else "VOICE",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                // 2. MESSAGE INPUT FIELD
                                OutlinedTextField(
                                    value = commandInput,
                                    onValueChange = { commandInput = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("command_input_field"),
                                    placeholder = {
                                        Text(
                                            text = "Message $assistantName (e.g. Call Rahul, Open WhatsApp)...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SuaTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (commandInput.isNotBlank()) {
                                                viewModel.executeCommandText(commandInput)
                                                commandInput = ""
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SuaCyan,
                                        unfocusedBorderColor = SuaTextPrimary,
                                        focusedTextColor = SuaTextPrimary,
                                        unfocusedTextColor = SuaTextPrimary,
                                        focusedContainerColor = SuaObsidian,
                                        unfocusedContainerColor = SuaObsidian
                                    )
                                )

                                // 3. SEND MESSAGE BUTTON
                                Button(
                                    onClick = {
                                        if (commandInput.isNotBlank()) {
                                            viewModel.executeCommandText(commandInput)
                                            commandInput = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .border(1.5.dp, SuaTextPrimary, RoundedCornerShape(12.dp))
                                        .testTag("send_command_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuaCyan,
                                        contentColor = SuaObsidian
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send Message"
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                // ULTRA-CLEAN MAIN SCREEN: Only Center Voice Orb, Status/Response Card & Fast Touch Action!
                // (All History & Memory Vault items are inside Settings as requested)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SuaObsidian)
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Identity & Quick Settings/History Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SuaDeepNavy)
                            .border(1.dp, SuaBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "USER: ${userName.uppercase()}",
                                style = MaterialTheme.typography.labelLarge,
                                color = SuaTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "WAKE-WORD: \"OK ${assistantName.uppercase()}\" / \"HI ${assistantName.uppercase()}\"",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuaCyan
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuaCyan),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("main_open_history_settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Open History & Settings",
                                tint = SuaCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "HISTORY (${commandLogs.size}) & SETTINGS",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuaTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // CENTER SHARP NEON BLUE VOICE ORB + RESPONSE HUD CARD
                    SharpNeonBlueOrbCard(
                        assistantName = assistantName,
                        userName = userName,
                        status = status,
                        isOnline = isBgRunning,
                        audioRms = audioRms,
                        liveTranscript = liveTranscript,
                        lastReply = lastSuaReply,
                        memoryCount = memoryItems.size,
                        onToggleOnlineSleep = {
                            if (isBgRunning) {
                                viewModel.activateSleepMode()
                            } else {
                                requestOnlineMode()
                            }
                        },
                        onTestVoice = { viewModel.testVoiceGreeting() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // FULL-SCREEN SECURITY LOCK OVERLAY
            AnimatedVisibility(
                visible = isScreenLockedOverlay,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SuaScreenLockOverlay(
                    onUnlock = { viewModel.unlockScreenOverlay() }
                )
            }
        }
    }
}

@Composable
private fun OnlineSleepSegmentedSwitch(
    isOnline: Boolean,
    onSelectOnline: () -> Unit,
    onSelectSleep: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SuaDeepNavy)
            .border(
                width = 1.5.dp,
                color = if (isOnline) SuaEmerald else SuaTextPrimary,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ONLINE PILL BUTTON
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isOnline) SuaEmerald else Color.Transparent)
                .clickable {
                    if (!isOnline) onSelectOnline()
                }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("online_mode_button"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Switch to Online Mode",
                    tint = if (isOnline) SuaObsidian else SuaTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "ONLINE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isOnline) SuaObsidian else SuaTextSecondary
                )
            }
        }

        // SLEEP PILL BUTTON (When in Sleep mode, tapping it also toggles Online as requested!)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (!isOnline) SuaCyanDim else Color.Transparent)
                .clickable {
                    if (isOnline) {
                        onSelectSleep()
                    } else {
                        onSelectOnline()
                    }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("sleep_mode_button"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = "Switch Sleep / Online Mode",
                    tint = if (!isOnline) SuaTextPrimary else SuaTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SLEEP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (!isOnline) SuaTextPrimary else SuaTextSecondary
                )
            }
        }
    }
}

@Composable
private fun SharpNeonBlueOrbCard(
    assistantName: String,
    userName: String,
    status: SuaStatus,
    isOnline: Boolean,
    audioRms: Float,
    liveTranscript: String,
    lastReply: String,
    memoryCount: Int,
    onToggleOnlineSleep: () -> Unit,
    onTestVoice: () -> Unit
) {
    val activeBorderColor = when {
        status == SuaStatus.SPEAKING -> SuaEmerald
        status == SuaStatus.PROCESSING -> SuaAmber
        isOnline -> SuaCyan
        else -> SuaTextPrimary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SuaObsidian)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SuaObsidian)
                .border(
                    width = 2.dp,
                    color = activeBorderColor,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Status Row inside Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SuaDeepNavy)
                        .border(
                            1.5.dp,
                            if (isOnline) SuaEmerald else SuaTextPrimary,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = when {
                            status == SuaStatus.SPEAKING -> "${assistantName.uppercase()} SPEAKING..."
                            status == SuaStatus.PROCESSING -> "${assistantName.uppercase()} PROCESSING..."
                            isOnline -> "ONLINE • SAY 'OK ${assistantName.uppercase()}'"
                            else -> "SLEEP MODE • TAP ORB TO WAKE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnline) SuaEmerald else SuaTextPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SuaDeepNavy)
                        .border(1.dp, SuaCyan, RoundedCornerShape(8.dp))
                        .clickable { onTestVoice() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("orb_test_voice_badge")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Test Sweet Voice",
                            tint = SuaCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SWEET VOICE • $memoryCount MEM",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuaTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // SHARP PITCH-BLACK & NEON BLUE INTERACTIVE ORB (Instant Touch Response, Zero Blur)
            val normalizedRms = ((audioRms + 2f) / 12f).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(SuaObsidian)
                    .clickable { onToggleOnlineSleep() }
                    .testTag("central_mic_orb"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f

                    // Outer crisp white precision ring
                    drawCircle(
                        color = Color.White,
                        radius = maxRadius * 0.96f,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Second dynamic Neon Blue ring reacting to live voice RMS
                    val rmsScale = if (isOnline) (0.78f + normalizedRms * 0.14f) else 0.78f
                    drawCircle(
                        color = activeBorderColor,
                        radius = maxRadius * rmsScale,
                        center = center,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 4 crisp tactical crosshair ticks (0, 90, 180, 270 degrees)
                    val tickOuter = maxRadius * 0.95f
                    val tickInner = maxRadius * 0.85f
                    drawLine(
                        color = SuaCyan,
                        start = Offset(center.x, center.y - tickOuter),
                        end = Offset(center.x, center.y - tickInner),
                        strokeWidth = 3.dp.toPx()
                    )
                    drawLine(
                        color = SuaCyan,
                        start = Offset(center.x, center.y + tickInner),
                        end = Offset(center.x, center.y + tickOuter),
                        strokeWidth = 3.dp.toPx()
                    )
                    drawLine(
                        color = SuaCyan,
                        start = Offset(center.x - tickOuter, center.y),
                        end = Offset(center.x - tickInner, center.y),
                        strokeWidth = 3.dp.toPx()
                    )
                    drawLine(
                        color = SuaCyan,
                        start = Offset(center.x + tickInner, center.y),
                        end = Offset(center.x + tickOuter, center.y),
                        strokeWidth = 3.dp.toPx()
                    )

                    // Inner solid pitch-black core with sharp Neon border
                    drawCircle(
                        color = Color(0xFF050811),
                        radius = maxRadius * 0.60f,
                        center = center
                    )
                    drawCircle(
                        color = if (isOnline) SuaCyan else Color.White,
                        radius = maxRadius * 0.60f,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isOnline) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (isOnline) "$assistantName Online" else "$assistantName Sleep Mode",
                        tint = if (isOnline) SuaCyan else SuaTextPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isOnline) "ONLINE" else "SLEEP",
                        style = MaterialTheme.typography.titleMedium,
                        color = SuaTextPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (isOnline) "Tap to Sleep" else "Tap to Wake",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuaCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Sharp High-Contrast Live Transcript & Response Box (Pure Black + White/Neon Borders)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SuaDeepNavy)
                    .border(1.5.dp, SuaTextPrimary, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VOICE / COMMAND INPUT:",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuaCyan,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "DEV: ${SuaPreferences.DEVELOPER_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuaEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = liveTranscript,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SuaTextPrimary,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(color = SuaBorder, thickness = 1.dp)

                Text(
                    text = "${assistantName.uppercase()} RESPONSE FOR ${userName.uppercase()}:",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuaEmerald,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = lastReply,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SuaTextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun MokoSideDrawerContent(
    assistantName: String,
    userName: String,
    isOnline: Boolean,
    hasApiKey: Boolean,
    batteryPercent: Int,
    isFlashlightOn: Boolean,
    brightnessPercent: Int,
    volumePercent: Int,
    isMusicPlaying: Boolean,
    memoryCount: Int,
    historyCount: Int,
    onCloseDrawer: () -> Unit,
    onOpenFullSettings: () -> Unit,
    onSwitchToOnline: () -> Unit,
    onSwitchToSleep: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onLockPhone: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessCommit: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVolumeCommit: () -> Unit,
    onExecuteCommand: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SuaObsidian)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${assistantName.uppercase()} CONTROL HUB",
                    style = MaterialTheme.typography.titleLarge,
                    color = SuaTextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "DEVELOPED BY ${SuaPreferences.DEVELOPER_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuaCyan,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onCloseDrawer) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Side Menu",
                    tint = SuaTextPrimary
                )
            }
        }

        // User & Wake-Word Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SuaDeepNavy)
                .border(1.5.dp, SuaCyan, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = SuaCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Active User: $userName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SuaTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Wake-Word: \"Ok $assistantName\" / \"Hi $assistantName\" • Battery: $batteryPercent%",
                style = MaterialTheme.typography.labelSmall,
                color = SuaTextSecondary
            )
        }

        // PRIMARY BUTTON: OPEN SETTINGS, MULTI-AI API, VOICE & HISTORY
        Button(
            onClick = onOpenFullSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .border(1.5.dp, SuaTextPrimary, RoundedCornerShape(12.dp))
                .testTag("open_settings_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SuaCyan,
                contentColor = SuaObsidian
            )
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Open Settings & History"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SETTINGS, API, VOICE & HISTORY",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Quick Access to History & Memory inside Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onOpenFullSettings,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SuaCyan)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = SuaCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("History ($historyCount)", color = SuaTextPrimary)
            }

            OutlinedButton(
                onClick = onOpenFullSettings,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SuaEmerald)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = SuaEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Memory ($memoryCount)", color = SuaTextPrimary)
            }
        }

        HorizontalDivider(color = SuaBorder)

        // ONLINE vs SLEEP Mode Control inside Drawer
        Text(
            text = "BACKGROUND WAKE MODE ('OK ${assistantName.uppercase()}')",
            style = MaterialTheme.typography.labelSmall,
            color = SuaCyan,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSwitchToOnline,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOnline) SuaEmerald else SuaDeepNavy,
                    contentColor = if (isOnline) SuaObsidian else SuaTextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("ONLINE", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    if (isOnline) onSwitchToSleep() else onSwitchToOnline()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isOnline) SuaCyanDim else SuaDeepNavy,
                    contentColor = SuaTextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("SLEEP", fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(color = SuaBorder)

        // Hardware Controls inside Side Menu Drawer
        Text(
            text = "QUICK HARDWARE CONTROLS",
            style = MaterialTheme.typography.labelSmall,
            color = SuaCyan,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onToggleFlashlight,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_flashlight_tile"),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isFlashlightOn) SuaEmerald else SuaTextPrimary
                )
            ) {
                Icon(
                    imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = "Toggle Flashlight",
                    tint = if (isFlashlightOn) SuaEmerald else SuaCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isFlashlightOn) "Torch ON" else "Torch OFF", color = SuaTextPrimary)
            }

            OutlinedButton(
                onClick = onLockPhone,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_lock_tile"),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SuaTextPrimary)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock Phone",
                    tint = SuaAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Lock Phone", color = SuaTextPrimary)
            }
        }

        // Brightness Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SuaDeepNavy)
                .border(1.dp, SuaBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BrightnessHigh,
                        contentDescription = null,
                        tint = SuaCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Brightness", style = MaterialTheme.typography.bodyMedium, color = SuaTextPrimary)
                }
                Text("$brightnessPercent%", style = MaterialTheme.typography.labelLarge, color = SuaCyan)
            }
            Slider(
                value = brightnessPercent.toFloat(),
                onValueChange = { onBrightnessChange(it.toInt()) },
                onValueChangeFinished = onBrightnessCommit,
                valueRange = 5f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = SuaCyan,
                    activeTrackColor = SuaCyan
                )
            )
        }

        // Volume Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SuaDeepNavy)
                .border(1.dp, SuaBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = SuaElectricBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sound Volume", style = MaterialTheme.typography.bodyMedium, color = SuaTextPrimary)
                }
                Text("$volumePercent%", style = MaterialTheme.typography.labelLarge, color = SuaElectricBlue)
            }
            Slider(
                value = volumePercent.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                onValueChangeFinished = onVolumeCommit,
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = SuaElectricBlue,
                    activeTrackColor = SuaElectricBlue
                )
            )
        }

        // Media Transport Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SuaDeepNavy)
                .border(1.dp, SuaBorder, RoundedCornerShape(12.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onExecuteCommand("Previous song") }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Track", tint = SuaTextPrimary)
            }
            IconButton(
                onClick = {
                    onExecuteCommand(if (isMusicPlaying) "Pause music" else "Play music")
                }
            ) {
                Icon(
                    imageVector = if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play or Pause Music",
                    tint = SuaCyan
                )
            }
            IconButton(onClick = { onExecuteCommand("Next song") }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next Track", tint = SuaTextPrimary)
            }
        }

        Text(
            text = "API Status: ${if (hasApiKey) "AI Key Linked" else "Offline Hardware Mode"} • Dev: ${SuaPreferences.DEVELOPER_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = if (hasApiKey) SuaEmerald else SuaTextSecondary
        )
    }
}

@Composable
private fun SuaScreenLockOverlay(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SuaObsidian)
            .clickable { onUnlock() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(32.dp)
                .border(2.dp, SuaCyan, RoundedCornerShape(20.dp))
                .background(SuaDeepNavy)
                .padding(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = SuaCyan,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "SCREEN LOCKED",
                style = MaterialTheme.typography.headlineMedium,
                color = SuaTextPrimary,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap anywhere to unlock display",
                style = MaterialTheme.typography.bodyMedium,
                color = SuaCyan,
                textAlign = TextAlign.Center
            )
        }
    }
}
