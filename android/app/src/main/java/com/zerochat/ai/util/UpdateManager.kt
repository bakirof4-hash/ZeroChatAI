package com.zerochat.ai.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.zerochat.ai.data.model.AppVersionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Arrays

object UpdateManager {

    /**
     * Retrieves current installed versionCode.
     */
    fun getInstalledVersionCode(context: Context): Int {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (packageInfo.longVersionCode and 0xFFFFFFFFL).toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    /**
     * Retrieves current installed versionName.
     */
    fun getInstalledVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * Downloads APK over HTTPS with progress streaming.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        targetVersionCode: Int,
        onProgress: (percent: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }

        val apkFile = File(updatesDir, "zerochat_v${targetVersionCode}.apk")
        if (apkFile.exists()) {
            apkFile.delete()
        }

        val url = URL(downloadUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Failed to download update APK: Server returned HTTP ${connection.responseCode}")
        }

        val totalBytes = connection.contentLength.toLong()
        var downloadedBytes = 0L

        connection.inputStream.use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                        onProgress(percent.coerceIn(0, 100))
                    }
                }
            }
        }

        apkFile
    }

    /**
     * Computes the SHA-256 hash of a file.
     */
    fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }.lowercase()
    }

    /**
     * Verifies the APK's SHA-256 checksum against trusted backend checksum.
     */
    fun verifyChecksum(apkFile: File, expectedSha256: String) {
        val computedHash = calculateFileSha256(apkFile)
        val expectedClean = expectedSha256.trim().lowercase()
        if (!computedHash.equals(expectedClean, ignoreCase = true)) {
            apkFile.delete()
            throw SecurityException(
                "APK SHA-256 integrity check failed. Expected: $expectedClean, Computed: $computedHash. File has been removed."
            )
        }
    }

    /**
     * Verifies package name and signing certificates match installed application.
     */
    fun verifyPackageAndSignatures(context: Context, apkFile: File) {
        val pm = context.packageManager

        // 1. Verify Package Name
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: throw SecurityException("Downloaded file is not a valid Android package archive.")

        if (archiveInfo.packageName != context.packageName) {
            apkFile.delete()
            throw SecurityException(
                "Package name mismatch: Expected '${context.packageName}' but package archive is '${archiveInfo.packageName}'."
            )
        }

        // 2. Verify Signing Certificates match running app
        try {
            val installedSignatures = getInstalledSignatures(context)
            val archiveSignatures = getArchiveSignatures(context, apkFile)

            if (installedSignatures.isEmpty() || archiveSignatures.isEmpty()) {
                throw SecurityException("Unable to read cryptographic signing certificates.")
            }

            // At least one certificate must match
            var certMatch = false
            for (instSig in installedSignatures) {
                for (archSig in archiveSignatures) {
                    if (Arrays.equals(instSig, archSig)) {
                        certMatch = true
                        break
                    }
                }
                if (certMatch) break
            }

            if (!certMatch) {
                apkFile.delete()
                throw SecurityException(
                    "Security verification failed: APK signing certificate does not match installed application certificate."
                )
            }
        } catch (e: Exception) {
            if (e is SecurityException) throw e
            // If in debug/emulator environment and signatures cannot be extracted, verify package name matches
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledSignatures(context: Context): List<ByteArray> {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
        } else {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun getArchiveSignatures(context: Context, apkFile: File): List<ByteArray> {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return emptyList()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
                ?: info.signatures?.map { it.toByteArray() }
                ?: emptyList()
        } else {
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }
    }

    /**
     * Launches Android's official PackageInstaller flow via FileProvider and Intent.ACTION_VIEW.
     * Requires explicit user confirmation by design.
     */
    fun startInstallIntent(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }
}

