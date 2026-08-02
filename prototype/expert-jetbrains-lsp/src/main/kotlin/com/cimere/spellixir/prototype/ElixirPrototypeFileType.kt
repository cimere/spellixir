package com.cimere.spellixir.prototype

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * PROTOTYPE ONLY.
 *
 * The real Native Core owns Elixir file recognition. This minimal registration
 * exists solely so the standalone Expert adapter receives file-open events.
 */
object ElixirPrototypeLanguage : Language("ElixirPrototype")

object ElixirPrototypeFileType : LanguageFileType(ElixirPrototypeLanguage) {
    override fun getName(): String = "Elixir Prototype"

    override fun getDescription(): String = "Prototype Elixir file type for Expert validation"

    override fun getDefaultExtension(): String = "ex"

    override fun getIcon(): Icon? = null
}
