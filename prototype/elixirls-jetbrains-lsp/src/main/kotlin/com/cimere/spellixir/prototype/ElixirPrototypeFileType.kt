package com.cimere.spellixir.prototype

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * PROTOTYPE ONLY.
 *
 * The real Native Core owns Elixir file recognition. This minimal registration
 * exists solely so this otherwise standalone LSP adapter receives file-open
 * notifications in a greenfield repository.
 */
object ElixirPrototypeLanguage : Language("ElixirPrototype")

object ElixirPrototypeFileType : LanguageFileType(ElixirPrototypeLanguage) {
    override fun getName(): String = "Elixir Prototype"

    override fun getDescription(): String = "Prototype Elixir file type for LSP validation"

    override fun getDefaultExtension(): String = "ex"

    override fun getIcon(): Icon? = null
}
