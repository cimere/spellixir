package com.cimere.spellixir.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object ElixirFileType : LanguageFileType(ElixirLanguage) {
    override fun getName(): String = "Elixir"

    override fun getDescription(): String = "Elixir source file"

    override fun getDefaultExtension(): String = "ex"

    override fun getIcon(): Icon? = null
}
