package com.github.gplassard.monorepoprofiles.services

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import kotlinx.coroutines.runBlocking

class ExcludeServiceTest : BasePlatformTestCase() {

    private lateinit var service: ExcludeService
    private lateinit var tempFixture: TempDirTestFixture

    override fun setUp() {
        super.setUp()
        service = ExcludeService()
        tempFixture = TempDirTestFixtureImpl()
        tempFixture.setUp()
    }

    override fun tearDown() {
        tempFixture.tearDown()
        super.tearDown()
    }

    fun testExcludePathsAddsPathsToModule() = runBlocking {
        val module = myFixture.module
        val pathToExclude = tempFixture.createFile("exclude-me/file.txt").parent

        val pathsToExclude = setOf(pathToExclude)

        service.excludePaths(module, pathsToExclude)

        // Verify that the path was added to the exclude folders
        val rootManager = ModuleRootManager.getInstance(module)
        val contentEntries = rootManager.contentEntries

        assertTrue("Module should have content entries", contentEntries.isNotEmpty())

        val excludeFolderUrls = contentEntries.flatMap { it.excludeFolderUrls.toList() }
        assertTrue("Path should be excluded", excludeFolderUrls.any { it == pathToExclude.url })
    }

    fun testCancelExcludePathsRemovesPathsFromModule() = runBlocking {
        val module = myFixture.module
        val pathToExclude = tempFixture.createFile("exclude-me/file.txt").parent

        val pathsToExclude = setOf(pathToExclude)

        // First exclude the path
        service.excludePaths(module, pathsToExclude)

        // Then cancel the exclusion
        service.cancelExcludePaths(module, pathsToExclude)

        // Verify that the path was removed from the exclude folders
        val rootManager = ModuleRootManager.getInstance(module)
        val contentEntries = rootManager.contentEntries

        val excludeFolderUrls = contentEntries.flatMap { it.excludeFolderUrls.toList() }
        assertFalse("Path should not be excluded", excludeFolderUrls.any { it == pathToExclude.url })
    }

    fun testExcludeMultiplePaths() = runBlocking {
        val module = myFixture.module
        val path1 = tempFixture.createFile("exclude1/file.txt").parent
        val path2 = tempFixture.createFile("exclude2/file.txt").parent

        val pathsToExclude = setOf(path1, path2)

        service.excludePaths(module, pathsToExclude)

        val rootManager = ModuleRootManager.getInstance(module)
        val contentEntries = rootManager.contentEntries
        val excludeFolderUrls = contentEntries.flatMap { it.excludeFolderUrls.toList() }

        assertTrue("Path1 should be excluded", excludeFolderUrls.any { it == path1.url })
        assertTrue("Path2 should be excluded", excludeFolderUrls.any { it == path2.url })
    }

    fun testExcludeAndCancelExcludeDifferentPaths() = runBlocking {
        val module = myFixture.module
        val pathToKeep = tempFixture.createFile("keep-me/file.txt").parent
        val pathToRemove = tempFixture.createFile("remove-me/file.txt").parent

        // Exclude both paths initially
        service.excludePaths(module, setOf(pathToKeep, pathToRemove))

        // Cancel exclusion of only one path
        service.cancelExcludePaths(module, setOf(pathToRemove))

        val rootManager = ModuleRootManager.getInstance(module)
        val contentEntries = rootManager.contentEntries
        val excludeFolderUrls = contentEntries.flatMap { it.excludeFolderUrls.toList() }

        assertTrue("Path to keep should still be excluded", excludeFolderUrls.any { it == pathToKeep.url })
        assertFalse("Path to remove should not be excluded", excludeFolderUrls.any { it == pathToRemove.url })
    }

    fun testExcludeEmptySetDoesNothing() = runBlocking {
        val module = myFixture.module
        val initialRootManager = ModuleRootManager.getInstance(module)
        val initialExcludedCount = initialRootManager.contentEntries
            .flatMap { it.excludeFolderUrls.toList() }.size

        service.excludePaths(module, emptySet())

        val finalRootManager = ModuleRootManager.getInstance(module)
        val finalExcludedCount = finalRootManager.contentEntries
            .flatMap { it.excludeFolderUrls.toList() }.size

        assertEquals("Excluded paths count should not change", initialExcludedCount, finalExcludedCount)
    }

    fun testCancelExcludeEmptySetDoesNothing() = runBlocking {
        val module = myFixture.module
        val pathToExclude = tempFixture.createFile("exclude-me/file.txt").parent

        // First exclude a path
        service.excludePaths(module, setOf(pathToExclude))

        val afterExcludeRootManager = ModuleRootManager.getInstance(module)
        val afterExcludeCount = afterExcludeRootManager.contentEntries
            .flatMap { it.excludeFolderUrls.toList() }.size

        // Cancel exclusion with empty set
        service.cancelExcludePaths(module, emptySet())

        val finalRootManager = ModuleRootManager.getInstance(module)
        val finalCount = finalRootManager.contentEntries
            .flatMap { it.excludeFolderUrls.toList() }.size

        assertEquals("Excluded paths count should not change", afterExcludeCount, finalCount)
    }
}