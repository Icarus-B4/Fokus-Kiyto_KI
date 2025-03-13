package com.deepcore.kiytoapp.update

import android.app.Dialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.airbnb.lottie.LottieAnimationView
import com.deepcore.kiytoapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File

class UpdateDialog : DialogFragment() {
    private var downloadId: Long = -1
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var downloadManager: DownloadManager
    private var downloadReceiver: BroadcastReceiver? = null

    companion object {
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_URL = "url"

        fun newInstance(description: String, url: String): UpdateDialog {
            return UpdateDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_DESCRIPTION, description)
                    putString(ARG_URL, url)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val description = requireArguments().getString(ARG_DESCRIPTION)
        val url = requireArguments().getString(ARG_URL)

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_update, null)

        // Initialisiere Views
        val updateAnimation = view.findViewById<LottieAnimationView>(R.id.updateAnimation)
        val descriptionText = view.findViewById<TextView>(R.id.updateDescriptionText)
        val downloadButton = view.findViewById<MaterialButton>(R.id.downloadButton)
        val remindButton = view.findViewById<MaterialButton>(R.id.remindButton)
        progressIndicator = view.findViewById(R.id.downloadProgress)
        
        downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Setze Beschreibung
        descriptionText.text = description

        // Starte Animation
        updateAnimation.setAnimation(R.raw.update_available)
        updateAnimation.playAnimation()

