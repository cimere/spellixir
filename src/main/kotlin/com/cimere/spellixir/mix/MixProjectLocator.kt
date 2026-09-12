package com.cimere.spellixir.mix

internal sealed interface MixLocation<out Node> {
    data class Ordinary<Node>(val root: Node) : MixLocation<Node>
    data class Umbrella<Node>(val root: Node) : MixLocation<Node>
    data class Child<Node>(val root: Node, val umbrellaRoot: Node) : MixLocation<Node>
    data object Standalone : MixLocation<Nothing>
    data object Unknown : MixLocation<Nothing>
}

/** Static layout lookup; metadata comes from a bounded reader with no process access. */
internal class MixProjectLocator<Node : Any>(
    private val parent: (Node) -> Node?,
    private val isDirectory: (Node) -> Boolean,
    private val child: (Node, String) -> Node?,
    private val metadata: (Node) -> MixMetadata,
) {
    fun classify(fileOrDirectory: Node): MixLocation<Node> {
        val root = findRoot(fileOrDirectory) ?: return MixLocation.Standalone
        return when (val info = read(root)) {
            MixMetadata.Unknown -> MixLocation.Unknown
            is MixMetadata.Umbrella -> {
                val apps = appsDirectory(root, info)
                var directory = if (isDirectory(fileOrDirectory)) fileOrDirectory else parent(fileOrDirectory)
                // A subtree in the apps directory without its own mix.exs is not a child app.
                while (directory != null && directory != root) {
                    if (parent(directory) == apps && apps != null) return MixLocation.Unknown
                    directory = parent(directory)
                }
                MixLocation.Umbrella(root)
            }
            MixMetadata.Ordinary -> {
                val enclosing = parent(root)?.let(::findRoot)
                when (val enclosingInfo = enclosing?.let(::read)) {
                    MixMetadata.Unknown -> MixLocation.Unknown
                    is MixMetadata.Umbrella -> {
                        if (appsDirectory(enclosing, enclosingInfo) == parent(root)) {
                            MixLocation.Child(root, enclosing)
                        } else MixLocation.Ordinary(root)
                    }
                    else -> MixLocation.Ordinary(root)
                }
            }
        }
    }

    private fun read(root: Node): MixMetadata = child(root, "mix.exs")?.let(metadata) ?: MixMetadata.Unknown

    private fun appsDirectory(root: Node, info: MixMetadata.Umbrella): Node? {
        var directory = root
        for (part in info.appsPath) {
            directory = child(directory, part)?.takeIf(isDirectory) ?: return null
        }
        return directory
    }

    fun findRoot(fileOrDirectory: Node): Node? {
        var directory = if (isDirectory(fileOrDirectory)) fileOrDirectory else parent(fileOrDirectory)
        while (directory != null) {
            val mixFile = child(directory, "mix.exs")
            if (mixFile != null && !isDirectory(mixFile)) return directory
            directory = parent(directory)
        }
        return null
    }
}
