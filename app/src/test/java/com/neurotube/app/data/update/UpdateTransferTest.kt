package com.neurotube.app.data.update

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class UpdateTransferTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `a part file makes the next request ask for the rest`() {
        assertThat(UpdateTransfer.rangeHeader(0)).isNull()
        assertThat(UpdateTransfer.rangeHeader(1024)).isEqualTo("bytes=1024-")
    }

    @Test
    fun `the start and total are read from a content range`() {
        assertThat(UpdateTransfer.contentRangeStart("bytes 1024-2047/4096")).isEqualTo(1024)
        assertThat(UpdateTransfer.contentRangeTotal("bytes 1024-2047/4096")).isEqualTo(4096)
    }

    @Test
    fun `an unknown or malformed content range reads as null rather than zero`() {
        assertThat(UpdateTransfer.contentRangeStart(null)).isNull()
        assertThat(UpdateTransfer.contentRangeStart("bytes */4096")).isNull()
        assertThat(UpdateTransfer.contentRangeStart("nonsense")).isNull()
        assertThat(UpdateTransfer.contentRangeTotal("bytes 0-99/*")).isNull()
        assertThat(UpdateTransfer.contentRangeTotal(null)).isNull()
    }

    @Test
    fun `a server answering a ranged request with the whole file is detected`() {
        assertThat(UpdateTransfer.serverIgnoredRange(rangeRequested = true, statusCode = 200)).isTrue()
        assertThat(UpdateTransfer.serverIgnoredRange(rangeRequested = true, statusCode = 206)).isFalse()
        assertThat(UpdateTransfer.serverIgnoredRange(rangeRequested = false, statusCode = 200)).isFalse()
    }

    @Test
    fun `resuming appends to the part and the digest covers the whole file`() =
        runTest {
            val all = ByteArray(4096) { (it % 251).toByte() }
            val target = folder.newFile("update.apk.part")
            target.writeBytes(all.copyOfRange(0, 1024))

            val digest = UpdateTransfer.sha256Of(target)
            val written =
                UpdateTransfer.copyTo(
                    input = ByteArrayInputStream(all.copyOfRange(1024, all.size)),
                    target = target,
                    append = true,
                    digest = digest,
                    totalBytes = all.size.toLong(),
                ) { _, _ -> }

            assertThat(written).isEqualTo(all.size.toLong())
            assertThat(target.readBytes()).isEqualTo(all)
            assertThat(digest.digest().joinToString("") { "%02x".format(it) }).isEqualTo(sha256(all))
        }

    @Test
    fun `a fresh attempt replaces whatever the part held`() =
        runTest {
            val body = ByteArray(2048) { 7 }
            val target = folder.newFile("update.apk.part")
            target.writeBytes(ByteArray(500) { 1 })

            val digest = MessageDigest.getInstance("SHA-256")
            UpdateTransfer.copyTo(
                input = ByteArrayInputStream(body),
                target = target,
                append = false,
                digest = digest,
                totalBytes = body.size.toLong(),
            ) { _, _ -> }

            assertThat(target.readBytes()).isEqualTo(body)
            assertThat(digest.digest().joinToString("") { "%02x".format(it) }).isEqualTo(sha256(body))
        }

    @Test
    fun `progress is measured against the whole file, not the bytes this attempt carries`() =
        runTest {
            val chunk = UpdateTransfer.BUFFER_SIZE
            val all = ByteArray(chunk * 4) { 3 }
            val target = folder.newFile("update.apk.part")
            target.writeBytes(all.copyOfRange(0, chunk))
            val seen = mutableListOf<Float>()

            UpdateTransfer.copyTo(
                input = ByteArrayInputStream(all.copyOfRange(chunk, all.size)),
                target = target,
                append = true,
                digest = UpdateTransfer.sha256Of(target),
                totalBytes = all.size.toLong(),
            ) { _, progress -> progress?.let { seen += it } }

            // 64 KB of 256 KB is already on disk, so the first byte read lands at half the file.
            // Measuring against the 192 KB still to come would report a third instead.
            assertThat(seen).isNotEmpty()
            assertThat(seen.first()).isWithin(0.01f).of(0.5f)
            assertThat(seen.last()).isEqualTo(1f)
        }

    @Test
    fun `the wait between attempts grows and then stops growing`() {
        assertThat(UpdateTransfer.backoffMillis(1)).isEqualTo(1_000L)
        assertThat(UpdateTransfer.backoffMillis(2)).isEqualTo(2_000L)
        assertThat(UpdateTransfer.backoffMillis(3)).isEqualTo(4_000L)
        assertThat(UpdateTransfer.backoffMillis(9)).isEqualTo(16_000L)
    }

    @Test
    fun `a part and an APK for the same version do not share a name`() {
        val part = UpdateTransfer.partFileName("1.5.13")
        val apk = UpdateTransfer.apkFileName("1.5.13")
        assertThat(part).isNotEqualTo(apk)
        assertThat(part).isEqualTo("flow-1.5.13.apk.part")
        assertThat(apk).isEqualTo("flow-1.5.13.apk")
    }
}
