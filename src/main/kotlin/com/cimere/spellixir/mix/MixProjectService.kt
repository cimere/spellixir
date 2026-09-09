package com.cimere.spellixir.mix

import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.VirtualFile

sealed interface MixProjectContext {
    data class MixProject(val root: VirtualFile) : MixProjectContext
    data object Standalone : MixProjectContext
    data object Unknown : MixProjectContext
}

/** Native Core project context, independent of any Elixir runtime or Semantic Backend. */
@Service(Service.Level.PROJECT)
class MixProjectService {
    private val locator = MixProjectLocator<VirtualFile>(
        parent = { it.parent },
        isDirectory = { it.isDirectory },
        child = { directory, name ->
            // Check the actual name too: case-insensitive filesystems can resolve MIX.EXS.
            directory.findChild(name)?.takeIf { it.isValid && it.name == name }
        },
    )

    /**
     * Classifies a file or directory by its nearest enclosing mix.exs file, including itself
     * when given a project directory. No IDE content-root boundary is imposed, so standalone
     * files opened from outside the IDE project can still find their own Mix project.
     *
     * Uses the current VFS snapshot without caching, refreshing, reading contents, or mutations.
     * Call under the platform's normal read access; external disk changes need a VFS refresh.
     * This establishes layout only, not validity of Mix metadata or umbrella relationships.
     */
    fun classify(fileOrDirectory: VirtualFile): MixProjectContext {
        if (!fileOrDirectory.isValid) return MixProjectContext.Unknown
        val root = locator.findRoot(fileOrDirectory) ?: return MixProjectContext.Standalone
        return MixProjectContext.MixProject(root)
    }
}
