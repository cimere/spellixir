package com.cimere.spellixir.lang

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame

class ElixirSyntaxHighlighterTest : BasePlatformTestCase() {
    fun testOpeningElixirFileKeepsRegisteredEditorHighlighting() {
        val source = "fn value -> {:ok, Demo, 42} end"
        val file = myFixture.tempDirFixture.createFile("sample.ex")
        WriteAction.run<RuntimeException> { VfsUtil.saveText(file, source) }

        myFixture.configureFromExistingVirtualFile(file)

        assertSame(ElixirFileType, myFixture.file.fileType)
        assertEquals(ElixirLanguage, myFixture.file.language)
        assertEquals(
            listOf(
                "fn" to "KEYWORD",
                "value" to "IDENTIFIER",
                "->" to "OPERATOR",
                "{" to "BRACES",
                ":ok" to "ATOM",
                "," to "OPERATOR",
                "Demo" to "ALIAS",
                "," to "OPERATOR",
                "42" to "NUMBER",
                "}" to "BRACES",
                "end" to "KEYWORD",
            ),
            editorTokens(source),
        )
    }

    fun testHighlightsRepresentativeCoreLexicalFormsThroughPlatformRegistration() {
        val source = "defmodule Demo do @answer :ok + 0x2A ?x # comment"

        assertEquals(
            listOf(
                "defmodule" to "ELIXIR_IDENTIFIER",
                "Demo" to "ELIXIR_ALIAS",
                "do" to "ELIXIR_KEYWORD",
                "@answer" to "ELIXIR_MODULE_ATTRIBUTE",
                ":ok" to "ELIXIR_ATOM",
                "+" to "ELIXIR_OPERATOR",
                "0x2A" to "ELIXIR_NUMBER",
                "?x" to "ELIXIR_CHARACTER",
                "# comment" to "ELIXIR_COMMENT",
            ),
            highlight(source),
        )
    }

    fun testHighlightsIdentifiersQuotedAtomsAndDelimiters() {
        val source = "call([{:ready, :'quoted atom'}])"

        assertEquals(
            listOf(
                "call" to "ELIXIR_IDENTIFIER",
                "(" to "ELIXIR_PARENTHESES",
                "[" to "ELIXIR_BRACKETS",
                "{" to "ELIXIR_BRACES",
                ":ready" to "ELIXIR_ATOM",
                "," to "ELIXIR_OPERATOR",
                ":'quoted atom'" to "ELIXIR_ATOM",
                "}" to "ELIXIR_BRACES",
                "]" to "ELIXIR_BRACKETS",
                ")" to "ELIXIR_PARENTHESES",
            ),
            highlight(source),
        )
    }

    fun testHighlightsInvalidCharacterWithoutCorruptingFollowingAtom() {
        val source = "§ :still_highlighted"
        assertEquals(
            listOf(
                "§" to "ELIXIR_BAD_CHARACTER",
                ":still_highlighted" to "ELIXIR_ATOM",
            ),
            highlight(source),
        )
    }

    private fun highlight(source: String): List<Pair<String, String>> {
        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(ElixirLanguage, project, null)
        val lexer = highlighter.highlightingLexer
        val highlightedTokens = mutableListOf<Pair<String, String>>()

        lexer.start(source)
        while (lexer.tokenType != null) {
            val tokenText = source.substring(lexer.tokenStart, lexer.tokenEnd)
            val attribute = highlighter.getTokenHighlights(lexer.tokenType).singleOrNull()?.externalName
            if (attribute != null) highlightedTokens += tokenText to attribute
            lexer.advance()
        }
        return highlightedTokens
    }

    private fun editorTokens(source: String): List<Pair<String, String>> {
        val iterator = myFixture.editor.highlighter.createIterator(0)
        val tokens = mutableListOf<Pair<String, String>>()
        while (!iterator.atEnd()) {
            val tokenType = iterator.tokenType?.toString()
            if (tokenType != null && tokenType != "WHITE_SPACE") {
                tokens += source.substring(iterator.start, iterator.end) to tokenType
            }
            iterator.advance()
        }
        return tokens
    }
}
