package com.cimere.spellixir.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class ElixirFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ElixirLanguage) {
    override fun getFileType() = ElixirFileType

    override fun toString(): String = "Elixir File"
}
