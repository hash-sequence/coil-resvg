package com.hashsequence.coilresvg.example

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.hashsequence.coilresvg.ResvgDecoder

class CoilResvgApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(ResvgDecoder.Factory())
            }
            .crossfade(true)
            .logger(DebugLogger()) // Add logger for debugging
            .build()
    }
}
