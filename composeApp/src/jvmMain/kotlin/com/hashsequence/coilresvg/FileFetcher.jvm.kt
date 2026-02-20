package com.hashsequence.coilresvg

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem
import java.io.File
import java.net.URL

/**
 * JVM platform file Fetcher, supports file:// and jar:file:// protocol for local file loading
 * 
 * This Fetcher resolves the issue of Coil 3 being unable to load local files in JVM desktop applications
 * Reference: https://github.com/coil-kt/coil/issues/2833
 */
class FileFetcher(
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val uriString = uri.toString()

        // Read resource data
        val data = when {
            // Handle jar:file:// protocol (resources in JAR)
            uriString.startsWith("jar:") -> {
                val url = URL(uriString)
                url.openStream().use { it.readBytes() }
            }
            // Handle file:// protocol (local file system)
            uri.scheme == "file" -> {
                val filePath = uriString
                    .removePrefix("file://")
                    .removePrefix("file:")
                    // Windows path handling: ensure /F:/ converts to F:/
                    .let { path ->
                        if (path.startsWith("/") && path.length > 2 && path[2] == ':') {
                            path.substring(1)
                        } else {
                            path
                        }
                    }

                val file = File(filePath)
                if (!file.exists() || !file.isFile) {
                    throw IllegalStateException("File does not exist or is not a file: $filePath")
                }
                file.readBytes()
            }
            // Handle direct file path
            else -> {
                val filePath = uri.path
                    ?: throw IllegalStateException("Invalid file path: $uriString")
                val file = File(filePath)
                if (!file.exists() || !file.isFile) {
                    throw IllegalStateException("File does not exist or is not a file: $filePath")
                }
                file.readBytes()
            }
        }

        // Create Buffer and write data
        val buffer = Buffer()
        buffer.write(data)

        // Create ImageSource
        val imageSource = ImageSource(
            source = buffer,
            fileSystem = FileSystem.SYSTEM
        )

        // Infer MIME type from file extension
        val mimeType = when {
            uriString.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
            uriString.endsWith(".png", ignoreCase = true) -> "image/png"
            uriString.endsWith(".jpg", ignoreCase = true) || 
            uriString.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            uriString.endsWith(".gif", ignoreCase = true) -> "image/gif"
            uriString.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> null
        }

        return SourceFetchResult(
            source = imageSource,
            mimeType = mimeType,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val uriString = data.toString()
            // Handle jar:, file:// protocol and direct file path
            return when {
                uriString.startsWith("jar:") -> FileFetcher(data, options)
                uriString.startsWith("file:") -> FileFetcher(data, options)
                data.scheme == null && data.path?.isNotEmpty() == true -> FileFetcher(data, options)
                else -> null
            }
        }
    }
}
