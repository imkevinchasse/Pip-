package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PipelineStage
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.ListeningGlow
import com.example.ui.theme.OracleCyan
import com.example.ui.theme.OraclePink
import com.example.ui.theme.OracleViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DynamicListenControlBar(
    isDynamicListening: Boolean,
    pipelineStage: PipelineStage,
    onToggleDynamicListen: (Boolean) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onInterrupt: () -> Unit,
    onOpenModelManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val presetChips = listOf(
        "Why is life hard?",
        "Should I put my savings in a meme coin?",
        "I've never gone skydiving. Should I?",
        "Who are you?",
        "What is love?"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CosmicSurface)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(CosmicBorder, Color.Transparent)),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(top = 10.dp, bottom = 8.dp)
            .navigationBarsPadding()
    ) {
        // Preset question chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presetChips.forEach { chipText ->
                Surface(
                    color = Color(0xFF231840),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder),
                    modifier = Modifier
                        .clickable { onSubmitQuery(chipText) }
                        .testTag("preset_chip_${chipText.take(10)}")
                ) {
                    Text(
                        text = chipText,
                        color = Color(0xFFE9D5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interruption Alert Banner (when Pip is speaking)
        AnimatedVisibility(visible = pipelineStage == PipelineStage.SPEAKING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onInterrupt,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE11D48),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("rapid_interrupt_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Interrupt Pip",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hush Pip",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Input & Controls Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Model Diagnostics / Config sheet button
            IconButton(
                onClick = onOpenModelManager,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF261D42), CircleShape)
                    .border(1.dp, CosmicBorder, CircleShape)
                    .testTag("open_model_manager_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Model Config & Storage",
                    tint = OracleViolet,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text input box
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = {
                    Text(
                        text = if (isDynamicListening) "Pip is listening, or type here…" else "Ask Pip anything…",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank()) {
                            onSubmitQuery(textInput)
                            textInput = ""
                            focusManager.clearFocus()
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OracleViolet,
                    unfocusedBorderColor = CosmicBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = Color(0xFF1E1538),
                    unfocusedContainerColor = Color(0xFF1E1538)
                ),
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    if (textInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onSubmitQuery(textInput)
                                textInput = ""
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.testTag("send_query_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = OracleViolet,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("query_text_input")
            )

            // Dynamic Listening / Mic Button with pulse animation
            DynamicMicButton(
                isDynamicListening = isDynamicListening,
                isSpeaking = pipelineStage == PipelineStage.SPEAKING,
                onClick = {
                    if (pipelineStage == PipelineStage.SPEAKING) {
                        onInterrupt()
                    } else {
                        onToggleDynamicListen(!isDynamicListening)
                    }
                }
            )
        }
    }
}

@Composable
private fun DynamicMicButton(
    isDynamicListening: Boolean,
    isSpeaking: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isDynamicListening) 1.14f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isSpeaking -> Color(0xFFE11D48)
            isDynamicListening -> OracleCyan
            else -> Color(0xFF2E224D)
        },
        label = "mic_bg"
    )

    val iconColor = if (isDynamicListening || isSpeaking) Color(0xFF0F0B1E) else OracleViolet

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(if (isDynamicListening) pulseScale else 1f)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                1.5.dp,
                if (isDynamicListening) ListeningGlow else CosmicBorder,
                CircleShape
            )
            .clickable { onClick() }
            .testTag("dynamic_mic_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSpeaking) Icons.Default.Stop else if (isDynamicListening) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = if (isDynamicListening) "Dynamic Listening Active" else "Dynamic Listening Off",
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}
