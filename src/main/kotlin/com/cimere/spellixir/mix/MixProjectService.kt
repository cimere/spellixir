package com.cimere.spellixir.mix

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

sealed interface MixProjectContext {
    data class MixProject(val root: VirtualFile) : MixProjectContext
    data class UmbrellaRoot(val root: VirtualFile) : MixProjectContext
    data class UmbrellaChild(val root: VirtualFile, val umbrellaRoot: VirtualFile) : MixProjectContext
    data object Standalone : MixProjectContext
    data object Unknown : MixProjectContext
}

/** Native Core project context, independent of any Elixir runtime or Semantic Backend. */
@Service(Service.Level.PROJECT)
class MixProjectService {
    private val locator = MixProjectLocator<VirtualFile>(
        parent = { ProgressManager.checkCanceled(); it.parent },
        isDirectory = { it.isDirectory },
        child = { directory, name ->
            // Check the actual name too: case-insensitive filesystems can resolve MIX.EXS.
            directory.findChild(name)?.takeIf { it.isValid && it.name == name }
        },
        metadata = ::readMetadata,
    )

    /**
     * Classifies a file or directory by its nearest enclosing mix.exs file, including itself
     * when given a project directory. No IDE content-root boundary is imposed, so standalone
     * files opened from outside the IDE project can still find their own Mix project.
     *
     * Uses bounded reads of saved mix.exs contents from the current VFS snapshot, without
     * caching, refreshing, evaluating code, or mutations. Unsaved editor changes are not read.
     * Call under the platform's normal read access; external disk changes need a VFS refresh.
     * Unsupported or ambiguous metadata yields Unknown without notifications. Platform
     * cancellation still propagates normally.
     */
    fun classify(fileOrDirectory: VirtualFile): MixProjectContext {
        if (!fileOrDirectory.isValid) return MixProjectContext.Unknown
        return when (val location = locator.classify(fileOrDirectory)) {
            is MixLocation.Ordinary -> MixProjectContext.MixProject(location.root)
            is MixLocation.Umbrella -> MixProjectContext.UmbrellaRoot(location.root)
            is MixLocation.Child -> MixProjectContext.UmbrellaChild(location.root, location.umbrellaRoot)
            MixLocation.Standalone -> MixProjectContext.Standalone
            MixLocation.Unknown -> MixProjectContext.Unknown
        }
    }

    private fun readMetadata(file: VirtualFile): MixMetadata {
        ProgressManager.checkCanceled()
        return try {
            if (!file.isValid || file.isDirectory || file.length > MixMetadataReader.MAX_BYTES) {
                return MixMetadata.Unknown
            }
            val bytes = file.inputStream.use { it.readNBytes(MixMetadataReader.MAX_BYTES + 1) }
            if (bytes.size > MixMetadataReader.MAX_BYTES) return MixMetadata.Unknown
            val source = file.charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            MixMetadataReader.read(source)
        } catch (_: IOException) {
            MixMetadata.Unknown
        } catch (_: SecurityException) {
            MixMetadata.Unknown
        }
    }
}
