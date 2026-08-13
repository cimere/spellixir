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
    private var lexicalState = DEFAULT_STATE
    private var tokenState = DEFAULT_STATE
    private var nextState = DEFAULT_STATE

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        bufferEnd = endOffset
        tokenStart = startOffset
        lexicalState = initialState
        locateToken()
    }

    override fun getState(): Int = tokenState
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = bufferEnd

    override fun advance() {
        tokenStart = tokenEnd
        lexicalState = nextState
        locateToken()
    }

    private fun locateToken() {
        tokenState = lexicalState
        nextState = if (tokenState.isInterpolationState()) tokenState else DEFAULT_STATE
        if (tokenStart >= bufferEnd) {
            tokenEnd = tokenStart
            tokenType = null
            return
        }

        tokenEnd = tokenStart + 1
        val first = buffer[tokenStart]
        if (tokenState == DOUBLE_STRING_STATE) {
            tokenType = locateStringContinuation()
            return
        }
        if (tokenState.isInterpolationState() && first == '}') {
            if (tokenState == INTERPOLATION_STATE) {
                nextState = DOUBLE_STRING_STATE
                tokenType = ElixirTokenTypes.INTERPOLATION
            } else {
                nextState = tokenState - 1
                tokenType = ElixirTokenTypes.BRACES
            }
            return
        }
        tokenType = when {
            first.isWhitespace() -> {
                scanWhile { it.isWhitespace() }
                nextState = tokenState
                TokenType.WHITE_SPACE
            }
            first == '#' -> scanComment().let { ElixirTokenTypes.COMMENT }
            first == '@' && charAt(tokenStart + 1).isIdentifierStart() -> {
                scanIdentifier(tokenStart + 1)
                ElixirTokenTypes.MODULE_ATTRIBUTE
            }
            first == ':' && charAt(tokenStart + 1) in listOf('\'', '"') -> {
                scanQuoted(tokenStart + 1)
                ElixirTokenTypes.ATOM
            }
            first == ':' && charAt(tokenStart + 1) != ':' && charAt(tokenStart + 1).isAtomStart() -> {
                scanAtom()
                ElixirTokenTypes.ATOM
            }
            first in listOf('\'', '"') -> {
                if (startsHeredoc(first)) scanHeredoc(first) else scanStringStart(first)
                ElixirTokenTypes.STRING
            }
            first == '?' && tokenStart + 1 < bufferEnd -> {
                scanCharacter()
                ElixirTokenTypes.CHARACTER
            }
            first == '&' && charAt(tokenStart + 1).isDigit() -> {
                scanWhile { it.isDigit() }
                ElixirTokenTypes.CAPTURE
            }
            first == '~' && scanSigil() -> ElixirTokenTypes.SIGIL
            first.isDigit() -> {
                if (scanNumber()) ElixirTokenTypes.NUMBER else TokenType.BAD_CHARACTER
            }
            first.isIdentifierStart() -> {
                scanIdentifier(tokenStart)
                val text = buffer.subSequence(tokenStart, tokenEnd).toString()
                when {
                    tokenState == EXPECT_FUNCTION_NAME_STATE -> ElixirTokenTypes.FUNCTION_DECLARATION
                    tokenState == EXPECT_MEMBER_STATE -> ElixirTokenTypes.MEMBER_ACCESS
                    charAt(tokenEnd) == ':' && charAt(tokenEnd + 1) != ':' -> {
                        tokenEnd++
                        ElixirTokenTypes.ATOM
                    }
                    text in FUNCTION_DEFINITION_KEYWORDS -> {
                        nextState = EXPECT_FUNCTION_NAME_STATE
                        ElixirTokenTypes.KEYWORD
                    }
                    text in KEYWORDS -> ElixirTokenTypes.KEYWORD
                    text in WORD_OPERATORS -> ElixirTokenTypes.OPERATOR
                    text in ATOM_LITERALS -> ElixirTokenTypes.LITERAL
                    first.isUpperCase() -> ElixirTokenTypes.ALIAS
                    else -> ElixirTokenTypes.IDENTIFIER
                }
            }
            first == '(' || first == ')' -> ElixirTokenTypes.PARENTHESES
            first == '[' || first == ']' -> ElixirTokenTypes.BRACKETS
            first == '{' || first == '}' -> {
                if (first == '{' && tokenState.isInterpolationState()) nextState = tokenState + 1
                ElixirTokenTypes.BRACES
            }
            first == ',' || first == ';' || first == '%' -> ElixirTokenTypes.PUNCTUATION
            scanOperator() -> {
                if (buffer.subSequence(tokenStart, tokenEnd).toString() == ".") nextState = EXPECT_MEMBER_STATE
                ElixirTokenTypes.OPERATOR
            }
            else -> TokenType.BAD_CHARACTER
        }
    }

    private fun scanWhile(predicate: (Char) -> Boolean) {
        while (tokenEnd < bufferEnd && predicate(buffer[tokenEnd])) tokenEnd++
    }

    private fun scanComment() {
        while (tokenEnd < bufferEnd && buffer[tokenEnd] != '\n' && buffer[tokenEnd] != '\r') tokenEnd++
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

    private fun startsHeredoc(quote: Char): Boolean =
        charAt(tokenStart + 1) == quote && charAt(tokenStart + 2) == quote

    private fun scanHeredoc(quote: Char) {
        tokenEnd = tokenStart + 3
        while (tokenEnd < bufferEnd) {
            if (charAt(tokenEnd) == quote && charAt(tokenEnd + 1) == quote && charAt(tokenEnd + 2) == quote) {
                tokenEnd += 3
                return
            }
            tokenEnd++
        }
    }

    private fun scanStringStart(quote: Char) {
        tokenEnd = tokenStart + 1
        if (quote != '"') {
            scanQuoted(tokenStart)
            return
        }
        scanStringContent()
    }

    private fun locateStringContinuation(): IElementType {
        if (buffer[tokenStart] == '\\') {
            tokenEnd = (tokenStart + 2).coerceAtMost(bufferEnd)
            nextState = DOUBLE_STRING_STATE
            return ElixirTokenTypes.ESCAPE
        }
        if (buffer[tokenStart] == '#' && charAt(tokenStart + 1) == '{') {
            tokenEnd = tokenStart + 2
            nextState = INTERPOLATION_STATE
            return ElixirTokenTypes.INTERPOLATION
        }
        tokenEnd = tokenStart
        scanStringContent()
        return ElixirTokenTypes.STRING
    }

    private fun scanStringContent() {
        while (tokenEnd < bufferEnd) {
            if (buffer[tokenEnd] == '\\' || (buffer[tokenEnd] == '#' && charAt(tokenEnd + 1) == '{')) {
                nextState = DOUBLE_STRING_STATE
                return
            }
            if (buffer[tokenEnd++] == '"') return
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

    private fun scanSigil(): Boolean {
        if (!charAt(tokenStart + 1).isLetter()) return false
        var delimiterOffset = tokenStart + 2
        if (charAt(tokenStart + 1).isUpperCase()) {
            while (charAt(delimiterOffset).isUpperCase()) delimiterOffset++
        }
        val opening = charAt(delimiterOffset)
        val closing = SIGIL_DELIMITERS[opening] ?: return false
        tokenEnd = delimiterOffset + 1
        var depth = 1
        var escaped = false
        while (tokenEnd < bufferEnd) {
            val current = buffer[tokenEnd++]
            if (!escaped && opening != closing && current == opening) depth++
            if (!escaped && current == closing && --depth == 0) {
                scanWhile { it.isLetterOrDigit() }
                return true
            }
            escaped = current == '\\' && !escaped
            if (current != '\\') escaped = false
        }
        return true
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
    private fun Int.isInterpolationState(): Boolean = this >= INTERPOLATION_STATE

    companion object {
        private const val DEFAULT_STATE = 0
        private const val EXPECT_FUNCTION_NAME_STATE = 1
        private const val EXPECT_MEMBER_STATE = 2
        private const val DOUBLE_STRING_STATE = 3
        private const val INTERPOLATION_STATE = 4

        private val FUNCTION_DEFINITION_KEYWORDS = setOf("def", "defguard", "defmacro", "defp")
        private val KEYWORDS = setOf(
            "after", "alias", "case", "catch", "cond", "def", "defdelegate", "defexception", "defguard", "defimpl",
            "defmacro", "defmodule", "defp", "defprotocol", "defstruct", "do", "else", "end", "fn",
            "for", "if", "import", "quote", "receive", "require", "rescue", "try", "unless", "unquote",
            "unquote_splicing", "use", "with",
        )
        private val WORD_OPERATORS = setOf("and", "in", "not", "or", "when")
        private val ATOM_LITERALS = setOf("false", "nil", "true")

        private val OPERATORS = listOf(
            "..//", "<<<", ">>>", "<<~", "~>>", "<~>", "<|>", "+++", "---", "...", "^^^", "~~~",
            "&&&", "|||", "===", "!==", "**", "=~", "==", "!=", "<=", ">=", "&&", "||", "++", "--",
            "<>", "|>", "<~", "~>", "<-", "->", "=>", "::", "\\\\", "..", "//", "+", "-", "*",
            "/", "=", "<", ">", "!", "^", "&", "|", "@", ".", ":",
        ).sortedByDescending(String::length)

        private val SIGIL_DELIMITERS = mapOf(
            '/' to '/', '|' to '|', '"' to '"', '\'' to '\'', '(' to ')', '[' to ']', '{' to '}', '<' to '>',
        )
    }
}
