package com.cimere.spellixir.lang

import com.cimere.spellixir.lang.psi.ElixirAliasDeclaration
import com.cimere.spellixir.lang.psi.ElixirCallExpression
import com.cimere.spellixir.lang.psi.ElixirCallableDeclaration
import com.cimere.spellixir.lang.psi.ElixirCaptureExpression
import com.cimere.spellixir.lang.psi.ElixirDoBlock
import com.cimere.spellixir.lang.psi.ElixirLiteralExpression
import com.cimere.spellixir.lang.psi.ElixirListExpression
import com.cimere.spellixir.lang.psi.ElixirModuleDeclaration
import com.cimere.spellixir.lang.psi.ElixirParameterList
import com.cimere.spellixir.lang.psi.ElixirPattern
import com.cimere.spellixir.lang.psi.ElixirQualifiedName
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
}
