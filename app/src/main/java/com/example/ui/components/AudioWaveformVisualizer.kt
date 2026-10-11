package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.PipelineStage
import com.example.ui.theme.ListeningGlow
import com.example.ui.theme.SpeakingGlow
import com.example.ui.theme.ThinkingGlow
import kotlin.math.sin

@Composable
fun AudioWaveformVisualizer(
    stage: PipelineStage,
    amplitude: Float,
    modifier: Modifier = Modifier,
    heightDp: androidx.compose.ui.unit.Dp = 36.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_anim"
    )

    val primaryColor = when (stage) {
        PipelineStage.LISTENING -> ListeningGlow
        PipelineStage.THINKING -> ThinkingGlow
        PipelineStage.SPEAKING -> SpeakingGlow
        PipelineStage.INTERRUPTED -> Color(0xFFFB7185)
        PipelineStage.IDLE -> Color(0xFF6366F1).copy(alpha = 0.5f)
    }

    val secondaryColor = when (stage) {
        PipelineStage.LISTENING -> Color(0xFF38BDF8)
        PipelineStage.THINKING -> Color(0xFFE879F9)
        PipelineStage.SPEAKING -> Color(0xFFF472B6)
        PipelineStage.INTERRUPTED -> Color(0xFFF43F5E)
        PipelineStage.IDLE -> Color(0xFF818CF8).copy(alpha = 0.3f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .testTag("audio_waveform_visualizer")
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2f

        val effectiveAmp = if (stage == PipelineStage.IDLE) 0.08f else amplitude.coerceIn(0.12f, 1.0f)
        val maxWaveHeight = (height * 0.42f) * effectiveAmp

        // Primary waveform
        val path1 = Path()
        path1.moveTo(0f, midY)

        val points = 80
        val step = width / points

        for (i in 0..points) {
            val x = i * step
            val normalizedX = i.toFloat() / points
            // Envelope damping at edges
            val envelope = sin(normalizedX * Math.PI).toFloat()
            val y = midY + sin(normalizedX * 4 * Math.PI + phase).toFloat() * maxWaveHeight * envelope
            path1.lineTo(x, y)
        }

        drawPath(
            path = path1,
            brush = Brush.horizontalGradient(
                listOf(
                    primaryColor.copy(alpha = 0.1f),
                    primaryColor,
                    secondaryColor,
                    primaryColor.copy(alpha = 0.1f)
                )
            ),
            style = Stroke(width = 3.5f)
        )

        // Secondary harmonic wave
        val path2 = Path()
        path2.moveTo(0f, midY)
        for (i in 0..points) {
            val x = i * step
            val normalizedX = i.toFloat() / points
            val envelope = sin(normalizedX * Math.PI).toFloat()
            val y = midY + sin(normalizedX * 6 * Math.PI - phase * 1.3f).toFloat() * (maxWaveHeight * 0.65f) * envelope
            path2.lineTo(x, y)
        }

        drawPath(
            path = path2,
            brush = Brush.horizontalGradient(
                listOf(
                    secondaryColor.copy(alpha = 0.1f),
                    secondaryColor.copy(alpha = 0.7f),
                    primaryColor.copy(alpha = 0.7f),
                    secondaryColor.copy(alpha = 0.1f)
                )
            ),
            style = Stroke(width = 2.0f)
        )

        // Center glow dots
        val centerX = width / 2f
        drawCircle(
            color = primaryColor,
            radius = 3.5f + (effectiveAmp * 3.5f),
            center = Offset(centerX, midY)
        )
    }
}
