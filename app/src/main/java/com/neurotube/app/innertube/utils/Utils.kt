package com.neurotube.app.innertube.utils

import com.neurotube.app.innertube.YouTube
import com.neurotube.app.innertube.pages.LibraryPage
import com.neurotube.app.innertube.pages.PlaylistPage
import com.neurotube.app.utils.ImportDiagnostics
import com.neurotube.app.utils.describeFailure
import java.security.MessageDigest

@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> =
    runCatching {
        val page = getOrThrow()
        val songs = page.songs.toMutableList()
        var continuation = page.songsContinuation
        val seenContinuations = mutableSetOf<String>()
        var requestCount = 0
        val maxRequests = 50 // Prevent excessive API calls

        while (continuation != null && requestCount < maxRequests) {
            // Prevent infinite loops by tracking seen continuations
            if (continuation in seenContinuations) {
                break
            }
            seenContinuations.add(continuation)
            requestCount++

            val continuationPage = YouTube.playlistContinuation(continuation).getOrThrow()
            songs += continuationPage.songs
            continuation = continuationPage.continuation
        }
        PlaylistPage(
            playlist = page.playlist,
            songs = songs,
            songsContinuation = null,
            continuation = page.continuation,
        )
    }

@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> =
    runCatching {
        val page = getOrThrow()
        val items = page.items.toMutableList()
        var continuation = page.continuation
        val seenContinuations = mutableSetOf<String>()

        // Every page is walked. A cap used to stop the walk after 50 pages, which silently truncated
        // the list of anyone with more likes than that; the seen-token set is what actually keeps a
        // loop from running forever, so a large library is imported whole.
        while (continuation != null) {
            // Prevent infinite loops by tracking seen continuations
            if (!seenContinuations.add(continuation)) break

            // A page that fails to load stops the walk but keeps everything gathered so far, so one
            // bad response cannot discard a whole library. It is recorded either way: a walk that
            // ends early is the difference between a short list and a complete one, and a silent
            // stop is what made a truncated import look like a successful one.
            val next = YouTube.libraryContinuation(continuation)
            val continuationPage = next.getOrNull()
            if (continuationPage == null) {
                ImportDiagnostics.record(
                    "library walk: stopped after ${items.size} items, page failed " +
                        describeFailure(next.exceptionOrNull()!!),
                )
                break
            }
            if (continuationPage.items.isEmpty() && continuationPage.continuation == null) {
                // A page the parser could not read: it carries neither items nor a next token, so
                // the walk cannot go on. Recorded because it means a shape is still unhandled.
                ImportDiagnostics.record(
                    "library walk: stopped after ${items.size} items, page carried nothing",
                )
                break
            }
            items += continuationPage.items
            continuation = continuationPage.continuation
        }
        LibraryPage(
            items = items,
            continuation = page.continuation,
        )
    }

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()

fun parseCookieString(cookie: String): Map<String, String> =
    cookie
        .split("; ")
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) {
                null
            } else {
                part.substring(0, splitIndex) to part.substring(splitIndex + 1)
            }
        }.toMap()

fun String.parseTime(): Int? {
    try {
        val parts = split(":").map { it.toInt() }
        if (parts.size == 2) {
            return parts[0] * 60 + parts[1]
        }
        if (parts.size == 3) {
            return parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    } catch (e: Exception) {
        return null
    }
    return null
}
