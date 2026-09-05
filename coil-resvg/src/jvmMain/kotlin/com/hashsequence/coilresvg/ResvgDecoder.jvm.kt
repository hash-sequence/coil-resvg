package com.hashsequence.coilresvg

import coil3.Image
import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo

internal actual val PlatformContext.density: Float
    get() = 1f

internal actual fun decodeCachedBitmap(bytes: ByteArray): Image? {
    val skiaImage = SkiaImage.makeFromEncoded(bytes)
    return try {
        Bitmap.makeFromImage(skiaImage).apply { setImmutable() }.asImage()
    } finally {
        skiaImage.close()
    }
}

actual suspend fun renderSvgImage(svgBytes: ByteArray, options: Options): DecodeResult =
    renderSvgImageWithCache(svgBytes, options, false).decodeResult

internal actual suspend fun renderSvgImageWithCache(
    svgBytes: ByteArray,
    options: Options,
    encodePng: Boolean,
): RenderedSvgImage =
    kotlinx.coroutines.runInterruptible(Dispatchers.Default) {
        val renderer = SvgRenderer.fromData(svgBytes)

        val svgSize = renderer.getSize()
        val renderSize = computeSvgRenderSize(svgSize.width, svgSize.height, options)

        val rendered = renderer.renderWithCache(
            renderSize.width.toUInt(), renderSize.height.toUInt(), encodePng,
        )
        val result = rendered.image

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

        RenderedSvgImage(
            decodeResult = DecodeResult(image = bitmap.asImage(), isSampled = true),
            pngBytes = rendered.png,
        )
    }
