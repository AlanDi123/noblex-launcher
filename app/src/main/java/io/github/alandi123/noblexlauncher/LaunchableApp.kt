package io.github.alandi123.noblexlauncher

import android.graphics.drawable.Drawable

data class LaunchableApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable,
)

data class TvInputEntry(
    val id: String,
    val label: String,
    val packageName: String,
)
