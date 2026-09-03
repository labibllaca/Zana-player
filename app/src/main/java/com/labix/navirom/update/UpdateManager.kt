package com.labix.navirom.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.labix.BuildConfig
import com.labix.navirom.diagnostics.AppDiagnostics
import com.labix.navirom.diagnostics.DiagnosticCodes
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
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
    val isNewer: Boolean,
    val assetUpdatedAt: String = "",
    val assetUpdatedAtMillis: Long = 0L,
    val assetDigest: String = ""
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val updateInfo: AppUpdateInfo) : UpdateState()
    data class UpToDate(val latestInfo: AppUpdateInfo? = null) : UpdateState()
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

            AppDiagnostics.logInfo(DiagnosticCodes.UPDATE_CHECK_START_701, TAG, "Checking for updates on repo: $repo (isManual: $isManual)")

            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Navirom-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errMsg = "GitHub API error: ${response.code} ${response.message}"
                Log.w(TAG, errMsg)
                AppDiagnostics.logWarn(DiagnosticCodes.UPDATE_CHECK_WARN_703, TAG, errMsg)
                _updateState.value = if (isManual) UpdateState.Error(errMsg) else UpdateState.Idle
                return@withContext null
            }

            val bodyString = response.body?.string() ?: ""
            val jsonArray = JSONArray(bodyString)
            if (jsonArray.length() == 0) {
                AppDiagnostics.logInfo(DiagnosticCodes.UPDATE_CHECK_SUCCESS_702, TAG, "No releases found on repo")
                _updateState.value = if (isManual) UpdateState.UpToDate() else UpdateState.Idle
                return@withContext null
            }

            // Gather candidate releases that contain at least one APK asset
            data class ReleaseCandidate(
                val release: JSONObject,
                val asset: JSONObject,
                val tagName: String,
                val title: String,
                val semver: String?,
                val assetTimeMillis: Long
            )

            val candidates = mutableListOf<ReleaseCandidate>()

            for (i in 0 until jsonArray.length()) {
                val rel = jsonArray.getJSONObject(i)
                if (rel.optBoolean("draft", false)) continue

                val assets = rel.optJSONArray("assets") ?: JSONArray()
                var bestAsset: JSONObject? = null
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    val n = a.optString("name", "")
                    if (n.endsWith(".apk", ignoreCase = true)) {
                        bestAsset = a
                        break
                    }
                }

                if (bestAsset != null) {
                    val tag = rel.optString("tag_name", "")
                    val t = rel.optString("name", tag).ifBlank { tag }
                    val sem = extractVersionString(tag) ?: extractVersionString(t)
                    val aTime = parseIso8601(bestAsset.optString("updated_at", rel.optString("published_at", "")))
                    candidates.add(ReleaseCandidate(rel, bestAsset, tag, t, sem, aTime))
                }
            }

            if (candidates.isEmpty()) {
                Log.i(TAG, "No APK asset found in any release for $repo")
                AppDiagnostics.logWarn(DiagnosticCodes.UPDATE_CHECK_WARN_703, TAG, "No APK asset found in releases")
                _updateState.value = if (isManual) UpdateState.UpToDate() else UpdateState.Idle
                return@withContext null
            }

            // Prioritize candidates with higher semver than current version, otherwise newest asset timestamp
            val currentVersion = BuildConfig.VERSION_NAME
            val higherSemverCandidates = candidates.filter { c ->
                c.semver != null && compareSemver(c.semver, currentVersion) > 0
            }.sortedWith { a, b -> compareSemver(b.semver!!, a.semver!!) }

            val chosen = if (higherSemverCandidates.isNotEmpty()) {
                higherSemverCandidates.first()
            } else {
                candidates.maxByOrNull { it.assetTimeMillis } ?: candidates.first()
            }

            val targetRelease = chosen.release
            val latestApkAsset = chosen.asset

            val tagName = targetRelease.optString("tag_name", "")
            val title = targetRelease.optString("name", tagName).ifBlank { tagName }
            val body = targetRelease.optString("body", "Neue Funktionen und Fehlerbehebungen.")
            val publishedAt = targetRelease.optString("published_at", "")
            val htmlUrl = targetRelease.optString("html_url", "https://github.com/$repo")
            val apkDownloadUrl = latestApkAsset.optString("browser_download_url", "")
            val apkName = latestApkAsset.optString("name", "navirom-update.apk")
            val apkSize = latestApkAsset.optLong("size", 0L)
            val assetUpdatedAt = latestApkAsset.optString("updated_at", publishedAt)
            val assetUpdatedAtMillis = chosen.assetTimeMillis
            val assetDigest = latestApkAsset.optString("digest", "")

            val isNewer = isRemoteVersionNewer(
                tag = tagName,
                title = title,
                assetUpdatedAt = assetUpdatedAt,
                assetDigest = assetDigest,
                currentVersion = currentVersion
            )

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
                isNewer = isNewer,
                assetUpdatedAt = assetUpdatedAt,
                assetUpdatedAtMillis = assetUpdatedAtMillis,
                assetDigest = assetDigest
            )

            if (isNewer) {
                AppDiagnostics.logInfo(DiagnosticCodes.UPDATE_CHECK_SUCCESS_702, TAG, "New update found: $tagName (current: $currentVersion)")
                _updateState.value = UpdateState.Available(updateInfo)
                updateInfo
            } else {
                AppDiagnostics.logInfo(DiagnosticCodes.UPDATE_CHECK_SUCCESS_702, TAG, "App is up-to-date (current: $currentVersion, server: $tagName)")
                _updateState.value = if (isManual) UpdateState.UpToDate(updateInfo) else UpdateState.Idle
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            AppDiagnostics.logError(DiagnosticCodes.UPDATE_CHECK_ERR_704, TAG, "Exception: ${e.message}", e)
            _updateState.value = if (isManual) UpdateState.Error(e.localizedMessage ?: "Network error") else UpdateState.Idle
            null
        }
    }

    suspend fun downloadAndInstall(updateInfo: AppUpdateInfo) = withContext(Dispatchers.IO) {
        try {
            AppDiagnostics.logInfo(DiagnosticCodes.UPDATE_DOWNLOAD_START_705, TAG, "Starting download of ${updateInfo.apkName}")
            _updateState.value = UpdateState.Downloading(0f, 0L, updateInfo.apkSize)

            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Clean up old apk files
            updateDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".apk", ignoreCase = true)) {
                    file.delete()
                }
            }

            val timestamp = System.currentTimeMillis()
            val safeTag = updateInfo.tagName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val outputFile = File(updateDir, "zana-update-${safeTag}-${timestamp}.apk")

            val request = Request.Builder()
                .url(updateInfo.apkDownloadUrl)
                .header("User-Agent", "Navirom-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errMsg = "Failed to download APK: ${response.code} ${response.message}"
                AppDiagnostics.logError(DiagnosticCodes.UPDATE_DOWNLOAD_ERR_706, TAG, errMsg)
                throw Exception(errMsg)
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

            // Save last installed build metadata
            prefs.edit()
                .putString(KEY_LAST_INSTALLED_DIGEST, updateInfo.assetDigest)
                .putString(KEY_LAST_INSTALLED_TAG, updateInfo.tagName)
                .putLong(KEY_LAST_INSTALLED_TIME, System.currentTimeMillis())
                .apply()

            _updateState.value = UpdateState.ReadyToInstall(outputFile, updateInfo)
            withContext(Dispatchers.Main) {
                installApk(context, outputFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading update", e)
            AppDiagnostics.logError(DiagnosticCodes.UPDATE_DOWNLOAD_ERR_706, TAG, "Download failed: ${e.message}", e)
            _updateState.value = UpdateState.Error("Download fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            AppDiagnostics.logInfo(DiagnosticCodes.UPDATE_INSTALL_START_707, TAG, "Initiating package installer for ${apkFile.name}")
            if (!apkFile.exists() || apkFile.length() == 0L) {
                val errMsg = "APK file does not exist or is empty"
                Log.e(TAG, errMsg)
                AppDiagnostics.logError(DiagnosticCodes.UPDATE_INSTALL_ERR_708, TAG, errMsg)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    Toast.makeText(context, "Please allow installing unknown apps for Zana", Toast.LENGTH_LONG).show()
                    return
                }
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
            AppDiagnostics.logError(DiagnosticCodes.UPDATE_INSTALL_ERR_708, TAG, "Install failed: ${e.message}", e)
            _updateState.value = UpdateState.Error("Installation konnte nicht gestartet werden: ${e.localizedMessage}")
        }
    }

    private fun extractVersionString(input: String): String? {
        val regex = Regex("(\\d+(?:\\.\\d+)+)")
        return regex.find(input)?.value
    }

    private fun compareSemver(v1: String, v2: String): Int {
        val clean1 = extractVersionString(v1) ?: v1.removePrefix("v").trim()
        val clean2 = extractVersionString(v2) ?: v2.removePrefix("v").trim()
        val parts1 = clean1.split(".").map { Regex("^\\d+").find(it.trim())?.value?.toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { Regex("^\\d+").find(it.trim())?.value?.toIntOrNull() ?: 0 }
        val len = maxOf(parts1.size, parts2.size)
        for (i in 0 until len) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 > p2) return 1
            if (p1 < p2) return -1
        }
        return 0
    }

    private fun parseIso8601(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            val clean = dateStr.replace(Regex("\\.\\d+Z$"), "Z")
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(clean)?.time ?: 0L
        } catch (_: Exception) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(dateStr.take(19))?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }

    private fun isRemoteVersionNewer(
        tag: String,
        title: String,
        assetUpdatedAt: String,
        assetDigest: String,
        currentVersion: String
    ): Boolean {
        AppDiagnostics.logInfo(
            DiagnosticCodes.UPDATE_CHECK_START_701,
            TAG,
            "Comparing remote: tag='$tag', title='$title', assetUpdatedAt='$assetUpdatedAt', digest='$assetDigest' with local: version='$currentVersion', buildTime=${BuildConfig.BUILD_TIME}"
        )

        // 1. Semantic Versioning comparison
        val cleanCurrent = extractVersionString(currentVersion) ?: currentVersion.removePrefix("v").trim()
        val remoteVersionFromTag = extractVersionString(tag)
        val remoteVersionFromTitle = extractVersionString(title)
        val cleanRemote = remoteVersionFromTag ?: remoteVersionFromTitle

        if (cleanRemote != null) {
            val semverComparison = compareSemver(cleanRemote, cleanCurrent)
            if (semverComparison > 0) {
                Log.i(TAG, "Remote semver $cleanRemote is newer than $cleanCurrent")
                return true
            } else if (semverComparison < 0) {
                Log.i(TAG, "Remote semver $cleanRemote is older than $cleanCurrent")
                return false
            }
        }

        // 2. Check if identical build digest was already downloaded/installed
        val lastInstalledDigest = prefs.getString(KEY_LAST_INSTALLED_DIGEST, "") ?: ""
        if (assetDigest.isNotBlank() && lastInstalledDigest.isNotBlank() && assetDigest.equals(lastInstalledDigest, ignoreCase = true)) {
            Log.i(TAG, "Identical asset digest already installed: $assetDigest")
            return false
        }

        // 3. Compare asset upload timestamp against local build / install time
        val assetTimeMillis = parseIso8601(assetUpdatedAt)
        val packageInstalledTime = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            }
        } catch (_: Exception) {
            0L
        }

        val lastInstalledTime = prefs.getLong(KEY_LAST_INSTALLED_TIME, 0L)
        val localBaselineTime = maxOf(
            BuildConfig.BUILD_TIME,
            packageInstalledTime,
            lastInstalledTime
        )

        if (assetTimeMillis > 0L && localBaselineTime > 0L) {
            val isAssetNewer = assetTimeMillis > (localBaselineTime + 60_000L)
            Log.i(TAG, "Timestamp check: assetTime=$assetTimeMillis ($assetUpdatedAt) vs localBaseline=$localBaselineTime -> isAssetNewer=$isAssetNewer")
            if (isAssetNewer) {
                return true
            }
        }

        // 4. For rolling releases (latest-build / latest / nightly)
        val isRollingRelease = tag.contains("latest", ignoreCase = true) ||
                tag.contains("nightly", ignoreCase = true) ||
                tag.contains("build", ignoreCase = true) ||
                title.contains("latest", ignoreCase = true) ||
                title.contains("build", ignoreCase = true)

        if (isRollingRelease) {
            if (assetDigest.isNotBlank() && lastInstalledDigest.isNotBlank()) {
                val isDifferent = !assetDigest.equals(lastInstalledDigest, ignoreCase = true)
                Log.i(TAG, "Rolling release digest diff: isDifferent=$isDifferent")
                if (isDifferent) return true
            } else if (assetTimeMillis > BuildConfig.BUILD_TIME) {
                Log.i(TAG, "Rolling release assetTime > BuildConfig.BUILD_TIME")
                return true
            }
        }

        return false
    }

    companion object {
        private const val TAG = "UpdateManager"
        const val DEFAULT_REPO = "labibllaca/Zana-player"
        private const val KEY_AUTO_CHECK = "auto_check_updates"
        private const val KEY_GITHUB_REPO = "github_repo_slug"
        private const val KEY_LAST_CHECKED = "last_checked_timestamp"
        private const val KEY_LAST_INSTALLED_DIGEST = "last_installed_asset_digest"
        private const val KEY_LAST_INSTALLED_TAG = "last_installed_tag"
        private const val KEY_LAST_INSTALLED_TIME = "last_installed_timestamp"
    }
}
