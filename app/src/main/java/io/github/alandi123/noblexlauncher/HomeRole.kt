package io.github.alandi123.noblexlauncher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

/** Read-only view of the HOME role plus the official request intent. Never forces the assignment. */
object HomeRole {
    enum class State { UNSUPPORTED, AVAILABLE, HELD }

    fun state(context: Context): State {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return State.UNSUPPORTED
        val manager = context.getSystemService(RoleManager::class.java) ?: return State.UNSUPPORTED
        return when {
            !manager.isRoleAvailable(RoleManager.ROLE_HOME) -> State.UNSUPPORTED
            manager.isRoleHeld(RoleManager.ROLE_HOME) -> State.HELD
            else -> State.AVAILABLE
        }
    }

    /** Intent for Android's own role-request screen, or null when the platform lacks the role. */
    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || state(context) != State.AVAILABLE) return null
        return context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }
}
