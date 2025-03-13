package com.github.gplassard.monorepoprofiles.ui

import com.github.gplassard.monorepoprofiles.Constants
import com.github.gplassard.monorepoprofiles.settings.PluginSettings
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiElement
import java.util.regex.Pattern

class ProfileGutterIconProvider : RelatedItemLineMarkerProvider() {

    private val namePattern = Pattern.compile("name:\\s*(?:\"([^\"]+)\"|([^\\s,]+))")

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        // Ensure the PsiElement is a leaf element
        if (element.children.isNotEmpty()) {
            return
        }

        // Check if the file is a profile file
        val file = element.containingFile
        if (!file.name.equals(Constants.PROFILE_FILE_NAME)) {
            return
        }

        // Only process the first element in each line to avoid duplicate markers
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile) ?: return
        val lineNumber = document.getLineNumber(element.textOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        if (element.textOffset != lineStartOffset) {
            return
        }

        // Get the line text
        val lineEndOffset = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset))

        // Check if the line contains a name definition
        val matcher = namePattern.matcher(lineText)
        if (!matcher.find()) {
            return
        }

        // Get the profile name (could be in group 1 for quoted or group 2 for unquoted)
        val profileName = matcher.group(1) ?: matcher.group(2)

        // If no profile name was found, return
        if (profileName == null) return

        // Get the active profile name from settings
        val project = element.project
        val pluginSettings = project.service<PluginSettings>()
        val activeProfileName = pluginSettings.state.activeProfileName

        // Determine if this profile is active
        val isActive = profileName == activeProfileName

        // Create a navigation gutter icon builder with an icon indicating if the profile is active
        val builder = NavigationGutterIconBuilder.create(
            if (isActive) AllIcons.General.InspectionsOK else AllIcons.General.InspectionsMixed
        )
            .setTarget(element)
            .setTooltipText(if (isActive) "Active profile" else "Inactive profile")

        // Add the marker to the result
        result.add(builder.createLineMarkerInfo(element))
    }
}
