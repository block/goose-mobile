package xyz.block.gosling.features.agent

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Manager class for handling permissions required by the app
 */
class PermissionsManager {
    companion object {
        private const val TAG = "PermissionsManager"

        /**
         * Check if the app has a specific permission
         */
        @JvmStatic
        fun hasPermission(context: Context, permission: String): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Check if the app has calendar read permission
         */
        @JvmStatic
        fun hasCalendarPermission(context: Context): Boolean {
            return hasPermission(context, Manifest.permission.READ_CALENDAR)
        }

        /**
         * Check if the app has contacts read permission
         */
        @JvmStatic
        fun hasContactsPermission(context: Context): Boolean {
            return hasPermission(context, Manifest.permission.READ_CONTACTS)
        }

        /**
         * Request a specific permission
         */
        @JvmStatic
        fun requestPermission(activity: Activity, permission: String, requestCode: Int) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(permission),
                requestCode
            )
        }

        /**
         * Open app settings to allow the user to grant permissions manually
         */
        @JvmStatic
        fun openAppSettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        /**
         * Formats a permission name for display to users
         */
        @JvmStatic
        fun formatPermissionName(permission: String): String {
            return when (permission) {
                Manifest.permission.READ_CALENDAR -> "Calendar"
                Manifest.permission.READ_CONTACTS -> "Contacts"
                else -> permission.substringAfterLast(".")
            }
        }
    }
}