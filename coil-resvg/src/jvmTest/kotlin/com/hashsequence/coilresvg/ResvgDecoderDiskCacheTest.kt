package com.hashsequence.coilresvg

import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Size
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class ResvgDecoderDiskCacheTest {

    @Test
    fun `second decode returns cached bitmap without rendering`() = withDiskCache { diskCache ->
        var renderCount = 0
        val render = countingRenderer { renderCount++ }
        val options = options(size = Size(120, 80), diskCacheKey = "https://example.com/image.svg")

        decoder(SVG_A, options, diskCache, render).decode()
        val cached = decoder(SVG_A, options, diskCache, render).decode()

        assertEquals(1, renderCount)
        assertEquals(120, cached.image.width)
        assertEquals(80, cached.image.height)
    }

    @Test
    fun `source size and explicit cache key are part of cache identity`() =
        withDiskCache { diskCache ->
            var renderCount = 0
            val render = countingRenderer { renderCount++ }

            decoder(SVG_A, options(Size(100, 100), "url-a"), diskCache, render).decode()
            decoder(SVG_A, options(Size(200, 100), "url-a"), diskCache, render).decode()
            decoder(SVG_B, options(Size(200, 100), "url-a"), diskCache, render).decode()
            decoder(SVG_B, options(Size(200, 100), "url-b"), diskCache, render).decode()

            assertEquals(4, renderCount)
        }

    @Test
    fun `concurrent identical decodes render only once`() = withDiskCache { diskCache ->
        var renderCount = 0
        val render = countingRenderer {
            renderCount++
            delay(100)
        }
        val options = options(Size(64, 64), "same-url")

        coroutineScope {
            listOf(
                async { decoder(SVG_A, options, diskCache, render).decode() },
                async { decoder(SVG_A, options, diskCache, render).decode() },
            ).awaitAll()
        }

        assertEquals(1, renderCount)
    }

    private fun decoder(
        svg: ByteArray,
        options: Options,
        diskCache: DiskCache,
        render: suspend (ByteArray, Options) -> DecodeResult,
    ) = ResvgDecoder(
        source = ImageSource(Buffer().write(svg), FileSystem.SYSTEM),
        options = options,
        diskCache = diskCache,
        render = render,
    )

    private fun options(size: Size, diskCacheKey: String) = Options(
        context = PlatformContext.INSTANCE,
        size = size,
        diskCacheKey = diskCacheKey,
    )

    private fun countingRenderer(
        onRender: suspend () -> Unit,
    ): suspend (ByteArray, Options) -> DecodeResult = { _, options ->
        onRender()
        val width = (options.size.width as Dimension.Pixels).px
        val height = (options.size.height as Dimension.Pixels).px
        val bitmap = Bitmap().apply {
            allocPixels(
                ImageInfo(
                    width = width,
                    height = height,
                    colorInfo = ColorInfo(
                        colorType = ColorType.RGBA_8888,
                        alphaType = ColorAlphaType.PREMUL,
                        colorSpace = null,
                    ),
                )
            )
            setImmutable()
        }
        DecodeResult(image = bitmap.asImage(), isSampled = true)
    }

    private fun withDiskCache(block: suspend (DiskCache) -> Unit) = runBlocking {
        val directory = File.createTempFile("coil-resvg-cache-test", "").let { file ->
            check(file.delete())
            check(file.mkdir())
            file
        }
        val diskCache = DiskCache.Builder()
            .directory(directory.toOkioPath())
            .maxSizeBytes(10L * 1024 * 1024)
            .build()
        try {
            block(diskCache)
        } finally {
            diskCache.shutdown()
            directory.deleteRecursively()
        }
    }

    private companion object {
        val SVG_A = "<svg width=\"10\" height=\"10\"/>".encodeToByteArray()
        val SVG_B = "<svg width=\"20\" height=\"20\"/>".encodeToByteArray()
    }
}
