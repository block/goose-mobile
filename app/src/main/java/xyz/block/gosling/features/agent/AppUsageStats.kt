package xyz.block.gosling.features.agent

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import java.util.Calendar
import xyz.block.gosling.features.settings.SettingsStore

/**
 * Manager class for retrieving app usage statistics
 */
class AppUsageStats(private val context: Context) {

    /**
     * Check if the app has permission to access usage stats
     */
    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        
        // Double check with a query to make sure we actually have access
        if (mode == AppOpsManager.MODE_ALLOWED) {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val startTime = calendar.timeInMillis
            
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            
            return !stats.isNullOrEmpty()
        }
        
        return false
    }

    /**
     * Open system settings to request usage stats permission
     */
    fun requestPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Get the most recently used apps
     * @param limit Number of apps to return
     * @param includeSystemApps Whether to include system apps in the results
     * @return List of app names and package names as strings
     */
    fun getRecentApps(limit: Int = 10, includeSystemApps: Boolean = false): List<String> {
        // Check the setting first
        val settingsStore = SettingsStore(context)
        if (!settingsStore.isUsageStatsEnabled) {
            return listOf("Permission to access app usage statistics is required. Please enable it in Settings.")
        }
        
        if (!hasPermission()) {
            return listOf("Permission to access app usage statistics is required.")
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager

        // Get usage stats for the last 30 days
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val startTime = calendar.timeInMillis

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStatsList.isNullOrEmpty()) {
            return listOf("No usage data available.")
        }

        // Sort by last time used (most recent first), remove duplicates, and take the top 'limit' entries
        val sortedStats = usageStatsList
            .sortedByDescending { it.lastTimeUsed }
            .distinctBy { it.packageName }
            .take(limit)
        
        val result = mutableListOf<String>()
        
        for (stats in sortedStats) {
            try {
                val appInfo = packageManager.getApplicationInfo(stats.packageName, 0)
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                
                // Skip system apps if not including them
                if (!includeSystemApps && isSystemApp) {
                    continue
                }
                
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                result.add(appName)
            } catch (e: PackageManager.NameNotFoundException) {
                // Skip apps that are no longer installed
                continue
            }
        }
        
        return result
    }

    /**
     * Get the most frequently used apps based on total time in foreground
     * @param limit Number of apps to return
     * @param includeSystemApps Whether to include system apps in the results
     * @return List of app names and package names as strings
     */
    fun getFrequentApps(limit: Int = 10, includeSystemApps: Boolean = false): List<String> {
        // Check the setting first
        val settingsStore = SettingsStore(context)
        if (!settingsStore.isUsageStatsEnabled) {
            return listOf("Permission to access app usage statistics is required. Please enable it in Settings.")
        }
        
        if (!hasPermission()) {
            return listOf("Permission to access app usage statistics is required.")
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager

        // Get usage stats for the last 30 days
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val startTime = calendar.timeInMillis

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_MONTHLY,
            startTime,
            endTime
        )

        if (usageStatsList.isNullOrEmpty()) {
            return listOf("No usage data available.")
        }

        // Group by package name and sum up total time in foreground
        val packageToTotalTimeMap = mutableMapOf<String, Long>()
        val packageToLastTimeMap = mutableMapOf<String, Long>()
        
        for (stats in usageStatsList) {
            val currentTotal = packageToTotalTimeMap.getOrDefault(stats.packageName, 0L)
            packageToTotalTimeMap[stats.packageName] = currentTotal + stats.totalTimeInForeground
            
            val currentLastTime = packageToLastTimeMap.getOrDefault(stats.packageName, 0L)
            if (stats.lastTimeUsed > currentLastTime) {
                packageToLastTimeMap[stats.packageName] = stats.lastTimeUsed
            }
        }
        
        // Create a list of package names sorted by total time
        val sortedPackages = packageToTotalTimeMap.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit * 2) // Take more than needed to account for filtering
        
        val result = mutableListOf<String>()
        
        for (packageName in sortedPackages) {
            if (result.size >= limit) break
            
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                
                // Skip system apps if not including them
                if (!includeSystemApps && isSystemApp) {
                    continue
                }
                
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                result.add(appName)
            } catch (e: PackageManager.NameNotFoundException) {
                // Skip apps that are no longer installed
                continue
            }
        }
        
        return result
    }

    companion object {
        /**
         * Static method to get recently used apps
         */
        @JvmStatic
        fun getRecentApps(context: Context, limit: Int = 10, includeSystemApps: Boolean = false): List<String> {
            return AppUsageStats(context).getRecentApps(limit, includeSystemApps)
        }

        /**
         * Static method to get frequently used apps
         */
        @JvmStatic
        fun getFrequentApps(context: Context, limit: Int = 10, includeSystemApps: Boolean = false): List<String> {
            return AppUsageStats(context).getFrequentApps(limit, includeSystemApps)
        }

        /**
         * Static method to check permission
         */
        @JvmStatic
        fun hasPermission(context: Context): Boolean {
            return AppUsageStats(context).hasPermission()
        }

        /**
         * Static method to request permission
         */
        @JvmStatic
        fun requestPermission(context: Context) {
            AppUsageStats(context).requestPermission()
        }
    }
}