package com.cimere.spellixir.lang

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class ElixirSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = ElixirLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = pack(ATTRIBUTES[tokenType])

    companion object {
        val KEYWORD = key("ELIXIR_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val IDENTIFIER = key("ELIXIR_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val FUNCTION_DECLARATION = key(
            "ELIXIR_FUNCTION_DECLARATION",
            DefaultLanguageHighlighterColors.FUNCTION_DECLARATION,
        )
        val MEMBER_ACCESS = key("ELIXIR_MEMBER_ACCESS", DefaultLanguageHighlighterColors.FUNCTION_CALL)
        val ALIAS = key("ELIXIR_ALIAS", DefaultLanguageHighlighterColors.CONSTANT)
        val ATOM = key("ELIXIR_ATOM", DefaultLanguageHighlighterColors.CONSTANT)
        val LITERAL = key("ELIXIR_LITERAL", DefaultLanguageHighlighterColors.CONSTANT)
        val STRING = key("ELIXIR_STRING", DefaultLanguageHighlighterColors.STRING)
        val MODULE_ATTRIBUTE = key("ELIXIR_MODULE_ATTRIBUTE", DefaultLanguageHighlighterColors.METADATA)
        val OPERATOR = key("ELIXIR_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val NUMBER = key("ELIXIR_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val CHARACTER = key("ELIXIR_CHARACTER", DefaultLanguageHighlighterColors.STRING)
        val SIGIL = key("ELIXIR_SIGIL", DefaultLanguageHighlighterColors.STRING)
        val CAPTURE = key("ELIXIR_CAPTURE", DefaultLanguageHighlighterColors.PARAMETER)
        val ESCAPE = key("ELIXIR_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
        val INTERPOLATION = key("ELIXIR_INTERPOLATION", DefaultLanguageHighlighterColors.BRACES)
        val COMMENT = key("ELIXIR_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val PUNCTUATION = key("ELIXIR_PUNCTUATION", DefaultLanguageHighlighterColors.COMMA)
        val PARENTHESES = key("ELIXIR_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
        val BRACKETS = key("ELIXIR_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
        val BRACES = key("ELIXIR_BRACES", DefaultLanguageHighlighterColors.BRACES)
        val BAD_CHARACTER = key("ELIXIR_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private val ATTRIBUTES = mapOf(
            ElixirTokenTypes.KEYWORD to KEYWORD,
            ElixirTokenTypes.IDENTIFIER to IDENTIFIER,
            ElixirTokenTypes.FUNCTION_DECLARATION to FUNCTION_DECLARATION,
            ElixirTokenTypes.MEMBER_ACCESS to MEMBER_ACCESS,
            ElixirTokenTypes.ALIAS to ALIAS,
            ElixirTokenTypes.ATOM to ATOM,
            ElixirTokenTypes.LITERAL to LITERAL,
            ElixirTokenTypes.STRING to STRING,
            ElixirTokenTypes.MODULE_ATTRIBUTE to MODULE_ATTRIBUTE,
            ElixirTokenTypes.OPERATOR to OPERATOR,
            ElixirTokenTypes.NUMBER to NUMBER,
            ElixirTokenTypes.CHARACTER to CHARACTER,
            ElixirTokenTypes.SIGIL to SIGIL,
            ElixirTokenTypes.CAPTURE to CAPTURE,
            ElixirTokenTypes.ESCAPE to ESCAPE,
            ElixirTokenTypes.INTERPOLATION to INTERPOLATION,
            ElixirTokenTypes.COMMENT to COMMENT,
            ElixirTokenTypes.PUNCTUATION to PUNCTUATION,
            ElixirTokenTypes.PARENTHESES to PARENTHESES,
            ElixirTokenTypes.BRACKETS to BRACKETS,
            ElixirTokenTypes.BRACES to BRACES,
            TokenType.BAD_CHARACTER to BAD_CHARACTER,
        )

        private fun key(name: String, fallback: TextAttributesKey): TextAttributesKey =
            TextAttributesKey.createTextAttributesKey(name, fallback)
    }
}
