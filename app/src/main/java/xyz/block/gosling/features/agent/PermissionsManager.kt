package xyz.block.gosling.features.agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Manager class for handling permissions required by the app
 */
class PermissionsManager {
    companion object {
        /**
         * Check if the app has a specific permission
         */
        private fun hasPermission(context: Context, permission: String): Boolean {
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
    }
}