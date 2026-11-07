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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Psychology
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.geometry.Size
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
import com.example.data.CommandLogEntry
import com.example.data.MokoMemoryItem
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

    var commandInput by remember { mutableStateOf("") }
    var selectedStreamTab by remember { mutableStateOf(0) } // 0 = Chat History, 1 = Memory Vault
    var newMemoryText by remember { mutableStateOf("") }

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

    val quickSuggestions = remember {
        listOf(
            "Hi Moko, Call Rahul" to "Hi Moko Rahul ko call karo",
            "Flashlight ON" to "Flashlight chalu karo",
            "Flashlight OFF" to "Flashlight band karo",
            "Lock Phone" to "Phone ko lock karo",
            "Save My Name" to "Mera naam Boss hai",
            "What do you remember?" to "Tumhe kya yaad hai",
            "Battery Status" to "Battery kitni hai"
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SuaDeepNavy,
                drawerContentColor = SuaTextPrimary,
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .fillMaxHeight()
            ) {
                MokoSideDrawerContent(
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
                    onSelectMemoryTab = {
                        selectedStreamTab = 1
                        coroutineScope.launch { drawerState.close() }
                    },
                    onSelectHistoryTab = {
                        selectedStreamTab = 0
                        coroutineScope.launch { drawerState.close() }
                    },
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
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SuaSurfaceElevated)
                                        .border(1.dp, SuaCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Open Moko Menu & Settings Drawer",
                                        tint = SuaCyan
                                    )
                                }
                            }
                        },
                        title = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "MOKO",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = SuaTextPrimary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(SuaCyan.copy(alpha = 0.18f))
                                            .border(1.dp, SuaCyan.copy(alpha = 0.6f), RoundedCornerShape(50))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "AI + MEMORY",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuaCyan
                                        )
                                    }
                                }
                                Text(
                                    text = if (isBgRunning) {
                                        "Online • Works on Lock Screen ('Hi Moko')"
                                    } else {
                                        "Sleep Mode • Background Mic Off"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBgRunning) SuaEmerald else SuaTextSecondary
                                )
                            }
                        },
                        actions = {
                            // Dedicated ONLINE / SLEEP Mode Switcher Pill in Top Bar
                            OnlineSleepSegmentedSwitch(
                                isOnline = isBgRunning,
                                onSelectOnline = { requestOnlineMode() },
                                onSelectSleep = { viewModel.activateSleepMode() }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = SuaDeepNavy
                        )
                    )
                },
                bottomBar = {
                    // Clean Voice + Message Dual Control Bar on Main Screen
                    Surface(
                        color = SuaDeepNavy,
                        tonalElevation = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SuaBorder.copy(alpha = 0.7f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        .testTag("bottom_voice_toggle_button"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isBgRunning) SuaEmerald else SuaSurfaceElevated,
                                        contentColor = if (isBgRunning) SuaObsidian else SuaCyan
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isBgRunning) Icons.Default.Mic else Icons.Default.MicOff,
                                        contentDescription = "Toggle Moko Voice Mode",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isBgRunning) "VOICE ON" else "VOICE",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
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
                                            text = "Message Moko (Hindi / English)...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
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
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuaCyan,
                                        contentColor = SuaObsidian
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send Message to Moko"
                                    )
                                }
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

                        // CLEAN HERO MOKO NEURAL VOICE ORB CARD
                        item {
                            MokoVioletGlassOrbCard(
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
                        }

                        // COMPACT QUICK SUGGESTION CHIPS
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickSuggestions.forEachIndexed { idx, (labelEn, spokenCmd) ->
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
                                            borderColor = SuaCyan.copy(alpha = 0.45f)
                                        ),
                                        shape = RoundedCornerShape(50)
                                    )
                                }
                            }
                        }

                        // STREAM HEADER: CHAT HISTORY vs SAVED MEMORY VAULT
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = selectedStreamTab == 0,
                                        onClick = { selectedStreamTab = 0 },
                                        modifier = Modifier.testTag("tab_chat_history"),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("History (${commandLogs.size})") },
                                        shape = RoundedCornerShape(50),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SuaCyan.copy(alpha = 0.24f),
                                            selectedLabelColor = SuaCyan
                                        )
                                    )

                                    FilterChip(
                                        selected = selectedStreamTab == 1,
                                        onClick = { selectedStreamTab = 1 },
                                        modifier = Modifier.testTag("tab_memory_vault"),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Psychology,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("Memory (${memoryItems.size})") },
                                        shape = RoundedCornerShape(50),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SuaElectricBlue.copy(alpha = 0.24f),
                                            selectedLabelColor = SuaElectricBlue
                                        )
                                    )
                                }

                                if (selectedStreamTab == 0 && commandLogs.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.clearCommandHistory() },
                                        modifier = Modifier.testTag("clear_logs_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = "Clear All Chat History",
                                            tint = SuaTextSecondary
                                        )
                                    }
                                } else if (selectedStreamTab == 1 && memoryItems.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.clearAllMemories() },
                                        modifier = Modifier.testTag("clear_memories_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = "Clear All Saved Memories",
                                            tint = SuaTextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // TAB 0: CONVERSATION HISTORY (with Keep/Pin to Memory & Delete per item)
                        if (selectedStreamTab == 0) {
                            if (commandLogs.isEmpty()) {
                                item {
                                    EmptyGlassStateCard(
                                        title = "No Conversation History Yet",
                                        subtitle = "Say 'Hi Moko' in Online mode or send a message below. Every chat is saved here so you can keep it in Memory or delete it anytime."
                                    )
                                }
                            } else {
                                items(commandLogs, key = { it.id }) { logEntry ->
                                    MokoHistoryItemCard(
                                        entry = logEntry,
                                        onPinToMemory = { viewModel.pinChatToMemory(logEntry) },
                                        onDeleteItem = { viewModel.deleteCommandLogItem(logEntry.id) }
                                    )
                                }
                            }
                        } else {
                            // TAB 1: MOKO MEMORY VAULT (Auto-saved facts + Manual Add + Delete)
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, SuaBorder, RoundedCornerShape(18.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = newMemoryText,
                                            onValueChange = { newMemoryText = it },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("add_memory_input"),
                                            placeholder = {
                                                Text(
                                                    text = "Add a fact for Moko to remember...",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = SuaElectricBlue,
                                                unfocusedBorderColor = SuaBorder,
                                                focusedTextColor = SuaTextPrimary,
                                                unfocusedTextColor = SuaTextPrimary
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                if (newMemoryText.isNotBlank()) {
                                                    viewModel.addManualMemoryFact(newMemoryText)
                                                    newMemoryText = ""
                                                }
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SuaElectricBlue,
                                                contentColor = SuaTextPrimary
                                            ),
                                            modifier = Modifier.testTag("save_memory_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Save Memory"
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("KEEP")
                                        }
                                    }
                                }
                            }

                            if (memoryItems.isEmpty()) {
                                item {
                                    EmptyGlassStateCard(
                                        title = "Moko Memory Vault is Empty",
                                        subtitle = "Tell Moko 'Mera naam Rahul hai' or 'Yaad rakhna ki...', or tap the Bookmark icon on any chat message to keep it in Moko's memory."
                                    )
                                }
                            } else {
                                items(memoryItems, key = { it.id }) { memItem ->
                                    MokoMemoryCard(
                                        item = memItem,
                                        onDelete = { viewModel.deleteMemoryFact(memItem.id) }
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
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
            .clip(RoundedCornerShape(50))
            .background(SuaSurfaceCard)
            .border(
                width = 1.dp,
                color = if (isOnline) SuaEmerald.copy(alpha = 0.7f) else SuaBorder,
                shape = RoundedCornerShape(50)
            )
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ONLINE PILL BUTTON
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
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
                    contentDescription = "Switch Moko to Online Mode",
                    tint = if (isOnline) SuaObsidian else SuaTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "ONLINE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isOnline) SuaObsidian else SuaTextSecondary
                )
            }
        }

        // SLEEP PILL BUTTON (When in Sleep mode, tapping it also toggles Online as requested!)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (!isOnline) SuaCyanDim else Color.Transparent)
                .clickable {
                    if (isOnline) {
                        onSelectSleep()
                    } else {
                        // User asked: "स्लिप बटन पर क्लिक करने पर ऑनलाइन हो जाए"
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
                    contentDescription = "Switch Moko Sleep / Online Mode",
                    tint = if (!isOnline) SuaTextPrimary else SuaTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SLEEP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (!isOnline) SuaTextPrimary else SuaTextSecondary
                )
            }
        }
    }
}

