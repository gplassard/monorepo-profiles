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
    override fun runInDispatchThread(): Boolean = false

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

    fun testExcludeServiceUpdatesModuleExcludes() {
        val excludedDir = myFixture.tempDirFixture.findOrCreateDir("excluded")
        val excludeService = project.service<ExcludeService>()

        runBlocking {
            excludeService.excludePaths(module, setOf(excludedDir))
        }

        val hasExclude = ModuleRootManager.getInstance(module).contentEntries
            .any { entry -> entry.excludeFolderUrls.contains(excludedDir.url) }
        assertTrue(hasExclude)

        runBlocking {
            excludeService.cancelExcludePaths(module, setOf(excludedDir))
        }

        val hasExcludeAfterCancel = ModuleRootManager.getInstance(module).contentEntries
            .any { entry -> entry.excludeFolderUrls.contains(excludedDir.url) }
        assertFalse(hasExcludeAfterCancel)
    }

    fun testPluginSettingsResolveExcludedPaths() {
        val excludedDir = myFixture.tempDirFixture.findOrCreateDir("settings-excluded")
        val settings = project.service<PluginSettings>()

        settings.state.updateExcludedPaths(setOf(excludedDir))

        val resolved = settings.state.resolveExcludedPaths(project)
        assertEquals(setOf(excludedDir.path), resolved.map { it.path }.toSet())
    }

    fun testLoadConfigsSortsByPriority() {
        myFixture.tempDirFixture.findOrCreateDir("app1")
        myFixture.tempDirFixture.findOrCreateDir("app2")

        val yamlHigh = """
            name: "High"
            priority: 10
            includedPaths:
              - "app1"
        """.trimIndent()

        val yamlLow = """
            name: "Low"
            priority: 1
            includedPaths:
              - "app2"
        """.trimIndent()

        myFixture.addFileToProject("app1/${Constants.PROFILE_FILE_NAME}", yamlHigh)
        myFixture.addFileToProject("app2/${Constants.PROFILE_FILE_NAME}", yamlLow)
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val profiles = runBlocking {
            project.service<ProfilesConfigService>().loadConfigs(project)
        }

        assertEquals(2, profiles.size)
        assertEquals(listOf("High", "Low"), profiles.map { it.name })
        assertEquals(listOf(10, 1), profiles.map { it.priority })
    }
}
