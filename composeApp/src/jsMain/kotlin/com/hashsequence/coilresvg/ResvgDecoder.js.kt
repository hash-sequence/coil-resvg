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

/**
 * JS platform screen density (default 1.0, no scaling)
 */
internal actual val PlatformContext.density: Float
    get() = 1f

/**
 * JS platform implementation: Render SVG using browser's native SVG engine via HTML Image element
 * 
 * This uses the HTML <img> element to load and render SVG, which is more compatible than createImageBitmap.
 */
actual suspend fun renderSvgImage(svgBytes: ByteArray, options: Options): DecodeResult {
    val startTime = kotlin.js.Date.now()
    console.log("[JS SVG] === renderSvgImage START === bytes: ${svgBytes.size}")
    
    // Create SVG Blob and Blob URL
    val decodeStartTime = kotlin.js.Date.now()
    val svgString = svgBytes.decodeToString()
    val decodeTime = kotlin.js.Date.now() - decodeStartTime

    // Parse viewBox from SVG to get actual dimensions
    val parseStartTime = kotlin.js.Date.now()
    val viewBoxDimensions = parseSvgViewBox(svgString)
    val parseTime = kotlin.js.Date.now() - parseStartTime

    val blobStartTime = kotlin.js.Date.now()
    val blob = Blob(arrayOf(svgString), BlobPropertyBag(type = "image/svg+xml"))
    val blobUrl = URL.createObjectURL(blob)
    val blobTime = kotlin.js.Date.now() - blobStartTime

    try {
        // Load SVG using HTML Image element
        console.log("[JS SVG] Loading image...")
        val loadStartTime = kotlin.js.Date.now()
        val img = loadImage(blobUrl)
        val loadTime = kotlin.js.Date.now() - loadStartTime

        // Use natural dimensions (browser already handles viewBox correctly)
        val svgWidth = if (img.naturalWidth > 0) img.naturalWidth.toFloat() else 100f
        val svgHeight = if (img.naturalHeight > 0) img.naturalHeight.toFloat() else 100f
        console.log("[JS SVG] Using dimensions for scaling: $svgWidth x $svgHeight")
        
        // Calculate render size (maintain aspect ratio)
        val renderSize = computeSvgRenderSize(svgWidth, svgHeight, options)
        console.log("[JS SVG] Computed render size: ${renderSize.width} x ${renderSize.height}")
        
        // Convert Image to pixel data via Canvas
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = renderSize.width
        canvas.height = renderSize.height
        console.log("[JS SVG] Canvas created: ${canvas.width} x ${canvas.height}")
        
        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
        
        // Draw entire SVG image to canvas (let browser handle viewBox)
        console.log("[JS SVG] Drawing image to canvas: ${renderSize.width} x ${renderSize.height}")
        val drawStartTime = kotlin.js.Date.now()
        ctx.drawImage(img, 0.0, 0.0, renderSize.width.toDouble(), renderSize.height.toDouble())
        val drawTime = kotlin.js.Date.now() - drawStartTime

        // Extract pixel data (RGBA format from Canvas)
        val extractStartTime = kotlin.js.Date.now()
        val imageData = ctx.getImageData(0.0, 0.0, renderSize.width.toDouble(), renderSize.height.toDouble())
        val extractTime = kotlin.js.Date.now() - extractStartTime

        val convertStartTime = kotlin.js.Date.now()
        
        // 关键优化：零拷贝操作
        // HTML Canvas 返回的是 RGBA 顺序的 Uint8ClampedArray
        // 创建 Int8Array 视图指向同一块内存，并强制转换为 Kotlin ByteArray
        val pixelBytes = Int8Array(imageData.data.buffer).unsafeCast<ByteArray>()

        // 配置 Skia ImageInfo
        // 显式告诉 Skia 数据格式是 RGBA_8888，这样 Skia 就能正确理解 Canvas 的数据
        val imageInfo = ImageInfo(
            width = renderSize.width,
            height = renderSize.height,
            colorInfo = ColorInfo(
                colorType = ColorType.RGBA_8888, // 直接使用 RGBA 格式
                alphaType = ColorAlphaType.UNPREMUL, // Canvas 通常返回未预乘的 Alpha
                colorSpace = null
            )
        )

        val bitmapStartTime = kotlin.js.Date.now()
        val bitmap = Bitmap()
        
        // 将字节直接安装到 Bitmap (installPixels 比 allocPixels + copy 更快)
        bitmap.installPixels(imageInfo, pixelBytes, renderSize.width * 4)
        
        // 调用 setImmutable 让 Skia 锁定数据，确保数据安全
        bitmap.setImmutable()

        val convertTime = kotlin.js.Date.now() - convertStartTime
        val bitmapTime = kotlin.js.Date.now() - bitmapStartTime
        
        val totalTime = kotlin.js.Date.now() - startTime
        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = true
        )
    } catch (e: Exception) {
        console.log("[JS SVG] === ERROR === ${e.message ?: "Unknown error"}")
        console.error(e)
        throw e
    } finally {
        // Clean up Blob URL to free memory
        URL.revokeObjectURL(blobUrl)
    }
}

/**
 * Load an image from a URL and return it as an HTMLImageElement.
 * Suspends until the image is fully loaded.
 */
private suspend fun loadImage(url: String): HTMLImageElement = suspendCoroutine { continuation ->
    val img = Image()
    
    var completed = false
    
    img.onload = {
        if (!completed) {
            completed = true
            console.log("[JS SVG] IMG load event fired")
            continuation.resume(img)
        }
    }
    
    img.onerror = { event, _, _, _, _ ->
        if (!completed) {
            completed = true
            console.log("[JS SVG] IMG error event fired: $event")
            continuation.resumeWithException(
                IllegalStateException("Failed to load SVG image: ${event}")
            )
        }
    }
    
    console.log("[JS SVG] Setting img.src: $url")
    img.src = url
}

/**
 * Parse SVG viewBox attribute to get actual dimensions
 * Returns (width, height) from viewBox, or null if not found
 */
private fun parseSvgViewBox(svgString: String): Pair<Double, Double>? {
    // Try to find viewBox attribute: viewBox="x y width height"
    val viewBoxRegex = Regex("viewBox\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    val match = viewBoxRegex.find(svgString) ?: return null
    
    val values = match.groupValues[1].trim().split(Regex("\\s+|,"))
    if (values.size >= 4) {
        return try {
            val width = values[2].toDouble()
            val height = values[3].toDouble()
            Pair(width, height)
        } catch (e: Exception) {
            null
        }
    }
    return null
}

/**
 * Format time with 2 decimal places
 */
private fun Double.format(): String = this.asDynamic().toFixed(2) as String
