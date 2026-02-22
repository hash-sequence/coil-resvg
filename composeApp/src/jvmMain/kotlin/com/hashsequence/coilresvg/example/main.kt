package com.hashsequence.coilresvg.example

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.util.DebugLogger

@OptIn(ExperimentalCoilApi::class)
fun main() = application {
    // Configure JVM platform ImageLoader with only Fetchers
    // Decoders are added later in PerformanceComparisonApp
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components {
                // Add file system Fetcher to support local file loading (file:// protocol)
                add(FileFetcher.Factory())
                add(KtorNetworkFetcherFactory())
            }
            .logger(DebugLogger()) // Add logger for debugging
            .build()
    }
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "CoilResvg - Performance Comparison",
    ) {
        PerformanceComparisonApp()
    }
}
