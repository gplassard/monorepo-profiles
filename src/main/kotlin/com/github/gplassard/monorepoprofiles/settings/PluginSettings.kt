package com.github.gplassard.monorepoprofiles.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.resolveFromRootOrRelative

@Service(Service.Level.PROJECT)
@State(name = "PluginSettings", storages = [Storage("monorepo-profiles.xml", roamingType = RoamingType.DISABLED)])
class PluginSettings : SimplePersistentStateComponent<PluginSettingsState>(PluginSettingsState())

class PluginSettingsState : BaseState() {
    var activeProfileName by string()
    var excludedPaths by stringSet()

    fun updateActiveProfileName(name: String?) {
        activeProfileName = name
        this.incrementModificationCount()
    }

    fun updateExcludedPaths(files: Set<VirtualFile>) {
        thisLogger().info("Updating excluded paths: ${files.map { it.path }}")
        excludedPaths = files.map { it.path }.toMutableSet()
        this.incrementModificationCount()
    }

    fun resolveExcludedPaths(project: Project): Set<VirtualFile> {
        thisLogger().info("Resolving excluded paths")
        val resolved = excludedPaths
            .flatMap { path -> project.getBaseDirectories().map { it.resolveFromRootOrRelative(path) } }
            .filterNotNull()
            .toSet()
        thisLogger().info("Resolved excluded paths: ${resolved.map { it.path }}")
        return resolved
    }
}
