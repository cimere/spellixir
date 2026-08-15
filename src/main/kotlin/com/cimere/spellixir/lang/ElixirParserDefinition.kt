package com.cimere.spellixir.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.cimere.spellixir.lang.parser.ElixirParser
import com.cimere.spellixir.lang.psi.ElixirTypes

class ElixirParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = ElixirParserLexer()

    override fun createParser(project: Project?): PsiParser = ElixirParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

    override fun getCommentTokens(): TokenSet = TokenSet.create(
        *ElixirLexicalVocabulary.tokenTypesIn(ElixirLexicalVocabulary.ParserGroup.COMMENT),
    )

    override fun getStringLiteralElements(): TokenSet = TokenSet.create(
        ElixirTypes.ATOM,
        ElixirTypes.LITERAL,
        ElixirTypes.STRING,
        ElixirTypes.CHARACTER,
    )

    override fun createElement(node: ASTNode): PsiElement = ElixirTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = ElixirFile(viewProvider)

    companion object {
        val FILE = IFileElementType(ElixirLanguage)

        @JvmStatic
        fun createElementType(debugName: String): IElementType = ElixirElementType(debugName)
    }
}

class ElixirElementType(debugName: String) : IElementType(debugName, ElixirLanguage)
