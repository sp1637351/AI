package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Call
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DeviceActionType
import com.example.data.PendingCommsConfirmation
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
    val pendingComms by viewModel.pendingComms.collectAsStateWithLifecycle()
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

    val violetGlassBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                SuaCyan.copy(alpha = 0.78f),
                SuaElectricBlue.copy(alpha = 0.65f),
                SuaBorder.copy(alpha = 0.85f)
            )
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SuaDeepNavy,
                drawerContentColor = SuaTextPrimary,
                modifier = Modifier
                    .widthIn(max = 335.dp)
                    .fillMaxHeight()
                    .border(1.5.dp, violetGlassBrush, RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SuaObsidian,
                            SuaDeepNavy,
                            SuaObsidian
                        )
                    )
                )
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = Color.Transparent,
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
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SuaSurfaceElevated.copy(alpha = 0.85f))
                                        .border(1.2.dp, SuaCyan.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
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
                                            .background(SuaCyan.copy(alpha = 0.16f))
                                            .border(1.dp, SuaCyan.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "KALPA AI VOICE",
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
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("top_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Open Settings & History",
                                    tint = SuaCyan
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                bottomBar = {
                    // Deep Purple & Neon Violet Glass Bottom Voice + Message Bar
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            SuaSurfaceCard.copy(alpha = 0.96f),
                                            SuaObsidian
                                        )
                                    )
                                )
                                .border(
                                    1.2.dp,
                                    violetGlassBrush,
                                    RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. VOICE BUTTON
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
                                            1.2.dp,
                                            if (isBgRunning) SuaEmerald else SuaCyan,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .testTag("bottom_voice_toggle_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isBgRunning) SuaEmerald.copy(alpha = 0.22f) else SuaSurfaceElevated,
                                        contentColor = if (isBgRunning) SuaEmerald else SuaCyan
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

                                // 2. MESSAGE INPUT FIELD (100% English placeholder)
                                OutlinedTextField(
                                    value = commandInput,
                                    onValueChange = { commandInput = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("command_input_field"),
                                    placeholder = {
                                        Text(
                                            text = "Ask $assistantName (Call, SMS, WhatsApp)...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SuaTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
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
                                        unfocusedBorderColor = SuaBorder,
                                        focusedTextColor = SuaTextPrimary,
                                        unfocusedTextColor = SuaTextPrimary,
                                        focusedContainerColor = SuaObsidian.copy(alpha = 0.78f),
                                        unfocusedContainerColor = SuaObsidian.copy(alpha = 0.65f)
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
                                        .testTag("send_command_button"),
                                    shape = RoundedCornerShape(14.dp),
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top Violet Glass Identity & Settings Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SuaSurfaceCard.copy(alpha = 0.85f))
                            .border(1.dp, violetGlassBrush, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "USER: ${userName.uppercase()} • DEV: ${SuaPreferences.DEVELOPER_NAME}",
                                style = MaterialTheme.typography.labelMedium,
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
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuaCyan.copy(alpha = 0.7f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("main_open_history_settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Open History & Settings",
                                tint = SuaCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "HISTORY (${commandLogs.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuaTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Active Call / SMS / WhatsApp Multi-Number & Hindi/English Confirmation Card
                    AnimatedVisibility(visible = pendingComms != null) {
                        pendingComms?.let { activePending ->
                            PendingCommsConfirmationCard(
                                pending = activePending,
                                onSelectCommand = { viewModel.executeCommandText(it) }
                            )
                        }
                    }

                    // RESTORED DEEP PURPLE & NEON VIOLET GLASS ORB CARD
                    NeonVioletGlassOrbCard(
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
                        onTestVoice = { viewModel.testVoiceGreeting() },
                        onQuickCommand = { viewModel.executeCommandText(it) }
                    )

                    Spacer(modifier = Modifier.height(6.dp))
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
private fun PendingCommsConfirmationCard(
    pending: PendingCommsConfirmation,
    onSelectCommand: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SuaSurfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, SuaAmber, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = SuaAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CONFIRM ${pending.actionType.label.uppercase()} • ${pending.contactName.uppercase()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = SuaAmber,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelMedium,
                    color = SuaCrimson,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectCommand("Cancel") }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (pending.phoneNumbers.size > 1) {
                Text(
                    text = "Saved Numbers: (1) ${pending.phoneNumbers[0]}  •  (2) ${pending.phoneNumbers[1]}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuaTextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickGlassChip("1st Number", onClick = { onSelectCommand("First number") })
                    QuickGlassChip("2nd Number", onClick = { onSelectCommand("Second number") })
                    QuickGlassChip("Speak Both Numbers", onClick = { onSelectCommand("Speak both numbers") })
                }
            } else if (pending.phoneNumbers.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Number: ${pending.phoneNumbers.first()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuaTextPrimary
                    )
                    QuickGlassChip("Speak Number", onClick = { onSelectCommand("Speak both numbers") })
                }
            }

            if (pending.actionType == DeviceActionType.SMS || pending.actionType == DeviceActionType.WHATSAPP_MSG) {
                Text(
                    text = "Choose Message Language (or speak your message directly):",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuaCyan
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickGlassChip("Type in Hindi", onClick = { onSelectCommand("In Hindi") })
                    QuickGlassChip("Type in English", onClick = { onSelectCommand("In English") })
                }
            }
        }
    }
}

@Composable
private fun QuickGlassChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SuaCyan.copy(alpha = 0.16f))
            .border(1.dp, SuaCyan.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = SuaTextPrimary,
            fontWeight = FontWeight.Bold
        )
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
            .clip(RoundedCornerShape(12.dp))
            .background(SuaSurfaceElevated.copy(alpha = 0.9f))
            .border(
                width = 1.2.dp,
                color = if (isOnline) SuaEmerald.copy(alpha = 0.8f) else SuaBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ONLINE PILL BUTTON
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
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

        // SLEEP PILL BUTTON
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
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
private fun NeonVioletGlassOrbCard(
    assistantName: String,
    userName: String,
    status: SuaStatus,
    isOnline: Boolean,
    audioRms: Float,
    liveTranscript: String,
    lastReply: String,
    memoryCount: Int,
    onToggleOnlineSleep: () -> Unit,
    onTestVoice: () -> Unit,
    onQuickCommand: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "violet_orb_pulse")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_glow"
    )

    val activeAccentColor = when {
        status == SuaStatus.SPEAKING -> SuaEmerald
        status == SuaStatus.PROCESSING -> SuaAmber
        isOnline -> SuaCyan
        else -> SuaElectricBlue
    }

    val glassCardBrush = remember(activeAccentColor) {
        Brush.linearGradient(
            colors = listOf(
                activeAccentColor.copy(alpha = 0.75f),
                SuaElectricBlue.copy(alpha = 0.55f),
                SuaBorder.copy(alpha = 0.7f)
            )
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SuaSurfaceElevated.copy(alpha = 0.7f),
                            SuaSurfaceCard.copy(alpha = 0.95f),
                            SuaObsidian.copy(alpha = 0.92f)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = glassCardBrush,
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Status Row inside Violet Glass Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(activeAccentColor.copy(alpha = 0.15f))
                        .border(
                            1.dp,
                            activeAccentColor.copy(alpha = 0.65f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = when {
                            status == SuaStatus.SPEAKING -> "${assistantName.uppercase()} SPEAKING..."
                            status == SuaStatus.PROCESSING -> "${assistantName.uppercase()} THINKING..."
                            isOnline -> "ONLINE • SAY 'OK ${assistantName.uppercase()}'"
                            else -> "SLEEP MODE • TAP ORB TO WAKE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = activeAccentColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SuaCyan.copy(alpha = 0.14f))
                        .border(1.dp, SuaCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .clickable { onTestVoice() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("orb_test_voice_badge")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Test Kalpa AI Voice",
                            tint = SuaCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "KALPA VOICE • $memoryCount MEM",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuaTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // DEEP PURPLE & NEON VIOLET GLASS VOICE ORB
            val normalizedRms = ((audioRms + 2f) / 12f).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .clickable { onToggleOnlineSleep() }
                    .testTag("central_mic_orb"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f

                    // Soft radial neon violet ambient halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeAccentColor.copy(alpha = 0.34f),
                                SuaElectricBlue.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = maxRadius * glowPulse.coerceAtMost(1f)
                        ),
                        radius = maxRadius,
                        center = center
                    )

                    // Outer Neon Violet Glass Ring
                    drawCircle(
                        color = activeAccentColor.copy(alpha = 0.65f),
                        radius = maxRadius * 0.92f,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Rotating Cyber Violet Arcs
                    rotate(degrees = ringRotation, pivot = center) {
                        drawArc(
                            color = SuaCyan,
                            startAngle = 15f,
                            sweepAngle = 70f,
                            useCenter = false,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = SuaElectricBlue,
                            startAngle = 195f,
                            sweepAngle = 70f,
                            useCenter = false,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Dynamic Voice-Reactive Ring
                    val rmsScale = if (isOnline) (0.72f + normalizedRms * 0.14f) else 0.72f
                    drawCircle(
                        color = activeAccentColor,
                        radius = maxRadius * rmsScale,
                        center = center,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Inner Deep Purple Glass Core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SuaSurfaceElevated,
                                SuaDeepNavy,
                                SuaObsidian
                            ),
                            center = center,
                            radius = maxRadius * 0.58f
                        ),
                        radius = maxRadius * 0.58f,
                        center = center
                    )
                    drawCircle(
                        color = activeAccentColor.copy(alpha = 0.9f),
                        radius = maxRadius * 0.58f,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isOnline) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (isOnline) "$assistantName Online" else "$assistantName Sleep Mode",
                        tint = activeAccentColor,
                        modifier = Modifier.size(38.dp)
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

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Action Chips Row (Strictly 100% English UI Labels)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickGlassChip("Call Contact") { onQuickCommand("Call Khushi") }
                QuickGlassChip("Send SMS") { onQuickCommand("Send SMS to Khushi") }
                QuickGlassChip("WhatsApp Msg") { onQuickCommand("Send WhatsApp message to Khushi") }
                QuickGlassChip("Speak Both Numbers") { onQuickCommand("Speak both numbers") }
                QuickGlassChip("Flashlight") { onQuickCommand("Turn on flashlight") }
                QuickGlassChip("Who Developed You?") { onQuickCommand("Who developed you?") }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Violet Glass Live Transcript & Response Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SuaObsidian.copy(alpha = 0.72f))
                    .border(1.2.dp, SuaBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE VOICE / COMMAND:",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuaCyan,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "HINDI & ENGLISH VOICE READY",
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

                HorizontalDivider(color = SuaBorder.copy(alpha = 0.6f), thickness = 1.dp)

                Text(
                    text = "${assistantName.uppercase()} RESPONSE:",
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(SuaDeepNavy, SuaObsidian)
                )
            )
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
                .clip(RoundedCornerShape(14.dp))
                .background(SuaSurfaceCard)
                .border(1.2.dp, SuaCyan.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
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

        // Primary Settings & History Button
        Button(
            onClick = onOpenFullSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("drawer_open_settings_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SuaCyan,
                contentColor = SuaObsidian
            )
        ) {
            Icon(imageVector = Icons.Default.Key, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SETTINGS, KALPA VOICE & HISTORY ($historyCount)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Online vs Sleep Mode Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSwitchToOnline,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOnline) SuaEmerald else SuaSurfaceCard,
                    contentColor = if (isOnline) SuaObsidian else SuaTextPrimary
                )
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ONLINE", fontWeight = FontWeight.ExtraBold)
            }

            Button(
                onClick = {
                    if (isOnline) onSwitchToSleep() else onSwitchToOnline()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isOnline) SuaCyanDim else SuaSurfaceCard,
                    contentColor = SuaTextPrimary
                )
            ) {
                Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isOnline) "SLEEP" else "WAKE", fontWeight = FontWeight.ExtraBold)
            }
        }

        HorizontalDivider(color = SuaBorder)

        // Hardware Controls
        Text(
            text = "HARDWARE CONTROLS",
            style = MaterialTheme.typography.labelMedium,
            color = SuaCyan,
            fontWeight = FontWeight.ExtraBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onToggleFlashlight,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    if (isFlashlightOn) SuaAmber else SuaBorder
                )
            ) {
                Icon(
                    imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = "Toggle Torch",
                    tint = if (isFlashlightOn) SuaAmber else SuaCyan
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isFlashlightOn) "TORCH ON" else "TORCH OFF",
                    color = SuaTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onLockPhone,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, SuaCrimson)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock Screen",
                    tint = SuaCrimson
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LOCK",
                    color = SuaTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Brightness Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SuaSurfaceCard)
                .border(1.dp, SuaBorder, RoundedCornerShape(14.dp))
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
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Brightness", color = SuaTextPrimary, fontWeight = FontWeight.Bold)
                }
                Text("$brightnessPercent%", color = SuaCyan, fontWeight = FontWeight.ExtraBold)
            }
            Slider(
                value = brightnessPercent.toFloat(),
                onValueChange = { onBrightnessChange(it.toInt()) },
                onValueChangeFinished = onBrightnessCommit,
                valueRange = 5f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = SuaCyan,
                    activeTrackColor = SuaCyan,
                    inactiveTrackColor = SuaBorder
                )
            )
        }

        // Volume Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SuaSurfaceCard)
                .border(1.dp, SuaBorder, RoundedCornerShape(14.dp))
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
                        tint = SuaEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("System Volume", color = SuaTextPrimary, fontWeight = FontWeight.Bold)
                }
                Text("$volumePercent%", color = SuaEmerald, fontWeight = FontWeight.ExtraBold)
            }
            Slider(
                value = volumePercent.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                onValueChangeFinished = onVolumeCommit,
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = SuaEmerald,
                    activeTrackColor = SuaEmerald,
                    inactiveTrackColor = SuaBorder
                )
            )
        }

        // Media Deck
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SuaSurfaceCard)
                .border(1.dp, SuaBorder, RoundedCornerShape(14.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onExecuteCommand("Previous song") }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = SuaTextPrimary)
            }
            IconButton(
                onClick = {
                    onExecuteCommand(if (isMusicPlaying) "Pause music" else "Play music")
                }
            ) {
                Icon(
                    imageVector = if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play or Pause",
                    tint = SuaCyan
                )
            }
            IconButton(onClick = { onExecuteCommand("Next song") }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = SuaTextPrimary)
            }
        }

        // Quick App Launchers & Voice Actions (100% English UI)
        Text(
            text = "VOICE APP LAUNCHER & ACTIONS",
            style = MaterialTheme.typography.labelMedium,
            color = SuaCyan,
            fontWeight = FontWeight.ExtraBold
        )

        val quickVoiceActions = listOf(
            "Open WhatsApp" to "Open WhatsApp",
            "Open YouTube" to "Open YouTube",
            "Open Instagram" to "Open Instagram",
            "Open Camera" to "Open Camera",
            "Send WhatsApp Msg" to "Send WhatsApp message to Khushi",
            "Speak Both Numbers" to "Speak both numbers",
            "Who Developed You?" to "Who developed you?",
            "Battery Status" to "Check battery status"
        )

        quickVoiceActions.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { (label, cmd) ->
                    OutlinedButton(
                        onClick = { onExecuteCommand(cmd) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuaCyan.copy(alpha = 0.65f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = SuaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuaScreenLockOverlay(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SuaObsidian)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SuaSurfaceCard)
                .border(2.dp, SuaCrimson, RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(SuaObsidian)
                    .border(2.dp, SuaCrimson, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Screen Locked",
                    tint = SuaCrimson,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "SCREEN LOCKED",
                style = MaterialTheme.typography.titleLarge,
                color = SuaTextPrimary,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Background voice control remains active. Say any command or tap below to unlock.",
                style = MaterialTheme.typography.bodyMedium,
                color = SuaTextSecondary,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onUnlock,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("unlock_overlay_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuaCyan,
                    contentColor = SuaObsidian
                )
            ) {
                Text(
                    text = "UNLOCK SCREEN",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
