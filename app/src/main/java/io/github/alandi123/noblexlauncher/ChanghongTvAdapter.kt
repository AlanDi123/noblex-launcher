package io.github.alandi123.noblexlauncher

import android.content.Context
import android.content.Intent

class ChanghongTvAdapter(private val context: Context) {
    fun openTelevisionApp(): Boolean {
        val launchIntent = TELEVISION_PACKAGES
            .asSequence()
            .mapNotNull(context.packageManager::getLaunchIntentForPackage)
            .firstOrNull()
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    companion object {
        private val TELEVISION_PACKAGES = listOf(
            "com.mediatek.wwtv.tvcenter",
            "com.homwee.aipont",
        )
    }
}
