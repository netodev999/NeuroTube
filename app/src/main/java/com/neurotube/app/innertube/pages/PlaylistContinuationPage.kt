package com.neurotube.app.innertube.pages

import com.neurotube.app.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
    val continuations: List<String> = listOfNotNull(continuation),
)
