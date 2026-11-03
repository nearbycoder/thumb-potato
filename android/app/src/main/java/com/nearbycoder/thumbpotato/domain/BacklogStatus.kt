package com.nearbycoder.thumbpotato.domain

enum class BacklogStatus(val raw: String, val label: String) {
    QUEUED("queued", "Want to Play"),
    PLAYING("playing", "Playing"),
    BEATEN("beaten", "Beaten"),
    SHELVED("shelved", "Shelved");

    companion object {
        fun fromRaw(raw: String?): BacklogStatus = entries.firstOrNull { it.raw == raw } ?: QUEUED
    }
}
