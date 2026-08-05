package com.cimere.spellixir.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class ElixirLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        bufferEnd = endOffset
        tokenStart = startOffset
        locateToken()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = bufferEnd

    override fun advance() {
        tokenStart = tokenEnd
        locateToken()
    }

    private fun locateToken() {
        if (tokenStart >= bufferEnd) {
            tokenEnd = tokenStart
            tokenType = null
            return
        }

        tokenEnd = tokenStart + 1
        val first = buffer[tokenStart]
        tokenType = when {
            first.isWhitespace() -> scanWhile { it.isWhitespace() }.let { TokenType.WHITE_SPACE }
            first == '#' -> scanToEnd().let { ElixirTokenTypes.COMMENT }
            first == '@' && charAt(tokenStart + 1).isIdentifierStart() -> {
                scanIdentifier(tokenStart + 1)
                ElixirTokenTypes.MODULE_ATTRIBUTE
            }
            first == ':' && charAt(tokenStart + 1) in listOf('\'', '"') -> {
                scanQuoted(tokenStart + 1)
                ElixirTokenTypes.ATOM
            }
            first == ':' && charAt(tokenStart + 1).isAtomStart() -> {
                scanAtom()
                ElixirTokenTypes.ATOM
            }
            first in listOf('\'', '"') -> {
                scanQuoted(tokenStart)
                ElixirTokenTypes.STRING
            }
            first == '?' && tokenStart + 1 < bufferEnd -> {
                scanCharacter()
                ElixirTokenTypes.CHARACTER
            }
            first.isDigit() -> {
                if (scanNumber()) ElixirTokenTypes.NUMBER else TokenType.BAD_CHARACTER
            }
            first.isIdentifierStart() -> {
                scanIdentifier(tokenStart)
                val text = buffer.subSequence(tokenStart, tokenEnd).toString()
                when {
                    text in KEYWORDS -> ElixirTokenTypes.KEYWORD
                    text in WORD_OPERATORS -> ElixirTokenTypes.OPERATOR
                    text in ATOM_LITERALS -> ElixirTokenTypes.ATOM
                    first.isUpperCase() -> {
                        scanAliasSegments()
                        ElixirTokenTypes.ALIAS
                    }
                    else -> ElixirTokenTypes.IDENTIFIER
                }
            }
            first == '(' || first == ')' -> ElixirTokenTypes.PARENTHESES
            first == '[' || first == ']' -> ElixirTokenTypes.BRACKETS
            first == '{' || first == '}' -> ElixirTokenTypes.BRACES
            scanOperator() -> ElixirTokenTypes.OPERATOR
            else -> TokenType.BAD_CHARACTER
        }
    }

    private fun scanWhile(predicate: (Char) -> Boolean) {
        while (tokenEnd < bufferEnd && predicate(buffer[tokenEnd])) tokenEnd++
    }

    private fun scanToEnd() {
        tokenEnd = bufferEnd
    }

    private fun scanIdentifier(start: Int) {
        tokenEnd = start + 1
        while (tokenEnd < bufferEnd && buffer[tokenEnd].isIdentifierPart()) tokenEnd++
        if (charAt(tokenEnd) == '!' || charAt(tokenEnd) == '?') tokenEnd++
    }

    private fun scanAtom() {
        tokenEnd = tokenStart + 1
        if (charAt(tokenEnd).isIdentifierStart()) {
            scanIdentifier(tokenEnd)
        } else {
            while (tokenEnd < bufferEnd && buffer[tokenEnd].isOperatorCharacter()) tokenEnd++
        }
    }

    private fun scanAliasSegments() {
        while (charAt(tokenEnd) == '.' && charAt(tokenEnd + 1).isUpperCase()) {
            scanIdentifier(tokenEnd + 1)
        }
    }

    private fun scanQuoted(quoteOffset: Int) {
        val quote = buffer[quoteOffset]
        tokenEnd = quoteOffset + 1
        var escaped = false
        while (tokenEnd < bufferEnd) {
            val current = buffer[tokenEnd++]
            if (current == quote && !escaped) return
            escaped = current == '\\' && !escaped
            if (current != '\\') escaped = false
        }
    }

    private fun scanCharacter() {
        tokenEnd = tokenStart + 1
        val codePoint = Character.codePointAt(buffer, tokenEnd)
        tokenEnd += Character.charCount(codePoint)
        if (buffer[tokenStart + 1] == '\\') {
            tokenEnd = (tokenStart + 3).coerceAtMost(bufferEnd)
            if (charAt(tokenStart + 2) == 'u' && charAt(tokenStart + 3) == '{') {
                while (tokenEnd < bufferEnd && buffer[tokenEnd] != '}') tokenEnd++
                if (tokenEnd < bufferEnd) tokenEnd++
            }
        }
    }

    private fun scanNumber(): Boolean {
        tokenEnd = tokenStart + 1
        val basePrefix = charAt(tokenStart + 1).lowercaseChar()
        if (buffer[tokenStart] == '0' && basePrefix in listOf('b', 'o', 'x')) {
            tokenEnd += 1
            val digitsStart = tokenEnd
            val validDigit: (Char) -> Boolean = when (basePrefix) {
                'b' -> { character -> character in '0'..'1' }
                'o' -> { character -> character in '0'..'7' }
                else -> { character -> character.isDigit() || character.lowercaseChar() in 'a'..'f' }
            }
            scanWhile { validDigit(it) || it == '_' }
            val validEnd = tokenEnd
            scanWhile { it.isLetterOrDigit() || it == '_' }
            return validEnd > digitsStart && validEnd == tokenEnd
        }
        scanWhile { it.isDigit() || it == '_' }
        if (charAt(tokenEnd) == '.' && charAt(tokenEnd + 1).isDigit()) {
            tokenEnd++
            scanWhile { it.isDigit() || it == '_' }
        }
        if (charAt(tokenEnd).lowercaseChar() == 'e') {
            val exponentStart = tokenEnd
            tokenEnd++
            if (charAt(tokenEnd) == '+' || charAt(tokenEnd) == '-') tokenEnd++
            val digitsStart = tokenEnd
            scanWhile { it.isDigit() || it == '_' }
            if (tokenEnd == digitsStart) tokenEnd = exponentStart
        }
        return true
    }

    private fun scanOperator(): Boolean {
        val operator = OPERATORS.firstOrNull { operator ->
            tokenStart + operator.length <= bufferEnd &&
                buffer.subSequence(tokenStart, tokenStart + operator.length).toString() == operator
        } ?: return false
        tokenEnd = tokenStart + operator.length
        return true
    }

    private fun charAt(offset: Int): Char = if (offset in 0 until bufferEnd) buffer[offset] else '\u0000'

    private fun Char.isIdentifierStart(): Boolean = this == '_' || isLetter()
    private fun Char.isAtomStart(): Boolean = isIdentifierStart() || isOperatorCharacter()
    private fun Char.isIdentifierPart(): Boolean = this == '_' || isLetterOrDigit()
    private fun Char.isOperatorCharacter(): Boolean = this in "+-*/\\|<>=~&^!.:"

    companion object {
        private val KEYWORDS = setOf("after", "catch", "do", "else", "end", "fn", "rescue")
        private val WORD_OPERATORS = setOf("and", "in", "not", "or", "when")
        private val ATOM_LITERALS = setOf("false", "nil", "true")

        private val OPERATORS = listOf(
            "..//", "<<<", ">>>", "<<~", "~>>", "<~>", "<|>", "+++", "---", "...", "^^^", "~~~",
            "&&&", "|||", "===", "!==", "**", "=~", "==", "!=", "<=", ">=", "&&", "||", "++", "--",
            "<>", "|>", "<~", "~>", "<-", "->", "=>", "::", "\\\\", "..", "//", "+", "-", "*",
            "/", "=", "<", ">", "!", "^", "&", "|", "@", ".", ",", ";", "%", ":",
        ).sortedByDescending(String::length)
    }
}
