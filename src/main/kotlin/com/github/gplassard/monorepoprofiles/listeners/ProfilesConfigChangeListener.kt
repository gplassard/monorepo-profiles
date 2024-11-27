package com.github.gplassard.monorepoprofiles.listeners

import com.github.gplassard.monorepoprofiles.Constants
import com.github.gplassard.monorepoprofiles.services.ExcludeService
import com.github.gplassard.monorepoprofiles.services.ProfilesConfigService
import com.github.gplassard.monorepoprofiles.settings.PluginSettings
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
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

                    val profile = profileConfigService.loadProfiles(project, virtualFile) ?: return@launch

                    thisLogger().info("New profile $profile")

                    val fromConfig = profile.excludedPaths
                    val fromState = pluginSettings.state.resolveExcludedPaths(project)

                    val toExclude = fromConfig.minus(fromState)
                    val toCancelExclude = fromState.minus(fromConfig)

                    Notifications.Bus.notify(
                        Notification(
                            "MonorepoProfiles",
                            "Profiles updated",
                            "New profile ${profile.name} loaded from ${event.path}",
                            NotificationType.INFORMATION,
                        )
                    )

                    if (toExclude.isNotEmpty() || toCancelExclude.isNotEmpty()) {
                        excludeService.excludePaths(module, toExclude)
                        excludeService.cancelExcludePaths(module, toCancelExclude)

                        pluginSettings.state.updateExcludedPaths(fromConfig)

                        Notifications.Bus.notify(
                            Notification(
                                "MonorepoProfiles",
                                "Excludes updated",
                                "Updates paths added ${toExclude.size} and removed ${toCancelExclude.size}",
                                NotificationType.INFORMATION,
                            )
                        )
                    }
                }
            }
        }
    }
}
