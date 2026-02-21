package com.hashsequence.coilresvg.example

import androidx.compose.runtime.mutableStateMapOf
import coil3.Extras
import coil3.getExtra
import coil3.request.Options

/**
 * Key for passing model identifier in ImageRequest's Extras.
 * Set when building ImageRequest, read in Decoder via Options.extras.
 */
val ModelKeyExtra = Extras.Key(default = "")

/**
 * Get model identifier from Options
 */
fun Options.getModelKey(): String = getExtra(ModelKeyExtra)

/**
 * Global performance tracker for passing decode time data between Decoder and UI.
 *
 * After Decoder completes decoding, write time via [record],
 * UI layer observes and displays time via [decodeTimes].
 *
 * key format: "${decoderType}:${modelKey}"
 */
object PerformanceTracker {
    /**
     * Store decode time for each decode task (milliseconds).
     * key: "${decoderType}:${modelKey}"
     * value: decode time (milliseconds)
     *
     * Uses Compose's mutableStateMapOf, UI can directly observe changes.
     */
    val decodeTimes = mutableStateMapOf<String, Long>()

    fun record(decoderType: String, modelKey: String, millis: Long) {
        decodeTimes["$decoderType:$modelKey"] = millis
    }

    fun get(decoderType: String, modelKey: String): Long? {
        return decodeTimes["$decoderType:$modelKey"]
    }
}
