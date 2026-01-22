package com.github.gplassard.monorepoprofiles.services

import com.github.gplassard.monorepoprofiles.model.Profile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class ProfilesConfigServiceTest : BasePlatformTestCase() {

    private lateinit var service: ProfilesConfigService
    private lateinit var tempFixture: TempDirTestFixture

    override fun setUp() {
        super.setUp()
        service = ProfilesConfigService()
        tempFixture = TempDirTestFixtureImpl()
        tempFixture.setUp()
    }

    override fun tearDown() {
        tempFixture.tearDown()
        super.tearDown()
    }

    fun testLoadValidSingleProfile() = runBlocking {
        val yamlContent = """
            name: "Test Profile"
            includedPaths:
              - "src/main"
              - "src/test"
            excludedPaths:
              - "target"
              - "build"
        """.trimIndent()

        val virtualFile = tempFixture.createFile("monorepo-profiles.yaml", yamlContent)
        val profiles = service.loadProfiles(project, virtualFile)

        assertEquals(1, profiles.size)
        val profile = profiles.first()
        assertEquals("Test Profile", profile.name)
        // Note: Path resolution testing would require mocking VFS which is complex
        // We'll focus on YAML parsing here
    }

    fun testLoadMultipleProfiles() = runBlocking {
        val yamlContent = """
            - name: "Frontend Profile"
              includedPaths:
                - "frontend"
              excludedPaths:
                - "frontend/node_modules"
            - name: "Backend Profile"
              includedPaths:
                - "backend"
              excludedPaths:
                - "backend/target"
        """.trimIndent()

        val virtualFile = tempFixture.createFile("monorepo-profiles.yaml", yamlContent)
        val profiles = service.loadProfiles(project, virtualFile)

        assertEquals(2, profiles.size)
        assertTrue(profiles.any { it.name == "Frontend Profile" })
        assertTrue(profiles.any { it.name == "Backend Profile" })
    }

    fun testLoadProfileWithEmptyName() = runBlocking {
        val yamlContent = """
            name: ""
            includedPaths:
              - "src"
            excludedPaths: []
        """.trimIndent()

        val virtualFile = tempFixture.createFile("monorepo-profiles.yaml", yamlContent)
        val profiles = service.loadProfiles(project, virtualFile)

        // Should skip invalid profile due to validation
        assertEquals(0, profiles.size)
    }

    fun testLoadProfileWithNoPathsSpecified() = runBlocking {
        val yamlContent = """
            name: "Invalid Profile"
            includedPaths: []
            excludedPaths: []
        """.trimIndent()

        val virtualFile = tempFixture.createFile("monorepo-profiles.yaml", yamlContent)
        val profiles = service.loadProfiles(project, virtualFile)

        // Should skip invalid profile due to validation
        assertEquals(0, profiles.size)
    }

    fun testLoadProfileWithBlankPaths() = runBlocking {
        val yamlContent = """
            name: "Profile With Blank Path"
            includedPaths:
              - "src"
              - ""
            excludedPaths:
              - "target"
        """.trimIndent()

        val virtualFile = tempFixture.createFile("monorepo-profiles.yaml", yamlContent)
        val profiles = service.loadProfiles(project, virtualFile)

        // Should skip invalid profile due to validation
        assertEquals(0, profiles.size)
    }

    fun testLoadInvalidYaml() = runBlocking {
        val yamlContent = """
            name: "Test Profile
            includedPaths:
              - "src/main"
            # Missing closing quote causes parse error
        """.trimIndent()

        val virtualFile = tempFixture.createFile("monorepo-profiles.yaml", yamlContent)
        val profiles = service.loadProfiles(project, virtualFile)

        // Should return empty list on parse error
        assertEquals(0, profiles.size)
    }

    fun testLoadEmptyFile() = runBlocking {
        val virtualFile = tempFixture.createFile("monorepo-profiles.yaml", "")
        val profiles = service.loadProfiles(project, virtualFile)

        assertEquals(0, profiles.size)
    }

    fun testLoadProfilesWithMixedValidAndInvalid() = runBlocking {
        val yamlContent = """
            - name: "Valid Profile"
              includedPaths:
                - "src"
              excludedPaths: []
            - name: ""
              includedPaths:
                - "test"
              excludedPaths: []
            - name: "Another Valid Profile"
              includedPaths: []
              excludedPaths:
                - "build"
        """.trimIndent()

        val virtualFile = tempFixture.createFile("monorepo-profiles.yaml", yamlContent)
        val profiles = service.loadProfiles(project, virtualFile)

        // Should load only the valid profiles
        assertEquals(2, profiles.size)
        assertTrue(profiles.any { it.name == "Valid Profile" })
        assertTrue(profiles.any { it.name == "Another Valid Profile" })
    }

    fun testLoadConfigsFromMultipleFiles() = runBlocking {
        // Create multiple profile files
        val yamlContent1 = """
            name: "Profile 1"
            includedPaths:
              - "module1"
            excludedPaths: []
        """.trimIndent()

        val yamlContent2 = """
            name: "Profile 2"
            includedPaths:
              - "module2"
            excludedPaths: []
        """.trimIndent()

        tempFixture.createFile("dir1/monorepo-profiles.yaml", yamlContent1)
        tempFixture.createFile("dir2/monorepo-profiles.yaml", yamlContent2)

        val allProfiles = service.loadConfigs(project)

        // Should find and load profiles from both files
        assertTrue(allProfiles.size >= 2)
        assertTrue(allProfiles.any { it.name == "Profile 1" })
        assertTrue(allProfiles.any { it.name == "Profile 2" })
    }
}
