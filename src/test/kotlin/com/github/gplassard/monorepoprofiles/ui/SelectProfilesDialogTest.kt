package com.github.gplassard.monorepoprofiles.ui

import com.github.gplassard.monorepoprofiles.model.Profile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.CheckBoxList
import java.awt.event.ActionEvent
import javax.swing.JCheckBox

class SelectProfilesDialogTest : BasePlatformTestCase() {

    fun testDialogInitializesWithProfiles() {
        val profiles = createTestProfiles()
        val activeProfileNames = setOf("Profile 1")

        val dialog = SelectProfilesDialog(project, profiles, activeProfileNames)

        // Verify dialog title
        assertEquals("Select Profiles", dialog.title)

        // Verify selected profiles are initialized correctly
        val selectedProfiles = dialog.getSelectedProfiles()
        assertEquals(1, selectedProfiles.size)
        assertTrue(selectedProfiles.any { it.name == "Profile 1" })
    }

    fun testDialogWithMultipleActiveProfiles() {
        val profiles = createTestProfiles()
        val activeProfileNames = setOf("Profile 1", "Profile 2")

        val dialog = SelectProfilesDialog(project, profiles, activeProfileNames)

        val selectedProfiles = dialog.getSelectedProfiles()
        assertEquals(2, selectedProfiles.size)
        assertTrue(selectedProfiles.any { it.name == "Profile 1" })
        assertTrue(selectedProfiles.any { it.name == "Profile 2" })
    }

    fun testDialogWithNoActiveProfiles() {
        val profiles = createTestProfiles()
        val activeProfileNames = emptySet<String>()

        val dialog = SelectProfilesDialog(project, profiles, activeProfileNames)

        val selectedProfiles = dialog.getSelectedProfiles()
        assertEquals(0, selectedProfiles.size)
    }

    fun testDialogWithEmptyProfiles() {
        val profiles = emptySet<Profile>()
        val activeProfileNames = emptySet<String>()

        val dialog = SelectProfilesDialog(project, profiles, activeProfileNames)

        val selectedProfiles = dialog.getSelectedProfiles()
        assertEquals(0, selectedProfiles.size)
    }

    fun testDialogWithActiveProfileNotInList() {
        val profiles = createTestProfiles()
        val activeProfileNames = setOf("Non-existent Profile")

        val dialog = SelectProfilesDialog(project, profiles, activeProfileNames)

        // Should not have any selected profiles since the active profile doesn't exist
        val selectedProfiles = dialog.getSelectedProfiles()
        assertEquals(0, selectedProfiles.size)
    }

    fun testDialogCreatesCorrectCenterPanel() {
        val profiles = createTestProfiles()
        val activeProfileNames = setOf("Profile 1")

        val dialog = SelectProfilesDialog(project, profiles, activeProfileNames)
        val centerPanel = dialog.createCenterPanel()

        assertNotNull("Center panel should be created", centerPanel)
        assertEquals(400, centerPanel.preferredSize.width)
        assertEquals(300, centerPanel.preferredSize.height)
    }

    private fun createTestProfiles(): Set<Profile> {
        // Create mock VirtualFile objects (in a real test environment, these would be proper mocks)
        return setOf(
            Profile(
                "Profile 1",
                emptySet(), // includedPaths
                emptySet()  // excludedPaths
            ),
            Profile(
                "Profile 2",
                emptySet(),
                emptySet()
            ),
            Profile(
                "Profile 3",
                emptySet(),
                emptySet()
            )
        )
    }
}