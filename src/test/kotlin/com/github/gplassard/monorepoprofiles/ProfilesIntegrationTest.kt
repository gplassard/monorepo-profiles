package com.github.gplassard.monorepoprofiles

import com.github.gplassard.monorepoprofiles.services.ExcludeService
import com.github.gplassard.monorepoprofiles.services.ProfilesConfigService
import com.github.gplassard.monorepoprofiles.settings.PluginSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

class ProfilesIntegrationTest : BasePlatformTestCase() {

    fun testLoadConfigsResolvesIncludedAndExcludedPaths() {
        val includedDir = myFixture.tempDirFixture.findOrCreateDir("app")
        val excludedDir = myFixture.tempDirFixture.findOrCreateDir("out")

        val yaml = """
            name: "Dev"
            includedPaths:
              - "app"
            excludedPaths:
              - "out"
        """.trimIndent()

        myFixture.addFileToProject(Constants.PROFILE_FILE_NAME, yaml)
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val profiles = runBlocking {
            project.service<ProfilesConfigService>().loadConfigs(project)
        }

        assertEquals(1, profiles.size)
        val profile = profiles.first()
        assertEquals("Dev", profile.name)
        assertTrue(profile.includedPaths.any { it.path == includedDir.path })
        assertTrue(profile.excludedPaths.any { it.path == excludedDir.path })
    }

    fun testPluginSettingsResolveExcludedPaths() {
        val excludedDir = myFixture.tempDirFixture.findOrCreateDir("settings-excluded")
        val settings = project.service<PluginSettings>()

        settings.state.updateExcludedPaths(setOf(excludedDir))

        val resolved = settings.state.resolveExcludedPaths(project)
        assertEquals(setOf(excludedDir.path), resolved.map { it.path }.toSet())
    }
}
