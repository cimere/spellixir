package com.cimere.spellixir.mix

import com.cimere.spellixir.lang.ElixirLexer
import com.cimere.spellixir.lang.ElixirLexicalVocabulary as Vocabulary
import com.intellij.psi.TokenType

internal sealed interface MixMetadata {
    data object Ordinary : MixMetadata
    data class Umbrella(val appsPath: List<String>) : MixMetadata
    data object Unknown : MixMetadata
}

/** A bounded recognizer for conventional Mix definitions, never an Elixir evaluator. */
internal object MixMetadataReader {
    const val MAX_BYTES = 256 * 1024

    fun read(source: String): MixMetadata {
        if (source.length > MAX_BYTES) return MixMetadata.Unknown
        return try {
            Parser(tokens(source)).read()
        } catch (_: UnsupportedMetadata) {
            MixMetadata.Unknown
        }
    }

    private class UnsupportedMetadata : RuntimeException(null, null, false, false)
    private fun unsupported(): Nothing = throw UnsupportedMetadata()

    private fun tokens(source: String): List<String> {
        val lexer = ElixirLexer()
        lexer.start(source)
        val tokens = mutableListOf<String>()
        while (lexer.tokenType != null) {
            val type = lexer.tokenType
            val text = source.substring(lexer.tokenStart, lexer.tokenEnd)
            when (type) {
                TokenType.WHITE_SPACE, Vocabulary.COMMENT -> Unit
                TokenType.BAD_CHARACTER, Vocabulary.INTERPOLATION, Vocabulary.ESCAPE,
                Vocabulary.SIGIL, Vocabulary.CHARACTER -> unsupported()
                Vocabulary.STRING -> {
                    // Split, interpolated, escaped, and heredoc strings need evaluation or
                    // additional decoding. They are outside this deliberately small subset.
                    if (!text.startsWith('"') || !text.endsWith('"') || text.length < 2 ||
                        text.startsWith("\"\"\"") || '\\' in text || "#{" in text) unsupported()
                    tokens += text
                }
                else -> tokens += text
            }
            if (tokens.size > 16_384) unsupported()
            lexer.advance()
        }
        return tokens
    }

    private sealed interface Value {
        data class Scalar(val text: String) : Value
        data class Keywords(val entries: Map<String, Value>) : Value
        data object Opaque : Value
    }

    private class Parser(private val tokens: List<String>) {
        private var position = 0
        private fun peek() = tokens.getOrNull(position)
        private fun take(): String = tokens.getOrNull(position++) ?: unsupported()
        private fun accept(text: String): Boolean = if (peek() == text) { position++; true } else false
        private fun expect(text: String) { if (!accept(text)) unsupported() }

        fun read(): MixMetadata {
            expect("defmodule")
            if (!take().matches(ALIAS)) unsupported()
            while (accept(".")) if (!take().matches(ALIAS)) unsupported()
            expect("do")
            expect("use")
            expect("Mix")
            expect(".")
            expect("Project")
            var project: Value.Keywords? = null
            while (peek() != "end") {
                val visibility = take()
                if (visibility !in setOf("def", "defp")) unsupported()
                val name = take()
                if (!name.matches(NAME) || name in RESERVED) unsupported()
                val arguments = if (accept("(")) sequence(")", 0).size else 0
                val inline = accept(",")
                val body = if (inline) {
                    expect("do:")
                    expression(0)
                } else {
                    expect("do")
                    expression(0).also { expect("end") }
                }
                if (name == "project") {
                    if (visibility != "def" || arguments != 0 || project != null) unsupported()
                    project = body as? Value.Keywords ?: unsupported()
                }
            }
            expect("end")
            if (position != tokens.size) unsupported()
            val entries = project?.entries ?: unsupported()
            // An explicit application selection needs separate membership interpretation.
            if ("apps" in entries) unsupported()
            val path = entries["apps_path"]
            if (path != null) {
                val literal = (path as? Value.Scalar)?.text ?: unsupported()
                if (!literal.startsWith('"') || !literal.endsWith('"')) unsupported()
                val components = literal.drop(1).dropLast(1).split('/')
                if (components.any { it.isEmpty() || it == "." || it == ".." ||
                        ':' in it || '\\' in it || it.any(Char::isISOControl) }) unsupported()
                return MixMetadata.Umbrella(components)
            }
            val app = (entries["app"] as? Value.Scalar)?.text ?: unsupported()
            if (!app.matches(ATOM)) unsupported()
            return MixMetadata.Ordinary
        }

        // Parse enough expression shape to avoid mistaking nested keys, comments, or strings
        // for project configuration. Calls are opaque: their bodies are never invoked.
        private fun expression(depth: Int): Value {
            if (depth > 64) unsupported()
            var value = primary(depth + 1)
            while (peek() in BINARY_OPERATORS) {
                take()
                primary(depth + 1)
                value = Value.Opaque
            }
            return value
        }

        private fun primary(depth: Int): Value {
            if (depth > 64) unsupported()
            val token = take()
            if (token == "[") {
                val entries = linkedMapOf<String, Value>()
                if (accept("]")) return Value.Keywords(entries)
                if (peek()?.matches(KEY) == true) {
                    do {
                        val key = take()
                        if (!key.matches(KEY) || entries.containsKey(key.dropLast(1))) unsupported()
                        entries[key.dropLast(1)] = expression(depth + 1)
                    } while (accept(",") && peek() != "]")
                    expect("]")
                    return Value.Keywords(entries)
                }
                sequence("]", depth)
                return Value.Opaque
            }
            if (token == "{") { sequence("}", depth); return Value.Opaque }
            if (token == "(") return expression(depth + 1).also { expect(")") }
            if (token.startsWith('"') || token.matches(ATOM) || token.matches(NUMBER) ||
                token in setOf("true", "false", "nil")) return Value.Scalar(token)
            if (!token.matches(NAME) || token in RESERVED) unsupported()
            while (accept(".")) if (!take().matches(NAME)) unsupported()
            if (accept("(")) sequence(")", depth)
            return Value.Opaque
        }

        private fun sequence(close: String, depth: Int): List<Value> {
            val values = mutableListOf<Value>()
            if (accept(close)) return values
            do {
                // Keyword arguments in calls and dependency tuples are irrelevant to layout.
                if (peek()?.matches(KEY) == true) take()
                values += expression(depth + 1)
            } while (accept(",") && peek() != close)
            expect(close)
            return values
        }
    }

    private val ALIAS = Regex("[A-Z][A-Za-z0-9_]*")
    private val NAME = Regex("[A-Za-z_][A-Za-z0-9_!?]*")
    private val KEY = Regex("[a-z_][A-Za-z0-9_!?]*:")
    private val ATOM = Regex(":[a-z_][A-Za-z0-9_!?]*")
    private val NUMBER = Regex("[0-9]+(?:\\.[0-9]+)?")
    private val RESERVED = setOf("do", "end", "def", "defp", "defmodule", "fn", "if", "case", "use")
    private val BINARY_OPERATORS = setOf("==", "!=", "===", "!==", "<", ">", "<=", ">=", "and", "or", "&&", "||", "++", "<>")
}
