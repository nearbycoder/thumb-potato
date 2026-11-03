package com.nearbycoder.thumbpotato.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nearbycoder.thumbpotato.R
import com.nearbycoder.thumbpotato.ui.components.PlatformPicker
import com.nearbycoder.thumbpotato.ui.components.PrimaryButton
import com.nearbycoder.thumbpotato.ui.navigation.LocalAppContainer
import com.nearbycoder.thumbpotato.ui.theme.Thumb
import kotlinx.coroutines.launch

/** Three skippable steps: welcome, pick your platforms, notifications. */
@Composable
fun OnboardingScreen() {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val colors = Thumb.colors
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val finish: () -> Unit = { scope.launch { container.catalog.reset(); container.settings.setHasOnboarded(true) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    Box(Modifier.fillMaxSize().background(colors.bg).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { i ->
                        Box(Modifier.size(if (i == step) 8.dp else 6.dp).clip(CircleShape).background(if (i == step) colors.accent else colors.textTertiary))
                    }
                }
                TextButton(onClick = finish) { Text("Skip", color = colors.textSecondary) }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "onboarding",
            ) { current ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (current) {
                            0 -> {
                                Spacer(Modifier.height(24.dp))
                                Box(
                                    Modifier.size(112.dp).clip(RoundedCornerShape(28.dp))
                                        .background(Brush.verticalGradient(listOf(Color(0xFF1E2027), Color(0xFF0B0C10)))),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(painterResource(R.mipmap.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(160.dp))
                                }
                                Text("Thumb Potato", style = MaterialTheme.typography.displaySmall, color = colors.textPrimary, textAlign = TextAlign.Center)
                                Text(
                                    "Everything you want to play, in one place. Keep a backlog, follow release dates, plan play sessions, and let Shuffle pick when you can't decide.",
                                    style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, textAlign = TextAlign.Center,
                                )
                            }
                            1 -> {
                                Text("Your platforms", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary, textAlign = TextAlign.Center)
                                Text(
                                    "Pick what you play on. Tonight, Moods and Shuffle are built around them. You can change this any time in Settings.",
                                    style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center,
                                )
                                PlatformPicker(selected = settings.myPlatformIds, onToggle = { p -> scope.launch { container.settings.togglePlatform(p) } })
                            }
                            else -> {
                                Spacer(Modifier.height(24.dp))
                                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = colors.accent, modifier = Modifier.size(56.dp))
                                Text("Release days and play nights", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary, textAlign = TextAlign.Center)
                                Text(
                                    "Get a heads-up at 9 AM when a game you follow comes out, and a reminder before a planned play session.",
                                    style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (step) {
                    0 -> PrimaryButton("Get Started", onClick = { step = 1 }, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
                    1 -> PrimaryButton(
                        if (settings.myPlatformIds.isEmpty()) "Continue" else "Continue with ${settings.myPlatformIds.size}",
                        onClick = { step = 2 },
                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    )
                    else -> {
                        PrimaryButton(
                            "Turn On Notifications",
                            onClick = { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else finish() },
                            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                        )
                        TextButton(onClick = finish) { Text("Not Now", color = colors.textSecondary) }
                    }
                }
            }
        }
    }
}
