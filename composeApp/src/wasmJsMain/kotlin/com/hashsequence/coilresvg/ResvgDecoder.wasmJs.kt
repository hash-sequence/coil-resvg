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

// External JS function for URI encoding
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(str) => encodeURIComponent(str)")
private external fun encodeURIComponent(str: String): String

// Helper function to get pixel data buffer as Int8Array
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(imageData) => new Int8Array(imageData.data.buffer)")
private external fun getPixelBytesAsInt8Array(imageData: ImageData): JsAny

// Helper to get byte at specific index from Int8Array
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(int8Array, index) => int8Array[index]")
private external fun getByteAt(int8Array: JsAny, index: Int): Byte

// Console logging for debugging
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(msg) => console.log(msg)")
private external fun consoleLog(message: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(msg) => console.error(msg)")
private external fun consoleError(message: String)

// Get current timestamp for performance measurement
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => performance.now()")
private external fun performanceNow(): Double

/**
 * WasmJS platform screen density (default 1.0, no scaling)
 */
internal actual val PlatformContext.density: Float
    get() = 1f

/**
 * WasmJS platform implementation: Render SVG using browser's native SVG engine via HTML Image element
 * 
 * This uses the HTML <img> element to load and render SVG, which is more compatible and feature-complete
 * than Skia's SVGDOM implementation. The browser's native SVG renderer supports more SVG features.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual suspend fun renderSvgImage(svgBytes: ByteArray, options: Options): DecodeResult {
    val startTime = performanceNow()
    val decodeStartTime = performanceNow()
    val svgString = svgBytes.decodeToString()
    val decodeTime = performanceNow() - decodeStartTime
    val svgId = "[${svgString.take(50).hashCode()}]"
    
    consoleLog("$svgId === renderSvgImage START === bytes: ${svgBytes.size}")
    consoleLog("$svgId SVG string length: ${svgString.length}, preview: ${svgString.take(100)}")
    consoleLog("$svgId Options size: ${options.size}, scale: ${options.scale}")
    consoleLog("$svgId ⏱️ String decode time: ${decodeTime.format()}ms")
    
    // Parse viewBox from SVG to get actual dimensions
    val parseStartTime = performanceNow()
    val viewBoxDimensions = parseSvgViewBox(svgString)
    val parseTime = performanceNow() - parseStartTime
    consoleLog("$svgId Parsed viewBox: ${viewBoxDimensions?.let { "${it.first} x ${it.second}" } ?: "none"}")
    consoleLog("$svgId ⏱️ ViewBox parse time: ${parseTime.format()}ms")
    
    val encodeStartTime = performanceNow()
    val encodedSvg = encodeURIComponent(svgString)
    val dataUrl = "data:image/svg+xml;charset=utf-8,$encodedSvg"
    val encodeTime = performanceNow() - encodeStartTime
    consoleLog("$svgId Data URL created, length: ${dataUrl.length}")
    consoleLog("$svgId ⏱️ URL encode time: ${encodeTime.format()}ms")
    
    try {
        // Load SVG using HTML Image element
        consoleLog("$svgId Loading image...")
        val loadStartTime = performanceNow()
        val img = loadImage(dataUrl, svgId)
        val loadTime = performanceNow() - loadStartTime
        consoleLog("$svgId Image loaded successfully")
        consoleLog("$svgId ⏱️ Image load time: ${loadTime.format()}ms")
        consoleLog("$svgId Image natural size: ${img.naturalWidth} x ${img.naturalHeight}")
        consoleLog("$svgId Parsed viewBox: ${viewBoxDimensions?.let { "${it.first} x ${it.second}" } ?: "none"}")
        
        // Use natural dimensions (browser already handles viewBox correctly)
        val svgWidth = if (img.naturalWidth > 0) img.naturalWidth.toFloat() else 100f
        val svgHeight = if (img.naturalHeight > 0) img.naturalHeight.toFloat() else 100f
        consoleLog("$svgId Using dimensions for scaling: $svgWidth x $svgHeight")
        
        // Calculate render size (maintain aspect ratio)
        val renderSize = computeSvgRenderSize(svgWidth, svgHeight, options)
        consoleLog("$svgId Computed render size: ${renderSize.width} x ${renderSize.height}")
        
        // Convert Image to pixel data via Canvas
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = renderSize.width
        canvas.height = renderSize.height
        consoleLog("$svgId Canvas created: ${canvas.width} x ${canvas.height}")
        
        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
        
        // Draw entire SVG image to canvas (let browser handle viewBox)
        consoleLog("$svgId Drawing image to canvas: ${renderSize.width} x ${renderSize.height}")
        val drawStartTime = performanceNow()
        ctx.drawImage(img, 0.0, 0.0, renderSize.width.toDouble(), renderSize.height.toDouble())
        val drawTime = performanceNow() - drawStartTime
        consoleLog("$svgId ⏱️ Canvas draw time: ${drawTime.format()}ms")
        
        // Extract pixel data (RGBA format from Canvas)
        val extractStartTime = performanceNow()
        val imageData = ctx.getImageData(0.0, 0.0, renderSize.width.toDouble(), renderSize.height.toDouble())
        val extractTime = performanceNow() - extractStartTime
        consoleLog("$svgId ⏱️ Pixel data extract time: ${extractTime.format()}ms")
        
        val convertStartTime = performanceNow()
        
        // 关键优化：单层循环复制 + 直接使用 RGBA 格式
        // 从 ImageData 获取 Int8Array，然后通过单层循环复制到 Kotlin ByteArray
        val totalBytes = renderSize.width * renderSize.height * 4
        val pixelBytes = ByteArray(totalBytes)
        val int8Array = getPixelBytesAsInt8Array(imageData)
        
        // 单层循环复制，比之前的双层循环快很多
        for (i in 0 until totalBytes) {
            pixelBytes[i] = getByteAt(int8Array, i)
        }

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

        val bitmapStartTime = performanceNow()
        val bitmap = Bitmap()
        
        // 将字节直接安装到 Bitmap (installPixels 比 allocPixels + copy 更快)
        bitmap.installPixels(imageInfo, pixelBytes, renderSize.width * 4)
        
        // 调用 setImmutable 让 Skia 锁定数据，确保数据安全
        bitmap.setImmutable()

        val convertTime = performanceNow() - convertStartTime
        val bitmapTime = performanceNow() - bitmapStartTime
        
        consoleLog("$svgId ⏱️ Pixel setup (Single-Loop) time: ${convertTime.format()}ms")
        consoleLog("$svgId ⏱️ Bitmap ready time: ${bitmapTime.format()}ms")
        
        val totalTime = performanceNow() - startTime
        consoleLog("$svgId ⏱️⏱️⏱️ TOTAL RENDER TIME: ${totalTime.format()}ms ⏱️⏱️⏱️")
        consoleLog("$svgId === renderSvgImage SUCCESS ===")
        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = true
        )
    } catch (e: Exception) {
        val errorMsg = "$svgId === ERROR === ${e.message ?: e.toString()}"
        consoleLog(errorMsg)
        consoleError(e.stackTraceToString())
        throw e
    } finally {
        // Data URL doesn't need cleanup
    }
}

/**
 * Load an image from a URL and return it as an HTMLImageElement.
 * Suspends until the image is fully loaded.
 */
private suspend fun loadImage(url: String, svgId: String): HTMLImageElement = suspendCoroutine { continuation ->
    val img = document.createElement("img") as HTMLImageElement
    
    var completed = false
    
    img.addEventListener("load", {
        if (!completed) {
            completed = true
            consoleLog("$svgId IMG load event fired")
            continuation.resume(img)
        }
    })
    
    img.addEventListener("error", {
        if (!completed) {
            completed = true
            consoleLog("$svgId IMG error event fired")
            continuation.resumeWithException(
                IllegalStateException("Failed to load SVG image")
            )
        }
    })
    
    consoleLog("$svgId Setting img.src, URL length: ${url.length}")
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
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(num) => num.toFixed(2)")
private external fun formatDouble(num: Double): String

private fun Double.format(): String = formatDouble(this)
