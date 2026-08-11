package com.github.gplassard.monorepoprofiles.model

data class ProfileConfig(
    val name: String,
    val includedPaths: Set<String>,
    val excludedPaths: Set<String> = emptySet(),
    val priority: Int = 0,
)