        // Setze Click-Listener
        downloadButton.setOnClickListener {
            try {
                startDownload()
            } catch (e: Exception) {
                Log.e("UpdateDialog", "Fehler beim Anzeigen des Dialogs", e)
                Toast.makeText(
                    requireContext(),
                    "Fehler beim Verarbeiten der Update-Anfrage",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        remindButton.setOnClickListener {
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
            }
    }

    private fun startDownload() {
        val url = arguments?.getString(ARG_URL) ?: return
        
        // Extrahiere die Release-URL (falls es eine GitHub-URL ist)
        val releaseUrl = if (url.contains("/releases/download/")) {
            url.split("/releases/download/").first() + "/releases/latest"
        } else {
            url
        }
        
        // Zeige zuerst den Deinstallations-Dialog
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Update verfügbar")
            .setMessage("Um das Update zu installieren, müssen Sie:\n\n" +
                    "1. Die aktuelle Version deinstallieren\n" +
                    "2. Die neue Version von GitHub herunterladen und installieren\n\n" +
                    "Möchten Sie jetzt mit der Deinstallation beginnen?")
            .setPositiveButton("Jetzt deinstallieren") { _, _ ->
                try {
                    // Speichere die URL für den Browser-Start
                    val prefs = requireContext().getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("pending_update_url", releaseUrl).apply()
                    
                    // Starte Deinstallation
                    val packageName = requireContext().packageName
                    val uninstallIntent = Intent(Intent.ACTION_DELETE)
                    uninstallIntent.data = Uri.parse("package:$packageName")
                    uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    // Zeige Hinweis
                    Toast.makeText(
                        requireContext(),
                        "Nach der Deinstallation wird die GitHub-Seite geöffnet.",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Beende den Dialog und starte die Deinstallation
                    dismiss()
                    requireActivity().finish()
                    startActivity(uninstallIntent)
                    
                } catch (e: Exception) {
                    Log.e("UpdateDialog", "Fehler beim Deinstallieren", e)
                    Toast.makeText(
                        requireContext(),
                        "Fehler beim Deinstallieren: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Später", null)
            .setNeutralButton("Abbrechen", null)
            .setCancelable(true)
            .show()
    }

    private fun downloadDirectly(url: String) {
        try {
            // Überprüfe Berechtigungen
            if (requireContext().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
                return
            }

            // Konvertiere Release-URL zu Asset-Download-URL
            val apkUrl = if (url.contains("/releases/tag/")) {
                val version = url.substringAfterLast("/")
                val directUrl = "https://github.com/Icarus-B4/Fokus-Kiyto_KI/releases/download/$version/app-release.apk"
                Log.d("UpdateDialog", "Download URL: $directUrl")
                directUrl
            } else {
                Log.d("UpdateDialog", "Original URL: $url")
                url
            }

            // Erstelle Download-Request
            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("Kiyto Update")
                setDescription("Lädt neue Version herunter...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "kiyto_update.apk")
                setMimeType("application/vnd.android.package-archive")
                addRequestHeader("Accept", "application/vnd.android.package-archive")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            // Starte Download
            downloadId = downloadManager.enqueue(request)
            Log.d("UpdateDialog", "Download gestartet mit ID: $downloadId")
            
            progressIndicator.visibility = android.view.View.VISIBLE
            progressIndicator.isIndeterminate = true
            
            // Registriere Download-Receiver
            registerDownloadReceiver()
            
            Toast.makeText(requireContext(), "Download wird gestartet...", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Log.e("UpdateDialog", "Fehler beim Starten des Downloads", e)
            Toast.makeText(
                requireContext(),
                "Fehler beim Download: ${e.localizedMessage ?: e.message ?: "Unbekannter Fehler"}",
                Toast.LENGTH_LONG
            ).show()
            progressIndicator.visibility = android.view.View.GONE
        }
    }

    private fun registerDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    when (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            progressIndicator.visibility = android.view.View.GONE
                            showInstallationDialog()
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
                            Log.e("UpdateDialog", "Download fehlgeschlagen: $reason")
                            progressIndicator.visibility = android.view.View.GONE
                            Toast.makeText(
                                context,
                                "Download fehlgeschlagen. Bitte versuchen Sie es später erneut.",
                                Toast.LENGTH_LONG
                            ).show()
                            dismiss()
                        }
                    }
                }
                cursor.close()
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            requireContext().registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun showInstallationDialog() {
        try {
            // Überprüfe, ob die Datei existiert
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "kiyto_update.apk")
            
            if (!file.exists() || file.length() == 0L) {
                Log.e("UpdateDialog", "APK-Datei nicht gefunden oder leer: ${file.absolutePath}")
                
                // Versuche alternative Speicherorte
                val alternativeFile = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "kiyto_update.apk")
                
                if (alternativeFile.exists() && alternativeFile.length() > 0L) {
                    Log.d("UpdateDialog", "APK-Datei im alternativen Speicherort gefunden: ${alternativeFile.absolutePath}")
                    installApk(alternativeFile)
                    return
                }
                
                Toast.makeText(
                    requireContext(),
                    "Update-Datei konnte nicht gefunden werden.",
                    Toast.LENGTH_LONG
                ).show()
                
                // Fallback zum Browser
                arguments?.getString(ARG_URL)?.let { url ->
                    offerBrowserFallback(url)
                }
                return
            }
            
            Log.d("UpdateDialog", "APK-Datei gefunden: ${file.absolutePath}, Größe: ${file.length()} Bytes")
            
            // Zeige Dialog mit Installationsoptionen
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Update installieren")
                .setMessage("Das Update wurde heruntergeladen. Um es zu installieren, müssen Sie zuerst die aktuelle Version deinstallieren.\n\n" +
                        "1. Klicken Sie auf 'Deinstallieren'\n" +
                        "2. Nach der Deinstallation wird die neue Version automatisch installiert")
                .setPositiveButton("Deinstallieren") { _, _ ->
                    try {
                        // Starte Deinstallation
                        val packageName = requireContext().packageName
                        val uninstallIntent = Intent(Intent.ACTION_DELETE)
                        uninstallIntent.data = Uri.parse("package:$packageName")
                        startActivity(uninstallIntent)
                        
                        // Zeige Hinweis
                        Toast.makeText(
                            requireContext(),
                            "Nach der Deinstallation wird die neue Version installiert.",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Verzögerte Installation (falls der Benutzer zurückkehrt)
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (isAdded) {
                                installApk(file)
                            }
                        }, 5000)
                    } catch (e: Exception) {
                        Log.e("UpdateDialog", "Fehler beim Deinstallieren", e)
                        Toast.makeText(
                            requireContext(),
                            "Fehler beim Deinstallieren: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .setNegativeButton("Manuell installieren") { _, _ ->
                    installApk(file)
                }
                .setNeutralButton("Abbrechen", null)
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            Log.e("UpdateDialog", "Fehler beim Anzeigen des Installations-Dialogs", e)
            Toast.makeText(
                requireContext(),
                "Fehler beim Vorbereiten der Installation: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun installApk(file: File) {
        try {
            Log.d("UpdateDialog", "Starte Installation von: ${file.absolutePath}")
            
            val intent = Intent(Intent.ACTION_VIEW)
            val uri: Uri
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    file
                )
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                uri = Uri.fromFile(file)
            }
            
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            startActivity(intent)
            dismiss()
        } catch (e: Exception) {
            Log.e("UpdateDialog", "Fehler beim Installieren der APK", e)
            
            // Zeige detaillierte Fehlermeldung
            val errorMessage = when {
                e is ActivityNotFoundException -> "Keine App zum Öffnen der APK-Datei gefunden."
                e.message?.contains("Permission") == true -> "Fehlende Berechtigung zum Installieren der APK."
                e.message?.contains("FileProvider") == true -> "Fehler mit dem FileProvider. Bitte manuell installieren."
                else -> "Fehler beim Installieren: ${e.message}"
            }
            
            Toast.makeText(
                requireContext(),
                errorMessage,
                Toast.LENGTH_LONG
            ).show()
            
            // Biete manuelle Installation an
            showManualInstallationOptions(file)
        }
    }

    private fun showManualInstallationOptions(file: File) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manuelle Installation")
            .setMessage("Die automatische Installation ist fehlgeschlagen. Sie können die APK-Datei manuell installieren:\n\n" +
                    "Die Datei befindet sich im Download-Ordner mit dem Namen 'kiyto_update.apk'.\n\n" +
                    "Möchten Sie den Dateimanager öffnen?")
            .setPositiveButton("Dateimanager öffnen") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.setDataAndType(Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path), "*/*")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    dismiss()
                } catch (e: Exception) {
                    Log.e("UpdateDialog", "Fehler beim Öffnen des Dateimanagers", e)
                    Toast.makeText(
                        requireContext(),
                        "Fehler beim Öffnen des Dateimanagers: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .setCancelable(true)
            .show()
    }

    private fun offerBrowserFallback(url: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Update über Browser herunterladen")
            .setMessage("Der direkte Download ist fehlgeschlagen. Möchten Sie das Update über den Browser herunterladen?\n\n" +
                    "WICHTIG: Sie müssen die aktuelle App deinstallieren, bevor Sie die neue Version installieren können.")
            .setPositiveButton("App deinstallieren") { _, _ ->
                try {
                    // Starte Deinstallation
                    val packageName = requireContext().packageName
                    val uninstallIntent = Intent(Intent.ACTION_DELETE)
                    uninstallIntent.data = Uri.parse("package:$packageName")
                    startActivity(uninstallIntent)
                    
                    // Zeige Hinweis
                    Toast.makeText(
                        requireContext(),
                        "Nach der Deinstallation wird die Update-Seite geöffnet.",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Verzögerte Browser-Öffnung
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            requireContext().startActivity(browserIntent)
                        } catch (e: Exception) {
                            Log.e("UpdateDialog", "Fehler beim Öffnen des Browsers", e)
                        }
                    }, 3000)
                    
                    dismiss()
                } catch (e: Exception) {
                    Log.e("UpdateDialog", "Fehler beim Deinstallieren", e)
                    Toast.makeText(
                        requireContext(),
                        "Fehler beim Deinstallieren: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Im Browser öffnen") { _, _ ->
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(browserIntent)
                    
                    Toast.makeText(
                        requireContext(),
                        "Denken Sie daran, die App zu deinstallieren, bevor Sie die neue Version installieren!",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    dismiss()
                } catch (e: Exception) {
                    Log.e("UpdateDialog", "Fehler beim Öffnen des Browsers", e)
                    Toast.makeText(
                        requireContext(),
                        "Fehler beim Öffnen des Browsers: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNeutralButton("Abbrechen", null)
            .setCancelable(true)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let {
            requireContext().unregisterReceiver(it)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Starte Download erneut nach Berechtigungserteilung
                arguments?.getString(ARG_URL)?.let { startDownload() }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Berechtigung zum Speichern der Update-Datei erforderlich",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
} 