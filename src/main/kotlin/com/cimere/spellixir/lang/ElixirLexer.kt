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
        nextState = if (tokenState.keepsContext()) tokenState else DEFAULT_STATE
        if (tokenStart >= bufferEnd) {
            tokenEnd = tokenStart
            tokenType = null
            return
        }

        tokenEnd = tokenStart + 1
        val first = buffer[tokenStart]
        if (tokenState.isQuotedState()) {
            tokenType = locateQuotedContinuation(tokenState)
            return
        }
        if (tokenState.isSigilState()) {
            tokenType = locateSigilContinuation(tokenState)
            return
        }
        if (tokenState.isInterpolationState() && first == '}') {
            if (tokenState.interpolationDepth() == 1) {
                nextState = tokenState.interpolationReturnState()
                tokenType = ElixirLexicalVocabulary.INTERPOLATION
            } else {
                nextState = tokenState.withInterpolationDepth(tokenState.interpolationDepth() - 1)
                tokenType = ElixirLexicalVocabulary.BRACES
            }
            return
        }
        tokenType = when {
            first.isWhitespace() -> {
                scanWhile { it.isWhitespace() }
                nextState = tokenState
                TokenType.WHITE_SPACE
            }
            first == '#' -> scanComment().let { ElixirLexicalVocabulary.COMMENT }
            first == '@' && charAt(tokenStart + 1).isIdentifierStart() -> {
                scanIdentifier(tokenStart + 1)
                ElixirLexicalVocabulary.MODULE_ATTRIBUTE
            }
            first == ':' && charAt(tokenStart + 1) in listOf('\'', '"') -> {
                scanQuoted(tokenStart + 1)
                ElixirLexicalVocabulary.ATOM
            }
            first == ':' && charAt(tokenStart + 1) != ':' && charAt(tokenStart + 1).isAtomStart() -> {
                scanAtom()
                ElixirLexicalVocabulary.ATOM
            }
            first in listOf('\'', '"') -> {
                if (tokenState.isInterpolationState()) {
                    if (startsHeredoc(first)) scanHeredocWhole(first) else scanQuoted(tokenStart)
                } else {
                    scanQuotedStart(first)
                }
                ElixirLexicalVocabulary.STRING
            }
            first == '?' && tokenStart + 1 < bufferEnd -> {
                scanCharacter()
                ElixirLexicalVocabulary.CHARACTER
            }
            first == '&' && charAt(tokenStart + 1).isDigit() -> {
                scanWhile { it.isDigit() }
                ElixirLexicalVocabulary.CAPTURE
            }
            first == '~' && scanSigilStart() -> ElixirLexicalVocabulary.SIGIL
            first.isDigit() -> {
                if (scanNumber()) ElixirLexicalVocabulary.NUMBER else TokenType.BAD_CHARACTER
            }
            first.isIdentifierStart() -> {
                scanIdentifier(tokenStart)
                val text = buffer.subSequence(tokenStart, tokenEnd).toString()
                when {
                    tokenState == EXPECT_FUNCTION_NAME_STATE -> ElixirLexicalVocabulary.FUNCTION_DECLARATION
                    tokenState == EXPECT_MEMBER_STATE -> ElixirLexicalVocabulary.MEMBER_ACCESS
                    charAt(tokenEnd) == ':' && charAt(tokenEnd + 1) != ':' -> {
                        tokenEnd++
                        ElixirLexicalVocabulary.ATOM
                    }
                    text in FUNCTION_DEFINITION_KEYWORDS -> {
                        if (!tokenState.isInterpolationState()) nextState = EXPECT_FUNCTION_NAME_STATE
                        ElixirLexicalVocabulary.KEYWORD
                    }
                    text in KEYWORDS -> ElixirLexicalVocabulary.KEYWORD
                    text in WORD_OPERATORS -> ElixirLexicalVocabulary.OPERATOR
                    text in ATOM_LITERALS -> ElixirLexicalVocabulary.LITERAL
                    first.isUpperCase() -> ElixirLexicalVocabulary.ALIAS
                    else -> ElixirLexicalVocabulary.IDENTIFIER
                }
            }
            first == '(' || first == ')' -> ElixirLexicalVocabulary.PARENTHESES
            first == '[' || first == ']' -> ElixirLexicalVocabulary.BRACKETS
            first == '{' || first == '}' -> {
                if (first == '{' && tokenState.isInterpolationState()) {
                    nextState = tokenState.withInterpolationDepth(tokenState.interpolationDepth() + 1)
                }
                ElixirLexicalVocabulary.BRACES
            }
            first == ',' || first == ';' || first == '%' -> ElixirLexicalVocabulary.PUNCTUATION
            scanOperator() -> {
                if (buffer.subSequence(tokenStart, tokenEnd).toString() == "." && !tokenState.isInterpolationState()) {
                    nextState = EXPECT_MEMBER_STATE
                }
                ElixirLexicalVocabulary.OPERATOR
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

    private fun scanHeredocWhole(quote: Char) {
        tokenEnd = tokenStart + 3
        while (tokenEnd < bufferEnd) {
            if (charAt(tokenEnd) == quote && charAt(tokenEnd + 1) == quote && charAt(tokenEnd + 2) == quote) {
                tokenEnd += 3
                return
            }
            tokenEnd++
        }
    }

    private fun scanQuotedStart(quote: Char) {
        val state = when {
            quote == '"' && startsHeredoc(quote) -> DOUBLE_HEREDOC_STATE
            quote == '\'' && startsHeredoc(quote) -> SINGLE_HEREDOC_STATE
            quote == '"' -> DOUBLE_STRING_STATE
            else -> SINGLE_STRING_STATE
        }
        tokenEnd = tokenStart + if (state.isHeredocState()) 3 else 1
        scanQuotedContent(state)
    }

    private fun locateQuotedContinuation(state: Int): IElementType {
        if (buffer[tokenStart] == '\\') {
            tokenEnd = (tokenStart + 2).coerceAtMost(bufferEnd)
            nextState = state
            return ElixirLexicalVocabulary.ESCAPE
        }
        if (buffer[tokenStart] == '#' && charAt(tokenStart + 1) == '{') {
            tokenEnd = tokenStart + 2
            nextState = interpolationState(state)
            return ElixirLexicalVocabulary.INTERPOLATION
        }
        tokenEnd = tokenStart
        scanQuotedContent(state)
        return ElixirLexicalVocabulary.STRING
    }

    private fun scanQuotedContent(state: Int) {
        val quote = if (state == DOUBLE_STRING_STATE || state == DOUBLE_HEREDOC_STATE) '"' else '\''
        val closingLength = if (state.isHeredocState()) 3 else 1
        while (tokenEnd < bufferEnd) {
            if (!state.isHeredocState() && buffer[tokenEnd].isLineBreak() && declarationStartsNextLine(tokenEnd)) {
                nextState = DEFAULT_STATE
                return
            }
            if (buffer[tokenEnd] == '\\' || (buffer[tokenEnd] == '#' && charAt(tokenEnd + 1) == '{')) {
                nextState = state
                return
            }
            if (buffer[tokenEnd] == quote &&
                (closingLength == 1 || (charAt(tokenEnd + 1) == quote && charAt(tokenEnd + 2) == quote))
            ) {
                tokenEnd += closingLength
                nextState = DEFAULT_STATE
                return
            }
            tokenEnd++
        }
        nextState = state
    }

    private fun declarationStartsNextLine(lineBreakOffset: Int): Boolean {
        var offset = lineBreakOffset + 1
        if (charAt(lineBreakOffset) == '\r' && charAt(offset) == '\n') offset++
        while (charAt(offset) == ' ' || charAt(offset) == '\t') offset++
        if (!charAt(offset).isIdentifierStart()) return false
        val start = offset
        while (charAt(offset).isIdentifierPart()) offset++
        return buffer.subSequence(start, offset).toString() in DECLARATION_KEYWORDS
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

    private fun scanSigilStart(): Boolean {
        if (!charAt(tokenStart + 1).isLetter()) return false
        var delimiterOffset = tokenStart + 2
        val interpolating = charAt(tokenStart + 1).isLowerCase()
        if (!interpolating) {
            while (charAt(delimiterOffset).isUpperCase()) delimiterOffset++
        }
        val opening = charAt(delimiterOffset)
        if (opening !in SIGIL_OPENINGS) return false
        tokenEnd = delimiterOffset + 1
        if (!interpolating) {
            scanLiteralSigil(opening)
            return true
        }
        scanSigilContent(sigilState(opening, 1))
        return true
    }

    private fun scanLiteralSigil(opening: Char) {
        val closing = SIGIL_DELIMITERS.getValue(opening)
        var depth = 1
        var escaped = false
        while (tokenEnd < bufferEnd) {
            val current = buffer[tokenEnd++]
            if (!escaped && opening != closing && current == opening) depth++
            if (!escaped && current == closing && --depth == 0) {
                scanWhile { it.isLetterOrDigit() }
                return
            }
            escaped = current == '\\' && !escaped
            if (current != '\\') escaped = false
        }
    }

    private fun locateSigilContinuation(state: Int): IElementType {
        if (buffer[tokenStart] == '\\') {
            tokenEnd = (tokenStart + 2).coerceAtMost(bufferEnd)
            nextState = state
            return ElixirLexicalVocabulary.ESCAPE
        }
        if (buffer[tokenStart] == '#' && charAt(tokenStart + 1) == '{') {
            tokenEnd = tokenStart + 2
            nextState = interpolationState(state)
            return ElixirLexicalVocabulary.INTERPOLATION
        }
        tokenEnd = tokenStart
        scanSigilContent(state)
        return ElixirLexicalVocabulary.SIGIL
    }

    private fun scanSigilContent(state: Int) {
        val opening = state.sigilOpening()
        val closing = SIGIL_DELIMITERS.getValue(opening)
        var depth = state.sigilDepth()
        while (tokenEnd < bufferEnd) {
            val current = buffer[tokenEnd]
            if (current == '\\' || (current == '#' && charAt(tokenEnd + 1) == '{')) {
                nextState = sigilState(opening, depth)
                return
            }
            tokenEnd++
            if (opening != closing && current == opening) depth++
            if (current == closing && --depth == 0) {
                scanWhile { it.isLetterOrDigit() }
                nextState = DEFAULT_STATE
                return
            }
        }
        nextState = sigilState(opening, depth)
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
    private fun Char.isLineBreak(): Boolean = this == '\n' || this == '\r'
    private fun Char.isAtomStart(): Boolean = isIdentifierStart() || isOperatorCharacter()
    private fun Char.isIdentifierPart(): Boolean = this == '_' || isLetterOrDigit()
    private fun Char.isOperatorCharacter(): Boolean = this in "+-*/\\|<>=~&^!.:"
    private fun Int.keepsContext(): Boolean = isQuotedState() || isSigilState() || isInterpolationState()
    private fun Int.kind(): Int = this and STATE_KIND_MASK
    private fun Int.isQuotedState(): Boolean = !isInterpolationState() && kind() in DOUBLE_STRING_STATE..SINGLE_HEREDOC_STATE
    private fun Int.isHeredocState(): Boolean = kind() == DOUBLE_HEREDOC_STATE || kind() == SINGLE_HEREDOC_STATE
    private fun Int.isSigilState(): Boolean = !isInterpolationState() && kind() == SIGIL_STATE
    private fun Int.isInterpolationState(): Boolean = this and INTERPOLATION_FLAG != 0
    private fun Int.interpolationReturnState(): Int = this and INTERPOLATION_RETURN_MASK
    private fun Int.interpolationDepth(): Int = (this ushr INTERPOLATION_DEPTH_SHIFT) and BYTE_MASK
    private fun Int.withInterpolationDepth(depth: Int): Int =
        (this and INTERPOLATION_DEPTH_MASK.inv()) or
            (depth.coerceAtMost(BYTE_MASK) shl INTERPOLATION_DEPTH_SHIFT)

    private fun interpolationState(returnState: Int): Int =
        INTERPOLATION_FLAG or (1 shl INTERPOLATION_DEPTH_SHIFT) or returnState

    private fun sigilState(opening: Char, depth: Int): Int =
        SIGIL_STATE or
            (SIGIL_OPENINGS.indexOf(opening) shl SIGIL_OPENING_SHIFT) or
            (depth.coerceAtMost(BYTE_MASK) shl SIGIL_DEPTH_SHIFT)

    private fun Int.sigilOpening(): Char =
        SIGIL_OPENINGS[(this ushr SIGIL_OPENING_SHIFT) and NIBBLE_MASK]

    private fun Int.sigilDepth(): Int = (this ushr SIGIL_DEPTH_SHIFT) and BYTE_MASK

    companion object {
        private const val DEFAULT_STATE = 0
        private const val EXPECT_FUNCTION_NAME_STATE = 1
        private const val EXPECT_MEMBER_STATE = 2
        private const val DOUBLE_STRING_STATE = 3
        private const val SINGLE_STRING_STATE = 4
        private const val DOUBLE_HEREDOC_STATE = 5
        private const val SINGLE_HEREDOC_STATE = 6
        private const val SIGIL_STATE = 7

        private const val STATE_KIND_MASK = 0xF
        private const val NIBBLE_MASK = 0xF
        private const val BYTE_MASK = 0xFF
        private const val SIGIL_OPENING_SHIFT = 4
        private const val SIGIL_DEPTH_SHIFT = 8
        private const val INTERPOLATION_DEPTH_SHIFT = 16
        private const val INTERPOLATION_RETURN_MASK = 0xFFFF
        private const val INTERPOLATION_DEPTH_MASK = BYTE_MASK shl INTERPOLATION_DEPTH_SHIFT
        private const val INTERPOLATION_FLAG = 1 shl 24

        private val FUNCTION_DEFINITION_KEYWORDS = setOf("def", "defguard", "defmacro", "defp")
        private val DECLARATION_KEYWORDS = FUNCTION_DEFINITION_KEYWORDS + "defmodule"
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
        private val SIGIL_OPENINGS = SIGIL_DELIMITERS.keys.toList()
    }
}
