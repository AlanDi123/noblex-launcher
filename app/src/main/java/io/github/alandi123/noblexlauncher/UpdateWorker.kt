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
import org.json.JSONException
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        try {
            val release = UpdateManager.fetchLatestBlocking(applicationContext)
            LauncherPreferences(applicationContext).saveReleaseManifest(
                org.json.JSONObject().apply {
                    put("version", release.versionName)
                    put("versionCode", release.versionCode)
                    put("apkUrl", release.apkUrl)
                    put("sha256", release.sha256)
                    put("releaseNotes", release.releaseNotes)
                    put("mandatory", release.mandatory)
                },
            )
            Result.success()
        } catch (error: IOException) {
            if (error.message?.contains("Aún no hay una versión pública") == true) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (error: JSONException) {
            Result.failure(workDataOf("error" to "El manifiesto de actualización no es JSON válido."))
        } catch (error: SecurityException) {
            Result.failure(workDataOf("error" to "Android rechazó la comprobación de actualización."))
        }
}

object UpdateScheduler {
    private const val PERIODIC_WORK = "noblex-release-check-periodic"
    private const val STARTUP_WORK = "noblex-release-check-startup"
    private const val LAST_CHECK = "last_update_check"
    private val CHECK_COOLDOWN_MS = TimeUnit.HOURS.toMillis(24)

    fun schedule(context: Context) {
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
