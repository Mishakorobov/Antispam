package ru.antispam.app.privacy

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * AppDormancyHelper: Detects whether an application is currently in Deep Sleep,
 * frozen, stopped, suspended, or background-restricted.
 *
 * In Samsung One UI & Android:
 * 1. Samsung One UI Deep Sleep sets `COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED` (constant 4).
 * 2. Samsung FAS & AOSP set `FLAG_STOPPED` (0x200000) when an app is placed in deep sleep or force-stopped.
 * 3. Android Standby Buckets set `STANDBY_BUCKET_RESTRICTED` (45) or `STANDBY_BUCKET_NEVER` (50).
 * 4. Android Suspended packages: `pm.isPackageSuspended(packageName)`.
 * 5. AppOps background restriction: `android:run_any_in_background` mode IGNORED.
 *
 * Apps in deep sleep CANNOT run background services, intercept notifications,
 * read screen via accessibility, or triangulate location. Therefore, they
 * do NOT pose an active security threat and must not trigger security vulnerability deductions.
 */
object AppDormancyHelper {

    private const val TAG = "AppDormancyHelper"

    // Official PackageManager constant: COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED = 4
    const val COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED = 4

    /**
     * Checks if the package is currently in deep sleep, stopped, or disabled.
     */
    fun isAppInDeepSleep(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val pm = context.packageManager

        try {
            // 1. Check Samsung One UI Deep Sleep state via package enabled setting
            val enabledSetting = try {
                pm.getApplicationEnabledSetting(packageName)
            } catch (_: Exception) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }

            if (enabledSetting == COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED ||
                enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            ) {
                Log.d(TAG, "App $packageName is in Deep Sleep / Disabled state ($enabledSetting)")
                return true
            }

            // 2. Check ApplicationInfo flags (FLAG_STOPPED, enabled)
            val appInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return false
            } catch (_: Exception) {
                return false
            }

            if (!appInfo.enabled) {
                Log.d(TAG, "App $packageName is disabled")
                return true
            }

            // In Android, Samsung One UI & AOSP set FLAG_STOPPED when an app is in deep sleep or force-stopped
            if ((appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0) {
                Log.d(TAG, "App $packageName has FLAG_STOPPED set (deep sleep / frozen)")
                return true
            }

            // 3. Check if package is suspended (API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    if (pm.isPackageSuspended(packageName)) {
                        Log.d(TAG, "App $packageName is suspended")
                        return true
                    }
                } catch (_: Throwable) {}
            }

            // 4. Check App Standby Bucket via reflection if available (RESTRICTED or NEVER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
                    if (usm != null) {
                        val getBucketMethod = usm.javaClass.getMethod("getAppStandbyBucket", String::class.java)
                        val bucket = getBucketMethod.invoke(usm, packageName) as? Int ?: 0
                        if (bucket == UsageStatsManager.STANDBY_BUCKET_RESTRICTED || bucket >= 50 /* NEVER */) {
                            Log.d(TAG, "App $packageName is in Standby Bucket Restricted/Never ($bucket)")
                            return true
                        }
                    }
                } catch (_: Throwable) {
                    // Hidden API or requires PACKAGE_USAGE_STATS; silently ignore if not accessible
                }
            }

            // 5. Check AppOps background run restriction
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                    if (appOps != null) {
                        val mode = appOps.unsafeCheckOpNoThrow(
                            "android:run_any_in_background",
                            appInfo.uid,
                            packageName
                        )
                        if (mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED) {
                            Log.d(TAG, "App $packageName has RUN_ANY_IN_BACKGROUND ignored")
                            return true
                        }
                    }
                } catch (_: Throwable) {}
            }

        } catch (e: Exception) {
            Log.w(TAG, "Error checking sleep state for $packageName: ${e.message}")
        }

        return false
    }
}
