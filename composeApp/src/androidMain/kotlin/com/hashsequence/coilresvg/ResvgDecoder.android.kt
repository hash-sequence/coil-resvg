package com.hashsequence.coilresvg

import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap

/**
 * Android platform screen density
 */
internal actual val PlatformContext.density: Float
    get() = resources.displayMetrics.density

/**
 * Android platform implementation: Render SVG using Resvg
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
        // Convert RGBA pixel data to Android Bitmap (ARGB format)
        val bitmap = createBitmap(renderSize.width, renderSize.height)
        val pixels = IntArray(renderSize.width * renderSize.height)
        for (i in pixels.indices) {
            val baseIdx = i * 4
            val r = result.pixels[baseIdx].toInt() and 0xFF
            val g = result.pixels[baseIdx + 1].toInt() and 0xFF
            val b = result.pixels[baseIdx + 2].toInt() and 0xFF
            val a = result.pixels[baseIdx + 3].toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
            bitmap.setPixels(pixels, 0, renderSize.width, 0, 0, renderSize.width, renderSize.height)

        DecodeResult(
            image = bitmap.asImage(),
            isSampled = true // SVG is vector format, can be re-decoded at higher resolution
        )
    }
