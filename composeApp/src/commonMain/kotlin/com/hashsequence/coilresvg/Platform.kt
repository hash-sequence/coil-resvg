package com.hashsequence.coilresvg

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform