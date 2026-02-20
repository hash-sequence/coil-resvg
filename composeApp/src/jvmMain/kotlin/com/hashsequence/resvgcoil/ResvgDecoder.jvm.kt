package com.hashsequence.resvgcoil

import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JVM/Desktop platform screen density (default 1.0, no scaling)
 */
internal actual val PlatformContext.density: Float
    get() = 1f

/**
 * JVM/Desktop platform implementation: Render SVG using Resvg (using Skia)
 */
actual suspend fun renderSvgImage(svgBytes: ByteArray, options: Options): DecodeResult = 
    kotlinx.coroutines.runInterruptible(Dispatchers.Default) {
        // Use Rust resvg to render SVG
        val renderer = uniffi.resvg_core.SvgRenderer.fromData(svgBytes)
        
        // Get SVG original size and calculate render size (maintain aspect ratio)
        val svgSize = renderer.getSize()
        val renderSize = computeSvgRenderSize(svgSize.width, svgSize.height, options)
        
        // Render SVG
        val result = renderer.render(renderSize.width.toUInt(), renderSize.height.toUInt())
        
        // Create Skia Bitmap
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeS32(renderSize.width, renderSize.height, ColorAlphaType.PREMUL))
        
        // Convert pixel data from RGBA to ARGB format
        val pixels = IntArray(renderSize.width * renderSize.height)
        for (i in pixels.indices) {
            val baseIdx = i * 4
            val r = result.pixels[baseIdx].toInt() and 0xFF
            val g = result.pixels[baseIdx + 1].toInt() and 0xFF
            val b = result.pixels[baseIdx + 2].toInt() and 0xFF
            val a = result.pixels[baseIdx + 3].toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        
        // Install pixel data to bitmap
        val buffer = ByteBuffer.allocate(pixels.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        pixels.forEach { buffer.putInt(it) }
        bitmap.installPixels(bitmap.imageInfo, buffer.array(), renderSize.width * 4)

        DecodeResult(
            image = bitmap.asImage(),
            isSampled = true
        )
    }
