package io.github.alandi123.noblexlauncher

import android.content.Context
import android.media.tv.TvInputManager
import android.media.tv.TvInputInfo

class TvInputRepository(private val context: Context) {
    fun discover(): List<TvInputEntry> {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            ?: return emptyList()

        return manager.tvInputList
            .filter { it.isPassthroughInput || it.type == TvInputInfo.TYPE_TUNER }
            .map { input ->
                TvInputEntry(
                    id = input.id,
                    label = input.loadLabel(context)?.toString()
                        ?.takeIf(String::isNotBlank)
                        ?: input.id,
                    packageName = input.serviceInfo.packageName,
                )
            }
            .distinctBy(TvInputEntry::id)
            .sortedBy(TvInputEntry::label)
    }
}
