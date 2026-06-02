package com.shimulfp.hub2stream.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String
)

class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val UPDATE_CHECK_URL = "https://raw.githubusercontent.com/mpshimul/hub2stream/main/version.json"
    }

    interface DownloadProgressListener {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long)
        fun onComplete(file: File)
        fun onFailed(errorMessage: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(UPDATE_CHECK_URL).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val json = response.body?.string() ?: return@withContext null
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val update = mapper.readValue(json, UpdateInfo::class.java)
            if (update.versionCode > getCurrentVersionCode()) update else null
        } catch (e: Exception) {
            Log.e(TAG, "Update check error", e)
            null
        }
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.longVersionCode.toInt()
        } catch (e: PackageManager.NameNotFoundException) {
            1
        }
    }

    fun downloadAndInstallWithProgress(activity: Activity, downloadUrl: String, listener: DownloadProgressListener) {
        val destination = File(activity.externalCacheDir, "app_update.apk")
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        listener.onFailed("HTTP ${response.code}: ${response.message}")
                    }
                    return@launch
                }

                val contentLength = response.body?.contentLength() ?: -1L
                var bytesDownloaded = 0L
                val inputStream = response.body?.byteStream()
                val outputStream = FileOutputStream(destination)

                inputStream?.use { input ->
                    outputStream.use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead
                            if (contentLength > 0) {
                                withContext(Dispatchers.Main) {
                                    listener.onProgress(bytesDownloaded, contentLength)
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    listener.onComplete(destination)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                withContext(Dispatchers.Main) {
                    listener.onFailed(e.message ?: "Download failed")
                }
            }
        }
    }

    fun installApk(activity: Activity, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}