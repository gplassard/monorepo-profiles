package com.github.gplassard.monorepoprofiles.listeners

import com.github.gplassard.monorepoprofiles.Constants
import com.github.gplassard.monorepoprofiles.helpers.PluginNotifications
import com.github.gplassard.monorepoprofiles.model.Profile
import com.github.gplassard.monorepoprofiles.services.ExcludeService
import com.github.gplassard.monorepoprofiles.services.ProfilesConfigService
import com.github.gplassard.monorepoprofiles.settings.PluginSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ProfilesConfigChangeListener(private val project: Project, private val cs: CoroutineScope) : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        cs.launch {
            if (events.none { it.path.endsWith(Constants.PROFILE_FILE_NAME) }) return@launch

            val profileConfigService = project.service<ProfilesConfigService>()
            val pluginSettings = project.service<PluginSettings>()
            val excludeService = project.service<ExcludeService>()

            thisLogger().info("Loading profile configs from project")
            val profiles = profileConfigService.loadConfigs(project)

            if (profiles.isEmpty()) {
                val fromState = pluginSettings.state.resolveExcludedPaths(project)
                if (fromState.isNotEmpty()) {
                    updateExcludes(excludeService, emptySet(), fromState)
                    pluginSettings.state.updateExcludedPaths(emptySet())
                }
                pluginSettings.state.updateActiveProfileName(null)
                return@launch
            }

            val selected = selectProfile(profiles, pluginSettings.state.activeProfileName)

            thisLogger().info("Active profile $selected")

            val fromConfig = selected.excludedPaths
            val fromState = pluginSettings.state.resolveExcludedPaths(project)

            val toExclude = fromConfig.minus(fromState)
            val toCancelExclude = fromState.minus(fromConfig)

            if (selected.name != pluginSettings.state.activeProfileName) {
                pluginSettings.state.updateActiveProfileName(selected.name)
            }

            PluginNotifications.info(
                "Profiles updated",
                "Active profile ${selected.name} (priority ${selected.priority})"
            )

            if (toExclude.isNotEmpty() || toCancelExclude.isNotEmpty()) {
                updateExcludes(excludeService, toExclude, toCancelExclude)
                pluginSettings.state.updateExcludedPaths(fromConfig)

                PluginNotifications.info(
                    "Excludes updated",
                    "Updated paths added ${toExclude.size} and removed ${toCancelExclude.size}"
                )
            }
        }
    }

    private suspend fun updateExcludes(
        excludeService: ExcludeService,
        toExclude: Set<VirtualFile>,
        toCancelExclude: Set<VirtualFile>
    ) {
        val modules = ModuleManager.getInstance(project).modules
        for (module in modules) {
            if (toExclude.isNotEmpty()) {
                excludeService.excludePaths(module, toExclude)
            }
            if (toCancelExclude.isNotEmpty()) {
                excludeService.cancelExcludePaths(module, toCancelExclude)
            }
        }
    }

    private fun selectProfile(profiles: List<Profile>, activeName: String?): Profile {
        if (!activeName.isNullOrBlank()) {
            val match = profiles.firstOrNull { it.name == activeName }
            if (match != null) return match
        }
        return profiles.first()
    }
}
