package com.shimulfp.hub2stream

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.shimulfp.hub2stream.ui.navigation.AppNavigation
import com.shimulfp.hub2stream.ui.theme.Hub2StreamTheme
import com.shimulfp.hub2stream.utils.UpdateInfo
import com.shimulfp.hub2stream.utils.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * Direct back-press handler set by player screens.
         * Bypasses OnBackPressedDispatcher entirely — this is the most reliable
         * path on TV firmware where BackHandler/onBackInvokedCallback are broken.
         */
        var directBackHandler: (() -> Unit)? = null
    }

    private lateinit var updateManager: UpdateManager
    private var progressDialog: AlertDialog? = null
    // Hold reference to the critical update dialog so it can't be dismissed by back press
    private var criticalUpdateDialog: AlertDialog? = null

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAndStartDownload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateManager = UpdateManager(this)

        setContent {
            Hub2StreamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationWrapper()
                }
            }
        }

        lifecycleScope.launch {
            delay(1000)
            checkForUpdates()
        }
    }

    @Composable
    private fun AppNavigationWrapper() {
        AppNavigation(
            blockBackPress = { criticalUpdateDialog?.isShowing == true }
        )
    }

    private suspend fun checkForUpdates() {
        val updateInfo = updateManager.checkForUpdate()
        if (updateInfo != null) {
            showUpdateDialog(updateInfo)
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        val hasBrowserUrl = updateInfo.browserUrl.isNotBlank()
        val hasDownloadUrl = updateInfo.downloadUrl.isNotBlank()
        val hasBoth = hasBrowserUrl && hasDownloadUrl
        val isCritical = updateInfo.critical

        // Build changelog text
        val message = buildString {
            append("Version ${updateInfo.versionName}\n\n")
            if (updateInfo.changelog.isNotBlank()) {
                append("What's new:\n${updateInfo.changelog}")
            }
            if (isCritical) {
                append("\n\n⚠️ This is a critical update. You must update to continue using the app.")
            }
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (isCritical) "⚠️ Critical Update" else "Update Available")
            .setMessage(message)
            .setCancelable(!isCritical)

        when {
            // Both URLs available: show in-app as primary, browser as neutral
            hasBoth -> {
                builder.setPositiveButton("In-App Update") { _, _ ->
                    startUpdate(updateInfo.downloadUrl)
                }
                builder.setNeutralButton("Download from Browser") { _, _ ->
                    updateManager.openInBrowser(updateInfo.browserUrl)
                }
            }
            // Only browser URL: open in browser
            hasBrowserUrl -> {
                builder.setPositiveButton("Download Update") { _, _ ->
                    updateManager.openInBrowser(updateInfo.browserUrl)
                }
            }
            // Only download URL: in-app download
            hasDownloadUrl -> {
                builder.setPositiveButton("Update") { _, _ ->
                    startUpdate(updateInfo.downloadUrl)
                }
            }
            // No URL available — just show the info
            else -> {
                builder.setPositiveButton("OK", null)
            }
        }

        // "Later" button — only show for non-critical updates
        if (!isCritical) {
            builder.setNegativeButton("Later", null)
        }

        val dialog = builder.create()
        dialog.show()

        if (isCritical) {
            criticalUpdateDialog = dialog
            // Prevent dismissal by tapping outside
            dialog.setCanceledOnTouchOutside(false)
        }
    }

    private fun startUpdate(downloadUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.canRequestPackageInstalls()) {
                startDownloadWithProgress(downloadUrl)
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Installation Required")
                    .setMessage("To install the update, you need to allow installation from this app.")
                    .setPositiveButton("Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        intent.data = Uri.parse("package:$packageName")
                        installPermissionLauncher.launch(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            startDownloadWithProgress(downloadUrl)
        }
    }

    private fun startDownloadWithProgress(downloadUrl: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_download_progress, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.downloadProgressBar)
        val textView = dialogView.findViewById<TextView>(R.id.downloadProgressText)

        progressDialog = AlertDialog.Builder(this)
            .setTitle("Downloading Update")
            .setView(dialogView)
            .setCancelable(false)
            .create()
        progressDialog?.show()

        updateManager.downloadAndInstallWithProgress(this, downloadUrl, object : UpdateManager.DownloadProgressListener {
            override fun onProgress(bytesDownloaded: Long, totalBytes: Long) {
                val progress = (bytesDownloaded * 100 / totalBytes).toInt()
                runOnUiThread {
                    progressBar.progress = progress
                    textView.text = "Downloading: $progress% (${bytesDownloaded / 1024 / 1024} MB / ${totalBytes / 1024 / 1024} MB)"
                }
            }

            override fun onComplete(file: File) {
                runOnUiThread {
                    progressDialog?.dismiss()
                    showInstallDialog(file)
                }
            }

            override fun onFailed(errorMessage: String) {
                runOnUiThread {
                    progressDialog?.dismiss()
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Download Failed")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        })
    }

    private fun showInstallDialog(apkFile: File) {
        AlertDialog.Builder(this)
            .setTitle("Download Complete")
            .setMessage("The update has been downloaded. Install now?")
            .setPositiveButton("Install") { _, _ ->
                updateManager.installApk(this, apkFile)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun checkAndStartDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.canRequestPackageInstalls()) {
            lifecycleScope.launch {
                val updateInfo = updateManager.checkForUpdate()
                updateInfo?.let {
                    if (it.downloadUrl.isNotBlank()) {
                        startUpdate(it.downloadUrl)
                    } else if (it.browserUrl.isNotBlank()) {
                        updateManager.openInBrowser(it.browserUrl)
                    }
                }
            }
        }
    }

    /**
     * Layer 4 defense: Direct Activity-level back override.
     * This is the ABSOLUTE last line of defense before finish() is called.
     * Called when firmware doesn't go through dispatchKeyEvent or OnBackPressedDispatcher.
     */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        android.util.Log.d("MainActivity", "onBackPressed() called — directBackHandler=${directBackHandler != null}")
        val handler = directBackHandler
        if (handler != null) {
            android.util.Log.d("MainActivity", "onBackPressed() → calling directBackHandler (player screen)")
            handler()
            return
        }
        // Not on a player screen — let normal dispatch handle it (exit dialog, etc.)
        android.util.Log.d("MainActivity", "onBackPressed() → no directBackHandler, calling super (normal back dispatch)")
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        // Re-check for critical update when returning from browser
        // This ensures the user can't skip a force update
        if (criticalUpdateDialog?.isShowing != true) {
            lifecycleScope.launch {
                val updateInfo = updateManager.checkForUpdate()
                if (updateInfo != null && updateInfo.critical) {
                    showUpdateDialog(updateInfo)
                }
            }
        }
    }
}