package com.cimere.spellixir.lang

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame

class ElixirFileTypeTest : BasePlatformTestCase() {
    fun testRecognizesElixirSourceExtensionsThroughPlatformRegistration() {
        val fileTypeManager = FileTypeManager.getInstance()

        for (fileName in listOf("example.ex", "example.exs", "mix.exs")) {
            val file = myFixture.tempDirFixture.createFile(fileName)

            assertSame(ElixirFileType, fileTypeManager.getFileTypeByFileName(fileName))
            assertSame(ElixirFileType, file.fileType)
            assertSame(ElixirLanguage, (file.fileType as LanguageFileType).language)
        }
    }

    fun testLeavesUnrelatedExtensionsUnaffected() {
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName("example.txt")

        assertNotSame(ElixirFileType, fileType)
    }
}
