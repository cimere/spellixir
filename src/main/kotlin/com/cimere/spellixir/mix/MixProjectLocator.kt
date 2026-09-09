package com.cimere.spellixir.mix

/** Static layout lookup. Its filesystem seam deliberately has no content or process access. */
internal class MixProjectLocator<Node : Any>(
    private val parent: (Node) -> Node?,
    private val isDirectory: (Node) -> Boolean,
    private val child: (Node, String) -> Node?,
) {
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
