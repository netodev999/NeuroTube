package com.neurotube.app.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.neurotube.app.MainActivity
import com.neurotube.app.R
import com.neurotube.app.data.update.UpdateEntryPoint
import com.neurotube.app.data.update.UpdateFailure
import com.neurotube.app.data.update.UpdateTransfer
import com.neurotube.app.notification.NotificationHelper
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val PROGRESS_NOTIFICATION_ID = 9998
private const val READY_NOTIFICATION_ID = 9999
private const val PROGRESS_STEP = 0.01f
private const val PROGRESS_INTERVAL_MS = 250L
private const val PERCENT = 100

/**
 * Downloads one release APK in the foreground, hashing it as it arrives. The file is kept only if
 * its SHA-256 matches the digest GitHub published and it is a NeuroTube package; anything else is
 * deleted and reported as a failure.
 *
 * A transfer that drops is resumed from where it stopped rather than restarted, and the attempts are
 * bounded so a link that never recovers still ends in a reported failure instead of a silent stall.
 */
class UpdateDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val version = inputData.getString(KEY_VERSION).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val url = inputData.getString(KEY_URL) ?: return@withContext fail(UpdateFailure.NETWORK)
            val expectedSha = inputData.getString(KEY_SHA256)
            setForeground(foregroundInfo(null))
            val part = UpdateFiles.partFile(applicationContext, version)
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                downloadWithResume(url, part, digest)
                setProgress(workDataOf(KEY_PHASE to PHASE_VERIFY))
                setForeground(foregroundInfo(1f))
                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                if (expectedSha != null &&
                    !expectedSha.equals(actualSha, ignoreCase = true)
                ) {
                    return@withContext fail(UpdateFailure.CHECKSUM, part)
                }
                if (!isFlowPackage(part)) return@withContext fail(UpdateFailure.PACKAGE, part)
                if (!part.renameTo(UpdateFiles.apkFile(applicationContext, version))) return@withContext fail(UpdateFailure.STORAGE, part)
                notifyReady()
                Result.success()
            } catch (e: IOException) {
                fail(if (e.message?.contains("ENOSPC") == true) UpdateFailure.STORAGE else UpdateFailure.NETWORK, part)
            }
        }

    /**
     * Brings [target] to the whole file, resuming a partial one across attempts.
     *
     * The digest has to cover every byte of the finished file, so it is fed the bytes already on
     * disk before each resumed attempt. A server that ignores the `Range` request answers with the
     * whole file from zero; that body replaces the part rather than being appended to it.
     */
    private suspend fun downloadWithResume(
        url: String,
        target: File,
        digest: MessageDigest,
    ) {
        val client =
            EntryPointAccessors
                .fromApplication(applicationContext, UpdateEntryPoint::class.java)
                .okHttpClient()
                .newBuilder()
                .cache(null)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .readTimeout(UpdateTransfer.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(UpdateTransfer.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        var lastError: IOException? = null
        for (attempt in 1..UpdateTransfer.MAX_ATTEMPTS) {
            if (attempt > 1) {
                val wait = UpdateTransfer.backoffMillis(attempt - 1)
                setProgress(workDataOf(KEY_PHASE to PHASE_RETRY, KEY_ATTEMPT to attempt))
                setForeground(foregroundInfo(null, attempt))
                delay(wait)
            }
            val start = if (target.exists()) target.length() else 0L
            digest.reset()
            if (start > 0) UpdateTransfer.sha256Of(target, digest)
            try {
                attemptDownload(client, url, target, start, digest)
                return
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("download failed")
    }

    /** One request, from [start] to the end of the file, appending to [target]. */
    private suspend fun attemptDownload(
        client: OkHttpClient,
        url: String,
        target: File,
        start: Long,
        digest: MessageDigest,
    ) {
        val range = UpdateTransfer.rangeHeader(start)
        val request =
            Request
                .Builder()
                .url(url)
                .apply { if (range != null) header("Range", range) }
                .build()
        client.newCall(request).execute().use { response ->
            // A part file that already holds the whole file gets a 416 for a range past its end.
            // Nothing is left to fetch, so the digest check decides whether what is on disk is good.
            if (UpdateTransfer.partAlreadyComplete(response.code, start)) return
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val contentRange = response.header("Content-Range")
            val append = range != null && !UpdateTransfer.serverIgnoredRange(true, response.code)
            val bodyStart = if (append) UpdateTransfer.contentRangeStart(contentRange) else 0L
            if (append && bodyStart != start) {
                // The body does not begin where the part ends, so it cannot be spliced onto it.
                target.delete()
                throw IOException("range mismatch: asked $start, got $bodyStart")
            }
            val total =
                UpdateTransfer.contentRangeTotal(contentRange)
                    ?: response.body
                        .contentLength()
                        .takeIf { it > 0 }
                        ?.let { if (append) start + it else it }
            val expectedBytes = total
            var reported = -1f
            var reportedAt = 0L
            UpdateTransfer.copyTo(
                input = response.body.byteStream(),
                target = target,
                append = append,
                digest = digest,
                totalBytes = expectedBytes,
            ) { written, progress ->
                val now = System.currentTimeMillis()
                if (progress != null && progress - reported >= PROGRESS_STEP && now - reportedAt >= PROGRESS_INTERVAL_MS) {
                    reported = progress
                    reportedAt = now
                    setProgress(workDataOf(KEY_PHASE to PHASE_DOWNLOAD, KEY_PROGRESS to progress, KEY_BYTES to written))
                    setForeground(foregroundInfo(progress))
                }
            }
            if (expectedBytes != null && target.length() != expectedBytes) {
                throw IOException("short body: ${target.length()} of $expectedBytes bytes")
            }
        }
    }

    private fun isFlowPackage(apk: File): Boolean =
        applicationContext.packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName == applicationContext.packageName

    private fun fail(
        reason: UpdateFailure,
        part: File? = null,
    ): Result {
        part?.delete()
        return Result.failure(workDataOf(KEY_FAILURE to reason.name))
    }

    private fun foregroundInfo(
        progress: Float?,
        attempt: Int = 1,
    ): ForegroundInfo {
        val title =
            if (attempt > 1) {
                applicationContext.getString(R.string.update_downloading_notification, version) +
                    " — " + applicationContext.getString(R.string.update_retrying_attempt, attempt)
            } else {
                applicationContext.getString(R.string.update_downloading_notification, version)
            }
        val notification =
            NotificationCompat
                .Builder(applicationContext, NotificationHelper.CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(title)
                .setProgress(PERCENT, ((progress ?: 0f) * PERCENT).toInt(), progress == null)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(
                    0,
                    applicationContext.getString(R.string.cancel),
                    WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
                ).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    private fun notifyReady() {
        val open =
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(NotificationHelper.EXTRA_OPEN_UPDATE, true)
        val notification =
            NotificationCompat
                .Builder(applicationContext, NotificationHelper.CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(applicationContext.getString(R.string.update_ready_notification, version))
                .setContentText(applicationContext.getString(R.string.update_ready_notification_text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        applicationContext,
                        0,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setAutoCancel(true)
                .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(READY_NOTIFICATION_ID, notification) }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_SHA256 = "sha256"
        const val KEY_VERSION = "version"
        const val KEY_PHASE = "phase"
        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES = "bytes"
        const val KEY_ATTEMPT = "attempt"
        const val KEY_FAILURE = "failure"
        const val PHASE_DOWNLOAD = "download"
        const val PHASE_RETRY = "retry"
        const val PHASE_VERIFY = "verify"
    }
}

/** Where downloaded updates live: one directory in the cache, one file per version. */
internal object UpdateFiles {
    private const val DIRECTORY = "updates"

    private fun directory(context: Context) = File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    fun apkFile(
        context: Context,
        version: String,
    ) = File(directory(context), UpdateTransfer.apkFileName(version))

    fun partFile(
        context: Context,
        version: String,
    ) = File(directory(context), UpdateTransfer.partFileName(version))

    /** Deletes every downloaded update except [keepVersion]'s, both its APK and its part file. */
    fun clean(
        context: Context,
        keepVersion: String?,
    ) {
        val keep =
            keepVersion?.let {
                setOf(
                    UpdateTransfer.apkFileName(it),
                    UpdateTransfer.partFileName(it),
                )
            } ?: emptySet()
        directory(context).listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }
}
