package com.hashsequence.coilresvg

import coil3.ImageLoader
import coil3.Image
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.isOriginal
import coil3.util.component1
import coil3.util.component2
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.use
import kotlin.math.roundToInt

class ResvgDecoder internal constructor(
    private val source: ImageSource,
    private val options: Options,
    private val diskCache: DiskCache?,
    private val render: suspend (ByteArray, Options, Boolean) -> RenderedSvgImage = ::renderSvgImageWithCache,
) : Decoder {

    constructor(source: ImageSource, options: Options) : this(
        source = source,
        options = options,
        diskCache = null,
        render = ::renderSvgImageWithCache,
    )

    override suspend fun decode(): DecodeResult {
        val svgBytes = source.source().use { it.readByteArray() }
        val diskCache = diskCache ?: return render(svgBytes, options, false).decodeResult
        val cacheKey = createRenderCacheKey(svgBytes, options)

        return RenderCacheLocks.withLock(cacheKey) {
            readFromDiskCache(diskCache, cacheKey, options)
                ?: render(svgBytes, options, options.diskCachePolicy.writeEnabled).let { result ->
                    writeToDiskCache(diskCache, cacheKey, result.pngBytes, options)
                    result.decodeResult
                }
        }
    }

    class Factory(
        private val diskCacheEnabled: Boolean,
    ) : Decoder.Factory {

        constructor() : this(diskCacheEnabled = true)

        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            if (!isSvg(result.source.source(), result.mimeType)) {
                return null
            }
            val cachePolicy = options.diskCachePolicy
            val diskCache = if (diskCacheEnabled && (cachePolicy.readEnabled || cachePolicy.writeEnabled)) {
                imageLoader.diskCache
            } else {
                null
            }
            return ResvgDecoder(result.source, options, diskCache)
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

internal data class RenderedSvgImage(
    val decodeResult: DecodeResult,
    val pngBytes: ByteArray? = null,
)

internal expect suspend fun renderSvgImageWithCache(
    svgBytes: ByteArray,
    options: Options,
    encodePng: Boolean,
): RenderedSvgImage

internal expect fun decodeCachedBitmap(bytes: ByteArray): Image?

internal expect val PlatformContext.density: Float

internal data class SvgRenderSize(val width: Int, val height: Int)

private const val SVG_DEFAULT_SIZE = 512

private const val RENDER_CACHE_VERSION = "coil-resvg-render-cache-v1"

@OptIn(ExperimentalCoilApi::class)
internal fun createRenderCacheKey(svgBytes: ByteArray, options: Options): String {
    val sourceHash = svgBytes.toByteString().sha256().hex()
    val explicitKeyHash = options.diskCacheKey
        ?.encodeUtf8()
        ?.sha256()
        ?.hex()
        ?: "-"

    return buildString {
        append(RENDER_CACHE_VERSION)
        append('|')
        append(sourceHash)
        append('|')
        append(explicitKeyHash)
        append('|')
        append(options.size.cacheKeyValue())
        append('|')
        append(options.scale.name)
        append('|')
        append(options.precision.name)
        append('|')
        append(options.maxBitmapSize.cacheKeyValue())
        append('|')
        append(options.context.density.toBits())
    }
}

private fun coil3.size.Size.cacheKeyValue(): String =
    "${width.cacheKeyValue()}x${height.cacheKeyValue()}"

private fun Dimension.cacheKeyValue(): String = when (this) {
    is Dimension.Pixels -> px.toString()
    Dimension.Undefined -> "original"
}

private fun readFromDiskCache(
    diskCache: DiskCache,
    cacheKey: String,
    options: Options,
): DecodeResult? {
    if (!options.diskCachePolicy.readEnabled) return null

    val snapshot = cacheOrNull {
        diskCache.openSnapshot(cacheKey)
    } ?: return null

    return try {
        cacheOrNull {
            val metadata = diskCache.fileSystem.read(snapshot.metadata) { readUtf8() }
            if (metadata != RENDER_CACHE_VERSION) return null

            val encodedBitmap = diskCache.fileSystem.read(snapshot.data) { readByteArray() }
            val image = decodeCachedBitmap(encodedBitmap) ?: return null
            DecodeResult(image = image, isSampled = true)
        }
    } finally {
        cacheOrNull { snapshot.close() }
    }
}

private fun writeToDiskCache(
    diskCache: DiskCache,
    cacheKey: String,
    pngBytes: ByteArray?,
    options: Options,
) {
    if (!options.diskCachePolicy.writeEnabled || pngBytes == null) return

    val editor = cacheOrNull {
        diskCache.openEditor(cacheKey)
    } ?: return

    var committed = false
    try {
        cacheOrNull {
            diskCache.fileSystem.write(editor.metadata) {
                writeUtf8(RENDER_CACHE_VERSION)
            }
            diskCache.fileSystem.write(editor.data) {
                write(pngBytes)
            }
            editor.commit()
            committed = true
        }
    } finally {
        if (!committed) cacheOrNull { editor.abort() }
    }
}

private inline fun <T> cacheOrNull(block: () -> T): T? = try {
    block()
} catch (error: Throwable) {
    // Optional cache I/O and codec linkage failures must not fail an otherwise valid image.
    if (error is CancellationException) throw error
    null
}

private object RenderCacheLocks {
    private val lock = SynchronizedObject()
    private val entries = mutableMapOf<String, Entry>()

    suspend fun <T> withLock(key: String, block: suspend () -> T): T {
        val entry = synchronized(lock) {
            entries.getOrPut(key, ::Entry).also { it.references++ }
        }
        try {
            return entry.mutex.withLock { block() }
        } finally {
            synchronized(lock) {
                entry.references--
                if (entry.references == 0 && entries[key] === entry) {
                    entries.remove(key)
                }
            }
        }
    }

    private class Entry {
        val mutex = Mutex()
        var references = 0
    }
}

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
