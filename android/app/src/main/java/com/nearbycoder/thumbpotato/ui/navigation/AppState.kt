package com.nearbycoder.thumbpotato.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.nearbycoder.thumbpotato.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Cross-screen requests that don't belong in navigation arguments. */
@Stable
class AppEvents {
    /** Game key the Planner should open a "Plan a Session" sheet for. */
    var pendingPlanKey by mutableStateOf<String?>(null)
}

val LocalAppEvents = compositionLocalOf { AppEvents() }

/** Current window width in dp, from the window container size. */
@Composable
fun windowWidthDp(): Float {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { size.width.toDp() }.value
}

/** Current window height in dp. */
@Composable
fun windowHeightDp(): Float {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { size.height.toDp() }.value
}

/** Medium and expanded width classes (>= 600 dp): tablets, desktops and unfolded foldables. */
@Composable
fun isWideLayout(): Boolean = windowWidthDp() >= 600f

/** Expanded width class (>= 840 dp). */
@Composable
fun isExpandedLayout(): Boolean = windowWidthDp() >= 840f

/** True when the app shows a navigation rail (medium and expanded widths) instead of a bottom bar. */
@Composable
fun hasNavigationRail(): Boolean = windowWidthDp() >= 600f

/**
 * Insets a screen's Scaffold should pad for. Under a bottom bar the bar already covers the
 * navigation-bar inset, so only the status bar is needed; next to a rail both apply.
 */
@Composable
fun screenInsets(): WindowInsets = if (hasNavigationRail()) WindowInsets.systemBars else WindowInsets.statusBars

/** Insets for edge-to-edge screens (hero at the top): only the bottom system bar next to a rail. */
@Composable
fun edgeToEdgeInsets(): WindowInsets = if (hasNavigationRail()) WindowInsets.navigationBars else WindowInsets(0, 0, 0, 0)
