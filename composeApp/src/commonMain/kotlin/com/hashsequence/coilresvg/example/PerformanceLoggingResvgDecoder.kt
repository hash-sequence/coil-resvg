package com.hashsequence.coilresvg.example

import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.hashsequence.coilresvg.ResvgDecoder
import kotlin.time.measureTimedValue

/**
 * Performance logging wrapper for ResvgDecoder
 * Used to measure and record Resvg decoder performance, and write time to [PerformanceTracker] for UI display
 */
class PerformanceLoggingResvgDecoder(
    private val delegate: Decoder,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val (result, duration) = measureTimedValue {
            checkNotNull(delegate.decode()) { "ResvgDecoder returned no result" }
        }

        val millis = duration.inWholeMilliseconds
        val modelKey = options.getModelKey()
        println("ResvgDecoder decode took ${millis}ms (model=$modelKey)")
        if (modelKey.isNotEmpty()) {
            PerformanceTracker.record("resvg", modelKey, millis)
        }
        return result
    }

    class Factory(
        diskCacheEnabled: Boolean,
    ) : Decoder.Factory {
        private val delegateFactory = ResvgDecoder.Factory(diskCacheEnabled)

        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val delegate = delegateFactory.create(result, options, imageLoader) ?: return null
            return PerformanceLoggingResvgDecoder(delegate, options)
        }
    }
}
