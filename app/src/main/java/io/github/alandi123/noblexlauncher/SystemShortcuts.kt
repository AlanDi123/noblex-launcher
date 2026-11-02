package io.github.alandi123.noblexlauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Opens official system screens only. Each entry tries supported intents in order and reports success. */
object SystemShortcuts {
    private const val ACTION_CONNECT_INPUT = "com.google.android.intent.action.CONNECT_INPUT"

    fun openWifi(context: Context): Boolean = startFirst(
        context,
        Intent(Settings.ACTION_WIFI_SETTINGS),
        Intent("android.settings.panel.action.INTERNET_CONNECTIVITY"),
        Intent(Settings.ACTION_SETTINGS),
    )

    /**
     * Android TV 11 exposes no ACTION_BLUETOOTH_SETTINGS. The supported Android TV entry is the
     * accessory screen, which only scans; pairing needs an explicit choice by the user.
     */
    fun openBluetooth(context: Context): Boolean = startFirst(
        context,
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
        Intent(ACTION_CONNECT_INPUT),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun openSettings(context: Context): Boolean = startFirst(context, Intent(Settings.ACTION_SETTINGS))

    fun openInstalledApps(context: Context): Boolean = startFirst(
        context,
        Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun openHomeSettings(context: Context): Boolean = startFirst(context, Intent(Settings.ACTION_HOME_SETTINGS))

    private fun startFirst(context: Context, vararg intents: Intent): Boolean {
        intents.forEach { intent ->
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }
}
