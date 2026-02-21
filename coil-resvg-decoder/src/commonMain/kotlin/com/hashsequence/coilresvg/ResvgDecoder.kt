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

class ResvgDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val svgBytes = source.source().use { it.readByteArray() }
        return renderSvgImage(svgBytes, options)
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
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

expect suspend fun renderSvgImage(svgBytes: ByteArray, options: Options): DecodeResult

internal expect val PlatformContext.density: Float

internal data class SvgRenderSize(val width: Int, val height: Int)

private const val SVG_DEFAULT_SIZE = 512

@OptIn(ExperimentalCoilApi::class)
internal fun computeSvgRenderSize(
    svgWidth: Float,
    svgHeight: Float,
    options: Options
): SvgRenderSize {

    var scaledWidth = svgWidth
    var scaledHeight = svgHeight
    if (options.size.isOriginal) {
        val density = options.context.density
        if (scaledWidth > 0f) scaledWidth *= density
        if (scaledHeight > 0f) scaledHeight *= density
    }

    val srcWidth = if (scaledWidth > 0f) scaledWidth.roundToInt() else SVG_DEFAULT_SIZE
    val srcHeight = if (scaledHeight > 0f) scaledHeight.roundToInt() else SVG_DEFAULT_SIZE

    val (dstWidth, dstHeight) = DecodeUtils.computeDstSize(
        srcWidth = srcWidth,
        srcHeight = srcHeight,
        targetSize = options.size,
        scale = options.scale,
        maxSize = options.maxBitmapSize,
    )

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

    val renderWidth = if (scaledWidth > 0f) (multiplier * scaledWidth).toInt() else dstWidth
    val renderHeight = if (scaledHeight > 0f) (multiplier * scaledHeight).toInt() else dstHeight

    return SvgRenderSize(renderWidth, renderHeight)
}
