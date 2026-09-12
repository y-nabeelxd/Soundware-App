package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
            // Central App Logo Box
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

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle / Attribution: "by LuffyXD | Team XD"
            Text(
                text = stringResource(id = R.string.splash_subtitle),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = SoundwaveGray,
                letterSpacing = 1.5.sp,
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
