package com.hashsequence.coilresvg.example

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.util.DebugLogger
import com.hashsequence.coilresvg.ResvgDecoder

@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoilApi::class)
fun main() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(ResvgDecoder.Factory())
            }
            .logger(DebugLogger())
            .build()
    }
    
    ComposeViewport {
        PerformanceComparisonApp()
    }
}
