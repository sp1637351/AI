package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CommandLogEntry
import com.example.data.SuaStatus
import com.example.ui.theme.SuaAmber
import com.example.ui.theme.SuaBorder
import com.example.ui.theme.SuaCrimson
import com.example.ui.theme.SuaCyan
import com.example.ui.theme.SuaDeepNavy
import com.example.ui.theme.SuaElectricBlue
import com.example.ui.theme.SuaEmerald
import com.example.ui.theme.SuaObsidian
import com.example.ui.theme.SuaSurfaceCard
import com.example.ui.theme.SuaSurfaceElevated
import com.example.ui.theme.SuaTextPrimary
import com.example.ui.theme.SuaTextSecondary
import kotlin.math.cos
import kotlin.math.sin

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
    val audioRms by viewModel.audioRmsDb.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasEffectiveApiKey.collectAsStateWithLifecycle()
    val batteryPercent by viewModel.batteryPercent.collectAsStateWithLifecycle()

    var commandInput by remember { mutableStateOf("") }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshPermissionsAndHardware()
        viewModel.toggleBackgroundVoiceService()
    }

    val sampleCommands = remember {
        listOf(
            "Call Khushi" to "Khushi ko call lagao",
            "Flashlight ON" to "Flashlight chalu karo",
            "Flashlight OFF" to "Flashlight band karo",
            "Lock Phone" to "Phone ko lock karo",
            "Brightness Up" to "Brightness badhao",
            "Brightness Down" to "Brightness kam karo",
            "Volume 100%" to "Sound full karo",
            "Play Music" to "Music chalao",
            "SMS Khushi" to "Khushi ko message karo ki I am coming",
            "Battery Status" to "Battery kitni hai"
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = SuaObsidian,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(SuaCyan, SuaElectricBlue))
                                    )
                                    .border(1.5.dp, SuaCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "SUA Arc Logo",
                                    tint = SuaObsidian,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "S.U.A.",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = SuaTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(SuaCyan.copy(alpha = 0.16f))
                                            .border(1.dp, SuaCyan.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "MARK VII HUD",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuaCyan
                                        )
                                    }
                                }
                                Text(
                                    text = if (isBgRunning) {
                                        "ONLINE // BACKGROUND VOICE LOOP ACTIVE"
                                    } else {
                                        "STANDBY // TAP ARC REACTOR TO ENGAGE"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBgRunning) SuaEmerald else SuaTextSecondary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.testVoiceGreeting() },
                            modifier = Modifier.testTag("hud_voice_test_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Test SUA Hindi Voice",
                                tint = SuaCyan
                            )
                        }

                        Surface(
                            onClick = onOpenSettings,
                            shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                            color = SuaSurfaceElevated,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("open_settings_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        SuaCyan.copy(alpha = 0.7f),
                                        CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Open SUA System Configuration",
                                    tint = SuaCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CONFIG",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SuaTextPrimary
                                )
                                if (!hasApiKey) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(SuaAmber)
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SuaDeepNavy
                    )
                )
            },
            bottomBar = {
                Surface(
                    color = SuaDeepNavy,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = commandInput,
                            onValueChange = { commandInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("command_input_field"),
                            placeholder = {
                                Text(
                                    text = "Enter command (e.g. Call Khushi, Flashlight off, Lock phone)...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
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
                                focusedContainerColor = SuaSurfaceCard,
                                unfocusedContainerColor = SuaSurfaceCard
                            )
                        )

                        Button(
                            onClick = {
                                if (commandInput.isNotBlank()) {
                                    viewModel.executeCommandText(commandInput)
                                    commandInput = ""
                                }
                            },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("send_command_button"),
                            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuaCyan,
                                contentColor = SuaObsidian
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Execute Command"
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    // TOP TELEMETRY RIBBON (IRON MAN HUD METRICS)
                    item {
                        TelemetryRibbonRow(
                            batteryPercent = batteryPercent,
                            brightnessPercent = brightnessPercent,
                            volumePercent = volumePercent,
                            isFlashlightOn = isFlashlightOn
                        )
                    }

                    // HERO IRON MAN ARC REACTOR CORE & BACKGROUND MIC CONTROLLER
                    item {
                        IronManArcReactorCard(
                            status = status,
                            isBgRunning = isBgRunning,
                            audioRms = audioRms,
                            liveTranscript = liveTranscript,
                            lastSuaReply = lastSuaReply,
                            onToggleMic = {
                                if (viewModel.controller.hasPermission(Manifest.permission.RECORD_AUDIO)) {
                                    viewModel.toggleBackgroundVoiceService()
                                } else {
                                    micPermLauncher.launch(
                                        viewModel.controller.getRequiredRuntimePermissions().toTypedArray()
                                    )
                                }
                            }
                        )
                    }

                    // QUICK VOICE COMMAND PROTOCOLS
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "TACTICAL VOICE PROTOCOLS // TAP OR SPEAK",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuaCyan
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                sampleCommands.forEachIndexed { idx, (labelEn, spokenCmd) ->
                                    AssistChip(
                                        onClick = { viewModel.executeCommandText(spokenCmd) },
                                        modifier = Modifier.testTag("quick_cmd_chip_$idx"),
                                        label = {
                                            Text(
                                                text = labelEn,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = SuaTextPrimary
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = SuaSurfaceCard
                                        ),
                                        border = AssistChipDefaults.assistChipBorder(
                                            enabled = true,
                                            borderColor = SuaCyan.copy(alpha = 0.55f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // LIVE HARDWARE CONTROL DECK
                    item {
                        Text(
                            text = "DIRECT HARDWARE OVERRIDE // TACTICAL MODULES",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuaCyan
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HardwareQuickCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("call_khushi_card"),
                                title = "CALL KHUSHI",
                                subtitle = "SYS.COMMS // DIRECT DIAL",
                                icon = Icons.Default.Call,
                                accentColor = SuaEmerald,
                                isActive = false,
                                onClick = {
                                    viewModel.executeCommandText("Khushi ko call lagao")
                                }
                            )

                            HardwareQuickCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("flashlight_toggle_card"),
                                title = if (isFlashlightOn) "TORCH: ON" else "TORCH: OFF",
                                subtitle = if (isFlashlightOn) "SYS.OPTICS // BEAM ACTIVE" else "SYS.OPTICS // STANDBY",
                                icon = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                accentColor = if (isFlashlightOn) SuaAmber else SuaCyan,
                                isActive = isFlashlightOn,
                                onClick = {
                                    viewModel.toggleFlashlightDirect()
                                }
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HardwareQuickCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("lock_phone_card"),
                                title = "LOCK PHONE",
                                subtitle = "SYS.SEC // FORCE LOCK",
                                icon = Icons.Default.Lock,
                                accentColor = SuaCrimson,
                                isActive = isScreenLockedOverlay,
                                onClick = {
                                    viewModel.lockScreenDirect()
                                }
                            )

                            HardwareQuickCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("send_sms_card"),
                                title = "DISPATCH SMS",
                                subtitle = "SYS.COMMS // KHUSHI",
                                icon = Icons.AutoMirrored.Filled.Message,
                                accentColor = SuaElectricBlue,
                                isActive = false,
                                onClick = {
                                    viewModel.executeCommandText("Khushi ko message karo ki I am on my way")
                                }
                            )
                        }
                    }

                    // LUMINANCE, AUDIO GAIN & MEDIA TRANSPORT CONSOLE
                    item {
                        BrightnessAndSoundCard(
                            brightnessPercent = brightnessPercent,
                            volumePercent = volumePercent,
                            isMusicPlaying = isMusicPlaying,
                            onBrightnessChange = { viewModel.adjustBrightnessDirect(it, speakFeedback = false) },
                            onBrightnessCommit = { viewModel.adjustBrightnessDirect(brightnessPercent, speakFeedback = true) },
                            onVolumeChange = { viewModel.adjustVolumeDirect(it, speakFeedback = false) },
                            onVolumeCommit = { viewModel.adjustVolumeDirect(volumePercent, speakFeedback = true) },
                            onMediaCommand = { cmd -> viewModel.executeCommandText(cmd) }
                        )
                    }

                    // COMMAND TELEMETRY LOG
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TACTICAL EXECUTION LOG // (${commandLogs.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuaCyan
                            )
                            if (commandLogs.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.clearCommandHistory() },
                                    modifier = Modifier.testTag("clear_logs_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Clear Execution Logs",
                                        tint = SuaTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (commandLogs.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard),
                                shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            1.dp,
                                            SuaBorder,
                                            CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
                                        )
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "NO TACTICAL LOGS RECORDED",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SuaTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Engage the Arc Reactor Core and speak in Hindi or English — SUA will execute your command and respond in Hindi voice.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SuaTextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(commandLogs, key = { it.id }) { logEntry ->
                            CommandLogItemCard(entry = logEntry)
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        // FULL-SCREEN SUA SECURITY LOCK SHIELD OVERLAY
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

@Composable
private fun TelemetryRibbonRow(
    batteryPercent: Int,
    brightnessPercent: Int,
    volumePercent: Int,
    isFlashlightOn: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TelemetryChip(
            modifier = Modifier.weight(1f),
            label = "PWR CORE",
            value = "$batteryPercent%",
            accent = SuaEmerald
        )
        TelemetryChip(
            modifier = Modifier.weight(1f),
            label = "LUMINANCE",
            value = "$brightnessPercent%",
            accent = SuaAmber
        )
        TelemetryChip(
            modifier = Modifier.weight(1f),
            label = "AUDIO GAIN",
            value = "$volumePercent%",
            accent = SuaCyan
        )
        TelemetryChip(
            modifier = Modifier.weight(1f),
            label = "OPTICS",
            value = if (isFlashlightOn) "ON" else "STBY",
            accent = if (isFlashlightOn) SuaAmber else SuaElectricBlue
        )
    }
}

@Composable
private fun TelemetryChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Column(
        modifier = modifier
            .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .background(SuaSurfaceCard)
            .border(1.dp, accent.copy(alpha = 0.45f), CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SuaTextSecondary,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IronManArcReactorCard(
    status: SuaStatus,
    isBgRunning: Boolean,
    audioRms: Float,
    liveTranscript: String,
    lastSuaReply: String,
    onToggleMic: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor_motion")
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rot"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rot"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val reactorColor = when {
        status == SuaStatus.SPEAKING -> SuaCyan
        status == SuaStatus.PROCESSING -> SuaAmber
        status == SuaStatus.LISTENING || isBgRunning -> SuaEmerald
        else -> SuaCyan
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 24.dp, bottomEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(reactorColor, SuaElectricBlue, reactorColor)),
                    shape = CutCornerShape(topStart = 24.dp, bottomEnd = 24.dp)
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_sua_core_banner_1793987301388),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CutCornerShape(topStart = 24.dp, bottomEnd = 24.dp)),
                alpha = 0.25f
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top HUD Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(reactorColor.copy(alpha = 0.16f))
                        .border(1.dp, reactorColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = when {
                            status == SuaStatus.SPEAKING -> "VOICE SYNTHESIS // SUA SPEAKING IN HINDI"
                            status == SuaStatus.PROCESSING -> "NEURAL ENGINE // EXECUTING DIRECTIVE"
                            isBgRunning -> "ARC REACTOR ONLINE // CONTINUOUS LISTENING"
                            else -> "ARC REACTOR STANDBY // TAP CORE TO ENGAGE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = reactorColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // IRON MAN MULTI-RING ARC REACTOR CANVAS + CORE BUTTON
                Box(
                    modifier = Modifier.size(196.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxR = size.minDimension / 2f
                        val rmsBoost = (audioRms / 12f) * 20f
                        val dynamicScale = if (isBgRunning || status != SuaStatus.IDLE) pulseScale else 0.96f

                        // Outer glowing aura
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    reactorColor.copy(alpha = 0.28f),
                                    SuaElectricBlue.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = maxR * dynamicScale + rmsBoost
                            ),
                            radius = maxR * dynamicScale + rmsBoost,
                            center = center
                        )

                        // Rotating Outer Degree Ticks (36 segments like Iron Man HUD)
                        rotate(degrees = outerRotation, pivot = center) {
                            val tickRadiusOuter = maxR * 0.95f
                            val tickRadiusInner = maxR * 0.87f
                            for (i in 0 until 36) {
                                val angleRad = Math.toRadians((i * 10).toDouble())
                                val start = Offset(
                                    x = center.x + (tickRadiusInner * cos(angleRad)).toFloat(),
                                    y = center.y + (tickRadiusInner * sin(angleRad)).toFloat()
                                )
                                val end = Offset(
                                    x = center.x + (tickRadiusOuter * cos(angleRad)).toFloat(),
                                    y = center.y + (tickRadiusOuter * sin(angleRad)).toFloat()
                                )
                                drawLine(
                                    color = if (i % 3 == 0) SuaCyan else SuaCyan.copy(alpha = 0.35f),
                                    start = start,
                                    end = end,
                                    strokeWidth = if (i % 3 == 0) 3.dp.toPx() else 1.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        // Counter-Rotating Segmented Arc Reactor Coils (10 segments)
                        rotate(degrees = innerRotation, pivot = center) {
                            val coilRadius = maxR * 0.76f
                            val arcSize = Size(coilRadius * 2f, coilRadius * 2f)
                            val topLeft = Offset(center.x - coilRadius, center.y - coilRadius)
                            for (seg in 0 until 10) {
                                drawArc(
                                    color = reactorColor.copy(alpha = 0.85f),
                                    startAngle = seg * 36f,
                                    sweepAngle = 24f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }

                        // Inner Stabilizer Ring
                        drawCircle(
                            color = SuaCyan.copy(alpha = 0.7f),
                            radius = maxR * 0.62f * dynamicScale,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    // Central Interactive Arc Reactor Core Button
                    Button(
                        onClick = onToggleMic,
                        modifier = Modifier
                            .size(104.dp)
                            .testTag("mic_toggle_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBgRunning) SuaEmerald else SuaCyan,
                            contentColor = SuaObsidian
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isBgRunning) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = if (isBgRunning) "Disengage SUA Core" else "Engage SUA Core",
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isBgRunning) "ONLINE" else "ENGAGE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (liveTranscript.isNotBlank()) {
                    Text(
                        text = "INPUT // \"$liveTranscript\"",
                        style = MaterialTheme.typography.labelLarge,
                        color = SuaCyan,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Tactical HUD Response Readout Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp))
                        .background(SuaObsidian.copy(alpha = 0.85f))
                        .border(
                            1.dp,
                            SuaCyan.copy(alpha = 0.45f),
                            CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SUA TELEMETRY READOUT",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuaCyan
                            )
                            Text(
                                text = "AUDIO: HINDI VOICE",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuaEmerald
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lastSuaReply,
                            style = MaterialTheme.typography.bodyLarge,
                            color = SuaTextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareQuickCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) accentColor.copy(alpha = 0.18f) else SuaSurfaceCard
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isActive) accentColor else SuaBorder,
                    shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(1.dp, accentColor.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SuaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BrightnessAndSoundCard(
    brightnessPercent: Int,
    volumePercent: Int,
    isMusicPlaying: Boolean,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessCommit: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVolumeCommit: () -> Unit,
    onMediaCommand: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp),
        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SuaBorder, CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Brightness Control Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BrightnessHigh,
                        contentDescription = "Luminance Level",
                        tint = SuaAmber
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DISPLAY LUMINANCE",
                        style = MaterialTheme.typography.labelLarge,
                        color = SuaTextPrimary
                    )
                }
                Text(
                    text = "$brightnessPercent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = SuaAmber
                )
            }

            Slider(
                value = brightnessPercent.toFloat(),
                onValueChange = { onBrightnessChange(it.toInt()) },
                onValueChangeFinished = { onBrightnessCommit() },
                valueRange = 5f..100f,
                modifier = Modifier.testTag("brightness_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = SuaAmber,
                    activeTrackColor = SuaAmber
                )
            )

            // Sound / Volume Control Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (volumePercent == 0) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = "Audio Gain",
                        tint = SuaCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MASTER AUDIO GAIN",
                        style = MaterialTheme.typography.labelLarge,
                        color = SuaTextPrimary
                    )
                }
                Text(
                    text = "$volumePercent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = SuaCyan
                )
            }

            Slider(
                value = volumePercent.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                onValueChangeFinished = { onVolumeCommit() },
                valueRange = 0f..100f,
                modifier = Modifier.testTag("volume_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = SuaCyan,
                    activeTrackColor = SuaCyan
                )
            )

            // Music Transport Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SuaDeepNavy)
                    .border(1.dp, SuaBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Media Deck",
                        tint = SuaCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MEDIA TRANSPORT DECK",
                        style = MaterialTheme.typography.labelLarge,
                        color = SuaTextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { onMediaCommand("Previous track") }) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = SuaTextPrimary
                        )
                    }
                    IconButton(
                        onClick = {
                            if (isMusicPlaying) onMediaCommand("Pause music")
                            else onMediaCommand("Play music")
                        },
                        modifier = Modifier.testTag("music_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play or Pause Music",
                            tint = SuaCyan
                        )
                    }
                    IconButton(onClick = { onMediaCommand("Next track") }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Track",
                            tint = SuaTextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandLogItemCard(entry: CommandLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SuaBorder, CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SuaCyan.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${entry.actionType.codeTag} // ${entry.actionType.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuaCyan
                    )
                }
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = SuaTextSecondary
                )
            }

            Text(
                text = "CMD > \"${entry.userCommand}\"",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = SuaTextPrimary
            )
            Text(
                text = "SUA > ${entry.suaResponse}",
                style = MaterialTheme.typography.bodyMedium,
                color = SuaEmerald
            )
        }
    }
}

@Composable
private fun SuaScreenLockOverlay(
    onUnlock: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF504070F))
            .clickable(enabled = true, onClick = {})
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(SuaCrimson.copy(alpha = 0.18f))
                    .border(2.dp, SuaCrimson, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "System Locked by SUA",
                    tint = SuaCrimson,
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                text = "SECURITY PROTOCOL // LOCKED",
                style = MaterialTheme.typography.headlineMedium,
                color = SuaTextPrimary
            )
            Text(
                text = "SUA Tactical Shield has locked this terminal on your voice command.",
                style = MaterialTheme.typography.bodyLarge,
                color = SuaTextSecondary,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onUnlock,
                modifier = Modifier
                    .height(54.dp)
                    .testTag("unlock_screen_button"),
                shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuaCyan,
                    contentColor = SuaObsidian
                )
            ) {
                Icon(imageVector = Icons.Default.LockOpen, contentDescription = "Unlock HUD")
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "DISENGAGE LOCK SHIELD",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
