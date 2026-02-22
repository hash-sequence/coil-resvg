package com.hashsequence.coilresvg

import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo

internal actual val PlatformContext.density: Float
    get() = 1f

actual suspend fun renderSvgImage(svgBytes: ByteArray, options: Options): DecodeResult =
    withContext(Dispatchers.Default) {
        val renderer = SvgRenderer.fromData(svgBytes)

        val svgSize = renderer.getSize()
        val renderSize = computeSvgRenderSize(svgSize.width, svgSize.height, options)

        val result = renderer.render(renderSize.width.toUInt(), renderSize.height.toUInt())

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
        bitmap.installPixels(imageInfo, result.pixels, renderSize.width * 4)
        bitmap.setImmutable()

        DecodeResult(
            image = bitmap.asImage(),
            isSampled = true
        )
    }
