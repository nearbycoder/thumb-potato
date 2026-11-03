package com.nearbycoder.thumbpotato.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object TonightRoute

@Serializable
object BrowseRoute

@Serializable
object BacklogRoute

@Serializable
object PlannerRoute

@Serializable
object ShuffleRoute

@Serializable
object SettingsRoute

@Serializable
data class DetailRoute(val key: String)

/** A paged grid: `kind` is platform, mood, genre, new or soon; `arg` is its id. */
@Serializable
data class GridRoute(val kind: String, val arg: String = "")
