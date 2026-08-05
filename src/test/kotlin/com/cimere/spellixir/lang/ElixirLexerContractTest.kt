package com.cimere.spellixir.lang

import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ElixirLexerContractTest : BasePlatformTestCase() {
    fun testCoversCoreSingleLineSyntaxContiguouslyWithNestedDelimiterBoundaries() {
        val source = "def run(value), do: [Demo.Item, {:ok, value + 0b1010}, ?\\n, @name, :'ready now'] # done"

        val tokens = lex(source)

        assertEquals(source, tokens.joinToString("") { it.text })
        assertTrue(tokens.all { it.end > it.start })
        assertEquals(
            listOf(
                "IDENTIFIER", "WHITE_SPACE", "IDENTIFIER", "PARENTHESES", "IDENTIFIER", "PARENTHESES",
                "OPERATOR", "WHITE_SPACE", "KEYWORD", "OPERATOR", "WHITE_SPACE", "BRACKETS", "ALIAS",
                "OPERATOR", "WHITE_SPACE", "BRACES", "ATOM", "OPERATOR", "WHITE_SPACE", "IDENTIFIER",
                "WHITE_SPACE", "OPERATOR", "WHITE_SPACE", "NUMBER", "BRACES", "OPERATOR", "WHITE_SPACE",
                "CHARACTER", "OPERATOR", "WHITE_SPACE", "MODULE_ATTRIBUTE", "OPERATOR", "WHITE_SPACE",
                "ATOM", "BRACKETS", "WHITE_SPACE", "COMMENT",
            ),
            tokens.map { it.type.toString() },
        )
    }

    fun testInvalidCharacterIsLocalAndFollowingHighlightingContinues() {
        val source = "left § :ok"

        val tokens = lex(source)

        assertEquals(source, tokens.joinToString("") { it.text })
        assertEquals(
            listOf("IDENTIFIER", "WHITE_SPACE", "BAD_CHARACTER", "WHITE_SPACE", "ATOM"),
            tokens.map { it.type.toString() },
        )
        assertEquals(TokenType.BAD_CHARACTER, tokens[2].type)
        assertEquals("§", tokens[2].text)
    }

    fun testRestartingAtEveryTokenBoundaryProducesEquivalentSuffix() {
        val source = "@answer :\"quoted atom\" Demo.Nested 0x2A 1.5e-2 ?\\u{1F680} § end"
        val complete = lex(source)

        for (tokenIndex in complete.indices) {
            val token = complete[tokenIndex]
            assertEquals(
                "restart at offset ${token.start}",
                complete.drop(tokenIndex),
                lex(source, token.start, token.state),
            )
        }
    }

    fun testKeepsNumericFormsSymbolicAtomsAndUnicodeCharactersWhole() {
        val source = "42 1_000 0b1010 0o755 0x2A 3.14 1.5e-2 :== ?λ ?🚀"

        val tokens = lex(source).filter { it.type.toString() != "WHITE_SPACE" }

        assertEquals(
            listOf(
                "42" to "NUMBER",
                "1_000" to "NUMBER",
                "0b1010" to "NUMBER",
                "0o755" to "NUMBER",
                "0x2A" to "NUMBER",
                "3.14" to "NUMBER",
                "1.5e-2" to "NUMBER",
                ":==" to "ATOM",
                "?λ" to "CHARACTER",
                "?🚀" to "CHARACTER",
            ),
            tokens.map { it.text to it.type.toString() },
        )
    }

    fun testRecognizesEveryLexicalOperatorFamilyAsOneToken() {
        val source = listOf(
            "**", "+++", "---", "...", "..//", "<<<", ">>>", "<<~", "~>>", "<~", "~>", "<~>",
            "<|>", "^^^", "~~~", "&&&", "|||", "@", "and", "or", "not", "in", "when",
        ).joinToString(" ")

        val tokens = lex(source).filter { it.type.toString() != "WHITE_SPACE" }

        assertEquals(source.split(" "), tokens.map { it.text })
        assertTrue(tokens.all { it.type == ElixirTokenTypes.OPERATOR })
    }

    fun testMalformedBasePrefixedNumberIsInvalidWithoutCorruptingFollowingTokens() {
        val source = "0xZZ :ok"

        val tokens = lex(source).filter { it.type.toString() != "WHITE_SPACE" }

        assertEquals(
            listOf("0xZZ" to "BAD_CHARACTER", ":ok" to "ATOM"),
            tokens.map { it.text to it.type.toString() },
        )
    }

    private fun lex(source: String, startOffset: Int = 0, initialState: Int = 0): List<Token> {
        val lexer = SyntaxHighlighterFactory
            .getSyntaxHighlighter(ElixirLanguage, project, null)
            .highlightingLexer
        val tokens = mutableListOf<Token>()

        lexer.start(source, startOffset, source.length, initialState)
        while (lexer.tokenType != null) {
            val tokenType = requireNotNull(lexer.tokenType)
            tokens += Token(
                tokenType,
                source.substring(lexer.tokenStart, lexer.tokenEnd),
                lexer.tokenStart,
                lexer.tokenEnd,
                lexer.state,
            )
            lexer.advance()
        }
        return tokens
    }

    private data class Token(
        val type: IElementType,
        val text: String,
        val start: Int,
        val end: Int,
        val state: Int,
    )
}
