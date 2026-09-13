package com.cimere.spellixir.lang

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.cimere.spellixir.lang.psi.ElixirTypes

object ElixirParserUtil : GeneratedParserUtilBase() {
    @JvmStatic
    fun isKeywordKey(builder: PsiBuilder, level: Int): Boolean =
        builder.tokenType == ElixirTypes.ATOM && builder.tokenText?.endsWith(":") == true

    @JvmStatic
    fun parseTokenText(builder: PsiBuilder, level: Int, expected: String): Boolean {
        if (builder.tokenText != expected) return false
        builder.advanceLexer()
        return true
    }

    @JvmStatic
    fun consumeUnexpectedToken(builder: PsiBuilder, level: Int): Boolean {
        if (builder.eof()) return false
        builder.advanceLexer()
        return true
    }
}
