package com.cimere.spellixir.lang

import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.cimere.spellixir.lang.psi.ElixirCallableDeclaration
import java.io.File
import kotlin.math.ceil
import kotlin.system.measureNanoTime

/** An editor/document integration gate; deliberately excluded from the fast test task. */
class ElixirResponsivenessTest : BasePlatformTestCase() {
    private data class Token(val start: Int, val end: Int, val type: String, val attributes: String)
    private data class Sample(val operation: String, val milliseconds: Double)
    private val samples = mutableListOf<Sample>()

    fun testLargeFileEditingRemainsResponsiveAndConsistent() {
        val source = source(300)
        var passed = false
        try {
            // Warm services/JIT on a smaller instance; IDE startup is outside the measured operation.
            exercise(source(12), warmup = true)
            exercise(source, warmup = false)
            val initial = samples.single { it.operation == "initial" }.milliseconds
            val edits = samples.filter { it.operation != "initial" }.map { it.milliseconds }.sorted()
            assertTrue("Initial processing ${initial}ms exceeds 3000ms; see responsiveness report", initial < 3000)
            assertTrue("Edit p95 ${p95(edits)}ms exceeds 250ms; see responsiveness report", p95(edits) < 250)
            assertTrue("Worst edit ${edits.last()}ms exceeds 2000ms; see responsiveness report", edits.last() < 2000)
            passed = true
        } finally {
            writeReport(source, passed)
        }
    }

    private fun p95(sorted: List<Double>): Double = sorted[ceil(sorted.size * 0.95).toInt() - 1]

    private fun writeReport(source: String, passed: Boolean) {
        val report = File(System.getProperty("spellixir.responsiveness.report",
            "build/reports/responsiveness/results.json"))
        report.parentFile.mkdirs()
        File(report.parentFile, "large.ex").writeText(source)
        val edits = samples.filter { it.operation != "initial" }.map { it.milliseconds }.sorted()
        report.writeText(GsonBuilder().setPrettyPrinting().create().toJson(mapOf(
            "passed" to passed,
            "recordedAt" to java.time.Instant.now().toString(),
            "platform" to ApplicationInfo.getInstance().build.asString(),
            "java" to System.getProperty("java.version"),
            "os" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
            "processors" to Runtime.getRuntime().availableProcessors(),
            "maxHeapBytes" to Runtime.getRuntime().maxMemory(),
            "utf16Characters" to source.length,
            "lines" to source.count { it == '\n' } + 1,
            "initialBudgetMs" to 3000, "editP95BudgetMs" to 250, "editMaxBudgetMs" to 2000,
            "editP95Ms" to edits.takeIf { it.isNotEmpty() }?.let(::p95),
            "editMaxMs" to edits.lastOrNull(),
            "samples" to samples,
        )))
    }

    private fun exercise(source: String, warmup: Boolean) {
        lateinit var file: PsiFile
        measure("initial", warmup) {
            file = myFixture.configureByText("large.ex", source)
            file.node.firstChildNode // Force lazy parsing within the timed region.
            tokens((myFixture.editor as EditorEx).highlighter, source.length)
        }
        val document = myFixture.editor.document
        val highlighter = (myFixture.editor as EditorEx).highlighter
        assertNull("The baseline fixture must parse without errors", PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java))
        val originalDeclarations = declarations(file)
        assertEquals(if (warmup) 18 else 306, originalDeclarations.size)
        assertEquivalentToFreshParse(file, highlighter)

