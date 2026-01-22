package com.github.gplassard.monorepoprofiles.services

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.dataformat.yaml.YAMLParser
import com.github.gplassard.monorepoprofiles.Constants
import com.github.gplassard.monorepoprofiles.helpers.PluginNotifications
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
    private val mapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule.Builder().build())
        .findAndRegisterModules()
        .also {
            // Accept either a single object or an array for the root YAML
            it.configure(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        }

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
        val content = document?.text ?: run {
            thisLogger().warn("Unable to read document content from ${profileFile.path}")
            return emptyList()
        }

        if (content.isBlank()) {
            thisLogger().warn("Profile file ${profileFile.path} is empty")
            return emptyList()
        }

        return try {
            // Deserialize as either a single object or an array of objects
            val type = mapper.typeFactory.constructCollectionType(List::class.java, ProfileConfig::class.java)
            val configs: List<ProfileConfig> = mapper.readValue(content, type)

            val profiles = mutableListOf<Profile>()
            var invalidCount = 0

            configs.forEach { config ->
                val validationError = validateProfileConfig(config)
                if (validationError != null) {
                    thisLogger().warn("Invalid profile '${config.name}' in ${profileFile.path}: $validationError")
                    invalidCount++
                } else {
                    val resolvedIncluded = config.includedPaths
                        .flatMap { path -> project.getBaseDirectories().map { it.resolveFromRootOrRelative(path) } }
                        .filterNotNull()
                        .toSet()

                    val resolvedExcluded = config.excludedPaths
                        .flatMap { path -> project.getBaseDirectories().map { it.resolveFromRootOrRelative(path) } }
                        .filterNotNull()
                        .toSet()

                    val unresolvedIncluded = config.includedPaths.size - resolvedIncluded.size
                    val unresolvedExcluded = config.excludedPaths.size - resolvedExcluded.size

                    if (unresolvedIncluded > 0 || unresolvedExcluded > 0) {
                        thisLogger().warn("Profile '${config.name}' has unresolved paths: $unresolvedIncluded included, $unresolvedExcluded excluded")
                    }

                    profiles.add(Profile(config.name, resolvedIncluded, resolvedExcluded))
                }
            }

            if (invalidCount > 0) {
                PluginNotifications.warning(
                    "Profile Loading Issues",
                    "Loaded ${profiles.size} profiles from ${profileFile.name}, but $invalidCount profile(s) were invalid. Check logs for details."
                )
            } else if (profiles.isNotEmpty()) {
                thisLogger().info("Successfully loaded ${profiles.size} profiles from ${profileFile.path}")
            }

            profiles
        } catch (e: JacksonException) {
            thisLogger().warn("Error parsing profile file ${profileFile.path}: ${e.message}")
            PluginNotifications.error(
                "Profile Loading Error",
                "Failed to parse ${profileFile.name}: ${e.message}"
            )
            emptyList()
        }
    }

    private fun validateProfileConfig(config: ProfileConfig): String? {
        return when {
            config.name.isBlank() -> "Profile name cannot be empty"
            config.includedPaths.isEmpty() && config.excludedPaths.isEmpty() ->
                "Profile must specify at least one included or excluded path"
            config.includedPaths.any { it.isBlank() } -> "Included paths cannot be empty"
            config.excludedPaths.any { it.isBlank() } -> "Excluded paths cannot be empty"
            else -> null
        }
    }
}
