package com.hashsequence.coilresvg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.crossfade
import org.jetbrains.compose.resources.ExperimentalResourceApi

import coilresvg.composeapp.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
@Composable
fun PerformanceComparisonApp() {
    var shouldLoadImages by remember { mutableStateOf(false) }
    
    val context = LocalPlatformContext.current
    
    // Test Icon - 始终显示
    val testIcon = remember { Res.getUri("drawable/test_icon.svg") }
    
    // 创建使用 Coil-SVG 的 ImageLoader
    val coilSvgLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(PerformanceLoggingSvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
    
    // 创建使用 Resvg 的 ImageLoader
    val resvgLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(ResvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
    
    // 测试图片列表
    val testImages = remember {
        listOf(
            Res.getUri("drawable/geometric_pattern.svg"),
            Res.getUri("drawable/flower_mandala.svg"),
            Res.getUri("drawable/abstract_waves.svg"),
            Res.getUri("drawable/hexagon_grid.svg")
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
            Text(
                text = "SVG 解码器性能对比",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
            
            Text(
                text = "Coil-SVG vs Resvg",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Test Icon 对比展示 - 始终显示
            Text(
                text = "Test Icon (始终显示)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Coil-SVG
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Coil-SVG",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    SubcomposeAsyncImage(
                        model = testIcon,
                        imageLoader = coilSvgLoader,
                        contentDescription = "Test Icon - Coil-SVG",
                        modifier = Modifier.size(100.dp),
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
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Resvg
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Resvg",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    SubcomposeAsyncImage(
                        model = testIcon,
                        imageLoader = resvgLoader,
                        contentDescription = "Test Icon - Resvg",
                        modifier = Modifier.size(100.dp),
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
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 其他图片的测试按钮
            Button(
                onClick = { shouldLoadImages = !shouldLoadImages },
                modifier = Modifier.padding(16.dp)
            ) {
                Text(if (shouldLoadImages) "清除其他图片" else "测试其他图片")
            }
            
            if (shouldLoadImages) {
                testImages.forEachIndexed { index, imageUrl ->
                    ComparisonCard(
                        imageUrl = imageUrl,
                        imageTitle = "SVG ${index + 1}",
                        coilSvgLoader = coilSvgLoader,
                        resvgLoader = resvgLoader
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun ComparisonCard(
    imageUrl: String,
    imageTitle: String,
    coilSvgLoader: ImageLoader,
    resvgLoader: ImageLoader
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = imageTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Coil-SVG
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Coil-SVG",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    SubcomposeAsyncImage(
                        model = imageUrl,
                        imageLoader = coilSvgLoader,
                        contentDescription = "$imageTitle - Coil-SVG",
                        modifier = Modifier.size(150.dp),
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
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Resvg
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Resvg",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    SubcomposeAsyncImage(
                        model = imageUrl,
                        imageLoader = resvgLoader,
                        contentDescription = "$imageTitle - Resvg",
                        modifier = Modifier.size(150.dp),
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
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "💡 查看控制台日志对比两者的解码耗时",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