@Composable
private fun MokoVioletGlassOrbCard(
    status: SuaStatus,
    isOnline: Boolean,
    audioRms: Float,
    liveTranscript: String,
    lastReply: String,
    memoryCount: Int,
    onToggleOnlineSleep: () -> Unit,
    onTestVoice: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "moko_orb_transition")
    val outerWaveRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_wave_rot"
    )
    val innerWaveRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_wave_rot"
    )
    val orbPulse by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val activeColor = when {
        status == SuaStatus.SPEAKING -> SuaElectricBlue
        status == SuaStatus.PROCESSING -> SuaAmber
        isOnline -> SuaCyan
        else -> SuaTextSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SuaSurfaceElevated.copy(alpha = 0.85f),
                            SuaSurfaceCard,
                            SuaDeepNavy
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            activeColor.copy(alpha = 0.8f),
                            SuaElectricBlue.copy(alpha = 0.5f),
                            SuaBorder
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Pill & Memory Badge Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isOnline) SuaEmerald.copy(alpha = 0.16f)
                                else SuaSurfaceElevated
                            )
                            .border(
                                1.dp,
                                if (isOnline) SuaEmerald else SuaBorder,
                                RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = when {
                                status == SuaStatus.SPEAKING -> "MOKO SPEAKING (HINDI)"
                                status == SuaStatus.PROCESSING -> "MOKO THINKING..."
                                isOnline -> "ONLINE • SAY 'HI MOKO'"
                                else -> "SLEEP MODE • MIC OFF"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOnline) SuaEmerald else SuaTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(SuaElectricBlue.copy(alpha = 0.15f))
                                .border(1.dp, SuaElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "$memoryCount Memories",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuaElectricBlue
                            )
                        }

                        IconButton(
                            onClick = onTestVoice,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("hud_voice_test_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Test Moko Voice",
                                tint = SuaCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // NEON VIOLET & MAGENTA GLASS ORB
                Box(
                    modifier = Modifier.size(176.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxR = size.minDimension / 2f
                        val rmsBoost = (audioRms / 12f) * 22f
                        val scale = if (isOnline || status != SuaStatus.IDLE) orbPulse else 0.92f

                        // Soft Neon Violet & Pink Radial Aura
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    activeColor.copy(alpha = if (isOnline) 0.36f else 0.12f),
                                    SuaElectricBlue.copy(alpha = if (isOnline) 0.16f else 0.04f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = maxR * scale + rmsBoost
                            ),
                            radius = maxR * scale + rmsBoost,
                            center = center
                        )

                        // Outer Rotating Violet Glass Arc
                        rotate(degrees = outerWaveRotation, pivot = center) {
                            val r1 = maxR * 0.88f
                            val arcSize = Size(r1 * 2f, r1 * 2f)
                            val topLeft = Offset(center.x - r1, center.y - r1)
                            for (i in 0 until 6) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        listOf(SuaCyan, SuaElectricBlue, SuaCyan)
                                    ),
                                    startAngle = i * 60f,
                                    sweepAngle = 38f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }

                        // Inner Counter-Rotating Magenta Pulse Ring
                        rotate(degrees = innerWaveRotation, pivot = center) {
                            val r2 = maxR * 0.72f
                            val arcSize = Size(r2 * 2f, r2 * 2f)
                            val topLeft = Offset(center.x - r2, center.y - r2)
                            for (i in 0 until 4) {
                                drawArc(
                                    color = activeColor.copy(alpha = 0.75f),
                                    startAngle = i * 90f,
                                    sweepAngle = 52f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    // Center Interactive Moko Core Button
                    Button(
                        onClick = onToggleOnlineSleep,
                        modifier = Modifier
                            .size(102.dp)
                            .testTag("mic_toggle_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOnline) SuaCyan else SuaSurfaceElevated,
                            contentColor = if (isOnline) SuaObsidian else SuaTextPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isOnline) Icons.Default.Mic else Icons.Default.Bedtime,
                                contentDescription = if (isOnline) "Switch Moko to Sleep" else "Wake Moko Online",
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isOnline) "ONLINE" else "SLEEP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = liveTranscript,
                    style = MaterialTheme.typography.labelLarge,
                    color = SuaCyan,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Frosted Glass Moko Reply Bubble
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SuaObsidian.copy(alpha = 0.72f))
                        .border(1.dp, SuaBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = SuaElectricBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "MOKO RESPONSE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuaElectricBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (isOnline) "LOCK-SCREEN READY" else "STANDBY",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOnline) SuaEmerald else SuaTextSecondary
                            )
                        }
                        Text(
                            text = lastReply,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SuaTextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MokoHistoryItemCard(
    entry: CommandLogEntry,
    onPinToMemory: () -> Unit,
    onDeleteItem: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (entry.isPinnedToMemory) SuaElectricBlue.copy(alpha = 0.7f) else SuaBorder,
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SuaCyan.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = entry.actionType.label,
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // KEEP / PIN TO MEMORY BUTTON
                    IconButton(
                        onClick = onPinToMemory,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("pin_memory_${entry.id}")
                    ) {
                        Icon(
                            imageVector = if (entry.isPinnedToMemory) {
                                Icons.Default.Bookmark
                            } else {
                                Icons.Default.BookmarkBorder
                            },
                            contentDescription = "Save to Moko Memory",
                            tint = if (entry.isPinnedToMemory) SuaElectricBlue else SuaTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // DELETE SINGLE HISTORY ITEM BUTTON
                    IconButton(
                        onClick = onDeleteItem,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_history_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete History Item",
                            tint = SuaCrimson.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = "You: \"${entry.userCommand}\"",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = SuaTextPrimary
            )
            Text(
                text = "Moko: ${entry.suaResponse}",
                style = MaterialTheme.typography.bodyMedium,
                color = SuaTextSecondary
            )
        }
    }
}

@Composable
private fun MokoMemoryCard(
    item: MokoMemoryItem,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SuaElectricBlue.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SuaElectricBlue.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = SuaElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = item.factText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SuaTextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${if (item.isAutoExtracted) "Auto-Remembered" else "Pinned Memory"} • ${item.timestamp}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuaTextSecondary
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_memory_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Memory Fact",
                    tint = SuaCrimson
                )
            }
        }
    }
}

