package com.cimere.spellixir.lang

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase

object ElixirParserUtil : GeneratedParserUtilBase() {
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
