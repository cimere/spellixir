package com.cimere.spellixir.lang

import com.cimere.spellixir.lang.psi.ElixirAliasDeclaration
import com.cimere.spellixir.lang.psi.ElixirCallExpression
import com.cimere.spellixir.lang.psi.ElixirCallableDeclaration
import com.cimere.spellixir.lang.psi.ElixirCaptureExpression
import com.cimere.spellixir.lang.psi.ElixirDoBlock
import com.cimere.spellixir.lang.psi.ElixirLiteralExpression
import com.cimere.spellixir.lang.psi.ElixirListExpression
import com.cimere.spellixir.lang.psi.ElixirMapExpression
import com.cimere.spellixir.lang.psi.ElixirModuleDeclaration
import com.cimere.spellixir.lang.psi.ElixirParameterList
import com.cimere.spellixir.lang.psi.ElixirParenthesizedExpression
import com.cimere.spellixir.lang.psi.ElixirPattern
import com.cimere.spellixir.lang.psi.ElixirQualifiedName
import com.cimere.spellixir.lang.psi.ElixirTupleExpression
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class ElixirPsiTest : BasePlatformTestCase() {
    fun testRegisteredParserExposesDurableDeclarationPsi() {
        val source = """
            defmodule Demo.Accounts do
              def fetch(user_id), do: Repo.get(User, user_id)
            end
        """.trimIndent()

        val file = myFixture.configureByText("accounts.ex", source)

        val module = PsiTreeUtil.findChildOfType(file, ElixirModuleDeclaration::class.java)
        val callable = PsiTreeUtil.findChildOfType(file, ElixirCallableDeclaration::class.java)
        assertEquals("defmodule Demo.Accounts do\n  def fetch(user_id), do: Repo.get(User, user_id)\nend", module?.text)
        assertEquals("def fetch(user_id), do: Repo.get(User, user_id)", callable?.text)
        assertEquals(source, file.node.text)
        assertEquals(0, file.textRange.startOffset)
        assertEquals(source.length, file.textRange.endOffset)
    }

    fun testRepresentativeLanguageConceptsHaveTypedPsi() {
        val source = """
            alias Demo.Repo

            defmodule Demo.Service do
              def fetch({:user, user_id}, options \\ []) do
                Repo.get(User, user_id)
                notify options
                {:ok, user_id, &1, "user=#{user_id}"}
              end
            end
        """.trimIndent()

        val file = myFixture.configureByText("service.ex", source)

        assertEquals(1, PsiTreeUtil.findChildrenOfType(file, ElixirAliasDeclaration::class.java).size)
        assertEquals(1, PsiTreeUtil.findChildrenOfType(file, ElixirModuleDeclaration::class.java).size)
        assertEquals(1, PsiTreeUtil.findChildrenOfType(file, ElixirCallableDeclaration::class.java).size)
        assertEquals(1, PsiTreeUtil.findChildrenOfType(file, ElixirParameterList::class.java).size)
        assertEquals(2, PsiTreeUtil.findChildrenOfType(file, ElixirPattern::class.java).size)
        assertEquals(2, PsiTreeUtil.findChildrenOfType(file, ElixirDoBlock::class.java).size)
        assertEquals(2, PsiTreeUtil.findChildrenOfType(file, ElixirCallExpression::class.java).size)
        assertEquals(1, PsiTreeUtil.findChildrenOfType(file, ElixirCaptureExpression::class.java).size)
        assertEquals(3, PsiTreeUtil.findChildrenOfType(file, ElixirQualifiedName::class.java).size)
        assertEquals(3, PsiTreeUtil.findChildrenOfType(file, ElixirLiteralExpression::class.java).size)
        assertEquals(source, file.node.text)
    }

    fun testRepresentativeScriptParsesCompletelyWithoutRuntimeServices() {
        val source = """
            # Standalone scripts remain entirely Native Core.
            users = [1, 2]
            result = Demo.Service.fetch({:user, 42}, users)
            IO.inspect(result)
        """.trimIndent()

        val file = myFixture.configureByText("sample.exs", source)

        assertEquals(2, PsiTreeUtil.findChildrenOfType(file, ElixirCallExpression::class.java).size)
        assertEquals(1, PsiTreeUtil.findChildrenOfType(file, ElixirListExpression::class.java).size)
        assertEquals(2, PsiTreeUtil.findChildrenOfType(file, ElixirQualifiedName::class.java).size)
        assertNull(PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java))
        assertEquals(source, file.node.text)
        assertEquals(source.length, file.textRange.length)
    }

    fun testUnfinishedDeclarationHeadDoesNotConsumeFollowingCallableDeclaration() {
        val source = """
            def unfinished(value,
            def complete(value), do: {:ok, value}
        """.trimIndent()

        val file = myFixture.configureByText("unfinished_call.ex", source)

        val declarations = PsiTreeUtil.findChildrenOfType(file, ElixirCallableDeclaration::class.java).toList()
        assertEquals(2, declarations.size)
        assertEquals("def unfinished(value,", declarations[0].text)
        assertEquals("def complete(value), do: {:ok, value}", declarations[1].text)
        assertEquals(source, file.node.text)
    }

    fun testUnfinishedListKeepsTypedPsiBeforeFollowingCallableDeclaration() {
        val source = """
            def unfinished(value), do: [Repo.get(value),
            def complete(value), do: {:ok, value}
        """.trimIndent()

        val file = myFixture.configureByText("unfinished_list.ex", source)

        val declarations = PsiTreeUtil.findChildrenOfType(file, ElixirCallableDeclaration::class.java).toList()
        assertEquals(2, declarations.size)
        assertEquals("def unfinished(value), do: [Repo.get(value),", declarations[0].text)
        assertEquals("def complete(value), do: {:ok, value}", declarations[1].text)
        assertEquals(1, PsiTreeUtil.findChildrenOfType(declarations[0], ElixirListExpression::class.java).size)
        assertEquals(source, file.node.text)
    }

    fun testUnfinishedDelimiterFamiliesKeepTheirTypedPsi() {
        val cases = listOf(
            "Repo.get(value," to ElixirCallExpression::class.java,
            "{:ok, value," to ElixirTupleExpression::class.java,
            "%{status: :ok," to ElixirMapExpression::class.java,
            "(value + 1" to ElixirParenthesizedExpression::class.java,
        )

        for ((unfinishedExpression, psiType) in cases) {
            val source = "def unfinished(value), do: $unfinishedExpression\ndef complete, do: :ok"
            val file = myFixture.configureByText("unfinished_delimiter.ex", source)
            val declarations = PsiTreeUtil.findChildrenOfType(file, ElixirCallableDeclaration::class.java).toList()

            assertEquals("$unfinishedExpression declaration count", 2, declarations.size)
            assertEquals("def complete, do: :ok", declarations[1].text)
            assertEquals(1, PsiTreeUtil.findChildrenOfType(declarations[0], psiType).size)
            assertEquals(source, file.node.text)
        }
    }

    fun testUnfinishedInterpolationStaysLocalToItsLiteralAndDeclaration() {
        val source = "def message(value), do: \"hello #{value\ndef complete, do: :ok"

        val file = myFixture.configureByText("unfinished_interpolation.ex", source)

        val declarations = PsiTreeUtil.findChildrenOfType(file, ElixirCallableDeclaration::class.java).toList()
        assertEquals(2, declarations.size)
        assertEquals("def message(value), do: \"hello #{value", declarations[0].text)
        assertEquals(1, PsiTreeUtil.findChildrenOfType(declarations[0], ElixirLiteralExpression::class.java).size)
        assertEquals("def complete, do: :ok", declarations[1].text)
        assertEquals(source, file.node.text)
    }

    fun testUnfinishedDoBlockKeepsDeclarationAndBlockPsi() {
        val source = """
            def run(value) do
              Repo.get(value)
        """.trimIndent()

        val file = myFixture.configureByText("unfinished_block.ex", source)

        val declaration = PsiTreeUtil.findChildOfType(file, ElixirCallableDeclaration::class.java)
        assertEquals(source, declaration?.text)
        assertEquals(1, PsiTreeUtil.findChildrenOfType(declaration, ElixirDoBlock::class.java).size)
        assertEquals(source, file.node.text)
    }

    fun testRepeatedEditsPreservePsiAcrossValidIncompleteAndInvalidForms() {
        val valid = "def run(value), do: [value]\ndef complete, do: :ok"
        val file = myFixture.configureByText("editing_recovery.ex", valid)
        val document = myFixture.editor.document

        replaceDocumentText(document.text.replace("]", ""))
        assertEditingStructure(file, "def run(value), do: [value")

        replaceDocumentText(document.text.replace("value\ndef", "value§\ndef"))
        assertEditingStructure(file, "def run(value), do: [value")

        replaceDocumentText(valid)
        assertEditingStructure(file, "def run(value), do: [value]")
    }

    private fun replaceDocumentText(text: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText(text)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun assertEditingStructure(file: com.intellij.psi.PsiFile, expectedFirstDeclaration: String) {
        val declarations = PsiTreeUtil.findChildrenOfType(file, ElixirCallableDeclaration::class.java).toList()
        assertEquals(2, declarations.size)
        assertEquals(expectedFirstDeclaration, declarations[0].text)
        assertEquals("def complete, do: :ok", declarations[1].text)
        assertEquals(1, PsiTreeUtil.findChildrenOfType(declarations[0], ElixirListExpression::class.java).size)
        assertEquals(myFixture.editor.document.text, file.node.text)
    }
}
