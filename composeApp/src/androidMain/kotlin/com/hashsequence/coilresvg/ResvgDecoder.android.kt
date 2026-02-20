package com.hashsequence.coilresvg

import androidx.core.graphics.createBitmap
import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        
        // 优化：单次循环转换 RGBA -> ARGB，避免多次遍历
        val pixelCount = renderSize.width * renderSize.height
        val pixels = IntArray(pixelCount)
        
        // 单次循环完成转换 (RGBA byte array -> ARGB int array)
        for (i in 0 until pixelCount) {
            val baseIdx = i * 4
            val r = result.pixels[baseIdx].toInt() and 0xFF
            val g = result.pixels[baseIdx + 1].toInt() and 0xFF
            val b = result.pixels[baseIdx + 2].toInt() and 0xFF
            val a = result.pixels[baseIdx + 3].toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        
        // 创建 Bitmap 并设置像素
        val bitmap = createBitmap(renderSize.width, renderSize.height)
        bitmap.setPixels(pixels, 0, renderSize.width, 0, 0, renderSize.width, renderSize.height)

        DecodeResult(
            image = bitmap.asImage(),
            isSampled = true // SVG is vector format, can be re-decoded at higher resolution
        )
    }
