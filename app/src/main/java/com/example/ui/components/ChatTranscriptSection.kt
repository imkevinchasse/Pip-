package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.OracleCyan
import com.example.ui.theme.OraclePink
import com.example.ui.theme.OracleViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatTranscriptSection(
    messages: List<ChatMessage>,
    onReplayAudio: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = OracleViolet.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Speak to Pip to hear fractured wisdom!",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("chat_transcript_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubbleCard(
                    msg = msg,
                    onReplay = { onReplayAudio(msg) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubbleCard(
    msg: ChatMessage,
    onReplay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_card_${msg.id}")
    ) {
        // User Query Bubble (Right-aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = Color(0xFF241A44),
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder),
                modifier = Modifier.padding(start = 48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg.userQuery,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pip Fractured Wisdom Bubble (Left-aligned)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = CosmicSurface
            ),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (msg.isPlaying) OraclePink else CosmicBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 24.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header: Pip tag + Replay Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(OracleViolet, OraclePink))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🐾",
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pip (Helium Voice)",
                            color = OraclePink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onReplay,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("replay_audio_button")
                    ) {
                        Icon(
                            imageVector = if (msg.isPlaying) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                            contentDescription = "Replay Helium Speech",
                            tint = if (msg.isPlaying) OraclePink else OracleCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fractured wisdom text
                Text(
                    text = msg.fracturedResponse,
                    color = Color(0xFFFDF4FF),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Normal
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Latency and Cache metrics chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (msg.wasCached) {
                        MetricPill(
                            icon = Icons.Default.Bolt,
                            label = "Cache Hit <10ms",
                            color = Color(0xFF10B981)
                        )
                    } else {
                        MetricPill(
                            icon = Icons.Default.Psychology,
                            label = "LLM: ${msg.llmLatencyMs}ms",
                            color = OracleViolet
                        )
                    }

                    if (msg.sttLatencyMs > 0) {
                        MetricPill(
                            icon = Icons.Default.GraphicEq,
                            label = "STT: ${msg.sttLatencyMs}ms",
                            color = OracleCyan
                        )
                    }

                    MetricPill(
                        icon = Icons.Default.RecordVoiceOver,
                        label = "TTS: ${msg.ttsLatencyMs}ms",
                        color = OraclePink
                    )

                    if (msg.wasInterrupted) {
                        MetricPill(
                            icon = Icons.Default.Pause,
                            label = "Interrupted",
                            color = Color(0xFFFB7185)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
