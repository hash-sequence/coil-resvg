package com.hashsequence.coilresvg

import coil3.PlatformContext
import coil3.decode.DecodeResult
import coil3.request.Options
import coil3.toImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo

/**
 * iOS platform screen density (default 1.0, no scaling)
 */
internal actual val PlatformContext.density: Float
    get() = 1f

/**
 * iOS platform implementation: Render SVG using Resvg (using Skia)
 */
actual suspend fun renderSvgImage(svgBytes: ByteArray, options: Options): DecodeResult = 
    withContext(Dispatchers.Default) {
        // Use Rust resvg to render SVG
        val renderer = uniffi.resvg_core.SvgRenderer.fromData(svgBytes)
        
        // Get SVG original size and calculate render size (maintain aspect ratio)
        val svgSize = renderer.getSize()
        val renderSize = computeSvgRenderSize(svgSize.width, svgSize.height, options)
        
        // Render SVG
        val result = renderer.render(renderSize.width.toUInt(), renderSize.height.toUInt())
        
        // Create Skia Bitmap and convert pixel data (RGBA -> ARGB)
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeS32(renderSize.width, renderSize.height, ColorAlphaType.PREMUL))
        
        val pixels = IntArray(renderSize.width * renderSize.height)
        for (i in pixels.indices) {
            val baseIdx = i * 4
            val r = result.pixels[baseIdx].toInt() and 0xFF
            val g = result.pixels[baseIdx + 1].toInt() and 0xFF
            val b = result.pixels[baseIdx + 2].toInt() and 0xFF
            val a = result.pixels[baseIdx + 3].toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        
        val pixelBytes = ByteArray(pixels.size * 4)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val base = i * 4
            pixelBytes[base] = (pixel and 0xFF).toByte()
            pixelBytes[base + 1] = ((pixel shr 8) and 0xFF).toByte()
            pixelBytes[base + 2] = ((pixel shr 16) and 0xFF).toByte()
            pixelBytes[base + 3] = ((pixel shr 24) and 0xFF).toByte()
        }
        bitmap.installPixels(bitmap.imageInfo, pixelBytes, renderSize.width * 4)

        DecodeResult(
            image = bitmap.toImage(),
            isSampled = true // SVG is vector format, can be re-decoded at higher resolution
        )
    }
