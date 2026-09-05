package com.hashsequence.coilresvg

import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.Options
import coil3.size.Size
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    @Test
    fun `factory respects all disk cache policies`() = withDiskCache { diskCache ->
        for (policy in CachePolicy.entries) {
            var reads = 0
            var writes = 0
            val trackedCache = object : DiskCache by diskCache {
                override fun openSnapshot(key: String): DiskCache.Snapshot? {
                    reads++
                    return diskCache.openSnapshot(key)
                }

                override fun openEditor(key: String): DiskCache.Editor? {
                    writes++
                    return diskCache.openEditor(key)
                }
            }
            val options = options(Size(120, 80), policy.name).copy(diskCachePolicy = policy)
            val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(trackedCache).build()
            val source = ImageSource(Buffer().write(SVG_A), FileSystem.SYSTEM)
            try {
                val decoder = assertNotNull(ResvgDecoder.Factory().create(
                    SourceFetchResult(source, "image/svg+xml", DataSource.MEMORY), options, loader,
                ))
                assertEquals(120, assertNotNull(decoder.decode()).image.width)
                assertEquals(if (policy.readEnabled) 1 else 0, reads, policy.name)
                assertEquals(if (policy.writeEnabled) 1 else 0, writes, policy.name)
            } finally {
                source.close()
                loader.shutdown()
            }
        }
    }

    @Test
    fun `disabled factory does not access the render cache`() = withDiskCache { diskCache ->
        var accesses = 0
        val inaccessibleCache = object : DiskCache by diskCache {
            override fun openSnapshot(key: String): DiskCache.Snapshot? {
                accesses++
                return null
            }
            override fun openEditor(key: String): DiskCache.Editor? {
                accesses++
                return null
            }
        }
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(inaccessibleCache).build()
        val source = ImageSource(Buffer().write(SVG_A), FileSystem.SYSTEM)
        try {
            val decoder = assertNotNull(ResvgDecoder.Factory(false).create(
                SourceFetchResult(source, "image/svg+xml", DataSource.MEMORY),
                options(Size(120, 80), "disabled"), loader,
            ))
            assertEquals(120, assertNotNull(decoder.decode()).image.width)
            assertEquals(0, accesses)
            assertEquals(0L, diskCache.size)
        } finally {
            source.close()
            loader.shutdown()
        }
    }

    @Test
    fun `PNG encoding is skipped without a writable cache`() = withDiskCache { diskCache ->
        val encodingRequests = mutableListOf<Boolean>()
        val render: suspend (ByteArray, Options, Boolean) -> RenderedSvgImage = { svg, options, encodePng ->
            encodingRequests += encodePng
            renderSvgImageWithCache(svg, options, encodePng).also {
                assertNull(it.pngBytes)
            }
        }
        val options = options(Size(120, 80), "no-encoding")
        decoder(SVG_A, options, null, render).decode()
        decoder(SVG_A, options.copy(diskCachePolicy = CachePolicy.READ_ONLY), diskCache, render).decode()
        decoder(SVG_A, options.copy(diskCachePolicy = CachePolicy.DISABLED), diskCache, render).decode()
        assertEquals(listOf(false, false, false), encodingRequests)
    }

    @Test
    fun `Rust PNG cache preserves translucent opaque and transparent pixels`() = withDiskCache { diskCache ->
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="3" height="1">
            <rect width="1" height="1" fill="red" fill-opacity="0.5"/>
            <rect x="1" width="1" height="1" fill="blue"/>
        </svg>""".encodeToByteArray()
        var renders = 0
        val render = countingRenderer { renders++ }
        val options = options(Size(30, 10), "alpha")
        val first = decoder(svg, options, diskCache, render).decode()
        val cached = decoder(svg, options, diskCache, render).decode()
        assertEquals(1, renders)
        for (result in listOf(first, cached)) {
            val bitmap = (result.image as BitmapImage).bitmap
            assertEquals(30, bitmap.width)
            assertEquals(10, bitmap.height)
            assertEquals(0x80ff0000.toInt(), bitmap.getColor(5, 5))
            assertEquals(0xff0000ff.toInt(), bitmap.getColor(15, 5))
            assertEquals(0, bitmap.getColor(25, 5))
        }
    }

    @Test
    fun `missing PNG after encoding failure still returns rendered image`() = withDiskCache { diskCache ->
        val render: suspend (ByteArray, Options, Boolean) -> RenderedSvgImage = { svg, options, encodePng ->
            assertTrue(encodePng)
            renderSvgImageWithCache(svg, options, false)
        }
        val result = decoder(SVG_A, options(Size(120, 80), "failed-encoding"), diskCache, render).decode()
        assertEquals(120, result.image.width)
        assertEquals(0L, diskCache.size)
    }

    @Test
    fun `cache read linkage failure falls back to rendering`() = withDiskCache { diskCache ->
        val brokenCache = object : DiskCache by diskCache {
            override fun openSnapshot(key: String): DiskCache.Snapshot? = throw NoSuchMethodError("codec")
        }
        var renders = 0
        val result = decoder(SVG_A, options(Size(120, 80), "broken-read"), brokenCache,
            countingRenderer { renders++ }).decode()
        assertEquals(1, renders)
        assertEquals(120, result.image.width)
    }

    @Test
    fun `failed commit aborts the edit and preserves rendered image`() = withDiskCache { diskCache ->
        var aborted = false
        val brokenCache = failingCommitCache(diskCache, NoSuchMethodError("cache write")) { aborted = true }
        val options = options(Size(120, 80), "broken-write")
        val render = countingRenderer {}
        val result = decoder(SVG_A, options, brokenCache, render).decode()
        assertEquals(120, result.image.width)
        assertTrue(aborted)
        assertEquals(0L, diskCache.size)
        decoder(SVG_A, options, diskCache, render).decode()
        assertTrue(diskCache.size > 0)
    }

    @Test
    fun `cache cancellation propagates and aborts the edit`() = withDiskCache { diskCache ->
        var aborted = false
        val cancellation = CancellationException("cancel cache write")
        val brokenCache = failingCommitCache(diskCache, cancellation) { aborted = true }
        val thrown = assertFailsWith<CancellationException> {
            decoder(SVG_A, options(Size(120, 80), "cancelled"), brokenCache, countingRenderer {}).decode()
        }
        assertSame(cancellation, thrown)
        assertTrue(aborted)
    }

    @Test
    fun `corrupted PNG is replaced by a fresh render`() = withDiskCache { diskCache ->
        val options = options(Size(120, 80), "corrupt")
        var renders = 0
        val render = countingRenderer { renders++ }
        decoder(SVG_A, options, diskCache, render).decode()
        val editor = assertNotNull(diskCache.openEditor(createRenderCacheKey(SVG_A, options)))
        diskCache.fileSystem.write(editor.data) { writeUtf8("not a PNG") }
        editor.commit()
        decoder(SVG_A, options, diskCache, render).decode()
        decoder(SVG_A, options, diskCache, render).decode()
        assertEquals(2, renders)
    }

    @Test
    fun `render failures are not swallowed by cache fallback`() = withDiskCache { diskCache ->
        val failure = IOException("render failed")
        val thrown = assertFailsWith<IOException> {
            decoder(SVG_A, options(Size(120, 80), "render-error"), diskCache) { _, _, _ -> throw failure }.decode()
        }
        assertSame(failure, thrown)
    }

    private fun failingCommitCache(
        diskCache: DiskCache,
        failure: Throwable,
        onAbort: () -> Unit,
    ): DiskCache = object : DiskCache by diskCache {
        override fun openEditor(key: String): DiskCache.Editor? {
            val editor = diskCache.openEditor(key) ?: return null
            return object : DiskCache.Editor by editor {
                override fun commit() = throw failure
                override fun abort() {
                    onAbort()
                    editor.abort()
                }
            }
        }
    }

    private fun decoder(
        svg: ByteArray,
        options: Options,
        diskCache: DiskCache?,
        render: suspend (ByteArray, Options, Boolean) -> RenderedSvgImage,
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
    ): suspend (ByteArray, Options, Boolean) -> RenderedSvgImage = { svg, options, encodePng ->
        onRender()
        renderSvgImageWithCache(svg, options, encodePng)
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
        val SVG_A = """<svg xmlns="http://www.w3.org/2000/svg" width="30" height="20">
            <rect width="30" height="20" fill="red"/>
        </svg>""".encodeToByteArray()
        val SVG_B = """<svg xmlns="http://www.w3.org/2000/svg" width="30" height="20">
            <rect width="30" height="20" fill="blue"/>
        </svg>""".encodeToByteArray()
    }
}
