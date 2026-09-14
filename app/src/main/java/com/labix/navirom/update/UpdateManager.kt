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

    private val _githubRepo = MutableStateFlow(sanitizeGithubRepo(prefs.getString(KEY_GITHUB_REPO, DEFAULT_REPO) ?: DEFAULT_REPO))
    val githubRepo: StateFlow<String> = _githubRepo.asStateFlow()

    private val _lastCheckedTime = MutableStateFlow(prefs.getLong(KEY_LAST_CHECKED, 0L))
    val lastCheckedTime: StateFlow<Long> = _lastCheckedTime.asStateFlow()

    fun setAutoCheckEnabled(enabled: Boolean) {
        _autoCheckEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
    }

    fun setGithubRepo(repo: String) {
        val sanitized = sanitizeGithubRepo(repo)
        _githubRepo.value = sanitized
        prefs.edit().putString(KEY_GITHUB_REPO, sanitized).apply()
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun installReadyApk() {
        val state = _updateState.value
        if (state is UpdateState.ReadyToInstall) {
            installApk(context, state.apkFile)
        }
    }

    suspend fun checkForUpdates(isManual: Boolean = false): AppUpdateInfo? = withContext(Dispatchers.IO) {
        if (!isManual && !_autoCheckEnabled.value) {
            return@withContext null
        }

        _updateState.value = UpdateState.Checking
        try {
            val repo = sanitizeGithubRepo(_githubRepo.value)
            val apiUrl = "https://api.github.com/repos/$repo/releases"

            AppDiagnostics.logInfo(DiagnosticCodes.UPDATE_CHECK_START_701, TAG, "Checking for updates on repo: $repo (isManual: $isManual)")

            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Navirom-Android-App")
                .build()

            var response = httpClient.newCall(request).execute()
            var bodyString = response.body?.string() ?: ""

            // If /releases 404 or empty, try /releases/latest as fallback
            var jsonArray: JSONArray? = null
            if (response.isSuccessful && bodyString.isNotBlank()) {
                try {
                    jsonArray = JSONArray(bodyString)
                } catch (_: Exception) {
                    try {
                        val singleObj = JSONObject(bodyString)
                        jsonArray = JSONArray().apply { put(singleObj) }
                    } catch (_: Exception) {}
                }
            }

            if (jsonArray == null || jsonArray.length() == 0) {
                val latestUrl = "https://api.github.com/repos/$repo/releases/latest"
                val latestReq = Request.Builder()
                    .url(latestUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Navirom-Android-App")
                    .build()
                val latestResp = httpClient.newCall(latestReq).execute()
                if (latestResp.isSuccessful) {
                    val latestBody = latestResp.body?.string() ?: ""
                    if (latestBody.isNotBlank()) {
                        try {
                            val singleObj = JSONObject(latestBody)
                            jsonArray = JSONArray().apply { put(singleObj) }
                        } catch (_: Exception) {}
                    }
                }
            }

            if (jsonArray == null || jsonArray.length() == 0) {
                if (!response.isSuccessful) {
                    val errMsg = "GitHub error (${response.code}): repo '$repo' not found or has no releases."
                    Log.w(TAG, errMsg)
                    AppDiagnostics.logWarn(DiagnosticCodes.UPDATE_CHECK_WARN_703, TAG, errMsg)
                    _updateState.value = if (isManual) UpdateState.Error(errMsg) else UpdateState.Idle
                    return@withContext null
                }
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
            
            // Build aggregated changelog from all intermediate newer releases
            val changelogBuilder = StringBuilder()
            if (higherSemverCandidates.size > 1) {
                for (cand in higherSemverCandidates) {
                    val relBody = cand.release.optString("body", "").trim()
                    val relTag = cand.tagName
                    val relTitle = cand.title
                    if (relBody.isNotBlank()) {
                        if (changelogBuilder.isNotEmpty()) changelogBuilder.append("\n\n---\n\n")
                        changelogBuilder.append("### $relTag")
                        if (relTitle.isNotBlank() && relTitle != relTag) {
                            changelogBuilder.append(" — $relTitle")
                        }
                        changelogBuilder.append("\n\n").append(relBody)
                    }
                }
            } else if (higherSemverCandidates.size == 1) {
                val relBody = higherSemverCandidates.first().release.optString("body", "").trim()
                changelogBuilder.append(relBody)
            }

            val aggregatedBody = if (changelogBuilder.isNotBlank()) changelogBuilder.toString() else targetRelease.optString("body", "")
            val body = if (aggregatedBody.isNotBlank()) aggregatedBody else "Neue Funktionen, Verbesserungen und Fehlerbehebungen."
            val publishedAt = targetRelease.optString("published_at", "")
            val htmlUrl = targetRelease.optString("html_url", "https://github.com/$repo")
            val apkDownloadUrl = latestApkAsset.optString("browser_download_url", "")
            val apkName = latestApkAsset.optString("name", "zana-update.apk")
            val apkSize = latestApkAsset.optLong("size", 0L)
            val assetUpdatedAt = latestApkAsset.optString("updated_at", publishedAt)
            val assetUpdatedAtMillis = chosen.assetTimeMillis
            val assetDigest = latestApkAsset.optString("digest", "")

            val isNewer = isRemoteVersionNewer(
                tag = tagName,
                title = title,
                assetUpdatedAt = assetUpdatedAt,
                assetDigest = assetDigest,
                currentVersion = currentVersion,
                isManual = isManual
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
                val errMsg = "APK file does not exist or is empty: ${apkFile.absolutePath}"
                Log.e(TAG, errMsg)
                AppDiagnostics.logError(DiagnosticCodes.UPDATE_INSTALL_ERR_708, TAG, errMsg)
                _updateState.value = UpdateState.Error(errMsg)
                return
            }

            apkFile.setReadable(true, false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    Toast.makeText(context, "Please allow installing unknown apps for Zana, then return to install.", Toast.LENGTH_LONG).show()
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

            // Explicitly grant URI read permissions to any resolver (package installer)
            val resInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(intent, 0)
            }
            for (resolveInfo in resInfoList) {
                val pkg = resolveInfo.activityInfo.packageName
                try {
                    context.grantUriPermission(pkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }

            // 1. Stop background playback service so audio and foreground notifications are stopped
            try {
                com.labix.navirom.player.NaviromPlaybackService.stopService(context)
            } catch (_: Exception) {}

            // 2. Start the system package installer
            context.startActivity(intent)

            // 3. Close the application completely so the system package installer is displayed cleanly
            try {
                com.labix.MainActivity.closeApplication(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to close MainActivity on update install", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer", e)
            AppDiagnostics.logError(DiagnosticCodes.UPDATE_INSTALL_ERR_708, TAG, "Install failed: ${e.message}", e)
            _updateState.value = UpdateState.Error("Installation could not be started: ${e.localizedMessage}")
        }
    }

    private fun extractVersionString(input: String): String? {
        // 1. Standard SemVer (e.g. 1.6.0, 1.5.1)
        val semverRegex = Regex("""(\d+(?:\.\d+)+)""")
        semverRegex.find(input)?.value?.let { return it }

        // 2. Date version (e.g. 2026-09-06 or 2026.09.06)
        val dateRegex = Regex("""(\d{4}[.-]\d{2}[.-]\d{2})""")
        dateRegex.find(input)?.value?.let { return it.replace('-', '.') }

        // 3. Build number (e.g. build-6, b6, v6)
        val buildRegex = Regex("""(?:v|build|b)[-_]?(\d+)""", RegexOption.IGNORE_CASE)
        buildRegex.find(input)?.groupValues?.getOrNull(1)?.let { return it }

        return null
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
        currentVersion: String,
        isManual: Boolean
    ): Boolean {
        AppDiagnostics.logInfo(
            DiagnosticCodes.UPDATE_CHECK_START_701,
            TAG,
            "Comparing remote: tag='$tag', title='$title', assetUpdatedAt='$assetUpdatedAt', digest='$assetDigest' with local: version='$currentVersion', buildTime=${BuildConfig.BUILD_TIME}, isManual=$isManual"
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

        // 2. Check if identical build digest was already downloaded/installed or matches currently installed APK
        val lastInstalledDigest = prefs.getString(KEY_LAST_INSTALLED_DIGEST, "") ?: ""
        val localApkDigest = getInstalledApkSha256(context)

        if (assetDigest.isNotBlank() && localApkDigest.isNotBlank()) {
            if (assetDigest.equals(localApkDigest, ignoreCase = true)) {
                Log.i(TAG, "Asset digest matches currently installed APK: $assetDigest")
                return false
            } else {
                Log.i(TAG, "Asset digest differs from running APK ($assetDigest vs $localApkDigest)")
                if (isManual) return true
            }
        }

        if (assetDigest.isNotBlank() && lastInstalledDigest.isNotBlank() && assetDigest.equals(lastInstalledDigest, ignoreCase = true)) {
            Log.i(TAG, "Identical asset digest already installed previously: $assetDigest")
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
            val isAssetNewer = assetTimeMillis > (localBaselineTime + 30_000L)
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
            if (isManual) {
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

        @Volatile
        private var instance: UpdateManager? = null

        fun getInstance(context: Context): UpdateManager {
            return instance ?: synchronized(this) {
                instance ?: UpdateManager(context.applicationContext).also { instance = it }
            }
        }

        fun sanitizeGithubRepo(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isBlank()) return DEFAULT_REPO

            // Match various GitHub URL structures:
            // https://api.github.com/repos/owner/repo/releases
            // https://github.com/owner/repo/releases
            // github.com/owner/repo
            // owner/repo
            val pattern = Regex("""(?:https?://)?(?:(?:api\.)?github\.com/(?:repos/)?)?([^/\s#?]+)/([^/\s#?]+)""", RegexOption.IGNORE_CASE)
            val match = pattern.find(trimmed)
            if (match != null) {
                val owner = match.groupValues[1].trim()
                val repo = match.groupValues[2].trim()
                    .removeSuffix(".git")
                    .removeSuffix("/releases")
                    .removeSuffix("/releases/")
                    .removeSuffix("/")
                if (owner.isNotBlank() && repo.isNotBlank()) {
                    return "$owner/$repo"
                }
            }

            return trimmed
                .removePrefix("https://api.github.com/repos/")
                .removePrefix("http://api.github.com/repos/")
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .removePrefix("github.com/")
                .removeSuffix("/releases")
                .removeSuffix("/releases/")
                .removeSuffix(".git")
                .trim('/')
                .trim()
                .ifBlank { DEFAULT_REPO }
        }

        fun getInstalledApkSha256(context: Context): String {
            return try {
                val apkPath = context.packageCodePath
                val file = File(apkPath)
                if (!file.exists() || !file.canRead()) return ""
                val md = java.security.MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var len: Int
                    while (input.read(buf).also { len = it } != -1) {
                        md.update(buf, 0, len)
                    }
                }
                "sha256:" + md.digest().joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                ""
            }
        }
    }
}
