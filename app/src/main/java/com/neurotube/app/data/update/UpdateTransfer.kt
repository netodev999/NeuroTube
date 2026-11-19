package com.neurotube.app.data.update

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * The mechanics of bringing one APK down: where a resume starts, how much of the file has arrived,
 * and what digest it has. Kept apart from the worker so it can be tested without a network.
 */
internal object UpdateTransfer {
    /**
     * The `Range` value that continues a download from [start], or null to ask for the whole file.
     *
     * A part file left by an interrupted attempt is the reason to send this: asking for the rest
     * instead of all of it is what turns a dropped connection into a short pause rather than a
     * download that starts again at zero.
     */
    fun rangeHeader(start: Long): String? = if (start > 0) "bytes=$start-" else null

    /**
     * The first byte offset a `Content-Range` header states, or null when it is absent or malformed.
     *
     * The header reads `bytes 1024-2047/4096`. Only the start is needed: it says where the body
     * being read begins, which has to match the offset asked for or the file would be spliced
     * together from the wrong place.
     */
    fun contentRangeStart(value: String?): Long? {
        val text = value?.trim()?.removePrefix("bytes")?.trim() ?: return null
        val start = text.substringBefore('-').trim()
        return start.toLongOrNull()?.takeIf { it >= 0 }
    }

    /**
     * The whole file's size from a `Content-Range` header's `/<total>` part, or null when unknown.
     *
     * Progress needs this: on a resumed download the body length is only what is left, so the bar
     * would jump back to zero and finish at once if the remaining size were used instead.
     */
    fun contentRangeTotal(value: String?): Long? =
        value
            ?.trim()
            ?.substringAfter('/', "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "*" }
            ?.toLongOrNull()
            ?.takeIf { it > 0 }

    /** Whether a server answered a ranged request with the whole file, so the part must be redone. */
    fun serverIgnoredRange(
        rangeRequested: Boolean,
        statusCode: Int,
    ): Boolean = rangeRequested && statusCode != HTTP_PARTIAL_CONTENT

    /**
     * Whether a 416 means the part file already holds the whole file rather than that it is bad.
     *
     * Asking for a range past the end is only possible when the bytes before it are already on disk,
     * which happens when a finished part file was left behind by an interrupted rename. The digest
     * check that follows decides whether those bytes are the right ones.
     */
    fun partAlreadyComplete(
        statusCode: Int,
        start: Long,
    ): Boolean = statusCode == HTTP_RANGE_NOT_SATISFIABLE && start > 0

    /** The digest of what [file] already holds, so a resumed download can keep hashing the whole file. */
    fun sha256Of(
        file: File,
        digest: MessageDigest = MessageDigest.getInstance("SHA-256"),
    ): MessageDigest {
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest
    }

    /**
     * Streams [input] into [target], updating [digest] and reporting the running total.
     *
     * [append] keeps what the file already holds, which is only correct once the response has been
     * shown to start at that offset. [totalBytes] is the whole file's size, so progress is measured
     * against the download the user started rather than against this attempt.
     */
    suspend fun copyTo(
        input: InputStream,
        target: File,
        append: Boolean,
        digest: MessageDigest,
        totalBytes: Long?,
        onProgress: suspend (written: Long, progress: Float?) -> Unit,
    ): Long {
        if (!append) target.delete()
        var written = if (append) target.length() else 0L
        val buffer = ByteArray(BUFFER_SIZE)
        // FileOutputStream in append mode, not outputStream(): the latter truncates the part, which
        // would leave a hole of zeros where the resumed bytes should go.
        FileOutputStream(target, append).use { output ->
            input.use { source ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    written += count
                    val progress = totalBytes?.takeIf { it > 0 }?.let { total -> (written.toFloat() / total).coerceIn(0f, 1f) }
                    onProgress(written, progress)
                }
            }
        }
        return written
    }

    /** A file name that cannot collide with another version's download. */
    fun partFileName(version: String): String = "flow-$version.apk.part"

    fun apkFileName(version: String): String = "flow-$version.apk"

    /**
     * How long a download waits on silence before it gives up on a read, and on a connection before
     * it gives up on the handshake.
     *
     * A read timeout is the only deadline a download can safely have. The app's shared client sets a
     * [okhttp3.OkHttpClient] call timeout of one minute, which caps the *whole* transfer: a 22 MB APK
     * needs roughly 375 KB/s to finish inside it, so on a slower link the call was cancelled part
     * way through — the download stopped near the end, retried, and stopped in the same place every
     * time. A read timeout instead measures the gap between bytes, so a slow but moving connection
     * is allowed to take as long as it needs.
     */
    const val READ_TIMEOUT_SECONDS = 60L

    const val CONNECT_TIMEOUT_SECONDS = 30L

    /** How many times a dropped transfer is resumed before the download is reported as failed. */
    const val MAX_ATTEMPTS = 5

    /** The pause before the next attempt, doubled each time, capped so a retry stays responsive. */
    fun backoffMillis(attempt: Int): Long = (1_000L shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(16_000L)

    const val HTTP_PARTIAL_CONTENT = 206
    const val HTTP_RANGE_NOT_SATISFIABLE = 416
    const val BUFFER_SIZE = 64 * 1024
}
