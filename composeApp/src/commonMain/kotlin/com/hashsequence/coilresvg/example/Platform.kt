package com.hashsequence.coilresvg.example

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