        // Each temporary edit is undone locally. Include invalid/incomplete expressions, not only typing letters.
        val edits = listOf(
            Triple("string", "string_target", "\\"),
            Triple("heredoc", "heredoc_target", "\nextra line"),
            Triple("sigil", "sigil_target", "("),
            Triple("interpolation", "interpolation_target", " + ("),
            Triple("nested-delimiters", "nested_target", ", ["),
            Triple("block", "block_target", "\n  if value do"),
        )
        repeat(if (warmup) 2 else 8) {
            for ((name, marker, insertion) in edits) {
                val offset = document.text.lastIndexOf(marker) + marker.length
                assertTrue("Missing edit marker $marker", offset >= marker.length)
                measure("$name-insert", warmup) {
                    WriteCommandAction.runWriteCommandAction(project) {
                        document.insertString(offset, insertion)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                    }
                    file.node.firstChildNode
                    tokens(highlighter, document.textLength)
                }
                assertEquivalentToFreshParse(file, highlighter)
                // Every other declaration must remain present with identical text, including those after the edit.
                val untouched = originalDeclarations.filterNot { marker in it }
                assertTrue("$name lost unrelated declarations", declarations(file).containsAll(untouched))
                measure("$name-delete", warmup) {
                    WriteCommandAction.runWriteCommandAction(project) {
                        document.deleteString(offset, offset + insertion.length)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                    }
                    file.node.firstChildNode
                    tokens(highlighter, document.textLength)
                }
                assertEquals(source, file.text)
                assertEquals(originalDeclarations, declarations(file))
                assertEquivalentToFreshParse(file, highlighter)
            }
        }
    }

    private fun declarations(file: PsiFile): List<String> =
        PsiTreeUtil.findChildrenOfType(file, ElixirCallableDeclaration::class.java).map { it.text }

    private fun assertEquivalentToFreshParse(file: PsiFile, highlighter: EditorHighlighter) {
        val source = myFixture.editor.document.text
        assertEquals(source, file.node.text)
        val fresh = PsiFileFactory.getInstance(project).createFileFromText("fresh.ex", ElixirFileType, source)
        assertEquals("Incremental PSI differs from a fresh parse", shape(fresh), shape(file))
        val freshHighlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file.virtualFile)
        freshHighlighter.setText(source)
        assertEquals("Incremental highlighting differs from a fresh scan",
            tokens(freshHighlighter, source.length), tokens(highlighter, source.length))
    }

    private fun shape(file: PsiFile): List<String> {
        val result = mutableListOf<String>()
        fun visit(node: com.intellij.lang.ASTNode, depth: Int) {
            result.add("$depth:${node.elementType}:${node.startOffset}:${node.textLength}")
            var child = node.firstChildNode
            while (child != null) {
                visit(child, depth + 1)
                child = child.treeNext
            }
        }
        visit(file.node, 0)
        return result
    }

    private fun tokens(highlighter: EditorHighlighter, length: Int): List<Token> {
        val result = mutableListOf<Token>()
        val iterator = highlighter.createIterator(0)
        var end = 0
        while (!iterator.atEnd()) {
            assertEquals("Noncontiguous highlight tokens", end, iterator.start)
            assertTrue("Empty highlight token", iterator.end > iterator.start)
            result.add(Token(iterator.start, iterator.end, iterator.tokenType.toString(), iterator.textAttributes.toString()))
            end = iterator.end
            iterator.advance()
        }
        assertEquals("Highlighting must cover the entire document", length, end)
        return result
    }

    private fun measure(operation: String, warmup: Boolean, action: () -> Unit) {
        val elapsed = measureNanoTime(action) / 1_000_000.0
        if (!warmup) samples.add(Sample(operation, elapsed))
    }

    private fun source(functions: Int): String = buildString {
        append("defmodule Large.Accounts do\n")
        repeat(functions) { index ->
            if (index == functions / 2) {
                append("  def string_case(value), do: \"string_target #{value}\"\n")
                append("  def heredoc_case(value), do: \"\"\"\nheredoc_target #{value}\n\"\"\"\n")
                append("  def sigil_case(value), do: ~s(sigil_target #{value})\n")
                append("  def interpolation_case(interpolation_target), do: \"#{interpolation_target}\"\n")
                append("  def nested_case(nested_target), do: [{:ok, [nested_target]}]\n")
                append("  def block_case(value) do\n    block_target = value\n    Repo.get(value)\n  end\n")
            }
            append("  # Account lookup $index: includes Unicode λ and nested data.\n")
            append("  def fetch_$index({:user, user_id}, options \\\\ []) do\n")
            append("    result = Repo.get(User, user_id)\n")
            append("    metadata = %{status: :ok, values: [1, 2, {user_id, options}]}\n")
            append("    Logger.info(\"user=#{user_id}\")\n")
            append("    {:ok, result, metadata}\n  end\n\n")
        }
        append("end\n")
    }
}
