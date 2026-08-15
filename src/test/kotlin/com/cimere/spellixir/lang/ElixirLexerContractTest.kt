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
                "KEYWORD", "WHITE_SPACE", "FUNCTION_DECLARATION", "PARENTHESES", "IDENTIFIER", "PARENTHESES",
                "PUNCTUATION", "WHITE_SPACE", "ATOM", "WHITE_SPACE", "BRACKETS", "ALIAS", "OPERATOR",
                "MEMBER_ACCESS", "PUNCTUATION", "WHITE_SPACE", "BRACES", "ATOM", "PUNCTUATION", "WHITE_SPACE",
                "IDENTIFIER", "WHITE_SPACE", "OPERATOR", "WHITE_SPACE", "NUMBER", "BRACES", "PUNCTUATION",
                "WHITE_SPACE", "CHARACTER", "PUNCTUATION", "WHITE_SPACE", "MODULE_ATTRIBUTE", "PUNCTUATION",
                "WHITE_SPACE", "ATOM", "BRACKETS", "WHITE_SPACE", "COMMENT",
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

    fun testCommentStopsAtLineBoundaryAndFollowingCodeRemainsTokenized() {
        val source = "# Pattern matching\ncase value do\nend"

        val tokens = lex(source)

        assertEquals(source, tokens.joinToString("") { it.text })
        assertEquals(
            listOf(
                "# Pattern matching" to "COMMENT",
                "\n" to "WHITE_SPACE",
                "case" to "KEYWORD",
                " " to "WHITE_SPACE",
                "value" to "IDENTIFIER",
                " " to "WHITE_SPACE",
                "do" to "KEYWORD",
                "\n" to "WHITE_SPACE",
                "end" to "KEYWORD",
            ),
            tokens.map { it.text to it.type.toString() },
        )
    }

    fun testRestartingAtEveryTokenBoundaryProducesEquivalentSuffix() {
        val source = "def full_name(value), do: \"hello\\n#{value}\" @answer Demo.Nested.call 0x2A ?\\u{1F680} § end"
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
        assertTrue(tokens.all { it.type == ElixirLexicalVocabulary.OPERATOR })
    }

    fun testMalformedBasePrefixedNumberIsInvalidWithoutCorruptingFollowingTokens() {
        val source = "0xZZ :ok"

        val tokens = lex(source).filter { it.type.toString() != "WHITE_SPACE" }

        assertEquals(
            listOf("0xZZ" to "BAD_CHARACTER", ":ok" to "ATOM"),
            tokens.map { it.text to it.type.toString() },
        )
    }

    fun testExampleSiteFormsKeepExpectedTokenBoundaries() {
        val source = "%User{email: \"#{value}\"}, ~S|literal #{text}|"

        assertEquals(
            listOf(
                "%" to "PUNCTUATION",
                "User" to "ALIAS",
                "{" to "BRACES",
                "email:" to "ATOM",
                " " to "WHITE_SPACE",
                "\"" to "STRING",
                "#{" to "INTERPOLATION",
                "value" to "IDENTIFIER",
                "}" to "INTERPOLATION",
                "\"" to "STRING",
                "}" to "BRACES",
                "," to "PUNCTUATION",
                " " to "WHITE_SPACE",
                "~S|literal #{text}|" to "SIGIL",
            ),
            lex(source).map { it.text to it.type.toString() },
        )
    }

    fun testCorpusVocabularyRemainsContiguousAndValid() {
        val source = """
            @callback fetch(%User{email: email}) :: {:ok, String.t()} | :error
            def read!(value \\ nil), do: Enum.map([value], & &1)
            message = "value=#{%{nested: value}}\n"
            words = ~w(one two)a
        """.trimIndent()

        val tokens = lex(source)

        assertEquals(source, tokens.joinToString("") { it.text })
        assertTrue(tokens.all { it.end > it.start })
        assertTrue(tokens.none { it.type == TokenType.BAD_CHARACTER })
        assertTrue(
            tokens.map { it.type }.containsAll(
                listOf(
                    ElixirLexicalVocabulary.MODULE_ATTRIBUTE,
                    ElixirLexicalVocabulary.ALIAS,
                    ElixirLexicalVocabulary.MEMBER_ACCESS,
                    ElixirLexicalVocabulary.FUNCTION_DECLARATION,
                    ElixirLexicalVocabulary.CAPTURE,
                    ElixirLexicalVocabulary.INTERPOLATION,
                    ElixirLexicalVocabulary.ESCAPE,
                    ElixirLexicalVocabulary.SIGIL,
                ),
            ),
        )
    }

    fun testTokenizesQuotedAndMultilineFormsWithEscapesAndInterpolation() {
        val source = "\"double\\n#{name}\" 'charlist\\t#{value}' \"\"\"\nhello #{user}\n\"\"\" '''\nworld #{item}\n'''"

        assertEquals(
            listOf(
                "\"double" to "STRING", "\\n" to "ESCAPE", "#{" to "INTERPOLATION",
                "name" to "IDENTIFIER", "}" to "INTERPOLATION", "\"" to "STRING",
                " " to "WHITE_SPACE",
                "'charlist" to "STRING", "\\t" to "ESCAPE", "#{" to "INTERPOLATION",
                "value" to "IDENTIFIER", "}" to "INTERPOLATION", "'" to "STRING",
                " " to "WHITE_SPACE",
                "\"\"\"\nhello " to "STRING", "#{" to "INTERPOLATION", "user" to "IDENTIFIER",
                "}" to "INTERPOLATION", "\n\"\"\"" to "STRING",
                " " to "WHITE_SPACE",
                "'''\nworld " to "STRING", "#{" to "INTERPOLATION", "item" to "IDENTIFIER",
                "}" to "INTERPOLATION", "\n'''" to "STRING",
            ),
            lex(source).map { it.text to it.type.toString() },
        )
    }

    fun testTokenizesInterpolatingAndLiteralSigilsWithModifiers() {
        val source = "~s(foo\\) #{name})u ~S|literal #{name}\\|| ~j<{#{value}}>u8"

        assertEquals(
            listOf(
                "~s(foo" to "SIGIL", "\\)" to "ESCAPE", " " to "SIGIL",
                "#{" to "INTERPOLATION", "name" to "IDENTIFIER", "}" to "INTERPOLATION",
                ")u" to "SIGIL", " " to "WHITE_SPACE",
                "~S|literal #{name}\\||" to "SIGIL", " " to "WHITE_SPACE",
                "~j<{" to "SIGIL", "#{" to "INTERPOLATION", "value" to "IDENTIFIER",
                "}" to "INTERPOLATION", "}>u8" to "SIGIL",
            ),
            lex(source).map { it.text to it.type.toString() },
        )
    }

    fun testUnfinishedQuotedFormsRemainContiguousAndRestartable() {
        val sources = listOf(
            "\"unfinished\\n#{value",
            "'''\nunfinished #{%{value: item}}",
            "~r/foo #{call(%{value: item})}",
            "~S|literal #{not_interpolation}",
        )

        for (source in sources) {
            val complete = lex(source)
            assertEquals(source, complete.joinToString("") { it.text })
            assertTrue(complete.all { it.end > it.start })
            for (tokenIndex in complete.indices) {
                val token = complete[tokenIndex]
                assertEquals(
                    "restart '$source' at offset ${token.start}",
                    complete.drop(tokenIndex),
                    lex(source, token.start, token.state),
                )
            }
        }
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
