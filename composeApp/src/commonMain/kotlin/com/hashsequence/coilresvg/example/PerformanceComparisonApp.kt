package com.hashsequence.coilresvg.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coilresvgproject.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
@Composable
fun PerformanceComparisonApp() {
    var shouldLoadImages by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    val context = LocalPlatformContext.current

    // Get the base ImageLoader with platform-specific components (e.g. FileFetcher on JVM)
    val baseImageLoader = SingletonImageLoader.get(context)

    // Create ImageLoader using Coil-SVG
    val coilSvgLoader = remember(baseImageLoader) {
        ImageLoader.Builder(context)
            .components {
                add(PerformanceLoggingSvgDecoder.Factory())
                @Suppress("UNCHECKED_CAST")
                baseImageLoader.components.fetcherFactories.forEach { (factory, type) ->
                    add(
                        factory as coil3.fetch.Fetcher.Factory<Any>,
                        type as kotlin.reflect.KClass<Any>
                    )
                }
            }
            .build()
    }

    // Create ImageLoader using Resvg
    val resvgLoader = remember(baseImageLoader) {
        ImageLoader.Builder(context)
            .components {
                add(PerformanceLoggingResvgDecoder.Factory())
                @Suppress("UNCHECKED_CAST")
                baseImageLoader.components.fetcherFactories.forEach { (factory, type) ->
                    add(
                        factory as coil3.fetch.Fetcher.Factory<Any>,
                        type as kotlin.reflect.KClass<Any>
                    )
                }
            }
            .build()
    }

    val testImages = remember {
        listOf(
            Res.getUri("drawable/test_icon.svg"),
            Res.getUri("drawable/flower_mandala.svg"),
            Res.getUri("drawable/test_inner_image.svg"),
            Res.getUri("drawable/advanced_filters_stress.svg"),
            Res.getUri("drawable/cosmic_nightscape.svg")
        )
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header and button in one row: Coil-SVG | Button | Resvg
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (shouldLoadImages) {
                    Text(
                        text = "Coil-SVG",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Button(
                    onClick = {
                        if (shouldLoadImages) {
                            reloadKey++
                        } else {
                            shouldLoadImages = true
                        }
                    }
                ) {
                    Text(if (shouldLoadImages) "Test Again" else "Start Test")
                }

                if (shouldLoadImages) {
                    Text(
                        text = "Resvg",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Hint text
            Text(
                text = "Memory cache disabled, forcing re-render",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                textAlign = TextAlign.Center
            )

            if (shouldLoadImages) {

                key(reloadKey) {
                    testImages.forEach { imageUrl ->
                        ComparisonCard(
                            imageUrl = imageUrl,
                            coilSvgLoader = coilSvgLoader,
                            resvgLoader = resvgLoader
                        )
                    }
                }
            }
        }
    }
}

/**
 * Image display column for a single decoder, including label, image, and decode time display
 */
@Composable
fun DecoderImageColumn(
    modelUrl: String,
    decoderType: String,
    labelColor: Color,
    imageLoader: ImageLoader,
    contentDescription: String,
    imageSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalPlatformContext.current

    // Build ImageRequest, write modelUrl to extras for Decoder recognition
    val request = remember(modelUrl) {
        ImageRequest.Builder(context)
            .data(modelUrl)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .apply { extras[ModelKeyExtra] = modelUrl }
            .build()
    }

    // Read decode time from PerformanceTracker (Compose can directly observe mutableStateMapOf changes)
    val decodeTime = PerformanceTracker.get(decoderType, modelUrl)

    Box(
        modifier = modifier
    ) {
        SubcomposeAsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            modifier = Modifier.size(imageSize),
            contentScale = ContentScale.Fit,
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Error",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red
                    )
                }
            }
        )

        // Decode time display in bottom right corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
        ) {
            DecodeTimeLabel(decodeTime = decodeTime, color = labelColor)
        }
    }
}

/**
 * Decode time label
 */
@Composable
fun DecodeTimeLabel(decodeTime: Long?, color: Color) {
    Box(
        modifier = Modifier
            .background(
                color = color,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (decodeTime != null) "${decodeTime}ms" else "...",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun ComparisonCard(
    imageUrl: String,
    coilSvgLoader: ImageLoader,
    resvgLoader: ImageLoader
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Coil-SVG
                DecoderImageColumn(
                    modelUrl = imageUrl,
                    decoderType = "coil-svg",
                    labelColor = Color(0xFF2196F3),
                    imageLoader = coilSvgLoader,
                    contentDescription = "Coil-SVG",
                    imageSize = 150.dp,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Resvg
                DecoderImageColumn(
                    modelUrl = imageUrl,
                    decoderType = "resvg",
                    labelColor = Color(0xFF4CAF50),
                    imageLoader = resvgLoader,
                    contentDescription = "Resvg",
                    imageSize = 150.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
