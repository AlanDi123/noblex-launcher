package io.github.alandi123.noblexlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo

class AppDiscovery(private val context: Context) {
    /** Safe to call from a background thread. Prefers the TV (leanback) entry of each package. */
    fun discover(): List<LaunchableApp> {
        val packageManager = context.packageManager
        val seen = HashSet<String>()
        val results = ArrayList<LaunchableApp>()
        listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).forEach { category ->
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            val activities: List<ResolveInfo> = packageManager.queryIntentActivities(query, 0)
            activities.forEach { result ->
                val info = result.activityInfo
                if (info.packageName == context.packageName || !seen.add(info.packageName)) return@forEach
                results += LaunchableApp(
                    packageName = info.packageName,
                    activityName = info.name,
                    label = result.loadLabel(packageManager).toString(),
                    icon = result.loadIcon(packageManager),
                    system = (info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0,
                    banner = if (category == Intent.CATEGORY_LEANBACK_LAUNCHER) info.loadBanner(packageManager) else null,
                )
            }
        }
        return results.sortedBy { it.label.lowercase() }
    }
}
