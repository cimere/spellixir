package com.cimere.spellixir.lang

import com.intellij.psi.tree.IElementType

class ElixirTokenType(debugName: String) : IElementType(debugName, ElixirLanguage)

object ElixirTokenTypes {
    val KEYWORD = ElixirTokenType("KEYWORD")
    val IDENTIFIER = ElixirTokenType("IDENTIFIER")
    val ALIAS = ElixirTokenType("ALIAS")
    val ATOM = ElixirTokenType("ATOM")
    val STRING = ElixirTokenType("STRING")
    val MODULE_ATTRIBUTE = ElixirTokenType("MODULE_ATTRIBUTE")
    val OPERATOR = ElixirTokenType("OPERATOR")
    val NUMBER = ElixirTokenType("NUMBER")
    val CHARACTER = ElixirTokenType("CHARACTER")
    val SIGIL = ElixirTokenType("SIGIL")
    val COMMENT = ElixirTokenType("COMMENT")
    val PUNCTUATION = ElixirTokenType("PUNCTUATION")
    val PARENTHESES = ElixirTokenType("PARENTHESES")
    val BRACKETS = ElixirTokenType("BRACKETS")
    val BRACES = ElixirTokenType("BRACES")
}