@Composable
private fun EmptyGlassStateCard(
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SuaBorder, RoundedCornerShape(18.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SuaTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SuaTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MokoSideDrawerContent(
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
    onSelectMemoryTab: () -> Unit,
    onSelectHistoryTab: () -> Unit,
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
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MOKO CONTROL HUB",
                    style = MaterialTheme.typography.titleLarge,
                    color = SuaTextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Settings, Memory & Hardware Deck",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuaCyan
                )
            }
            IconButton(onClick = onCloseDrawer) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Side Menu",
                    tint = SuaTextSecondary
                )
            }
        }

        // PRIMARY SETTINGS & PERMISSIONS BUTTON IN SIDE DRAWER
        Button(
            onClick = onOpenFullSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("open_settings_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SuaCyan,
                contentColor = SuaObsidian
            )
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Open Moko Settings & Permissions"
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "SETTINGS, API KEY & PERMISSIONS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (!hasApiKey) {
            Text(
                text = "Tip: Open Settings above to paste your Gemini API Key & grant all permissions.",
                style = MaterialTheme.typography.labelSmall,
                color = SuaAmber
            )
        }

        HorizontalDivider(color = SuaBorder)

        // ONLINE vs SLEEP MODE CONTROL IN SIDE DRAWER
        Text(
            text = "BACKGROUND & LOCK-SCREEN MODE",
            style = MaterialTheme.typography.labelSmall,
            color = SuaCyan
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSwitchToOnline,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOnline) SuaEmerald else SuaSurfaceCard,
                    contentColor = if (isOnline) SuaObsidian else SuaTextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("ONLINE")
            }

            OutlinedButton(
                onClick = {
                    if (isOnline) onSwitchToSleep() else onSwitchToOnline()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (!isOnline) SuaCyan else SuaTextSecondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("SLEEP")
            }
        }

        HorizontalDivider(color = SuaBorder)

        // MEMORY & CHAT VAULT SHORTCUTS
        Text(
            text = "MEMORY & CONVERSATION VAULT",
            style = MaterialTheme.typography.labelSmall,
            color = SuaCyan
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onSelectHistoryTab,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = SuaCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("History ($historyCount)", color = SuaTextPrimary)
            }

            OutlinedButton(
                onClick = onSelectMemoryTab,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = SuaElectricBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Memory ($memoryCount)", color = SuaTextPrimary)
            }
        }

        HorizontalDivider(color = SuaBorder)

        // QUICK HARDWARE OVERRIDES (Moved here so Main Screen stays clean!)
        Text(
            text = "QUICK HARDWARE CONTROLS • BATTERY $batteryPercent%",
            style = MaterialTheme.typography.labelSmall,
            color = SuaCyan
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DrawerHardwareButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("flashlight_toggle_card"),
                label = if (isFlashlightOn) "Torch ON" else "Torch OFF",
                icon = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                accent = if (isFlashlightOn) SuaAmber else SuaCyan,
                onClick = onToggleFlashlight
            )

            DrawerHardwareButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("lock_phone_card"),
                label = "Lock Phone",
                icon = Icons.Default.Lock,
                accent = SuaCrimson,
                onClick = onLockPhone
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DrawerHardwareButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("call_khushi_card"),
                label = "Voice Call",
                icon = Icons.Default.Call,
                accent = SuaEmerald,
                onClick = { onExecuteCommand("Khushi ko call lagao") }
            )

            DrawerHardwareButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("send_sms_card"),
                label = "Quick SMS",
                icon = Icons.AutoMirrored.Filled.Message,
                accent = SuaElectricBlue,
                onClick = { onExecuteCommand("Khushi ko message karo ki I am on my way") }
            )
        }

        // Brightness & Volume Sliders inside Drawer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SuaBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BrightnessHigh,
                            contentDescription = null,
                            tint = SuaAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Brightness", style = MaterialTheme.typography.labelLarge, color = SuaTextPrimary)
                    }
                    Text("$brightnessPercent%", style = MaterialTheme.typography.labelLarge, color = SuaAmber)
                }
                Slider(
                    value = brightnessPercent.toFloat(),
                    onValueChange = { onBrightnessChange(it.toInt()) },
                    onValueChangeFinished = onBrightnessCommit,
                    valueRange = 5f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = SuaAmber,
                        activeTrackColor = SuaAmber
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = SuaCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sound Volume", style = MaterialTheme.typography.labelLarge, color = SuaTextPrimary)
                    }
                    Text("$volumePercent%", style = MaterialTheme.typography.labelLarge, color = SuaCyan)
                }
                Slider(
                    value = volumePercent.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt()) },
                    onValueChangeFinished = onVolumeCommit,
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = SuaCyan,
                        activeTrackColor = SuaCyan
                    )
                )

                // Media Transport Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onExecuteCommand("Previous song") }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Track", tint = SuaTextPrimary)
                    }
                    Button(
                        onClick = {
                            if (isMusicPlaying) onExecuteCommand("Music band karo")
                            else onExecuteCommand("Music chalao")
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuaCyan,
                            contentColor = SuaObsidian
                        )
                    ) {
                        Icon(
                            imageVector = if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play or Pause Music"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isMusicPlaying) "PAUSE" else "PLAY")
                    }
                    IconButton(onClick = { onExecuteCommand("Next song") }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Track", tint = SuaTextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHardwareButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = SuaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SuaScreenLockOverlay(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SuaObsidian.copy(alpha = 0.96f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(SuaCrimson.copy(alpha = 0.16f))
                    .border(2.dp, SuaCrimson, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Moko Security Shield",
                    tint = SuaCrimson,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "MOKO SCREEN LOCKED",
                style = MaterialTheme.typography.headlineMedium,
                color = SuaTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Moko is still listening in the background if Online Mode is active.",
                style = MaterialTheme.typography.bodyMedium,
                color = SuaTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onUnlock,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("unlock_overlay_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuaCyan,
                    contentColor = SuaObsidian
                )
            ) {
                Text(
                    text = "UNLOCK MOKO SCREEN",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
