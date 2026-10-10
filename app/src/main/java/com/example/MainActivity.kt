package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.core.ByteFormat
import com.example.model.ModelId
import com.example.model.ModelItem
import com.example.model.ModelStatus
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
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val ramMb by viewModel.ramMb.collectAsStateWithLifecycle()
    val wantListening by viewModel.wantListening.collectAsStateWithLifecycle()
    val amplitude by viewModel.liveAmplitude.collectAsStateWithLifecycle()
    val showSheet by viewModel.showModelSheet.collectAsStateWithLifecycle()
    val models by viewModel.modelsState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val partial by viewModel.partialText.collectAsStateWithLifecycle()
    val loadingEars by viewModel.isLoadingEars.collectAsStateWithLifecycle()
    val pitch by viewModel.voicePitch.collectAsStateWithLifecycle()
    val rate by viewModel.voiceRate.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { viewModel.onPermissionResult() }

    // Ask for the microphone once at start (the setup card explains why Pip needs it).
    LaunchedEffect(Unit) {
        if (!viewModel.ears.hasPermission()) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.onPermissionResult()
        }
    }

    val listening = wantListening && viewModel.ears.hasPermission() && models[ModelId.STT]?.status == ModelStatus.READY

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("main_scaffold"),
        containerColor = CosmicDeepBg,
        bottomBar = {
            DynamicListenControlBar(
                isDynamicListening = listening,
                pipelineStage = stage,
                onToggleDynamicListen = {
                    if (!viewModel.ears.hasPermission()) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.toggleListening()
                    }
                },
                onSubmitQuery = { viewModel.ask(it) },
                onInterrupt = { viewModel.interrupt() },
                onOpenModelManager = { viewModel.toggleModelSheet(true) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).statusBarsPadding()
        ) {
            Header(ramMb = ramMb, onOpenSheet = { viewModel.toggleModelSheet(true) })

            val missing = listOf(ModelId.STT, ModelId.LLM).filter { models[it]?.status != ModelStatus.READY }
            if (missing.isNotEmpty()) {
                SetupCard(
                    models = models,
                    onDownload = { viewModel.downloadAll() },
                    onCancel = { missing.forEach { viewModel.cancelDownload(it) } }
                )
            }

            notice?.let { NoticeBar(it) }

            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                MascotPip(stage = stage, amplitude = amplitude, onHeadClick = { viewModel.onMascotTap() })
            }

            AudioWaveformVisualizer(
                stage = stage,
                amplitude = amplitude,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            StatusLine(stage = stage, listening = listening, loadingEars = loadingEars, partial = partial)

            Spacer(Modifier.height(4.dp))

            ChatTranscriptSection(
                messages = messages,
                onReplayAudio = { viewModel.replay(it) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showSheet) {
        ModelManagerSheet(
            models = models,
            ramMb = ramMb,
            cacheEntries = viewModel.cache.size(),
            pitch = pitch,
            rate = rate,
            urlFor = { viewModel.downloads.urlFor(it) },
            onDownload = { viewModel.download(it) },
            onCancel = { viewModel.cancelDownload(it) },
            onDelete = { viewModel.deleteModel(it) },
            onUrlChange = { id, url -> viewModel.updateModelUrl(id, url) },
            onProfile = { viewModel.applyVoiceProfile(it) },
            onPitch = { viewModel.setPitch(it) },
            onRate = { viewModel.setRate(it) },
            onTestVoice = { viewModel.testVoice() },
            onClearMemory = { viewModel.clearMemory() },
            onDismiss = { viewModel.toggleModelSheet(false) }
        )
    }
}

@Composable
private fun Header(ramMb: Float, onOpenSheet: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Pip", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("Lives on this phone. No cloud.", color = OracleViolet, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = Color(0xFF1E1538),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CosmicBorder),
                modifier = Modifier.clickable { onOpenSheet() }
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, null, tint = OracleCyan, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${"%.0f".format(ramMb)} MB", color = OracleCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onOpenSheet,
                modifier = Modifier.size(36.dp).background(Color(0xFF1E1538), CircleShape).border(1.dp, CosmicBorder, CircleShape)
            ) {
                Icon(Icons.Default.Tune, "Pip's brains and voice", tint = TextPrimary, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun SetupCard(models: Map<ModelId, ModelItem>, onDownload: () -> Unit, onCancel: () -> Unit) {
    val stt = models[ModelId.STT]
    val llm = models[ModelId.LLM]
    val active = listOfNotNull(stt, llm).firstOrNull {
        it.status == ModelStatus.DOWNLOADING || it.status == ModelStatus.INSTALLING
    }
    val failed = listOfNotNull(stt, llm).firstOrNull { it.status == ModelStatus.FAILED }

    Surface(
        color = Color(0xFF1E143B),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (failed != null && active == null) Color(0xFFFB7185) else OracleViolet.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("setup_card")
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Pip needs to learn to hear and think", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "One-time download of about 150 to 250 MB (best on Wi-Fi). After that Pip works with no internet at all, " +
                    "and nothing you say ever leaves the phone. Typing to Pip already works.",
                color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))

            if (active != null) {
                if (active.status == ModelStatus.DOWNLOADING && active.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { active.progress },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = OracleCyan, trackColor = Color(0xFF2E224D)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = OracleCyan, trackColor = Color(0xFF2E224D)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(if (active.id == ModelId.STT) "Ears" else "Brain")
                        append(": ")
                        append(active.currentFile)
                        if (active.totalBytes > 0) append("  ${ByteFormat.human(active.downloadedBytes)} of ${ByteFormat.human(active.totalBytes)}")
                        val s = ByteFormat.speed(active.bytesPerSecond)
                        if (s.isNotEmpty()) append("  ·  $s")
                    },
                    color = OracleCyan, fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(34.dp)) {
                    Text("Cancel", fontSize = 12.sp)
                }
            } else {
                failed?.error?.let {
                    Text(it, color = Color(0xFFFB7185), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("setup_error"))
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = OracleViolet, contentColor = Color(0xFF0F0B1E)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(38.dp).testTag("download_all_button")
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (failed != null) "Try again" else "Download Pip's brain", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NoticeBar(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .background(Color(0xFF3A1626), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, null, tint = Color(0xFFFB7185), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFFFFE4E6), fontSize = 12.sp, modifier = Modifier.testTag("notice_text"))
    }
}

@Composable
private fun StatusLine(stage: PipelineStage, listening: Boolean, loadingEars: Boolean, partial: String) {
    val dot = when (stage) {
        PipelineStage.LISTENING -> OracleCyan
        PipelineStage.THINKING -> OracleViolet
        PipelineStage.SPEAKING -> OraclePink
        PipelineStage.INTERRUPTED -> Color(0xFFFB7185)
        PipelineStage.IDLE -> TextSecondary
    }
    val text = when {
        loadingEars -> "Pip is waking up its ears…"
        stage == PipelineStage.LISTENING && partial.isNotBlank() -> "“$partial”"
        stage == PipelineStage.LISTENING -> "Listening… just talk to Pip"
        stage == PipelineStage.THINKING -> "Pip is thinking…"
        stage == PipelineStage.SPEAKING -> "Pip is talking (tap Pip to hush)"
        stage == PipelineStage.INTERRUPTED -> "Hush. Pip stops."
        listening -> "Ready"
        else -> "Mic is off. Tap the mic or type to Pip"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, modifier = Modifier.testTag("status_text"))
    }
}
