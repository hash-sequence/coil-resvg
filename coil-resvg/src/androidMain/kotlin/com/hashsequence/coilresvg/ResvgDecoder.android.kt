package com.hashsequence.coilresvg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.createBitmap
import coil3.BitmapImage
import coil3.Image
import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.request.Options
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual val PlatformContext.density: Float
    get() = resources.displayMetrics.density

internal actual fun encodeCachedBitmap(image: Image): ByteArray? {
    val bitmap = (image as? BitmapImage)?.bitmap ?: return null
    val output = ByteArrayOutputStream()
    return if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
        output.toByteArray()
    } else {
        null
    }
}

internal actual fun decodeCachedBitmap(bytes: ByteArray): Image? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImage()

actual suspend fun renderSvgImage(svgBytes: ByteArray, options: Options): DecodeResult =
    withContext(Dispatchers.Default) {
        val renderer = SvgRenderer.fromData(svgBytes)

        val svgSize = renderer.getSize()
        val renderSize = computeSvgRenderSize(svgSize.width, svgSize.height, options)

        val result = renderer.render(renderSize.width.toUInt(), renderSize.height.toUInt())

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
        bitmap.isPremultiplied = false
        bitmap.setPixels(pixels, 0, renderSize.width, 0, 0, renderSize.width, renderSize.height)
        bitmap.isPremultiplied = true

        DecodeResult(
            image = bitmap.asImage(),
            isSampled = true
        )
    }
