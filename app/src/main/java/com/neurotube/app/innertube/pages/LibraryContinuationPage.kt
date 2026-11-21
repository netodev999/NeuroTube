package com.neurotube.app.innertube.pages

import com.neurotube.app.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
    /**
     * Every next-page token this page offered, not only the first.
     *
     * A continuation can carry more than one list, and each hands out its own token. A walk that
     * follows only [continuation] reads one list to its end and stops, leaving the other lists'
     * pages unread — which is how a long liked-songs list imported short.
     */
    val continuations: List<String> = listOfNotNull(continuation),
)
