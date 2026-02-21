package com.hashsequence.coilresvg

import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.request.Options
import kotlinx.coroutines.await
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.ColorInfo
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.Image
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlinx.browser.document
import org.khronos.webgl.Int8Array
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal actual val PlatformContext.density: Float
    get() = 1f

actual suspend fun renderSvgImage(svgBytes: ByteArray, options: Options): DecodeResult {
    val svgString = svgBytes.decodeToString()

    val blob = Blob(arrayOf(svgString), BlobPropertyBag(type = "image/svg+xml"))
    val blobUrl = URL.createObjectURL(blob)

    try {
        val img = loadImage(blobUrl)

        val svgWidth = if (img.naturalWidth > 0) img.naturalWidth.toFloat() else 100f
        val svgHeight = if (img.naturalHeight > 0) img.naturalHeight.toFloat() else 100f

        val renderSize = computeSvgRenderSize(svgWidth, svgHeight, options)

        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = renderSize.width
        canvas.height = renderSize.height

        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D

        ctx.drawImage(img, 0.0, 0.0, renderSize.width.toDouble(), renderSize.height.toDouble())

        val imageData =
            ctx.getImageData(0.0, 0.0, renderSize.width.toDouble(), renderSize.height.toDouble())

        val pixelBytes = Int8Array(imageData.data.buffer).unsafeCast<ByteArray>()

        val imageInfo = ImageInfo(
            width = renderSize.width,
            height = renderSize.height,
            colorInfo = ColorInfo(
                colorType = ColorType.RGBA_8888,
                alphaType = ColorAlphaType.UNPREMUL,
                colorSpace = null
            )
        )

        val bitmap = Bitmap()

        bitmap.installPixels(imageInfo, pixelBytes, renderSize.width * 4)

        bitmap.setImmutable()
        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = true
        )
    } catch (e: Exception) {
        console.error(e)
        throw e
    } finally {
        URL.revokeObjectURL(blobUrl)
    }
}

private suspend fun loadImage(url: String): HTMLImageElement = suspendCoroutine { continuation ->
    val img = Image()
    var completed = false

    img.onload = {
        if (!completed) {
            completed = true
            continuation.resume(img)
        }
    }

    img.onerror = { event, _, _, _, _ ->
        if (!completed) {
            completed = true
            continuation.resumeWithException(
                IllegalStateException("Failed to load SVG image: $event")
            )
        }
    }

    img.src = url
}