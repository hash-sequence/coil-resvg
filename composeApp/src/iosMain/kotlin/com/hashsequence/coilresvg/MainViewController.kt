package com.hashsequence.coilresvg

import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.util.DebugLogger

@OptIn(ExperimentalCoilApi::class)
fun MainViewController() = ComposeUIViewController {
    // Configure iOS platform ImageLoader
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(ResvgDecoder.Factory())
            }
            .logger(DebugLogger()) // Add logger for debugging
            .build()
    }
    
    App()
}