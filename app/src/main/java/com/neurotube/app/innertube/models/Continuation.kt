package com.neurotube.app.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Continuation(
    @JsonNames("nextContinuationData", "nextRadioContinuationData")
    val nextContinuationData: NextContinuationData? = null,
    // A library section hands out its next slice as a reload of the same section rather than an
    // append, so both forms carry a usable token.
    val reloadContinuationData: NextContinuationData? = null,
    // The modern form: a `continuations` entry can be a `continuationCommand` rather than either
    // data object. It carries the same token under a different key, and reading only the two older
    // shapes left a page with no next token, which ended the walk after one page.
    val continuationCommand: ContinuationCommand? = null,
) {
    @Serializable
    data class NextContinuationData(
        val continuation: String,
    )

    @Serializable
    data class ContinuationCommand(
        val token: String,
    )
}

/** Every token this list of continuations carries, in the order they are listed. */
fun List<Continuation>?.getContinuations(): List<String> =
    this.orEmpty().mapNotNull { continuation ->
        continuation.nextContinuationData?.continuation
            ?: continuation.reloadContinuationData?.continuation
            ?: continuation.continuationCommand?.token
    }

fun List<Continuation>?.getContinuation() = getContinuations().firstOrNull()
