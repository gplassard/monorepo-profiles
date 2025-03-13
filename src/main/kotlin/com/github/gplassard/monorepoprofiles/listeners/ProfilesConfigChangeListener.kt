package com.github.gplassard.monorepoprofiles.listeners

import com.github.gplassard.monorepoprofiles.Constants
import com.github.gplassard.monorepoprofiles.helpers.PluginNotifications
import com.github.gplassard.monorepoprofiles.services.ExcludeService
import com.github.gplassard.monorepoprofiles.services.ProfilesConfigService
import com.github.gplassard.monorepoprofiles.settings.PluginSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ProfilesConfigChangeListener(private val project: Project, private val cs: CoroutineScope) : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        cs.launch {

            for (event in events) {
                val profileFileChange = event.path.endsWith(Constants.PROFILE_FILE_NAME)
                if (profileFileChange) {
                    val virtualFile = event.file ?: continue
                    val module = ModuleUtilCore.findModuleForFile(virtualFile, project) ?: continue

                    val profileConfigService = project.service<ProfilesConfigService>()
                    val pluginSettings = project.service<PluginSettings>()
                    val excludeService = project.service<ExcludeService>()

                    thisLogger().info("Loading profile configs from ${event.path}")

                    val profiles = profileConfigService.loadProfiles(project, virtualFile)
                    if (profiles.isEmpty()) return@launch

                    thisLogger().info("New profiles $profiles")

                    // Combine all excluded paths from all profiles
                    val fromConfig = profiles.flatMap { it.excludedPaths }.toSet()
                    val fromState = pluginSettings.state.resolveExcludedPaths(project)

                    val toExclude = fromConfig.minus(fromState)
                    val toCancelExclude = fromState.minus(fromConfig)

                    PluginNotifications.info(
                        "Profiles updated",
                        "${profiles.size} profile(s) loaded from ${event.path}"
                    )

                    if (toExclude.isNotEmpty() || toCancelExclude.isNotEmpty()) {
                        excludeService.excludePaths(module, toExclude)
                        excludeService.cancelExcludePaths(module, toCancelExclude)

                        pluginSettings.state.updateExcludedPaths(fromConfig)

                        PluginNotifications.info(
                            "Excludes updated",
                            "Updated paths added ${toExclude.size} and removed ${toCancelExclude.size}"
                        )
                    }
                }
            }
        }
    }
}
