package io.github.alandi123.noblexlauncher

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.net.UnknownHostException
import org.json.JSONException
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        try {
            val release = UpdateManager.fetchLatestBlocking()
            val preferences = LauncherPreferences(applicationContext)
            val decision = UpdatePolicy.decide(release, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
            if (decision == UpdateDecision.AVAILABLE) {
                preferences.saveReleaseManifest(
                    org.json.JSONObject().apply {
                        put("version", release.versionName)
                        put("versionCode", release.versionCode)
                    },
                )
            } else {
                preferences.saveReleaseManifest(null)
            }
            Result.success()
        } catch (error: UnknownHostException) {
            retryOrGiveUp()
        } catch (error: IOException) {
            if (error.message?.contains("Aún no hay una versión pública") == true) Result.success() else retryOrGiveUp()
        } catch (error: JSONException) {
            Result.failure(workDataOf("error" to "El manifiesto de actualización no es JSON válido."))
        } catch (error: SecurityException) {
            Result.failure(workDataOf("error" to "Android rechazó la comprobación de actualización."))
        }

    private fun retryOrGiveUp(): Result = if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

object UpdateScheduler {
    private const val PERIODIC_WORK = "noblex-release-check-periodic"
    private const val STARTUP_WORK = "noblex-release-check-startup"
    private const val LAST_CHECK = "last_update_check"
    private val CHECK_COOLDOWN_MS = TimeUnit.HOURS.toMillis(24)

    fun schedule(context: Context) {
        UpdateManager.cleanupCache(context)
        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build(),
        )

        val preferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - preferences.getLong(LAST_CHECK, 0L) >= CHECK_COOLDOWN_MS) {
            preferences.edit().putLong(LAST_CHECK, now).apply()
            workManager.enqueueUniqueWork(
                STARTUP_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                    .setConstraints(constraints)
                    .build(),
            )
        }
    }
}
