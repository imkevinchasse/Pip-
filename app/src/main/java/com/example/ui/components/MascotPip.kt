package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.PipelineStage
import com.example.ui.theme.ListeningGlow
import com.example.ui.theme.SpeakingGlow
import com.example.ui.theme.ThinkingGlow
import kotlin.math.roundToInt

@Composable
fun MascotPip(
    stage: PipelineStage,
    amplitude: Float,
    onHeadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pip_animations")

    // Gentle floating idle breathing
    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pip_float"
    )

    // Pulsing halo scale
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pip_halo"
    )

    // Dynamic amplitude bounce when speaking or listening
    val dynamicScale = remember { Animatable(1f) }
    LaunchedEffect(amplitude) {
        val target = 1f + (amplitude * 0.12f).coerceIn(0f, 0.20f)
        dynamicScale.animateTo(target, tween(60))
    }

    // Dynamic Head Sizing: BIG when listening, SMALL when processing/thinking!
    val animatedAvatarSize by animateDpAsState(
        targetValue = when (stage) {
            PipelineStage.LISTENING -> 195.dp  // BIG HEAD when listening!
            PipelineStage.THINKING -> 92.dp    // SMALL HEAD when processing!
            PipelineStage.SPEAKING -> 145.dp   // Lively medium size when speaking
            PipelineStage.INTERRUPTED -> 136.dp
            PipelineStage.IDLE -> 136.dp
        },
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "mascot_head_size"
    )

    val animatedHaloSize by animateDpAsState(
        targetValue = when (stage) {
            PipelineStage.LISTENING -> 255.dp
            PipelineStage.THINKING -> 122.dp
            PipelineStage.SPEAKING -> 190.dp
            PipelineStage.INTERRUPTED -> 174.dp
            PipelineStage.IDLE -> 174.dp
        },
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "mascot_halo_size"
    )

    val glowColor = when (stage) {
        PipelineStage.LISTENING -> ListeningGlow
        PipelineStage.THINKING -> ThinkingGlow
        PipelineStage.SPEAKING -> SpeakingGlow
        PipelineStage.INTERRUPTED -> Color(0xFFFB7185)
        PipelineStage.IDLE -> Color(0xFF818CF8)
    }

    val statusBadgeText = when (stage) {
        PipelineStage.LISTENING -> "🔴 Big Head: Listening • Tap to Process"
        PipelineStage.THINKING -> "⚡ Small Head: Thinking…"
        PipelineStage.SPEAKING -> "🐾 Helium Voice • Tap to Stop"
        PipelineStage.INTERRUPTED -> "Oop! Cut-in"
        PipelineStage.IDLE -> "🎙️ Tap Head to Talk"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .testTag("mascot_pip_container")
                .offset { IntOffset(0, floatOffsetY.roundToInt()) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onHeadClick() },
            contentAlignment = Alignment.Center
        ) {
            // Outer glowing celestial ring (dynamically scaled)
            Box(
                modifier = Modifier
                    .size(animatedHaloSize)
                    .scale(haloPulse * dynamicScale.value)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.50f),
                                glowColor.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Mascot avatar container (BIG on listen, SMALL on think)
            Box(
                modifier = Modifier
                    .size(animatedAvatarSize)
                    .scale(dynamicScale.value)
                    .clip(CircleShape)
                    .border(
                        width = if (stage == PipelineStage.LISTENING) 4.dp else 2.5.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                glowColor,
                                Color(0xFFE9D5FF),
                                glowColor.copy(alpha = 0.6f),
                                glowColor
                            )
                        ),
                        shape = CircleShape
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_pip_mascot),
                    contentDescription = "Pip Mascot Head (Click to speak or process)",
                    modifier = Modifier
                        .size(animatedAvatarSize)
                        .clip(CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive status hint badge under head
        Badge(
            containerColor = glowColor,
            contentColor = Color(0xFF0F0B1E),
            modifier = Modifier
                .border(1.5.dp, Color(0xFF1E1538), CircleShape)
                .clickable { onHeadClick() }
        ) {
            Text(
                text = statusBadgeText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}
