package com.cimere.spellixir.lang

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame

class ElixirLexicalVocabularyTest : BasePlatformTestCase() {
    fun testEveryCategoryHasOneStableHighlightingIdentity() {
        val categories = ElixirLexicalVocabulary.categories

        assertEquals(21, categories.size)
        assertEquals(categories.size, categories.map { it.tokenType }.distinct().size)
        assertEquals(categories.size, categories.map { it.highlightingKeyName }.distinct().size)
        assertEquals(
            categories.map { "ELIXIR_${it.tokenType}" },
            categories.map { it.highlightingKeyName },
        )
    }

    fun testAliasesUseVisibleConstantStyle() {
        val alias = requireNotNull(ElixirLexicalVocabulary.categoryFor(ElixirLexicalVocabulary.ALIAS))

        assertSame(DefaultLanguageHighlighterColors.CONSTANT, alias.fallbackStyle)
    }

    fun testParserGroupsRemainPartOfTheVocabulary() {
        assertEquals(
            setOf(ElixirLexicalVocabulary.COMMENT),
            ElixirLexicalVocabulary
                .tokenTypesIn(ElixirLexicalVocabulary.ParserGroup.COMMENT)
                .toSet(),
        )
        assertEquals(
            setOf(
                ElixirLexicalVocabulary.ATOM,
                ElixirLexicalVocabulary.LITERAL,
                ElixirLexicalVocabulary.STRING,
                ElixirLexicalVocabulary.CHARACTER,
            ),
            ElixirLexicalVocabulary
                .tokenTypesIn(ElixirLexicalVocabulary.ParserGroup.STRING_LITERAL)
                .toSet(),
        )
    }
}
