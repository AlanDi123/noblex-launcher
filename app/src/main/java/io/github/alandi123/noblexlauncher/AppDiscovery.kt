package io.github.alandi123.noblexlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class AppDiscovery(private val context: Context) {
    @Suppress("DEPRECATION")
    fun discover(): List<LaunchableApp> {
        val packageManager = context.packageManager
        val activities = sequenceOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        ).flatMap { intent ->
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).asSequence()
        }

        return activities
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { result ->
                LaunchableApp(
                    packageName = result.activityInfo.packageName,
                    activityName = result.activityInfo.name,
                    label = result.loadLabel(packageManager).toString(),
                    icon = result.loadIcon(packageManager),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
