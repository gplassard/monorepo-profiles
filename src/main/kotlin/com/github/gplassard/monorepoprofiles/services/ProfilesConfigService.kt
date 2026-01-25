package com.github.gplassard.monorepoprofiles.services

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.gplassard.monorepoprofiles.Constants
import com.github.gplassard.monorepoprofiles.model.Profile
import com.github.gplassard.monorepoprofiles.model.ProfileConfig
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.resolveFromRootOrRelative
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

@Service(Service.Level.PROJECT)
class ProfilesConfigService {
    private val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule.Builder().build())

    suspend fun loadConfigs(project: Project): List<Profile> {
        val excludeFiles = readAction {
            FilenameIndex.getVirtualFilesByName(
                Constants.PROFILE_FILE_NAME,
                true,
                GlobalSearchScope.projectScope(project)
            )
        }

        thisLogger().info("Profiles files $excludeFiles")

        return excludeFiles
            .mapNotNull { loadProfiles(project, it) }
            .sortedWith(compareByDescending<Profile> { it.priority }.thenBy { it.name })
    }

    suspend fun loadProfiles(project: Project, profileFile: VirtualFile): Profile? {
        val document = readAction { FileDocumentManager.getInstance().getDocument(profileFile) }
        val content = document?.text ?: return null
        try {
            val config = mapper.readValue(content, ProfileConfig::class.java)
            return readAction {
                val resolvedIncluded = resolvePaths(project, config.includedPaths)
                val includeOnlyExcluded = computeIncludeOnlyExcluded(project, resolvedIncluded)
                val resolvedExcluded = resolvePaths(project, config.excludedPaths)
                Profile(
                    config.name,
                    config.priority,
                    resolvedIncluded,
                    includeOnlyExcluded + resolvedExcluded
                )
            }
        } catch (e: JacksonException) {
            thisLogger().warn("Error while parsing profile file ${profileFile.path} ${e.message}")
            return null
        }
    }

    private fun resolvePaths(project: Project, paths: Set<String>): Set<VirtualFile> {
        if (paths.isEmpty()) return emptySet()
        val baseDirs = project.getBaseDirectories()
        return paths
            .flatMap { path -> baseDirs.mapNotNull { it.resolveFromRootOrRelative(path) } }
            .mapNotNull { toDirectory(it) }
            .toSet()
    }

    private fun computeIncludeOnlyExcluded(project: Project, includedPaths: Set<VirtualFile>): Set<VirtualFile> {
        val includedDirs = includedPaths.mapNotNull { toDirectory(it) }.toSet()
        if (includedDirs.isEmpty()) return emptySet()

        val baseDirs = project.getBaseDirectories().toSet()
        val allowed = HashSet<VirtualFile>()
        for (included in includedDirs) {
            var current: VirtualFile? = included
            while (current != null) {
                if (!allowed.add(current)) break
                if (baseDirs.contains(current)) break
                current = current.parent
            }
        }

        val excludes = HashSet<VirtualFile>()
        for (base in baseDirs) {
            if (includedDirs.contains(base)) continue
            collectExcluded(base, allowed, includedDirs, excludes)
        }
        return excludes
    }

    private fun collectExcluded(
        root: VirtualFile,
        allowed: Set<VirtualFile>,
        includedDirs: Set<VirtualFile>,
        excludes: MutableSet<VirtualFile>
    ) {
        for (child in root.children) {
            if (!child.isDirectory) continue
            if (includedDirs.contains(child)) continue
            if (allowed.contains(child)) {
                collectExcluded(child, allowed, includedDirs, excludes)
            } else {
                excludes.add(child)
            }
        }
    }

    private fun toDirectory(file: VirtualFile): VirtualFile? {
        return if (file.isDirectory) file else file.parent
    }
}
