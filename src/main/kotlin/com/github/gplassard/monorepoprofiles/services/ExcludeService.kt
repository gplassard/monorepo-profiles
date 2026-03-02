package com.github.gplassard.monorepoprofiles.services

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.util.function.Consumer

@Service(Service.Level.PROJECT)
class ExcludeService {

    suspend fun excludePaths(module: Module, paths: Set<VirtualFile>) {
        process(module, paths) { (entry, path) ->
            thisLogger().info("Excluding folder $path")
            entry.addExcludeFolder(path)
        }
    }

    suspend fun cancelExcludePaths(module: Module, paths: Set<VirtualFile>) {
        process(module, paths) { (entry, path) ->
            thisLogger().info("Cancel excluding folder $path")
            entry.removeExcludeFolder(path.url)
        }
    }

    private suspend fun process(
        module: Module,
        paths: Set<VirtualFile>,
        processor: Consumer<Pair<ContentEntry, VirtualFile>>
    ) {
        val model = ModuleRootManager.getInstance(module).modifiableModel
        writeAction {
            for (entry in model.contentEntries) {
                for (path in paths) {
                    val entryFile = entry.file ?: continue
                    if (!VfsUtilCore.isAncestor(entryFile, path, false)) continue
                    processor.accept(Pair(entry, path))
                }
            }
            thisLogger().info("Committing model for module $module")
            model.commit()
        }
    }

}
