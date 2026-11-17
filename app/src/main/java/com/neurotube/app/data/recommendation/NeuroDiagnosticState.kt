/*
 * Copyright (C) 2025-2026 NeuroTube | netodev999
 *
 * This file is part of NeuroTube (https://github.com/netodev999/NeuroTube).
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property
 * of the NeuroTube project. Any use of this code in other projects must
 * explicitly credit "NeuroTube Android Client" and link back to the original repository.
 */

package com.neurotube.app.data.recommendation

import kotlinx.serialization.json.JsonElement

/** Everything a rank pass reads besides its candidates, as a Home snapshot stores it. */
internal data class NeuroDiagnosticState(
    val brain: JsonElement,
    val categories: NeuroCategoryStore.CategoryIndex,
    val coWatch: NeuroCoWatchStore.CoWatchIndex,
)
