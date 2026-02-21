package com.hashsequence.coilresvg

import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo

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
        
        // tiny-skia 输出 premultiplied alpha RGBA，必须标记为 PREMUL
        val imageInfo = ImageInfo(
            width = renderSize.width,
            height = renderSize.height,
            colorInfo = org.jetbrains.skia.ColorInfo(
                colorType = org.jetbrains.skia.ColorType.RGBA_8888,
                alphaType = ColorAlphaType.PREMUL,
                colorSpace = null
            )
        )
        
        val bitmap = Bitmap()
        // 直接安装像素数据，避免中间转换和复制
        bitmap.installPixels(imageInfo, result.pixels, renderSize.width * 4)
        bitmap.setImmutable()

        DecodeResult(
            image = bitmap.asImage(),
            isSampled = true
        )
    }
