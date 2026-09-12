package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.SoundwaveDark
import com.example.ui.theme.SoundwaveGray
import com.example.ui.theme.SoundwaveGreen
import com.example.ui.theme.SoundwaveSurfaceVariant
import com.example.ui.theme.SoundwaveWhite
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    progress: Float = -1f, // -1 for indeterminate
    animate: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SoundwaveDark)
            .testTag("splash_screen_root"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Central Logo
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_soundwave_logo),
                    contentDescription = "Soundwave Logo",
                    tint = SoundwaveWhite,
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("splash_logo")
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Equalizer Wave
            if (animate) {
                SoundwaveBarsAnimation()
            } else {
                StaticSoundwaveBars()
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App Name
            Text(
                text = stringResource(id = R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = SoundwaveWhite,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("splash_title")
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(id = R.string.splash_subtitle),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = SoundwaveGray,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Progress bar in Spotify green
            if (progress >= 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .width(180.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .testTag("splash_progress_determinate"),
                    color = SoundwaveGreen,
                    trackColor = SoundwaveSurfaceVariant
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(180.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .testTag("splash_progress_indeterminate"),
                    color = SoundwaveGreen,
                    trackColor = SoundwaveSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SoundwaveBarsAnimation() {
    val barHeights = listOf(
        remember { Animatable(12f) },
        remember { Animatable(24f) },
        remember { Animatable(36f) },
        remember { Animatable(22f) },
        remember { Animatable(14f) }
    )

    val targetHeights = listOf(
        listOf(24f, 10f, 20f, 14f),
        listOf(34f, 16f, 30f, 20f),
        listOf(42f, 20f, 38f, 26f),
        listOf(32f, 14f, 28f, 18f),
        listOf(22f, 8f, 18f, 12f)
    )

    barHeights.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            delay(index * 90L)
            var step = 0
            while (true) {
                val nextTarget = targetHeights[index][step % targetHeights[index].size]
                animatable.animateTo(
                    targetValue = nextTarget,
                    animationSpec = tween(
                        durationMillis = 350 + (index * 40),
                        easing = FastOutSlowInEasing
                    )
                )
                step++
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(48.dp)
    ) {
        barHeights.forEach { heightAnim ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(heightAnim.value.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SoundwaveGreen)
            )
        }
    }
}

@Composable
private fun StaticSoundwaveBars() {
    val staticHeights = listOf(14.dp, 26.dp, 38.dp, 24.dp, 16.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(48.dp)
    ) {
        staticHeights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SoundwaveGreen)
            )
        }
    }
}

