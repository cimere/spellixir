package com.cimere.spellixir.lang

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class ElixirSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = ElixirLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = pack(ATTRIBUTES[tokenType])

    companion object {
        private val ATTRIBUTES = buildMap {
            ElixirLexicalVocabulary.categories.forEach { category ->
                put(
                    category.tokenType,
                    TextAttributesKey.createTextAttributesKey(
                        category.highlightingKeyName,
                        category.fallbackStyle,
                    ),
                )
            }
            put(
                TokenType.BAD_CHARACTER,
                TextAttributesKey.createTextAttributesKey("ELIXIR_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER),
            )
        }
    }
}
