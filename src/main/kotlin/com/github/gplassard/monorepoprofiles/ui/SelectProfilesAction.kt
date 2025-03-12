package com.github.gplassard.monorepoprofiles.ui

import com.github.gplassard.monorepoprofiles.helpers.PluginNotifications
import com.github.gplassard.monorepoprofiles.services.ExcludeService
import com.github.gplassard.monorepoprofiles.services.ProfilesConfigService
import com.github.gplassard.monorepoprofiles.settings.PluginSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectProfilesAction : AnAction(), DumbAware {
    private val cs = CoroutineScope(Dispatchers.IO)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        cs.launch {
            val profilesConfigService = project.service<ProfilesConfigService>()
            val pluginSettings = project.service<PluginSettings>()

            // Load all available profiles
            val profiles = profilesConfigService.loadConfigs(project)

            // Get the currently active profile name
            val activeProfileName = pluginSettings.state.activeProfileName

            withContext(Dispatchers.Main.immediate) {
                // Show the dialog with the profiles
                val dialog = SelectProfilesDialog(project, profiles, activeProfileName)
                if (dialog.showAndGet()) {
                    // User clicked OK, apply the changes
                    val selectedProfiles = dialog.getSelectedProfiles()

                    // Update the active profile name in settings
                    pluginSettings.state.activeProfileName = selectedProfiles.firstOrNull()?.name

                    // Apply the selected profiles
                    val excludeService = project.service<ExcludeService>()

                    // Get all modules in the project
                    val modules = ModuleManager.getInstance(project).modules

                    // Calculate the paths to exclude
                    val pathsToExclude = selectedProfiles
                        .flatMap { it.excludedPaths }
                        .toSet()

                    // Get the current excluded paths
                    val currentExcludedPaths = pluginSettings.state.resolveExcludedPaths(project)

                    // Calculate the paths to add and remove
                    val pathsToAdd = pathsToExclude.minus(currentExcludedPaths)
                    val pathsToRemove = currentExcludedPaths.minus(pathsToExclude)

                    // Apply the changes to all modules
                    for (module in modules) {
                        if (pathsToAdd.isNotEmpty()) {
                            excludeService.excludePaths(module, pathsToAdd)
                        }
                        if (pathsToRemove.isNotEmpty()) {
                            excludeService.cancelExcludePaths(module, pathsToRemove)
                        }
                    }

                    // Update the excluded paths in settings
                    pluginSettings.state.updateExcludedPaths(pathsToExclude)

                    // Show a notification
                    PluginNotifications.info(
                        "Profiles Updated",
                        "Applied ${selectedProfiles.size} profiles with ${pathsToExclude.size} excluded paths"
                    )
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        // Enable the action only if a project is open
        e.presentation.isEnabled = e.project != null
    }
}
