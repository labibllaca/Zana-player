package com.labix.navirom.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.labix.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val tagName: String,
    val title: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkDownloadUrl: String,
    val apkName: String,
    val apkSize: Long,
    val isNewer: Boolean
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val updateInfo: AppUpdateInfo) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateState()
    data class ReadyToInstall(val apkFile: File, val updateInfo: AppUpdateInfo) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class UpdateManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("navirom_update_prefs", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _autoCheckEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK, true))
    val autoCheckEnabled: StateFlow<Boolean> = _autoCheckEnabled.asStateFlow()

    private val _githubRepo = MutableStateFlow(prefs.getString(KEY_GITHUB_REPO, DEFAULT_REPO) ?: DEFAULT_REPO)
    val githubRepo: StateFlow<String> = _githubRepo.asStateFlow()

    private val _lastCheckedTime = MutableStateFlow(prefs.getLong(KEY_LAST_CHECKED, 0L))
    val lastCheckedTime: StateFlow<Long> = _lastCheckedTime.asStateFlow()

    fun setAutoCheckEnabled(enabled: Boolean) {
        _autoCheckEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
    }

    fun setGithubRepo(repo: String) {
        val sanitized = repo.trim().removePrefix("https://github.com/").removeSuffix("/")
        _githubRepo.value = sanitized
        prefs.edit().putString(KEY_GITHUB_REPO, sanitized).apply()
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    suspend fun checkForUpdates(isManual: Boolean = false): AppUpdateInfo? = withContext(Dispatchers.IO) {
        if (!isManual && !_autoCheckEnabled.value) {
            return@withContext null
        }

        _updateState.value = UpdateState.Checking
        try {
            val repo = _githubRepo.value.ifBlank { DEFAULT_REPO }
            val apiUrl = "https://api.github.com/repos/$repo/releases"

            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Navirom-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errMsg = "GitHub API error: ${response.code} ${response.message}"
                Log.w(TAG, errMsg)
                _updateState.value = if (isManual) UpdateState.Error(errMsg) else UpdateState.Idle
                return@withContext null
            }

            val bodyString = response.body?.string() ?: ""
            val jsonArray = JSONArray(bodyString)
            if (jsonArray.length() == 0) {
                _updateState.value = if (isManual) UpdateState.UpToDate else UpdateState.Idle
                return@withContext null
            }

            // Find the most relevant release with an APK
            var latestApkAsset: JSONObject? = null
            var targetRelease: JSONObject? = null

            for (i in 0 until jsonArray.length()) {
                val rel = jsonArray.getJSONObject(i)
                val isDraft = rel.optBoolean("draft", false)
                if (isDraft) continue

                val assets = rel.optJSONArray("assets") ?: JSONArray()
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        latestApkAsset = asset
                        targetRelease = rel
                        break
                    }
                }
                if (targetRelease != null) break
            }

            if (targetRelease == null || latestApkAsset == null) {
                Log.i(TAG, "No APK asset found in releases for $repo")
                _updateState.value = if (isManual) UpdateState.UpToDate else UpdateState.Idle
                return@withContext null
            }

            val tagName = targetRelease.optString("tag_name", "")
            val title = targetRelease.optString("name", tagName).ifBlank { tagName }
            val body = targetRelease.optString("body", "Neue Funktionen und Fehlerbehebungen.")
            val publishedAt = targetRelease.optString("published_at", "")
            val htmlUrl = targetRelease.optString("html_url", "https://github.com/$repo")
            val apkDownloadUrl = latestApkAsset.optString("browser_download_url", "")
            val apkName = latestApkAsset.optString("name", "navirom-update.apk")
            val apkSize = latestApkAsset.optLong("size", 0L)

            val currentVersion = BuildConfig.VERSION_NAME
            val isNewer = isRemoteVersionNewer(tagName, currentVersion, publishedAt)

            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_CHECKED, now).apply()
            _lastCheckedTime.value = now

            val updateInfo = AppUpdateInfo(
                tagName = tagName,
                title = title,
                body = body,
                publishedAt = publishedAt,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkDownloadUrl,
                apkName = apkName,
                apkSize = apkSize,
                isNewer = isNewer
            )

            if (isNewer) {
                _updateState.value = UpdateState.Available(updateInfo)
                updateInfo
            } else {
                _updateState.value = if (isManual) UpdateState.UpToDate else UpdateState.Idle
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            _updateState.value = if (isManual) UpdateState.Error(e.localizedMessage ?: "Network error") else UpdateState.Idle
            null
        }
    }

    suspend fun downloadAndInstall(updateInfo: AppUpdateInfo) = withContext(Dispatchers.IO) {
        try {
            _updateState.value = UpdateState.Downloading(0f, 0L, updateInfo.apkSize)

            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outputFile = File(updateDir, "navirom-update-${updateInfo.tagName.replace('/', '_')}.apk")

            if (outputFile.exists()) {
                outputFile.delete()
            }

            val request = Request.Builder()
                .url(updateInfo.apkDownloadUrl)
                .header("User-Agent", "Navirom-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to download APK: ${response.code} ${response.message}")
            }

            val responseBody = response.body ?: throw Exception("Response body is empty")
            val totalBytes = if (responseBody.contentLength() > 0) responseBody.contentLength() else updateInfo.apkSize
            var downloadedBytes = 0L

            responseBody.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var lastReportTime = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastReportTime > 200 || downloadedBytes == totalBytes) {
                            lastReportTime = currentTime
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0.5f
                            _updateState.value = UpdateState.Downloading(progress.coerceIn(0f, 1f), downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            _updateState.value = UpdateState.ReadyToInstall(outputFile, updateInfo)
            withContext(Dispatchers.Main) {
                installApk(context, outputFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading update", e)
            _updateState.value = UpdateState.Error("Download fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Log.e(TAG, "APK file does not exist or is empty")
                return
            }

            val authority = "${context.packageName}.provider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer", e)
            _updateState.value = UpdateState.Error("Installation konnte nicht gestartet werden: ${e.localizedMessage}")
        }
    }

    private fun isRemoteVersionNewer(tag: String, currentVersion: String, publishedAt: String): Boolean {
        val cleanTag = tag.removePrefix("v").trim()
        val cleanCurrent = currentVersion.removePrefix("v").trim()

        if (cleanTag.equals("latest-build", ignoreCase = true) || cleanTag.equals("latest", ignoreCase = true)) {
            // Check if last checked or published date is newer than app build time
            return true
        }

        val tagParts = cleanTag.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(tagParts.size, currentParts.size)
        for (i in 0 until length) {
            val t = tagParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (t > c) return true
            if (t < c) return false
        }

        return false
    }

    companion object {
        private const val TAG = "UpdateManager"
        const val DEFAULT_REPO = "labibllaca/navirom"
        private const val KEY_AUTO_CHECK = "auto_check_updates"
        private const val KEY_GITHUB_REPO = "github_repo_slug"
        private const val KEY_LAST_CHECKED = "last_checked_timestamp"
    }
}
