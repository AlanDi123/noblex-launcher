package io.github.alandi123.noblexlauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

class TvInputRepository(private val context: Context) {
    private val manager get() = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

    fun discover(): List<TvInputEntry> {
        val manager = manager ?: return emptyList()
        return manager.tvInputList
            .filter { it.isPassthroughInput || it.type == TvInputInfo.TYPE_TUNER }
            .filterNot { it.isHidden(context) }
            .map { input ->
                TvInputEntry(
                    id = input.id,
                    label = input.loadLabel(context)?.toString()?.takeIf(String::isNotBlank) ?: input.id,
                    packageName = input.serviceInfo.packageName,
                    passthrough = input.isPassthroughInput,
                    state = manager.getInputState(input.id),
                )
            }
            .distinctBy(TvInputEntry::id)
            .sortedBy(TvInputEntry::label)
    }

    /**
     * Asks the system TV app to show a passthrough input using the public TvContract URI. Returns
     * false when no activity handles it, in which case the caller should explain the limitation.
     */
    fun switchTo(input: TvInputEntry): Boolean {
        if (!input.passthrough) return false
        val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        fun stateLabel(state: Int): String = when (state) {
            TvInputManager.INPUT_STATE_CONNECTED -> "Conectada"
            TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> "Dispositivo en espera"
            TvInputManager.INPUT_STATE_DISCONNECTED -> "Sin conexión"
            else -> "Estado desconocido"
        }
    }
}
