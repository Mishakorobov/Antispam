package ru.antispam.app.privacy.remediation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * PrivilegedRemediationExecutor: Handles automated silent remediation on devices
 * with Root privileges (Magisk, KernelSU, APatch) or ADB/Shizuku access.
 *
 * If root is granted, system settings (NotificationListeners, runtime permissions)
 * can be revoked directly in the background without directing the user to system menus.
 */
object PrivilegedRemediationExecutor {

    private const val TAG = "PrivilegedRemediation"

    /**
     * Checks if a functional `su` binary is accessible on the device.
     */
    fun isRootAvailable(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        )
        for (path in paths) {
            try {
                if (File(path).exists()) return true
            } catch (_: Throwable) {}
        }
        return false
    }

    /**
     * Executes a privileged root shell command with a timeout.
     */
    suspend fun executeRootCommand(command: String, timeoutSeconds: Long = 4): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                Log.w(TAG, "Root command timed out: $command")
                return@withContext false
            }

            val exitCode = process.exitValue()
            Log.d(TAG, "Root command '$command' exited with code $exitCode")
            return@withContext exitCode == 0
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to execute root command: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Silently revokes NotificationListenerService access for a package.
     */
    suspend fun revokeNotificationListener(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val cmd = "cmd notification set_access_granted_package $packageName false"
        return executeRootCommand(cmd)
    }

    /**
     * Silently revokes location permissions for a package.
     */
    suspend fun revokeLocationPermissions(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val cmd1 = "pm revoke $packageName android.permission.ACCESS_FINE_LOCATION"
        val cmd2 = "pm revoke $packageName android.permission.ACCESS_COARSE_LOCATION"
        val r1 = executeRootCommand(cmd1)
        val r2 = executeRootCommand(cmd2)
        return r1 || r2
    }

    /**
     * Silently revokes package visibility for a surveillance superapp.
     */
    suspend fun revokeQueryAllPackages(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val cmd = "cmd appops set $packageName android:query_all_packages deny"
        return executeRootCommand(cmd)
    }
}
