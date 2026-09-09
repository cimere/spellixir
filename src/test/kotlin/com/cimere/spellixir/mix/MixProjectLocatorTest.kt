package com.cimere.spellixir.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MixProjectLocatorTest {
    private class Layout(vararg files: String, private val directories: Set<String> = emptySet()) {
        private val files = files.toSet()
        val locator = MixProjectLocator<String>(
            parent = { it.substringBeforeLast('/', "").ifEmpty { null } },
            isDirectory = { it !in this.files },
            child = { directory, name ->
                "$directory/$name".takeIf { it in this.files || it in directories }
            },
        )
    }

    @Test
    fun findsOrdinaryRootFromDirectoryMixFileAndNestedSource() {
        val layout = Layout("workspace/app/mix.exs", "workspace/app/lib/demo/account.ex")
        for (path in listOf("workspace/app", "workspace/app/mix.exs", "workspace/app/lib/demo/account.ex")) {
            assertEquals("workspace/app", layout.locator.findRoot(path))
        }
    }

    @Test
    fun standaloneFilesDoNotInheritSiblingOrDescendantProjects() {
        val layout = Layout("workspace/app/mix.exs", "workspace/script.exs", "workspace/other/demo.ex")
        for (path in listOf("workspace", "workspace/script.exs", "workspace/other/demo.ex")) {
            assertNull(layout.locator.findRoot(path))
        }
    }

    @Test
    fun usesNearestEnclosingProject() {
        val layout = Layout("workspace/mix.exs", "workspace/nested/mix.exs", "workspace/nested/lib/demo.ex")
        assertEquals("workspace/nested", layout.locator.findRoot("workspace/nested/lib/demo.ex"))
    }

    @Test
    fun ignoresMisleadingFilenamesAndDirectoriesNamedMixExs() {
        val layout = Layout(
            "workspace/app/mix.exs.bak", "workspace/app/MIX.EXS", "workspace/app/mix.ex",
            "workspace/app/lib/demo.ex", directories = setOf("workspace/app/mix.exs"),
        )
        assertNull(layout.locator.findRoot("workspace/app/lib/demo.ex"))
    }
}
