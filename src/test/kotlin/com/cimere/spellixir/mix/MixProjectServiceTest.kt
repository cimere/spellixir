package com.cimere.spellixir.mix

import com.cimere.spellixir.lang.ElixirFileType
import com.cimere.spellixir.lang.psi.ElixirModuleDeclaration
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame

class MixProjectServiceTest : BasePlatformTestCase() {
    private val service get() = project.getService(MixProjectService::class.java)

    fun testRegisteredServiceClassifiesOrdinaryProjectWithoutEvaluatingMixFile() {
        val mix = myFixture.addFileToProject("app/mix.exs", mixDefinition("app: :demo")).virtualFile
        val source = myFixture.addFileToProject("app/lib/demo.ex", "defmodule Demo do\nend").virtualFile
        val expected = MixProjectContext.MixProject(mix.parent)

        assertSame(service, project.getService(MixProjectService::class.java))
        assertEquals(expected, service.classify(source))
        assertEquals(expected, service.classify(mix))
        assertEquals(expected, service.classify(mix.parent))
        assertEquals(mixDefinition("app: :demo"), String(mix.contentsToByteArray()))
    }

    fun testStandaloneElixirFilesKeepRegisteredEditingSupport() {
        myFixture.addFileToProject("unrelated/mix.exs", "")
        for (extension in listOf("ex", "exs")) {
            val file = myFixture.addFileToProject("standalone/demo.$extension", "defmodule Demo.Standalone do\nend")
            assertEquals(MixProjectContext.Standalone, service.classify(file.virtualFile))
            assertSame(ElixirFileType, file.fileType)
            assertNotNull(PsiTreeUtil.findChildOfType(file, ElixirModuleDeclaration::class.java))
            myFixture.configureFromExistingVirtualFile(file.virtualFile)
            myFixture.doHighlighting()
        }
    }

    fun testNestedProjectUsesItsOwnRoot() {
        myFixture.addFileToProject("app/mix.exs", mixDefinition("app: :demo"))
        val nestedMix = myFixture.addFileToProject("app/nested/mix.exs", mixDefinition("app: :nested")).virtualFile
        val source = myFixture.addFileToProject("app/nested/lib/demo.ex", "").virtualFile
        assertEquals(MixProjectContext.MixProject(nestedMix.parent), service.classify(source))
    }

    fun testMisleadingMarkerNamesAndDirectoriesDoNotCreateMixProjects() {
        myFixture.addFileToProject("app/mix.exs.bak", "")
        myFixture.addFileToProject("app/MIX.EXS", "")
        val source = myFixture.addFileToProject("app/lib/demo.ex", "").virtualFile
        assertEquals(MixProjectContext.Standalone, service.classify(source))

        val directorySource = myFixture.addFileToProject("other/mix.exs/demo.ex", "").virtualFile
        assertEquals(MixProjectContext.Standalone, service.classify(directorySource))
    }

    fun testClassificationTracksMarkerCreationRenameAndRemoval() {
        val source = myFixture.addFileToProject("app/lib/demo.ex", "").virtualFile
        assertEquals(MixProjectContext.Standalone, service.classify(source))
        val mix = myFixture.addFileToProject("app/mix.exs", mixDefinition("app: :demo")).virtualFile
        val expected = MixProjectContext.MixProject(mix.parent)
        assertEquals(expected, service.classify(source))
        WriteCommandAction.runWriteCommandAction(project) { mix.rename(this, "mix.exs.bak") }
        assertEquals(MixProjectContext.Standalone, service.classify(source))
        WriteCommandAction.runWriteCommandAction(project) { mix.rename(this, "mix.exs") }
        assertEquals(expected, service.classify(source))
        WriteCommandAction.runWriteCommandAction(project) { mix.delete(this) }
        assertEquals(MixProjectContext.Standalone, service.classify(source))
        assertEquals(MixProjectContext.Unknown, service.classify(mix))
    }
}
