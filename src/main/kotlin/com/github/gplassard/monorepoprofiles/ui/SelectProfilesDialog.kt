package com.github.gplassard.monorepoprofiles.ui

import com.github.gplassard.monorepoprofiles.model.Profile
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class SelectProfilesDialog(
    project: Project,
    private val profiles: Set<Profile>,
    private val activeProfileNames: Set<String>
) : DialogWrapper(project) {

    private val checkBoxList = CheckBoxList<Profile>()
    private val selectedProfiles = mutableSetOf<Profile>()

    init {
        title = "Select Profiles"
        init()
        initCheckBoxList()
    }

    private fun initCheckBoxList() {
        // Add all profiles to the checkbox list
        profiles.forEach { profile ->
            checkBoxList.addItem(
                profile,
                profile.name,
                activeProfileNames.contains(profile.name) // Check if profile is active
            )
        }

        // Add a listener to update the selected profiles when checkboxes are toggled
        checkBoxList.setCheckBoxListListener { index, value ->
            val profile = checkBoxList.getItemAt(index)
            if (profile != null) {
                if (value) {
                    selectedProfiles.add(profile)
                } else {
                    selectedProfiles.remove(profile)
                }
            }
        }

        // Initialize the selected profiles with the active profiles
        profiles.filter { activeProfileNames.contains(it.name) }.forEach { profile ->
            selectedProfiles.add(profile)
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(400, 300)
        panel.border = JBUI.Borders.empty(10)

        val scrollPane = JBScrollPane(checkBoxList)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    fun getSelectedProfiles(): Set<Profile> {
        return selectedProfiles
    }
}
