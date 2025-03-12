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
    private val activeProfileName: String?
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
                activeProfileName != null && profile.name == activeProfileName // Check the active profile
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

        // Initialize the selected profiles with the active profile
        if (activeProfileName != null) {
            profiles.find { it.name == activeProfileName }?.let {
                selectedProfiles.add(it)
            }
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
