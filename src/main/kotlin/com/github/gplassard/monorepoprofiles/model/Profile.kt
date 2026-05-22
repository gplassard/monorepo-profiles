package com.github.gplassard.monorepoprofiles.model

import com.intellij.openapi.vfs.VirtualFile

data class Profile(
    val name: String,
    val priority: Int,
    val includedPaths: Set<VirtualFile>,
    val excludedPaths: Set<VirtualFile>,
)
