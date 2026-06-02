package com.shimulfp.hub2stream

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    private lateinit var updateManager: UpdateManager
    private var progressDialog: AlertDialog? = null

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
        var showExitDialog by remember { mutableStateOf(false) }
        var backPressedTime by remember { mutableStateOf(0L) }

        // Handle back button at the root level
        BackHandler(enabled = true) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressedTime < 2000) {
                // Double press within 2 seconds - exit immediately
                finish()
            } else {
                backPressedTime = currentTime
                // Show exit dialog on single back press
                showExitDialog = true
            }
        }

        AppNavigation(
            showExitDialog = showExitDialog,
            onExitDialogDismiss = { showExitDialog = false }
        )
    }

    private suspend fun checkForUpdates() {
        val updateInfo = updateManager.checkForUpdate()
        if (updateInfo != null) {
            showUpdateDialog(updateInfo)
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage(
                "Version ${updateInfo.versionName}\n\n" +
                        "What's new:\n${updateInfo.changelog}\n\n" +
                        "Download and install now?"
            )
            .setPositiveButton("Update") { _, _ ->
                startUpdate(updateInfo.downloadUrl)
            }
            .setNegativeButton("Later", null)
            .show()
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
                    startUpdate(it.downloadUrl)
                }
            }
        }
    }
}