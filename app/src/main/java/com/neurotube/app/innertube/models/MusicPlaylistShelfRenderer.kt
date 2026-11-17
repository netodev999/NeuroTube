package com.neurotube.app.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicPlaylistShelfRenderer(
    val playlistId: String? = null,
    val contents: List<MusicShelfRenderer.Content>? = null,
    // The shelf hands out its next slice here when a page is longer than one screen. Dropping it
    // imported only the first page of a long liked-songs list.
    val continuations: List<Continuation>? = null,
    // Optional: a shelf that omits it must not fail the whole response, as every other optional
    // field here already guards against.
    val collapsedItemCount: Int = 0,
)
