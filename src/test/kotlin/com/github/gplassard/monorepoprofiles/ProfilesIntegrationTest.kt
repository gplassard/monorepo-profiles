package com.github.gplassard.monorepoprofiles

import com.github.gplassard.monorepoprofiles.services.ExcludeService
import com.github.gplassard.monorepoprofiles.services.ProfilesConfigService
import com.github.gplassard.monorepoprofiles.settings.PluginSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import kotlinx.coroutines.runBlocking

class ProfilesIntegrationTest : BasePlatformTestCase() {

    private lateinit var tempFixture: TempDirTestFixture
    private lateinit var profilesConfigService: ProfilesConfigService
    private lateinit var excludeService: ExcludeService
    private lateinit var pluginSettings: PluginSettings

    override fun setUp() {
        super.setUp()
        tempFixture = TempDirTestFixtureImpl()
        tempFixture.setUp()

        profilesConfigService = ProfilesConfigService()
        excludeService = ExcludeService()
        pluginSettings = project.service<PluginSettings>()
    }

    override fun tearDown() {
        tempFixture.tearDown()
        super.tearDown()
    }

    fun testEndToEndProfileWorkflow() = runBlocking {
        // 1. Create a profile configuration file
        val yamlContent = """
            name: "Frontend Profile"
            includedPaths:
              - "frontend/src"
            excludedPaths:
              - "frontend/node_modules"
              - "frontend/dist"
            ---
            name: "Backend Profile"
            includedPaths:
              - "backend/src"
            excludedPaths:
              - "backend/target"
              - "backend/build"
        """.trimIndent()

        // Create the profile file in the project
        val profileFile = myFixture.addFileToProject("monorepo-profiles.yaml", yamlContent)

        // 2. Load profiles from the configuration
        val profiles = profilesConfigService.loadProfiles(project, profileFile.virtualFile)
        assertEquals(2, profiles.size)

        val frontendProfile = profiles.find { it.name == "Frontend Profile" }
        val backendProfile = profiles.find { it.name == "Backend Profile" }

        assertNotNull("Frontend profile should be loaded", frontendProfile)
        assertNotNull("Backend profile should be loaded", backendProfile)

        // 3. Set active profiles in settings
        pluginSettings.state.updateActiveProfiles(setOf("Frontend Profile"))

        // Verify settings are updated
        assertTrue(pluginSettings.state.isProfileActive("Frontend Profile"))
        assertFalse(pluginSettings.state.isProfileActive("Backend Profile"))

        // 4. Test changing active profiles
        pluginSettings.state.updateActiveProfiles(setOf("Backend Profile", "Frontend Profile"))

        assertTrue(pluginSettings.state.isProfileActive("Frontend Profile"))
        assertTrue(pluginSettings.state.isProfileActive("Backend Profile"))

        // 5. Test deactivating all profiles
        pluginSettings.state.updateActiveProfiles(emptySet())

        assertFalse(pluginSettings.state.isProfileActive("Frontend Profile"))
        assertFalse(pluginSettings.state.isProfileActive("Backend Profile"))
    }

    fun testProfileExclusionWorkflow() = runBlocking {
        // Create directories to exclude
        tempFixture.createFile("frontend/node_modules/package.json", "{}")
        tempFixture.createFile("backend/target/classes/Main.class", "")

        val nodeModules = tempFixture.findOrCreateDir("frontend/node_modules")
        val targetDir = tempFixture.findOrCreateDir("backend/target")

        val module = myFixture.module

        // Test excluding paths
        val pathsToExclude = setOf(nodeModules, targetDir)
        excludeService.excludePaths(module, pathsToExclude)

        // Verify exclusion
        val rootManager = ModuleRootManager.getInstance(module)
        val excludeFolderUrls = rootManager.contentEntries.flatMap { it.excludeFolderUrls.toList() }

        assertTrue("Node modules should be excluded", excludeFolderUrls.any { it == nodeModules.url })
        assertTrue("Target dir should be excluded", excludeFolderUrls.any { it == targetDir.url })

        // Test canceling exclusion
        excludeService.cancelExcludePaths(module, setOf(nodeModules))

        val updatedRootManager = ModuleRootManager.getInstance(module)
        val updatedExcludeFolderUrls = updatedRootManager.contentEntries.flatMap { it.excludeFolderUrls.toList() }

        assertFalse("Node modules should not be excluded", updatedExcludeFolderUrls.any { it == nodeModules.url })
        assertTrue("Target dir should still be excluded", updatedExcludeFolderUrls.any { it == targetDir.url })
    }

    fun testSettingsPersistence() {
        // Test that settings are persisted correctly
        val profileNames = setOf("Test Profile 1", "Test Profile 2")

        pluginSettings.state.updateActiveProfiles(profileNames)

        // Verify the settings were updated
        assertEquals(profileNames, pluginSettings.state.activeProfileNames)

        profileNames.forEach { name ->
            assertTrue("Profile $name should be active", pluginSettings.state.isProfileActive(name))
        }

        // Test updating with different profiles
        val newProfileNames = setOf("Test Profile 3")
        pluginSettings.state.updateActiveProfiles(newProfileNames)

        assertEquals(newProfileNames, pluginSettings.state.activeProfileNames)
        assertFalse(pluginSettings.state.isProfileActive("Test Profile 1"))
        assertFalse(pluginSettings.state.isProfileActive("Test Profile 2"))
        assertTrue(pluginSettings.state.isProfileActive("Test Profile 3"))
    }


    fun testMultipleProfileConfigurationFiles() = runBlocking {
        // Create multiple profile configuration files
        val frontendYaml = """
            name: "Frontend Only"
            includedPaths:
              - "frontend"
            excludedPaths:
              - "frontend/node_modules"
        """.trimIndent()

        val backendYaml = """
            name: "Backend Only"
            includedPaths:
              - "backend"
            excludedPaths:
              - "backend/target"
        """.trimIndent()

        myFixture.addFileToProject("frontend/monorepo-profiles.yaml", frontendYaml)
        myFixture.addFileToProject("backend/monorepo-profiles.yaml", backendYaml)

        // Load all configurations
        val allProfiles = profilesConfigService.loadConfigs(project)

        assertTrue("Should load profiles from multiple files", allProfiles.size >= 2)
        assertTrue(allProfiles.any { it.name == "Frontend Only" })
        assertTrue(allProfiles.any { it.name == "Backend Only" })
    }
}
