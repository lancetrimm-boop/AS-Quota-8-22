package com.example.ui.components

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AuraMoment
import com.example.data.MediaRepository
import com.example.data.MomentCategory
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.AuraSurface
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryViolet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AuraMomentOverlay(
    repository: MediaRepository,
    modifier: Modifier = Modifier
) {
    var activeMoment by remember { mutableStateOf<AuraMoment?>(null) }
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    LaunchedEffect(Unit) {
        repository.momentDispatcher.moments.collectLatest { moment ->
            // Trigger Haptic feedback based on category
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    when (moment.category) {
                        MomentCategory.PULSE -> {
                            vibrator?.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                        MomentCategory.INSIGHT -> {
                            vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                        MomentCategory.CELEBRATION -> {
                            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 15, 50, 20), -1))
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(10)
                }
            } catch (_: Exception) {}

            if (moment.category != MomentCategory.PULSE) {
                activeMoment = moment
                val showDuration = if (moment.category == MomentCategory.CELEBRATION) 6000L else 4000L
                delay(showDuration)
                activeMoment = null
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = activeMoment != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .padding(horizontal = 24.dp)
        ) {
            activeMoment?.let { moment ->
                Surface(
                    color = AuraCrispWhite,
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(DiscoveryGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when(moment.category) {
                                    MomentCategory.CELEBRATION -> Icons.Outlined.AutoAwesome
                                    MomentCategory.INSIGHT -> Icons.Outlined.Lightbulb
                                    else -> Icons.Outlined.Info
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column {
                            Text(
                                text = moment.title.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = DiscoveryViolet,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = moment.message,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AuraMidnight
                            )
                        }
                    }
                }
            }
        }
    }
}
