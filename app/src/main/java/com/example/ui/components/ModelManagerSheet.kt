package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ByteFormat
import com.example.engine.RamMeter
import com.example.engine.VoiceProfile
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

private val Good = Color(0xFF10B981)
private val Bad = Color(0xFFFB7185)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerSheet(
    models: Map<ModelId, ModelItem>,
    ramMb: Float,
    cacheEntries: Int,
    pitch: Float,
    rate: Float,
    urlFor: (ModelId) -> String,
    onDownload: (ModelId) -> Unit,
    onCancel: (ModelId) -> Unit,
    onDelete: (ModelId) -> Unit,
    onUrlChange: (ModelId, String) -> Unit,
    onProfile: (VoiceProfile) -> Unit,
    onPitch: (Float) -> Unit,
    onRate: (Float) -> Unit,
    onTestVoice: () -> Unit,
    onClearMemory: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Pip's brains", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Everything runs on this phone. The internet is only used to download these once.",
                        color = TextSecondary, fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_sheet_button")) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(14.dp))
            RamCard(ramMb)
            Spacer(Modifier.height(14.dp))

            for (id in ModelId.entries) {
                val item = models[id] ?: ModelItem(id)
                ModelCard(
                    item = item,
                    url = urlFor(id),
                    onDownload = { onDownload(id) },
                    onCancel = { onCancel(id) },
                    onDelete = { onDelete(id) },
                    onUrlChange = { onUrlChange(id, it) }
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(6.dp))
            VoiceCard(pitch, rate, onProfile, onPitch, onRate, onTestVoice)

            Spacer(Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF22173D)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CosmicBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Pip's memory", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "$cacheEntries recent answers remembered (text only, in RAM, gone when Pip closes)",
                            color = TextMuted, fontSize = 11.sp
                        )
                    }
                    OutlinedButton(onClick = onClearMemory, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Delete, null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RamCard(ramMb: Float) {
    val fraction = (ramMb / RamMeter.BUDGET_MB).coerceIn(0f, 1f)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF22173D)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CosmicBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, null, tint = OracleCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Memory in use right now: ${"%.0f".format(ramMb)} MB",
                    color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = if (fraction > 0.9f) Bad else OracleCyan,
                trackColor = Color(0xFF2E224D)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Measured from the phone, not estimated. Pip's goal is to stay far below 1.5 GB: " +
                    "the language model loads only when thinking and is released when Pip is idle.",
                color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun ModelCard(
    item: ModelItem,
    url: String,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onUrlChange: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var urlText by remember(url) { mutableStateOf(url) }
    val downloadable = item.id != ModelId.TTS
    val (label, color) = when (item.status) {
        ModelStatus.READY -> "Ready" to Good
        ModelStatus.DOWNLOADING -> "Downloading ${(item.progress * 100).toInt()}%" to OracleCyan
        ModelStatus.INSTALLING -> "Unpacking…" to OracleCyan
        ModelStatus.FAILED -> "Problem" to Bad
        ModelStatus.NOT_DOWNLOADED -> (if (downloadable) "Not downloaded" else "Starting…") to TextMuted
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1538)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (item.status == ModelStatus.FAILED) Bad.copy(alpha = 0.6f) else CosmicBorder),
        modifier = Modifier.fillMaxWidth().testTag("model_card_${item.id.name}")
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.id.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(item.id.subtitle, color = TextSecondary, fontSize = 11.sp)
                }
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
                ) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (item.status == ModelStatus.FAILED) Icons.Default.Warning else Icons.Default.CheckCircle,
                            null, tint = color, modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(item.id.primaryFunction, color = OracleViolet, fontSize = 11.sp)

            if (item.status == ModelStatus.READY && item.sizeOnDiskBytes > 0) {
                Spacer(Modifier.height(2.dp))
                Text("Stored on this phone: ${ByteFormat.human(item.sizeOnDiskBytes)}", color = TextMuted, fontSize = 10.sp)
            }

            if (item.status == ModelStatus.DOWNLOADING || item.status == ModelStatus.INSTALLING) {
                Spacer(Modifier.height(8.dp))
                if (item.status == ModelStatus.DOWNLOADING && item.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = OracleCyan, trackColor = Color(0xFF2E224D)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = OracleCyan, trackColor = Color(0xFF2E224D)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(item.currentFile)
                        if (item.totalBytes > 0) append("  ${ByteFormat.human(item.downloadedBytes)} of ${ByteFormat.human(item.totalBytes)}")
                        val s = ByteFormat.speed(item.bytesPerSecond)
                        if (s.isNotEmpty()) append("  ·  $s")
                    },
                    color = OracleCyan, fontSize = 10.sp
                )
            }

            item.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Bad, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.testTag("model_error_${item.id.name}"))
            }

            if (downloadable) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (item.status) {
                        ModelStatus.DOWNLOADING, ModelStatus.INSTALLING ->
                            OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(32.dp)) {
                                Text("Cancel", fontSize = 11.sp)
                            }
                        ModelStatus.READY ->
                            OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(32.dp)) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Remove", fontSize = 11.sp)
                            }
                        else ->
                            Button(
                                onClick = onDownload,
                                colors = ButtonDefaults.buttonColors(containerColor = OracleViolet, contentColor = Color(0xFF0F0B1E)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp).testTag("download_${item.id.name}")
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (item.status == ModelStatus.FAILED) "Try again" else "Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                    }
                    IconButton(onClick = { editing = !editing }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "Use a different download link", tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                }
                if (editing) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("Download link (https only)", fontSize = 10.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onUrlChange(urlText); editing = false }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedBorderColor = OracleViolet, unfocusedBorderColor = CosmicBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceCard(
    pitch: Float,
    rate: Float,
    onProfile: (VoiceProfile) -> Unit,
    onPitch: (Float) -> Unit,
    onRate: (Float) -> Unit,
    onTest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF22173D)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CosmicBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RecordVoiceOver, null, tint = OraclePink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pip's voice", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onTest,
                    colors = ButtonDefaults.buttonColors(containerColor = OraclePink, contentColor = Color(0xFF0F0B1E)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(32.dp).testTag("test_voice_button")
                ) { Text("Hear Pip", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VoiceProfile.ALL.forEach { profile ->
                    Surface(
                        color = Color(0xFF2A1C4E),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, OraclePink.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { onProfile(profile) }
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.icon, fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(profile.name, color = Color(0xFFFCE7F3), fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SliderRow("Pitch", pitch, 0.5f..2.5f, OraclePink, onPitch)
            SliderRow("Speed", rate, 0.5f..2.0f, OracleViolet, onRate)
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, color: Color, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color(0xFFF1E8FF), fontSize = 11.sp)
            Text("${"%.2f".format(value)}x", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = Color(0xFF382963))
        )
    }
}
