package com.hashsequence.coilresvg

import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.request.Options
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.ColorInfo
import kotlinx.browser.document
import org.w3c.dom.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsAny

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(str) => encodeURIComponent(str)")
private external fun encodeURIComponent(str: String): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(imageData) => new Int8Array(imageData.data.buffer)")
private external fun getPixelBytesAsInt8Array(imageData: ImageData): JsAny

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(int8Array, index) => int8Array[index]")
private external fun getByteAt(int8Array: JsAny, index: Int): Byte

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(msg) => console.error(msg)")
private external fun consoleError(message: String)

internal actual val PlatformContext.density: Float
    get() = 1f

@OptIn(ExperimentalWasmJsInterop::class)
actual suspend fun renderSvgImage(svgBytes: ByteArray, options: Options): DecodeResult {
    val svgString = svgBytes.decodeToString()

    val encodedSvg = encodeURIComponent(svgString)
    val dataUrl = "data:image/svg+xml;charset=utf-8,$encodedSvg"

    try {
        val img = loadImage(dataUrl)

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

        val totalBytes = renderSize.width * renderSize.height * 4
        val pixelBytes = ByteArray(totalBytes)
        val int8Array = getPixelBytesAsInt8Array(imageData)

        for (i in 0 until totalBytes) {
            pixelBytes[i] = getByteAt(int8Array, i)
        }

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
        consoleError(e.stackTraceToString())
        throw e
    } finally {
    }
}

private suspend fun loadImage(url: String): HTMLImageElement =
    suspendCoroutine { continuation ->
        val img = document.createElement("img") as HTMLImageElement
        var completed = false

        img.addEventListener("load", {
            if (!completed) {
                completed = true
                continuation.resume(img)
            }
        })

        img.addEventListener("error", {
            if (!completed) {
                completed = true
                continuation.resumeWithException(
                    IllegalStateException("Failed to load SVG image")
                )
            }
        })

        img.src = url
    }