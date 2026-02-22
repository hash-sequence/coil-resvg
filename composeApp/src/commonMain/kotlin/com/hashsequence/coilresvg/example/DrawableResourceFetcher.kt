package com.hashsequence.coilresvg.example

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * A Coil [Fetcher] that reads bytes from a Compose [DrawableResource].
 *
 * This allows passing `DrawableResource` directly as `ImageRequest.data`,
 * e.g. `ImageRequest.Builder(context).data(Res.drawable.my_svg).build()`.
 */
@OptIn(ExperimentalResourceApi::class)
class DrawableResourceFetcher(
    private val resource: DrawableResource,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val environment = getSystemResourceEnvironment()
        val bytes = getDrawableResourceBytes(environment, resource)
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(bytes) },
                fileSystem = options.fileSystem,
            ),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<DrawableResource> {
        override fun create(
            data: DrawableResource,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return DrawableResourceFetcher(data, options)
        }
    }
}
