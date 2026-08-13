package com.cimere.spellixir.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class ElixirParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = ElixirLexer()

    override fun createParser(project: Project?): PsiParser = ElixirTokenPreservingParser

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

    override fun getCommentTokens(): TokenSet = TokenSet.create(ElixirTokenTypes.COMMENT)

    override fun getStringLiteralElements(): TokenSet = TokenSet.create(
        ElixirTokenTypes.ATOM,
        ElixirTokenTypes.LITERAL,
        ElixirTokenTypes.STRING,
        ElixirTokenTypes.CHARACTER,
    )

    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = ElixirFile(viewProvider)

    companion object {
        val FILE = IFileElementType(ElixirLanguage)
    }
}

private object ElixirTokenPreservingParser : PsiParser {
    override fun parse(root: com.intellij.psi.tree.IElementType, builder: PsiBuilder): ASTNode {
        val file = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        file.done(root)
        return builder.treeBuilt
    }
}
