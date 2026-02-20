package com.hashsequence.resvgcoil

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform