package com.hashsequence.coilresvg.example

import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.svg.SvgDecoder
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.measureTimedValue

/**
 * Coil-SVG Decoder 的性能日志包装器
 * 用于测量和记录 Coil 原生 SVG 解码器的性能
 */
class PerformanceLoggingSvgDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val (result, duration) = measureTimedValue {
            // 使用 Coil 原生的 SVG 解码器
            SvgDecoder(source, options).decode()
        }
        
        println("Coil-SVG decode took ${duration.inWholeMilliseconds}ms")
        return result
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            // 检查是否是 SVG 文件
            if (!isSvg(result.source.source(), result.mimeType)) {
                return null
            }
            return PerformanceLoggingSvgDecoder(result.source, options)
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
