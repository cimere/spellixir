package com.cimere.spellixir.lang

import com.cimere.spellixir.lang.psi.ElixirTypes
import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

internal class ElixirParserLexer(
    private val delegate: ElixirLexer = ElixirLexer(),
) : LexerBase() {
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        delegate.start(buffer, startOffset, endOffset, initialState)
    }

    override fun getState(): Int = delegate.state
    override fun getTokenType(): IElementType? {
        val tokenType = delegate.tokenType ?: return null
        return TOKEN_TYPES[tokenType] ?: tokenType
    }
    override fun getTokenStart(): Int = delegate.tokenStart
    override fun getTokenEnd(): Int = delegate.tokenEnd
    override fun advance() = delegate.advance()
    override fun getBufferSequence(): CharSequence = delegate.bufferSequence
    override fun getBufferEnd(): Int = delegate.bufferEnd

    companion object {
        // Whitespace, comments, and bad characters remain JetBrains/adapter tokens.
        private val TOKEN_TYPES = mapOf(
            ElixirLexicalVocabulary.ALIAS to ElixirTypes.ALIAS,
            ElixirLexicalVocabulary.ATOM to ElixirTypes.ATOM,
            ElixirLexicalVocabulary.BRACES to ElixirTypes.BRACES,
            ElixirLexicalVocabulary.BRACKETS to ElixirTypes.BRACKETS,
            ElixirLexicalVocabulary.CAPTURE to ElixirTypes.CAPTURE,
            ElixirLexicalVocabulary.CHARACTER to ElixirTypes.CHARACTER,
            ElixirLexicalVocabulary.ESCAPE to ElixirTypes.ESCAPE,
            ElixirLexicalVocabulary.FUNCTION_DECLARATION to ElixirTypes.FUNCTION_DECLARATION,
            ElixirLexicalVocabulary.IDENTIFIER to ElixirTypes.IDENTIFIER,
            ElixirLexicalVocabulary.INTERPOLATION to ElixirTypes.INTERPOLATION,
            ElixirLexicalVocabulary.KEYWORD to ElixirTypes.KEYWORD,
            ElixirLexicalVocabulary.LITERAL to ElixirTypes.LITERAL,
            ElixirLexicalVocabulary.MEMBER_ACCESS to ElixirTypes.MEMBER_ACCESS,
            ElixirLexicalVocabulary.MODULE_ATTRIBUTE to ElixirTypes.MODULE_ATTRIBUTE,
            ElixirLexicalVocabulary.NUMBER to ElixirTypes.NUMBER,
            ElixirLexicalVocabulary.OPERATOR to ElixirTypes.OPERATOR,
            ElixirLexicalVocabulary.PARENTHESES to ElixirTypes.PARENTHESES,
            ElixirLexicalVocabulary.PUNCTUATION to ElixirTypes.PUNCTUATION,
            ElixirLexicalVocabulary.SIGIL to ElixirTypes.SIGIL,
            ElixirLexicalVocabulary.STRING to ElixirTypes.STRING,
        )
    }
}
