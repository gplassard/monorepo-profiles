package com.github.gplassard.monorepoprofiles.actions

import com.github.gplassard.monorepoprofiles.Constants
import com.github.gplassard.monorepoprofiles.settings.PluginSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.Dimension
import javax.swing.JComponent

class ShowDebugStateAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        DebugStateDialog(project).show()
    }
}

private class DebugStateDialog(private val project: Project) : DialogWrapper(project, true) {
    private val textArea = JBTextArea()

    init {
        title = "Monorepo Profiles Debug State"
        textArea.isEditable = false
        textArea.text = buildStateDump()
        textArea.caretPosition = 0
        init()
    }

    override fun createCenterPanel(): JComponent {
        val scrollPane = JBScrollPane(textArea)
        scrollPane.preferredSize = Dimension(780, 520)
        return scrollPane
    }

    override fun getPreferredFocusedComponent(): JComponent = textArea

    private fun buildStateDump(): String {
        val settings = project.service<PluginSettings>().state
        val data = ReadAction.compute<DebugStateData, RuntimeException> {
            val baseDirectories = project.getBaseDirectories()
                .map { it.path }
                .sorted()
            val excludedPaths = settings.excludedPaths.toList().sorted()
            val resolvedExcludedPaths = settings.resolveExcludedPaths(project)
                .map { it.path }
                .sorted()
            val profileFiles = FilenameIndex.getVirtualFilesByName(
                Constants.PROFILE_FILE_NAME,
                true,
                GlobalSearchScope.projectScope(project)
            ).map { it.path }.sorted()
            val modules = ModuleManager.getInstance(project).modules
                .map { it.name }
                .sorted()

            DebugStateData(
                project.name,
                project.basePath,
                baseDirectories,
                modules,
                profileFiles,
                settings.activeProfileName,
                excludedPaths,
                resolvedExcludedPaths
            )
        }

        return buildString {
            appendLine("Monorepo Profiles Debug State")
            appendLine("Project name: ${data.projectName}")
            appendLine("Base path: ${data.basePath ?: "<none>"}")
            appendLine("Profile file name: ${Constants.PROFILE_FILE_NAME}")
            appendLine()
            appendLine("Settings:")
            appendLine("  activeProfileName: ${data.activeProfileName ?: "<none>"}")
            appendLine("  excludedPaths (${data.excludedPaths.size}):")
            appendList(data.excludedPaths)
            appendLine()
            appendSection("Resolved excluded paths", data.resolvedExcludedPaths)
            appendLine()
            appendSection("Profile files", data.profileFiles)
            appendLine()
            appendSection("Base directories", data.baseDirectories)
            appendLine()
            appendSection("Modules", data.modules)
        }
    }

    private fun StringBuilder.appendSection(title: String, items: List<String>) {
        appendLine("$title (${items.size}):")
        appendList(items)
    }

    private fun StringBuilder.appendList(items: List<String>) {
        if (items.isEmpty()) {
            appendLine("  <empty>")
            return
        }
        for (item in items) {
            appendLine("  $item")
        }
    }
}

private data class DebugStateData(
    val projectName: String,
    val basePath: String?,
    val baseDirectories: List<String>,
    val modules: List<String>,
    val profileFiles: List<String>,
    val activeProfileName: String?,
    val excludedPaths: List<String>,
    val resolvedExcludedPaths: List<String>
)
