package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.PipelineStage
import com.example.ui.PetOracleViewModel
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.ChatTranscriptSection
import com.example.ui.components.DynamicListenControlBar
import com.example.ui.components.MascotPip
import com.example.ui.components.ModelManagerSheet
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicDeepBg
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OracleCyan
import com.example.ui.theme.OraclePink
import com.example.ui.theme.OracleViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private val viewModel: PetOracleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: PetOracleViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val pipelineStage by viewModel.memoryOrchestrator.currentStage.collectAsStateWithLifecycle()
    val ramMb by viewModel.memoryOrchestrator.estimatedRamUsageMb.collectAsStateWithLifecycle()
    val activePhaseDesc by viewModel.memoryOrchestrator.activePhaseDescription.collectAsStateWithLifecycle()
    val isDynamicListening by viewModel.whisperSttEngine.isDynamicListeningEnabled.collectAsStateWithLifecycle()
    val liveAmplitude by viewModel.liveVisualizerAmplitude.collectAsStateWithLifecycle()
    val showModelSheet by viewModel.showModelSheet.collectAsStateWithLifecycle()
    val modelsState by viewModel.downloadManager.modelsState.collectAsStateWithLifecycle()
    val isOwnerOnlyMode by viewModel.whisperSttEngine.isOwnerOnlyMode.collectAsStateWithLifecycle()
    val voiceMatchStatus by viewModel.whisperSttEngine.voiceMatchStatus.collectAsStateWithLifecycle()
    val isEnrollingVoice by viewModel.whisperSttEngine.isEnrollingVoice.collectAsStateWithLifecycle()
    val voicePrint by viewModel.whisperSttEngine.voicePrint.collectAsStateWithLifecycle()

    // Record Audio Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setDynamicListening(true)
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.whisperSttEngine.hasRecordPermission()) {
            viewModel.setDynamicListening(true)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        containerColor = CosmicDeepBg,
        bottomBar = {
            DynamicListenControlBar(
                isDynamicListening = isDynamicListening,
                pipelineStage = pipelineStage,
                onToggleDynamicListen = { enabled ->
                    if (enabled && !viewModel.whisperSttEngine.hasRecordPermission()) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.setDynamicListening(enabled)
                    }
                },
                onSubmitQuery = { query ->
                    viewModel.processUserQuery(query, isSpoken = false)
                },
                onInterrupt = {
                    viewModel.handleUserInterruption()
                },
                onOpenModelManager = {
                    viewModel.toggleModelSheet(true)
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // Top App Bar: Brand + RAM metric pill + Model manager icon
            TopCosmicHeader(
                currentRamMb = ramMb,
                onOpenModelManager = { viewModel.toggleModelSheet(true) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Speaker Learning & Owner Voice Match bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isOwnerOnlyMode) Color(0xFF132D24) else Color(0xFF1F1836),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isOwnerOnlyMode) Color(0xFF10B981) else CosmicBorder),
                    modifier = Modifier.clickable { viewModel.toggleOwnerVoiceOnly(!isOwnerOnlyMode) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isOwnerOnlyMode) Icons.Default.Person else Icons.Default.Group,
                            contentDescription = null,
                            tint = if (isOwnerOnlyMode) Color(0xFF10B981) else OracleCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isOwnerOnlyMode) "Owner Voice Only" else "Mode: All Voices",
                            color = if (isOwnerOnlyMode) Color(0xFF10B981) else Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.enrollUserVoice() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        tint = if (voicePrint.isEnrolled) Color(0xFF10B981) else OracleViolet,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEnrollingVoice) "Listening…" else if (voicePrint.isEnrolled) "Voice Learned ✓" else "Learn My Voice",
                        color = if (voicePrint.isEnrolled) Color(0xFF10B981) else OracleViolet,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Center Mascot & Reactive Aura (Big when listening, Small when processing)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                MascotPip(
                    stage = pipelineStage,
                    amplitude = liveAmplitude,
                    onHeadClick = { viewModel.onMascotHeadClick() }
                )
            }

            // Real-time Audio Waveform Visualizer
            AudioWaveformVisualizer(
                stage = pipelineStage,
                amplitude = liveAmplitude,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // Dynamic Listening Status Label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusDotColor = when (pipelineStage) {
                    PipelineStage.LISTENING -> OracleCyan
                    PipelineStage.THINKING -> OracleViolet
                    PipelineStage.SPEAKING -> OraclePink
                    PipelineStage.INTERRUPTED -> Color(0xFFFB7185)
                    PipelineStage.IDLE -> TextSecondary
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusDotColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (pipelineStage) {
                        PipelineStage.LISTENING -> "Dynamic Listening active • Whisper listening"
                        PipelineStage.THINKING -> "SmolLM2-135M thinking fractured wisdom…"
                        PipelineStage.SPEAKING -> "Piper TTS speaking helium pet voice"
                        PipelineStage.INTERRUPTED -> "Cut-in detected • Swift memory flush"
                        PipelineStage.IDLE -> "Standby • Tap mic or speak to Pip"
                    },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Conversational Transcript & Answers Feed
            ChatTranscriptSection(
                messages = messages,
                onReplayAudio = { msg ->
                    viewModel.replayAudio(msg)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Modal Bottom Sheet for Tri-Model & Phased Memory Management
    if (showModelSheet) {
        ModelManagerSheet(
            downloadManager = viewModel.downloadManager,
            modelsState = modelsState,
            memoryOrchestrator = viewModel.memoryOrchestrator,
            localServer = viewModel.localServer,
            currentRamMb = ramMb,
            activePhaseDescription = activePhaseDesc,
            cache = viewModel.cache,
            heliumPitch = viewModel.piperAudioEngine.pitchFactor,
            speechRate = viewModel.piperAudioEngine.speechRate,
            volumeLevel = viewModel.piperAudioEngine.volumeLevel,
            pitchVariance = viewModel.piperAudioEngine.pitchVariance,
            formantShift = viewModel.piperAudioEngine.formantShift,
            phonemeLength = viewModel.piperAudioEngine.phonemeLengthScale,
            onPitchChange = { viewModel.setHeliumPitch(it) },
            onRateChange = { viewModel.setSpeechSpeed(it) },
            onVolumeChange = { viewModel.setVolumeLevel(it) },
            onVarianceChange = { viewModel.setPitchVariance(it) },
            onFormantChange = { viewModel.setFormantShift(it) },
            onPhonemeLengthChange = { viewModel.setPhonemeLength(it) },
            onSelectProfile = { viewModel.applyVoiceProfile(it) },
            onTestHeliumVoice = {
                viewModel.piperAudioEngine.speak("Pip voice is helium pet squeak! Very light, much cute!")
            },
            onRestartServer = { viewModel.restartLocalServer() },
            onUpdateModelUrl = { id, url -> viewModel.updateModelUrl(id, url) },
            onFastInstallAll = { viewModel.fastInstallAllModels() },
            onDismiss = { viewModel.toggleModelSheet(false) }
        )
    }
}

@Composable
private fun TopCosmicHeader(
    currentRamMb: Float,
    onOpenModelManager: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Pip Oracle",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Whisper • SmolLM2-135M • Piper Helium",
                color = OracleViolet,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Live Phased RAM Indicator
            Surface(
                color = Color(0xFF1E1538),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder),
                modifier = Modifier.clickable { onOpenModelManager() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = OracleCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${"%.0f".format(currentRamMb)} MB RAM",
                        color = OracleCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onOpenModelManager,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF1E1538), CircleShape)
                    .border(1.dp, CosmicBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Model Config",
                    tint = TextPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
