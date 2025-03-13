package com.github.gplassard.monorepoprofiles.services

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.dataformat.yaml.YAMLParser
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
            .flatMap { loadProfiles(project, it) }
            .toSet()
    }

    suspend fun loadProfiles(project: Project, profileFile: VirtualFile): List<Profile> {
        val document = readAction { FileDocumentManager.getInstance().getDocument(profileFile) }
        val content = document?.text ?: return emptyList()

        val profiles = mutableListOf<Profile>()

        try {
            val factory = YAMLFactory()
            val parser = factory.createParser(content)

            while (!parser.isClosed) {
                try {
                    val config = mapper.readValue(parser, ProfileConfig::class.java)
                    if (config != null) {
                        val resolvedIncluded = config.includedPaths
                            .flatMap { path -> project.getBaseDirectories().map { it.resolveFromRootOrRelative(path) } }
                            .filterNotNull()
                            .toSet()

                        val resolvedExcluded = config.excludedPaths
                            .flatMap { path -> project.getBaseDirectories().map { it.resolveFromRootOrRelative(path) } }
                            .filterNotNull()
                            .toSet()

                        profiles.add(Profile(config.name, resolvedIncluded, resolvedExcluded))
                    }
                } catch (e: JacksonException) {
                    thisLogger().warn("Error while parsing profile in file ${profileFile.path}: ${e.message}")
                    // Continue to next document
                }
            }

            return profiles
        } catch (e: JacksonException) {
            thisLogger().warn("Error while parsing profile file ${profileFile.path}: ${e.message}")
            return emptyList()
        }
    }
}
