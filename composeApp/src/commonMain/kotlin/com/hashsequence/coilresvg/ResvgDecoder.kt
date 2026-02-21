package com.hashsequence.coilresvg

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.isOriginal
import coil3.util.component1
import coil3.util.component2
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.use
import kotlin.math.roundToInt
import kotlin.time.measureTimedValue

/**
 * Render SVG using Resvg (Rust)
 */
class ResvgDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val svgBytes = source.source().use { it.readByteArray() }
        
        val (result, duration) = measureTimedValue {
            try {
                renderSvgImage(svgBytes, options)
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
        
        println("ResvgDecoder decode took ${duration.inWholeMilliseconds}ms")
        return result
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            // Check if it's an SVG file
            if (!isSvg(result.source.source(), result.mimeType)) {
                return null
            }
            return ResvgDecoder(result.source, options)
        }

        private fun isSvg(source: BufferedSource, mimeType: String?): Boolean {
            return mimeType == MIME_TYPE_SVG ||
                   mimeType == MIME_TYPE_XML ||
                   (source.rangeEquals(0, LEFT_ANGLE_BRACKET) &&
                    source.indexOf(SVG_TAG, 0, SVG_DETECT_BUFFER_SIZE) != -1L)
        }

        private companion object {
            private const val MIME_TYPE_SVG = "image/svg+xml"
            private const val MIME_TYPE_XML = "text/xml"
            private const val SVG_DETECT_BUFFER_SIZE = 1024L
            private val SVG_TAG = "<svg".encodeUtf8()
            private val LEFT_ANGLE_BRACKET = "<".encodeUtf8()
        }
    }
}

/**
 * Platform-specific implementation: Render SVG using Resvg
 */
expect suspend fun renderSvgImage(svgBytes: ByteArray, options: Options): DecodeResult

/**
 * Get platform display density, used for scaling when Size.ORIGINAL
 * Android returns actual screen density, other platforms return 1.0 (no scaling)
 */
internal expect val PlatformContext.density: Float

/**
 * SVG render size
 */
internal data class SvgRenderSize(val width: Int, val height: Int)

/**
 * SVG default size (used when SVG file doesn't specify size)
 */
private const val SVG_DEFAULT_SIZE = 512

/**
 * Calculate SVG render size, maintaining aspect ratio
 * Uses Coil's standard method to ensure consistent cross-platform behavior
 */
@OptIn(ExperimentalCoilApi::class)
internal fun computeSvgRenderSize(
    svgWidth: Float,
    svgHeight: Float,
    options: Options
): SvgRenderSize {
    
    // Handle Size.ORIGINAL request, scale according to screen density
    var scaledWidth = svgWidth
    var scaledHeight = svgHeight
    if (options.size.isOriginal) {
        val density = options.context.density
        if (scaledWidth > 0f) scaledWidth *= density
        if (scaledHeight > 0f) scaledHeight *= density
    }
    
    // Use SVG original size, or default if invalid
    val srcWidth = if (scaledWidth > 0f) scaledWidth.roundToInt() else SVG_DEFAULT_SIZE
    val srcHeight = if (scaledHeight > 0f) scaledHeight.roundToInt() else SVG_DEFAULT_SIZE
    
    // Use Coil standard method to calculate target size
    val (dstWidth, dstHeight) = DecodeUtils.computeDstSize(
        srcWidth = srcWidth,
        srcHeight = srcHeight,
        targetSize = options.size,
        scale = options.scale,
        maxSize = options.maxBitmapSize,
    )
    
    // Calculate scale multiplier to maintain aspect ratio
    val multiplier = if (scaledWidth > 0f && scaledHeight > 0f) {
        DecodeUtils.computeSizeMultiplier(
            srcWidth = scaledWidth,
            srcHeight = scaledHeight,
            dstWidth = dstWidth.toFloat(),
            dstHeight = dstHeight.toFloat(),
            scale = options.scale,
        )
    } else {
        1f
    }
    
    // Apply scale multiplier to original size, maintaining aspect ratio
    val renderWidth = if (scaledWidth > 0f) (multiplier * scaledWidth).toInt() else dstWidth
    val renderHeight = if (scaledHeight > 0f) (multiplier * scaledHeight).toInt() else dstHeight
    
    return SvgRenderSize(renderWidth, renderHeight)
}
