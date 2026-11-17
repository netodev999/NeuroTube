package com.neurotube.app.innertube.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class ContinuationItemRenderer(
    val continuationEndpoint: ContinuationEndpoint? = null,
) {
    @Serializable
    data class ContinuationEndpoint(
        val continuationCommand: ContinuationCommand? = null,
    ) {
        @Serializable
        data class ContinuationCommand(
            val token: String? = null,
        )
    }
}

/**
 * The next-page token of a modern `continuationItemViewModel`, which a library list now carries in
 * place of the older `continuationItemRenderer` at the end of its items.
 *
 * The token sits at `continuationCommand.token` in some captures and one level deeper, under
 * `continuationCommand.innertubeCommand.continuationCommand.token`, in others; both are read.
 */
internal fun JsonObject.continuationItemViewModelToken(): String? {
    val command = this["continuationCommand"] as? JsonObject ?: return null
    val token =
        command["token"].stringOrNull()
            ?: (command["innertubeCommand"] as? JsonObject)
                ?.get("continuationCommand")
                ?.let { (it as? JsonObject)?.get("token") }
                .stringOrNull()
    return token?.takeIf(String::isNotBlank)
}

private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull
