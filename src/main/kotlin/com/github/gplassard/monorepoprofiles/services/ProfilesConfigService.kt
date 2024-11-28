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

    suspend fun loadConfigs(project: Project): Set<Profile> {
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
            .toSet()
    }

    suspend fun loadProfiles(project: Project, profileFile: VirtualFile): Profile? {
        val document = readAction { FileDocumentManager.getInstance().getDocument(profileFile) }
        val content = document?.text ?: return null
        try {
            val config = mapper.readValue(content, ProfileConfig::class.java)
            val resolvedIncluded = config.includedPaths
                .flatMap { path -> project.getBaseDirectories().map { it.resolveFromRootOrRelative(path) } }
                .filterNotNull()
                .toSet()

            val resolvedExcluded = config.excludedPaths
                .flatMap { path -> project.getBaseDirectories().map { it.resolveFromRootOrRelative(path) } }
                .filterNotNull()
                .toSet()

            return Profile(config.name, resolvedIncluded, resolvedExcluded)
        } catch (e: JacksonException) {
            thisLogger().warn("Error while parsing profile file ${profileFile.path} ${e.message}")
            return null
        }
    }
}
