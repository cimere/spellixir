package com.cimere.spellixir.mix

import com.cimere.spellixir.lang.ElixirFileType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.IOException
import java.io.InputStream

class MixUmbrellaServiceTest : BasePlatformTestCase() {
    private val service get() = project.getService(MixProjectService::class.java)

    fun testRegisteredServiceRecognizesRootAndMultipleChildrenInCustomDirectory() {
        val mix = myFixture.addFileToProject("umbrella/mix.exs", mixDefinition("apps_path: \"components/elixir\"")).virtualFile
        assertEquals(MixProjectContext.UmbrellaRoot(mix.parent), service.classify(mix))
        for (name in listOf("one", "two")) {
            val childMix = myFixture.addFileToProject("umbrella/components/elixir/$name/mix.exs", mixDefinition("app: :$name")).virtualFile
            val source = myFixture.addFileToProject("umbrella/components/elixir/$name/lib/nested/demo.ex", "").virtualFile
            val expected = MixProjectContext.UmbrellaChild(childMix.parent, mix.parent)
            assertEquals(expected, service.classify(childMix.parent))
            assertEquals(expected, service.classify(source))
        }
        val unrelated = myFixture.addFileToProject("umbrella/apps/other/mix.exs", mixDefinition("app: :other")).virtualFile
        assertEquals(MixProjectContext.MixProject(unrelated.parent), service.classify(unrelated))
    }

    fun testSavedMetadataChangesAndMissingChildFilesRecoverWithoutDisruptingEditing() {
        val source = myFixture.addFileToProject("umbrella/apps/one/lib/demo.ex", "defmodule Demo.Child do\nend").virtualFile
        val rootMix = myFixture.addFileToProject("umbrella/mix.exs", mixDefinition("apps_path: \"apps\"")).virtualFile
        assertEquals(MixProjectContext.Unknown, service.classify(source))
        val childMix = myFixture.addFileToProject("umbrella/apps/one/mix.exs", "not a Mix project").virtualFile
        assertEquals(MixProjectContext.Unknown, service.classify(source))
        assertSame(ElixirFileType, source.fileType)
        myFixture.configureFromExistingVirtualFile(source)
        myFixture.doHighlighting()

        write(childMix, mixDefinition("app: :one"))
        assertEquals(MixProjectContext.UmbrellaChild(childMix.parent, rootMix.parent), service.classify(source))
        write(rootMix, mixDefinition("apps_path: System.get_env(\"APPS\")"))
        assertEquals(MixProjectContext.Unknown, service.classify(source))
        write(rootMix, mixDefinition("apps_path: \"apps\""))
        assertEquals(MixProjectContext.UmbrellaChild(childMix.parent, rootMix.parent), service.classify(source))
        WriteCommandAction.runWriteCommandAction(project) { rootMix.delete(this) }
        assertEquals(MixProjectContext.MixProject(childMix.parent), service.classify(source))
        WriteCommandAction.runWriteCommandAction(project) { childMix.delete(this) }
        assertEquals(MixProjectContext.Standalone, service.classify(source))
    }

    fun testMovingChildOutsideAppsDirectoryRemovesUmbrellaMembership() {
        val rootMix = myFixture.addFileToProject("umbrella/mix.exs", mixDefinition("apps_path: \"apps\"")).virtualFile
        val childMix = myFixture.addFileToProject("umbrella/apps/one/mix.exs", mixDefinition("app: :one")).virtualFile
        val destination = myFixture.tempDirFixture.findOrCreateDir("umbrella/separate")
        assertEquals(MixProjectContext.UmbrellaChild(childMix.parent, rootMix.parent), service.classify(childMix))
        WriteCommandAction.runWriteCommandAction(project) { childMix.parent.move(this, destination) }
        assertEquals(MixProjectContext.MixProject(childMix.parent), service.classify(childMix))
    }

    fun testOversizedAndMalformedContentsStayUnknown() {
        val mix = myFixture.addFileToProject("app/mix.exs", mixDefinition("app: :demo")).virtualFile
        for (text in listOf("", "# apps_path: \"apps\"", mixDefinition("app: :demo").dropLast(3), " ".repeat(MixMetadataReader.MAX_BYTES + 1))) {
            write(mix, text)
            assertEquals(MixProjectContext.Unknown, service.classify(mix))
        }
        write(mix, mixDefinition("app: :demo"))
        assertEquals(MixProjectContext.MixProject(mix.parent), service.classify(mix))
    }

    fun testUnreadableMetadataIsContainedByRegisteredService() {
        for (failure in listOf(IOException("unreadable"), SecurityException("denied"))) {
            val marker = object : LightVirtualFile("mix.exs", "") {
                override fun getInputStream(): InputStream = throw failure
            }
            val directory = object : LightVirtualFile("project", "") {
                override fun isDirectory() = true
                override fun findChild(name: String): VirtualFile? = marker.takeIf { name == "mix.exs" }
            }
            assertEquals(MixProjectContext.Unknown, service.classify(directory))
        }
    }

    fun testMetadataHelperCallsAreNotExecuted() {
        val source = mixDefinition("app: :demo, deps: deps()", "defp deps do System.cmd(\"must-not-run\", []) end")
        val mix = myFixture.addFileToProject("app/mix.exs", source).virtualFile
        assertEquals(MixProjectContext.MixProject(mix.parent), service.classify(mix))
        assertEquals(source, String(mix.contentsToByteArray()))
    }

    private fun write(file: VirtualFile, text: String) {
        WriteCommandAction.runWriteCommandAction(project) { file.setBinaryContent(text.toByteArray()) }
    }
}
