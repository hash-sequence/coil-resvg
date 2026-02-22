package com.hashsequence.coilresvg.example

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coilresvgproject.composeapp.generated.resources.Res
import coilresvgproject.composeapp.generated.resources.blend_paintorder_marker
import coilresvgproject.composeapp.generated.resources.flower_mandala
import coilresvgproject.composeapp.generated.resources.test_inner_image
import coilresvgproject.composeapp.generated.resources.test_shadow_blur
import coilresvgproject.composeapp.generated.resources.test_text
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun PerformanceComparisonApp() {
    var shouldLoadImages by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    val context = LocalPlatformContext.current

    val baseImageLoader = SingletonImageLoader.get(context)

    val coilSvgLoader = remember(baseImageLoader) {
        ImageLoader.Builder(context)
            .components {
                add(DrawableResourceFetcher.Factory())
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

    val resvgLoader = remember(baseImageLoader) {
        ImageLoader.Builder(context)
            .components {
                add(DrawableResourceFetcher.Factory())
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

    val testSvgs = remember {
        listOf(
            Pair(Res.drawable.test_text, "Text\nEmoji & CJK"),
            Pair(Res.drawable.flower_mandala, "<use> clone\n& gradient"),
            Pair(Res.drawable.test_shadow_blur, "Shadow & blur\nfilter chain"),
            Pair(Res.drawable.test_inner_image, "Embedded\nbase64 image"),
            Pair(Res.drawable.blend_paintorder_marker, "Blend, marker\n& paint-order"),
        )
    }

    MaterialTheme {
        BoxWithConstraints(
            modifier = Modifier
                .background(Color(0xFFF5F5F5))
                .safeContentPadding()
                .fillMaxSize()
        ) {
            // 表头约 80dp，副标题约 30dp，留给 5 行卡片的高度
            // 每行卡片额外占用约 8dp（Card padding），label 宽 60dp
            val availableHeight = maxHeight
            val headerHeight = 80.dp
            val imageSize = min((availableHeight - headerHeight) / 5 - 8.dp, 150.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = "Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray,
                        modifier = Modifier.width(80.dp),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Coil-SVG",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

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

                    Text(
                        text = "Resvg",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = "Memory cache disabled, forcing re-render",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    textAlign = TextAlign.Center
                )

                if (shouldLoadImages) {

                    key(reloadKey) {
                        testSvgs.forEach { (svg, label) ->
                            ComparisonCard(
                                svgResource = svg,
                                label = label,
                                coilSvgLoader = coilSvgLoader,
                                resvgLoader = resvgLoader,
                                imageSize = imageSize
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DecoderImageColumn(
    svgResource: DrawableResource,
    decoderType: String,
    labelColor: Color,
    imageLoader: ImageLoader,
    contentDescription: String,
    imageSize: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalPlatformContext.current
    val modelKey = svgResource.hashCode().toString()

    val request = remember(svgResource) {
        ImageRequest.Builder(context)
            .data(svgResource)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .apply { extras[ModelKeyExtra] = modelKey }
            .build()
    }

    val decodeTime = PerformanceTracker.get(decoderType, modelKey)

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

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            DecodeTimeLabel(decodeTime = decodeTime, color = labelColor)
        }
    }
}

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
    svgResource: DrawableResource,
    label: String,
    coilSvgLoader: ImageLoader,
    resvgLoader: ImageLoader,
    imageSize: Dp = 150.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center
        )

        Card(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DecoderImageColumn(
                    svgResource = svgResource,
                    decoderType = "coil-svg",
                    labelColor = Color(0xFF2196F3),
                    imageLoader = coilSvgLoader,
                    contentDescription = "Coil-SVG",
                    imageSize = imageSize,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                DecoderImageColumn(
                    svgResource = svgResource,
                    decoderType = "resvg",
                    labelColor = Color(0xFF4CAF50),
                    imageLoader = resvgLoader,
                    contentDescription = "Resvg",
                    imageSize = imageSize,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }


}
