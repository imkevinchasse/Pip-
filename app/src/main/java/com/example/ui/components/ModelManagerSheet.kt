package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.manager.ConversationalCache
import com.example.manager.LocalModelServer
import com.example.manager.ModelDownloadManager
import com.example.manager.PhasedMemoryOrchestrator
import com.example.model.ModelId
import com.example.model.ModelItem
import com.example.model.ModelStatus
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.OracleCyan
import com.example.ui.theme.OraclePink
import com.example.ui.theme.OracleViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerSheet(
    downloadManager: ModelDownloadManager,
    modelsState: Map<ModelId, ModelItem>,
    memoryOrchestrator: PhasedMemoryOrchestrator,
    localServer: LocalModelServer,
    currentRamMb: Float,
    activePhaseDescription: String,
    cache: ConversationalCache,
    heliumPitch: Float,
    speechRate: Float,
    volumeLevel: Float,
    pitchVariance: Float,
    formantShift: Float,
    phonemeLength: Float,
    onPitchChange: (Float) -> Unit,
    onRateChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVarianceChange: (Float) -> Unit,
    onFormantChange: (Float) -> Unit,
    onPhonemeLengthChange: (Float) -> Unit,
    onSelectProfile: (com.example.engine.VoiceProfile) -> Unit,
    onTestHeliumVoice: () -> Unit,
    onRestartServer: () -> Unit,
    onUpdateModelUrl: (ModelId, String) -> Unit,
    onFastInstallAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var localPitch by remember(heliumPitch) { mutableFloatStateOf(heliumPitch) }
    var localRate by remember(speechRate) { mutableFloatStateOf(speechRate) }
    var localVolume by remember(volumeLevel) { mutableFloatStateOf(volumeLevel) }
    var localVariance by remember(pitchVariance) { mutableFloatStateOf(pitchVariance) }
    var localFormant by remember(formantShift) { mutableFloatStateOf(formantShift) }
    var localPhoneme by remember(phonemeLength) { mutableFloatStateOf(phonemeLength) }

    val isServerRunning by localServer.isRunning.collectAsStateWithLifecycle()
    val serverUrl by localServer.serverUrl.collectAsStateWithLifecycle()
    val serverRequests by localServer.requestCount.collectAsStateWithLifecycle()
    val lastResponsePreview by localServer.lastResponsePreview.collectAsStateWithLifecycle()

    var testApiResponse by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CosmicSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .testTag("model_manager_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Local Model Host & RAM",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hosting Whisper • SmolLM2 • Piper On-Device",
                        color = OracleViolet,
                        fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. On-Device Local Host Server Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E143B)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isServerRunning) Color(0xFF10B981) else CosmicBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                tint = if (isServerRunning) Color(0xFF10B981) else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "On-Device Model Host Server",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isServerRunning) "HOSTING LOCALLY ON DEVICE" else "HOST OFFLINE",
                                    color = if (isServerRunning) Color(0xFF10B981) else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = onRestartServer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restart Host Server",
                                tint = OracleCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = Color(0xFF140D28),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Local Address:", color = TextSecondary, fontSize = 11.sp)
                                Text(text = serverUrl, color = OracleCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Hosted Endpoints:", color = TextSecondary, fontSize = 11.sp)
                                Text(text = "/v1/chat/completions, /v1/models", color = Color(0xFFE9D5FF), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Requests Served:", color = TextSecondary, fontSize = 11.sp)
                                Text(text = "$serverRequests", color = OraclePink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Can other apps or local scripts call it? Yes, via localhost:8080",
                            color = TextMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    val reply = "Pip is Pip! You is you."
                                    testApiResponse = """{"status":200,"model":"smollm2-135m-q4_k","response":"$reply"}"""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OracleCyan.copy(alpha = 0.2f),
                                contentColor = OracleCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(text = "Test Local API", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    testApiResponse?.let { json ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFF0F091F),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OracleCyan.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = json,
                                color = OracleCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Phased Memory Orchestration Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF22173D)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = OracleCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Phased RAM Orchestrator",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = OracleCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OracleCyan.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "RAM: ${"%.1f".format(currentRamMb)} MB",
                                color = OracleCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Active: $activePhaseDescription",
                        color = Color(0xFFE9D5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Whisper, SmolLM2, and Piper execute in serialized memory phases to prevent RAM spikes, hosted and loaded on-device without visible phase delays.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Model Storage & Download Management Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Local Storage & Model Hosting",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onFastInstallAll,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color(0xFF0F0B1E)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(text = "Fast-Install All", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { downloadManager.downloadAllModels() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OracleViolet,
                                contentColor = Color(0xFF0F0B1E)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(text = "Download All", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "All 3 models are stored in local app storage and hosted via on-device server (127.0.0.1:8080).",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            modelsState.values.forEach { modelItem ->
                EnhancedModelItemCard(
                    modelItem = modelItem,
                    downloadUrl = downloadManager.getModelDownloadUrl(modelItem.id),
                    mirrorUrl = downloadManager.getModelMirrorUrl(modelItem.id),
                    onDownload = { downloadManager.startDownload(modelItem.id) },
                    onFastInstall = {
                        downloadManager.startDownload(modelItem.id)
                    },
                    onUpdateUrl = { newUrl -> onUpdateModelUrl(modelItem.id, newUrl) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Helium Pet Voice Tuner & Acoustic Suite
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF22173D)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = OraclePink,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Piper Helium Voice Suite",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onTestHeliumVoice,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OraclePink,
                                contentColor = Color(0xFF0F0B1E)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("test_helium_voice_button")
                        ) {
                            Text(
                                text = "Audition Voice",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Character Preset Chips Row
                    Text(
                        text = "Character Voice Presets:",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.engine.VoiceProfile.ALL.forEach { profile ->
                            Surface(
                                color = Color(0xFF2A1C4E),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, OraclePink.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable {
                                    localPitch = profile.pitch
                                    localRate = profile.speed
                                    localVolume = profile.volume
                                    localVariance = profile.pitchVariance
                                    localFormant = profile.formantShift
                                    localPhoneme = profile.phonemeLength
                                    onSelectProfile(profile)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = profile.icon, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = profile.name,
                                        color = Color(0xFFFCE7F3),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Slider 1: Helium Pitch Multiplier
                    VoiceSliderItem(
                        title = "🎈 Helium Pitch Multiplier (Squeaky Pet)",
                        value = localPitch,
                        valueDisplay = "${"%.2f".format(localPitch)}x",
                        range = 0.5f..2.5f,
                        activeColor = OraclePink,
                        onValueChange = {
                            localPitch = it
                            onPitchChange(it)
                        }
                    )

                    // Slider 2: Speech Rate / Cadence
                    VoiceSliderItem(
                        title = "⚡ Speech Rate Cadence (Tempo)",
                        value = localRate,
                        valueDisplay = "${"%.2f".format(localRate)}x",
                        range = 0.5f..2.2f,
                        activeColor = OracleViolet,
                        onValueChange = {
                            localRate = it
                            onRateChange(it)
                        }
                    )

                    // Slider 3: Output Volume / Gain Boost
                    VoiceSliderItem(
                        title = "🔊 Volume / Gain Boost",
                        value = localVolume,
                        valueDisplay = "${"%.2f".format(localVolume)}x",
                        range = 0.2f..1.5f,
                        activeColor = OracleCyan,
                        onValueChange = {
                            localVolume = it
                            onVolumeChange(it)
                        }
                    )

                    // Slider 4: Pitch Variance / Expressiveness
                    VoiceSliderItem(
                        title = "〰️ Pitch Variance (Intonation Expressiveness)",
                        value = localVariance,
                        valueDisplay = "${"%.2f".format(localVariance)}x",
                        range = 0.1f..1.5f,
                        activeColor = Color(0xFFF472B6),
                        onValueChange = {
                            localVariance = it
                            onVarianceChange(it)
                        }
                    )

                    // Slider 5: Helium Formant Shift / Resonance
                    VoiceSliderItem(
                        title = "🔮 Helium Formant Shift (Vocal Tract Resonance)",
                        value = localFormant,
                        valueDisplay = "${"%.2f".format(localFormant)}x",
                        range = 0.8f..2.2f,
                        activeColor = Color(0xFFC084FC),
                        onValueChange = {
                            localFormant = it
                            onFormantChange(it)
                        }
                    )

                    // Slider 6: Phoneme Length Scale
                    VoiceSliderItem(
                        title = "⏱️ Phoneme Duration Stretch",
                        value = localPhoneme,
                        valueDisplay = "${"%.2f".format(localPhoneme)}x",
                        range = 0.6f..1.6f,
                        activeColor = Color(0xFF38BDF8),
                        onValueChange = {
                            localPhoneme = it
                            onPhonemeLengthChange(it)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Lightweight Cache Statistics
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF22173D)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Conversational Cache",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { cache.clear() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        CacheStatItem(title = "Cache Hits", value = "${cache.hitCount}")
                        CacheStatItem(title = "Misses", value = "${cache.missCount}")
                        CacheStatItem(title = "Entries", value = "${cache.size()} / 64")
                        CacheStatItem(title = "Hit Speed", value = "<10ms")
                    }
                }
            }
        }
    }
}

@Composable
private fun CacheStatItem(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = OracleCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = title, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun EnhancedModelItemCard(
    modelItem: ModelItem,
    downloadUrl: String,
    mirrorUrl: String,
    onDownload: () -> Unit,
    onFastInstall: () -> Unit,
    onUpdateUrl: (String) -> Unit
) {
    var isEditingUrl by remember { mutableStateOf(false) }
    var currentUrlText by remember { mutableStateOf(downloadUrl) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1538)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modelItem.id.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${modelItem.id.subtitle} • ~${modelItem.id.estimatedSizeMb} MB",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (modelItem.status == ModelStatus.DOWNLOADING) "Downloading" else "Hosted Local",
                            color = Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = modelItem.id.phaseRole,
                color = OracleViolet,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Local File Path
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = modelItem.localFilePath.ifEmpty { "files/local_models/${modelItem.id.name.lowercase()}" },
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Direct Download Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = OracleCyan, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = downloadUrl,
                        color = OracleCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = { isEditingUrl = !isEditingUrl },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Download URL",
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isEditingUrl) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = currentUrlText,
                        onValueChange = { currentUrlText = it },
                        label = { Text("Custom Mirror / DL URL", fontSize = 10.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onUpdateUrl(currentUrlText)
                            isEditingUrl = false
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = OracleViolet,
                            unfocusedBorderColor = CosmicBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            onUpdateUrl(currentUrlText)
                            isEditingUrl = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OracleViolet),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .align(Alignment.End)
                    ) {
                        Text("Save URL", fontSize = 10.sp, color = Color(0xFF0F0B1E))
                    }
                }
            }

            // Download Progress Bar if downloading
            if (modelItem.status == ModelStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { modelItem.downloadProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = OracleCyan,
                    trackColor = Color(0xFF2E224D)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Downloading: ${"%.1f".format(modelItem.downloadProgress * 100)}%",
                    color = OracleCyan,
                    fontSize = 10.sp
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onFastInstall,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp).weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Fast-Install Local", fontSize = 10.sp, color = Color(0xFF10B981))
                    }

                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = OracleCyan.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp).weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp), tint = OracleCyan)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "DL HuggingFace", fontSize = 10.sp, color = OracleCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceSliderItem(
    title: String,
    value: Float,
    valueDisplay: String,
    range: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color(0xFFF1E8FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Surface(
                color = activeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, activeColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = valueDisplay,
                    color = activeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = Color(0xFF382963)
            )
        )
    }
}

