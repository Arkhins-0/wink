package com.arkhins.wink.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/**
 * Battery optimisation puts a sleeping app on hold, which delays or drops
 * its popups. The system dialog asks the person to exempt the app; on
 * Xiaomi phones the separate "autostart" switch matters too, so its page
 * is offered as well.
 */
object Battery {
    fun isExempt(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    /** The system's "allow to run in background?" dialog for this app. */
    fun requestExemption(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /**
     * Whether MIUI lets Wink start by itself (its hidden app-op 10008), which is what
     * brings popups back after recent apps are cleared. Null when the phone cannot say.
     */
    fun autostartAllowed(context: Context): Boolean? {
        if (!Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) return null
        return runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val check = AppOpsManager::class.java.getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            check.invoke(ops, 10008, Process.myUid(), context.packageName) as Int == AppOpsManager.MODE_ALLOWED
        }.getOrNull()
    }

    /** The phone maker's autostart page, if it has one (MIUI, ColorOS, …); null otherwise. */
    fun autostartIntent(context: Context): Intent? {
        val candidates = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        )
        for ((pkg, cls) in candidates) {
            val intent = Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) return intent
        }
        return null
    }
}
