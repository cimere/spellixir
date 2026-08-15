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
                "," to "PUNCTUATION",
                "Demo" to "ALIAS",
                "," to "PUNCTUATION",
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
                "defmodule" to "ELIXIR_KEYWORD",
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
                "," to "ELIXIR_PUNCTUATION",
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

    fun testHighlightsRepresentativeEctoExampleForms() {
        val source = "defmodule User do use Ecto.Schema; schema \"users\" do field first_name: :string end end"

        assertEquals(
            listOf(
                "defmodule" to "ELIXIR_KEYWORD",
                "User" to "ELIXIR_ALIAS",
                "do" to "ELIXIR_KEYWORD",
                "use" to "ELIXIR_KEYWORD",
                "Ecto" to "ELIXIR_ALIAS",
                "." to "ELIXIR_OPERATOR",
                "Schema" to "ELIXIR_MEMBER_ACCESS",
                ";" to "ELIXIR_PUNCTUATION",
                "schema" to "ELIXIR_IDENTIFIER",
                "\"users\"" to "ELIXIR_STRING",
                "do" to "ELIXIR_KEYWORD",
                "field" to "ELIXIR_IDENTIFIER",
                "first_name:" to "ELIXIR_ATOM",
                ":string" to "ELIXIR_ATOM",
                "end" to "ELIXIR_KEYWORD",
                "end" to "ELIXIR_KEYWORD",
            ),
            highlight(source),
        )
    }

    fun testHighlightsDefinitionDirectivesPunctuationAndSigils() {
        val source = "alias Demo.User, as: User; defp valid?(value), do: value =~ ~r/^[a-z]+$/iu"

        assertEquals(
            listOf(
                "alias" to "ELIXIR_KEYWORD",
                "Demo" to "ELIXIR_ALIAS",
                "." to "ELIXIR_OPERATOR",
                "User" to "ELIXIR_MEMBER_ACCESS",
                "," to "ELIXIR_PUNCTUATION",
                "as:" to "ELIXIR_ATOM",
                "User" to "ELIXIR_ALIAS",
                ";" to "ELIXIR_PUNCTUATION",
                "defp" to "ELIXIR_KEYWORD",
                "valid?" to "ELIXIR_FUNCTION_DECLARATION",
                "(" to "ELIXIR_PARENTHESES",
                "value" to "ELIXIR_IDENTIFIER",
                ")" to "ELIXIR_PARENTHESES",
                "," to "ELIXIR_PUNCTUATION",
                "do:" to "ELIXIR_ATOM",
                "value" to "ELIXIR_IDENTIFIER",
                "=~" to "ELIXIR_OPERATOR",
                "~r/^[a-z]+$/iu" to "ELIXIR_SIGIL",
            ),
            highlight(source),
        )
    }

    fun testHighlightsCoreControlFlowForms() {
        val source = "case cond if unless for with try receive"

        assertEquals(
            source.split(" ").map { it to "ELIXIR_KEYWORD" },
            highlight(source),
        )
    }

    fun testHighlightsFunctionNamesAtDefinitionSites() {
        val source = "def full_name(user), do: user.name; full_name(user)"

        assertEquals(
            listOf(
                "def" to "ELIXIR_KEYWORD",
                "full_name" to "ELIXIR_FUNCTION_DECLARATION",
                "(" to "ELIXIR_PARENTHESES",
                "user" to "ELIXIR_IDENTIFIER",
                ")" to "ELIXIR_PARENTHESES",
                "," to "ELIXIR_PUNCTUATION",
                "do:" to "ELIXIR_ATOM",
                "user" to "ELIXIR_IDENTIFIER",
                "." to "ELIXIR_OPERATOR",
                "name" to "ELIXIR_MEMBER_ACCESS",
                ";" to "ELIXIR_PUNCTUATION",
                "full_name" to "ELIXIR_IDENTIFIER",
                "(" to "ELIXIR_PARENTHESES",
                "user" to "ELIXIR_IDENTIFIER",
                ")" to "ELIXIR_PARENTHESES",
            ),
            highlight(source),
        )
    }

    fun testHighlightsQualifiedCallsAndFieldAccess() {
        val source = "Map.get(%User{email: value}, :email); user.email; Enum.filter(items)"

        assertEquals(
            listOf(
                "Map" to "ELIXIR_ALIAS",
                "." to "ELIXIR_OPERATOR",
                "get" to "ELIXIR_MEMBER_ACCESS",
                "(" to "ELIXIR_PARENTHESES",
                "%" to "ELIXIR_PUNCTUATION",
                "User" to "ELIXIR_ALIAS",
                "{" to "ELIXIR_BRACES",
                "email:" to "ELIXIR_ATOM",
                "value" to "ELIXIR_IDENTIFIER",
                "}" to "ELIXIR_BRACES",
                "," to "ELIXIR_PUNCTUATION",
                ":email" to "ELIXIR_ATOM",
                ")" to "ELIXIR_PARENTHESES",
                ";" to "ELIXIR_PUNCTUATION",
                "user" to "ELIXIR_IDENTIFIER",
                "." to "ELIXIR_OPERATOR",
                "email" to "ELIXIR_MEMBER_ACCESS",
                ";" to "ELIXIR_PUNCTUATION",
                "Enum" to "ELIXIR_ALIAS",
                "." to "ELIXIR_OPERATOR",
                "filter" to "ELIXIR_MEMBER_ACCESS",
                "(" to "ELIXIR_PARENTHESES",
                "items" to "ELIXIR_IDENTIFIER",
                ")" to "ELIXIR_PARENTHESES",
            ),
            highlight(source),
        )
    }

    fun testHighlightsLiteralsCapturesAndCorrectOperatorBoundaries() {
        val source = "true false nil &1 value :: term \\\\ default"

        assertEquals(
            listOf(
                "true" to "ELIXIR_LITERAL",
                "false" to "ELIXIR_LITERAL",
                "nil" to "ELIXIR_LITERAL",
                "&1" to "ELIXIR_CAPTURE",
                "value" to "ELIXIR_IDENTIFIER",
                "::" to "ELIXIR_OPERATOR",
                "term" to "ELIXIR_IDENTIFIER",
                "\\\\" to "ELIXIR_OPERATOR",
                "default" to "ELIXIR_IDENTIFIER",
            ),
            highlight(source),
        )
    }

    fun testHighlightsHeredocsEscapesAndInterpolation() {
        val source = "\"hello\\n#{name}\" \"\"\"long\ntext\"\"\""

        assertEquals(
            listOf(
                "\"hello" to "ELIXIR_STRING",
                "\\n" to "ELIXIR_ESCAPE",
                "#{" to "ELIXIR_INTERPOLATION",
                "name" to "ELIXIR_IDENTIFIER",
                "}" to "ELIXIR_INTERPOLATION",
                "\"" to "ELIXIR_STRING",
                "\"\"\"long\ntext\"\"\"" to "ELIXIR_STRING",
            ),
            highlight(source),
        )
    }

    fun testHighlightsGenericSigilsWithPairedDelimitersAndModifiers() {
        val source = "~w(one two)a ~S|literal #{text}| ~JSON<{\"ok\":true}>u8"

        assertEquals(
            listOf(
                "~w(one two)a" to "ELIXIR_SIGIL",
                "~S|literal #{text}|" to "ELIXIR_SIGIL",
                "~JSON<{\"ok\":true}>u8" to "ELIXIR_SIGIL",
            ),
            highlight(source),
        )
    }

    fun testHighlightsInterpolationInsideCharlistHeredocAndLowercaseSigil() {
        val source = "'hi #{name}' \"\"\"\nvalue=#{value}\n\"\"\" ~r/foo #{bar}/iu ~S|#{literal}|"

        assertEquals(
            listOf(
                "'hi " to "ELIXIR_STRING", "#{" to "ELIXIR_INTERPOLATION",
                "name" to "ELIXIR_IDENTIFIER", "}" to "ELIXIR_INTERPOLATION", "'" to "ELIXIR_STRING",
                "\"\"\"\nvalue=" to "ELIXIR_STRING", "#{" to "ELIXIR_INTERPOLATION",
                "value" to "ELIXIR_IDENTIFIER", "}" to "ELIXIR_INTERPOLATION", "\n\"\"\"" to "ELIXIR_STRING",
                "~r/foo " to "ELIXIR_SIGIL", "#{" to "ELIXIR_INTERPOLATION",
                "bar" to "ELIXIR_IDENTIFIER", "}" to "ELIXIR_INTERPOLATION", "/iu" to "ELIXIR_SIGIL",
                "~S|#{literal}|" to "ELIXIR_SIGIL",
            ),
            highlight(source),
        )
    }

    fun testUnfinishedOrdinaryStringDoesNotCorruptFollowingDeclarationHighlighting() {
        val source = "message = \"unfinished\ndef complete(value), do: :ok"

        assertEquals(
            listOf(
                "message" to "ELIXIR_IDENTIFIER",
                "=" to "ELIXIR_OPERATOR",
                "\"unfinished" to "ELIXIR_STRING",
                "def" to "ELIXIR_KEYWORD",
                "complete" to "ELIXIR_FUNCTION_DECLARATION",
                "(" to "ELIXIR_PARENTHESES",
                "value" to "ELIXIR_IDENTIFIER",
                ")" to "ELIXIR_PARENTHESES",
                "," to "ELIXIR_PUNCTUATION",
                "do:" to "ELIXIR_ATOM",
                ":ok" to "ELIXIR_ATOM",
            ),
            highlight(source),
        )
    }

    fun testIncrementalTypingAfterUnfinishedInterpolationHighlightsFollowingDeclaration() {
        myFixture.configureByText("incremental_interpolation.ex", "def message(value), do: \"hello #{value")
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)

        myFixture.type("\ndef run(value), do: :ok")
        myFixture.doHighlighting()

        val source = myFixture.editor.document.text
        assertEquals(
            listOf(
                "def" to "KEYWORD",
                "message" to "FUNCTION_DECLARATION",
                "def" to "KEYWORD",
                "run" to "FUNCTION_DECLARATION",
            ),
            editorTokens(source).filter { it.first == "def" || it.first == "message" || it.first == "run" },
        )
    }

    fun testIncrementalTypingAfterUnfinishedStringHighlightsFollowingDeclaration() {
        myFixture.configureByText("incremental_string.ex", "message = \"unfinished")
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)

        myFixture.type("\ndef complete(value), do: :ok")
        myFixture.doHighlighting()

        val source = myFixture.editor.document.text
        assertEquals(
            listOf(
                "def" to "KEYWORD",
                "complete" to "FUNCTION_DECLARATION",
            ),
            editorTokens(source).filter { it.first == "def" || it.first == "complete" },
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
