package com.deepcore.kiytoapp.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class PermissionManager {
    companion object {
        const val PERMISSION_REQUEST_CODE = 123
        private const val PREFS_NAME = "permission_prefs"
        private const val KEY_PERMISSIONS_REQUESTED = "permissions_requested"
        
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        private fun isFirstRequest(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return !prefs.getBoolean(KEY_PERMISSIONS_REQUESTED, false)
        }

        private fun markPermissionsRequested(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_PERMISSIONS_REQUESTED, true).apply()
        }

        fun checkAndRequestPermissions(activity: Activity) {
            // Nur beim ersten Start die Berechtigungen anfordern
            if (!isFirstRequest(activity)) {
                return
            }

            val permissionsToRequest = mutableListOf<String>()
            
            for (permission in REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission)
                }
            }
            
            if (permissionsToRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    activity,
                    permissionsToRequest.toTypedArray(),
                    PERMISSION_REQUEST_CODE
                )
                markPermissionsRequested(activity)
            }
        }

        fun checkAndRequestPermissions(fragment: Fragment) {
            // Nur beim ersten Start die Berechtigungen anfordern
            if (!isFirstRequest(fragment.requireContext())) {
                return
            }

            val permissionsToRequest = mutableListOf<String>()
            
            for (permission in REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(fragment.requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission)
                }
            }
            
            if (permissionsToRequest.isNotEmpty()) {
                fragment.requestPermissions(
                    permissionsToRequest.toTypedArray(),
                    PERMISSION_REQUEST_CODE
                )
                markPermissionsRequested(fragment.requireContext())
            }
        }

        fun hasAllPermissions(context: Context): Boolean {
            for (permission in REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
            return true
        }

        fun shouldShowRationale(activity: Activity): Boolean {
            // Nur beim ersten Start die Rationale anzeigen
            if (!isFirstRequest(activity)) {
                return false
            }

            for (permission in REQUIRED_PERMISSIONS) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                    return true
                }
            }
            return false
        }
    }
} 