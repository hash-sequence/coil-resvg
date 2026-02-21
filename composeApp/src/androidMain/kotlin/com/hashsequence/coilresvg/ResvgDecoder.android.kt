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
        val renderer = uniffi.resvg_core.SvgRenderer.fromData(svgBytes)
        
        val svgSize = renderer.getSize()
        val renderSize = computeSvgRenderSize(svgSize.width, svgSize.height, options)
        
        val result = renderer.render(renderSize.width.toUInt(), renderSize.height.toUInt())
        
        // tiny-skia 输出 premultiplied RGBA，只需重排通道为 ARGB
        val pixelCount = renderSize.width * renderSize.height
        val pixels = IntArray(pixelCount)
        for (i in 0 until pixelCount) {
            val baseIdx = i * 4
            val r = result.pixels[baseIdx].toInt() and 0xFF
            val g = result.pixels[baseIdx + 1].toInt() and 0xFF
            val b = result.pixels[baseIdx + 2].toInt() and 0xFF
            val a = result.pixels[baseIdx + 3].toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        
        val bitmap = createBitmap(renderSize.width, renderSize.height)
        // 数据已经是预乘的，先关闭预乘标记防止 setPixels 再次预乘
        bitmap.isPremultiplied = false
        bitmap.setPixels(pixels, 0, renderSize.width, 0, 0, renderSize.width, renderSize.height)
        // 恢复预乘标记，确保渲染正确
        bitmap.isPremultiplied = true

        DecodeResult(
            image = bitmap.asImage(),
            isSampled = true
        )
    }
