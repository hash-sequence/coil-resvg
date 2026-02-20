package com.hashsequence.resvgcoil

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.util.DebugLogger

@OptIn(ExperimentalCoilApi::class)
fun main() = application {
    // Configure JVM platform ImageLoader
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components {
                // Add file system Fetcher to support local file loading (file:// protocol)
                add(FileFetcher.Factory())
                add(KtorNetworkFetcherFactory())
                // Add Mock SVG Decoder
                add(ResvgDecoder.Factory())
            }
            .logger(DebugLogger()) // Add logger for debugging
            .build()
    }
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "ResvgCoil",
    ) {
        App()
    }
}