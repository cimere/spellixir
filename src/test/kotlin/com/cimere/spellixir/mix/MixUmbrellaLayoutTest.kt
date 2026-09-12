package com.cimere.spellixir.mix

import org.junit.Assert.assertEquals
import org.junit.Test

class MixUmbrellaLayoutTest {
    private class Layout {
        val files = mutableMapOf<String, String?>()
        private fun exists(path: String) = files.containsKey(path) || files.keys.any { it.startsWith("$path/") }
        val locator = MixProjectLocator<String>(
            parent = { it.substringBeforeLast('/', "").ifEmpty { null } },
            isDirectory = { !files.containsKey(it) },
            child = { directory, name -> "$directory/$name".takeIf(::exists) },
            metadata = { files[it]?.let(MixMetadataReader::read) ?: MixMetadata.Unknown },
        )
    }

    @Test
    fun classifiesRootMultipleChildrenAndNestedSourceDirectories() {
        val layout = Layout()
        layout.files["workspace/mix.exs"] = mixDefinition("apps_path: \"apps\"")
        for (name in listOf("one", "two")) {
            layout.files["workspace/apps/$name/mix.exs"] = mixDefinition("app: :$name")
            layout.files["workspace/apps/$name/lib/nested/demo.ex"] = ""
            assertEquals(MixLocation.Child("workspace/apps/$name", "workspace"),
                layout.locator.classify("workspace/apps/$name/lib/nested/demo.ex"))
        }
        assertEquals(MixLocation.Umbrella("workspace"), layout.locator.classify("workspace"))
        assertEquals(MixLocation.Umbrella("workspace"), layout.locator.classify("workspace/mix.exs"))
    }

    @Test
    fun respectsCustomPathsAndRequiresDirectChildMembership() {
        val layout = Layout()
        layout.files["workspace/mix.exs"] = mixDefinition("apps_path: \"components/elixir\"")
        for (path in listOf("components/elixir/one", "apps/two", "components/elixir/one/nested")) {
            layout.files["workspace/$path/mix.exs"] = mixDefinition("app: :demo")
        }
        assertEquals(MixLocation.Child("workspace/components/elixir/one", "workspace"),
            layout.locator.classify("workspace/components/elixir/one"))
        assertEquals(MixLocation.Ordinary("workspace/apps/two"), layout.locator.classify("workspace/apps/two"))
        assertEquals(MixLocation.Ordinary("workspace/components/elixir/one/nested"),
            layout.locator.classify("workspace/components/elixir/one/nested"))
    }

    @Test
    fun doesNotInferUmbrellasFromAnAppsDirectoryAlone() {
        val layout = Layout()
        layout.files["workspace/mix.exs"] = mixDefinition("app: :root")
        layout.files["workspace/apps/one/mix.exs"] = mixDefinition("app: :one")
        assertEquals(MixLocation.Ordinary("workspace/apps/one"), layout.locator.classify("workspace/apps/one"))
    }

    @Test
    fun containsMissingUnreadableAndMalformedMetadataAndRecoversAfterChanges() {
        val layout = Layout()
        val root = "workspace/mix.exs"
        val child = "workspace/apps/one/mix.exs"
        val source = "workspace/apps/one/lib/demo.ex"
        layout.files[source] = ""
        assertEquals(MixLocation.Standalone, layout.locator.classify(source))
        layout.files[root] = mixDefinition("apps_path: \"apps\"")
        assertEquals(MixLocation.Unknown, layout.locator.classify(source))
        for (content in listOf(null, "", "not Mix metadata", mixDefinition("app: :one").dropLast(3))) {
            layout.files[child] = content
            assertEquals(MixLocation.Unknown, layout.locator.classify(source))
        }
        layout.files[child] = mixDefinition("app: :one")
        assertEquals(MixLocation.Child("workspace/apps/one", "workspace"), layout.locator.classify(source))
        layout.files[root] = null
        assertEquals(MixLocation.Unknown, layout.locator.classify(source))
        layout.files.remove(root)
        assertEquals(MixLocation.Ordinary("workspace/apps/one"), layout.locator.classify(source))
    }
}
