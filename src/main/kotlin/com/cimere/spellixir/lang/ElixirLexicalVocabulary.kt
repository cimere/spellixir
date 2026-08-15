package com.cimere.spellixir.lang

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.tree.IElementType

class ElixirTokenType(debugName: String) : IElementType(debugName, ElixirLanguage)

object ElixirLexicalVocabulary {
    enum class ParserGroup {
        COMMENT,
        STRING_LITERAL,
    }

    class Category internal constructor(
        val tokenType: ElixirTokenType,
        val highlightingKeyName: String,
        val fallbackStyle: TextAttributesKey,
        val parserGroups: Set<ParserGroup>,
    )

    private val categoryDefinitions = mutableListOf<Category>()

    val KEYWORD = category("KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val IDENTIFIER = category("IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
    val FUNCTION_DECLARATION = category(
        "FUNCTION_DECLARATION",
        DefaultLanguageHighlighterColors.FUNCTION_DECLARATION,
    )
    val MEMBER_ACCESS = category("MEMBER_ACCESS", DefaultLanguageHighlighterColors.FUNCTION_CALL)
    val ALIAS = category("ALIAS", DefaultLanguageHighlighterColors.CONSTANT)
    val ATOM = category("ATOM", DefaultLanguageHighlighterColors.CONSTANT, ParserGroup.STRING_LITERAL)
    val LITERAL = category("LITERAL", DefaultLanguageHighlighterColors.CONSTANT, ParserGroup.STRING_LITERAL)
    val STRING = category("STRING", DefaultLanguageHighlighterColors.STRING, ParserGroup.STRING_LITERAL)
    val MODULE_ATTRIBUTE = category("MODULE_ATTRIBUTE", DefaultLanguageHighlighterColors.METADATA)
    val OPERATOR = category("OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val NUMBER = category("NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val CHARACTER = category("CHARACTER", DefaultLanguageHighlighterColors.STRING, ParserGroup.STRING_LITERAL)
    val SIGIL = category("SIGIL", DefaultLanguageHighlighterColors.STRING)
    val CAPTURE = category("CAPTURE", DefaultLanguageHighlighterColors.PARAMETER)
    val ESCAPE = category("ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
    val INTERPOLATION = category("INTERPOLATION", DefaultLanguageHighlighterColors.BRACES)
    val COMMENT = category("COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT, ParserGroup.COMMENT)
    val PUNCTUATION = category("PUNCTUATION", DefaultLanguageHighlighterColors.COMMA)
    val PARENTHESES = category("PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
    val BRACKETS = category("BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
    val BRACES = category("BRACES", DefaultLanguageHighlighterColors.BRACES)

    val categories: List<Category> = categoryDefinitions.toList()

    fun categoryFor(tokenType: IElementType): Category? = categoriesByTokenType[tokenType]

    fun tokenTypesIn(group: ParserGroup): Array<IElementType> = categories
        .filter { group in it.parserGroups }
        .map(Category::tokenType)
        .toTypedArray()

    private val categoriesByTokenType: Map<IElementType, Category> by lazy {
        categories.associateBy(Category::tokenType)
    }

    private fun category(
        name: String,
        fallbackStyle: TextAttributesKey,
        vararg parserGroups: ParserGroup,
    ): ElixirTokenType {
        val tokenType = ElixirTokenType(name)
        categoryDefinitions += Category(
            tokenType = tokenType,
            highlightingKeyName = "ELIXIR_$name",
            fallbackStyle = fallbackStyle,
            parserGroups = parserGroups.toSet(),
        )
        return tokenType
    }
}
