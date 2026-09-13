package com.cimere.spellixir.lang

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Checked-in behavioral expectations; candidate snapshots are written only under build/. */
class ElixirSyntaxCorpusTest : BasePlatformTestCase() {
    private val root = Path.of(System.getProperty("spellixir.corpus.dir"))
    private val report = Path.of(System.getProperty("spellixir.corpus.report"))
    private val gson = Gson()

    fun testVersionedCorpusMatchesNativeCoreContracts() {
        val manifest = JsonParser.parseString(Files.readString(root.resolve("manifest.json"))).asJsonObject
        assertEquals(1, manifest["schemaVersion"].asInt)
        assertEquals(1, manifest["corpusVersion"].asInt)
        val fixtures = manifest.getAsJsonArray("fixtures").map { it.asJsonObject }
        assertEquals(fixtures.size, fixtures.map { it["id"].asString }.toSet().size)
        val listed = fixtures.map { it["path"].asString }.toSet()
        val actualFiles = Files.list(root.resolve("fixtures")).use { paths ->
            paths.map { "fixtures/${it.fileName}" }.toList().toSet()
        }
        assertEquals("Every source fixture must have provenance and expectations", listed, actualFiles)
        Files.createDirectories(report)
        val failures = mutableListOf<String>()
        val tokenTypes = mutableSetOf<String>()
        val psiTypes = mutableSetOf<String>()

        for (fixture in fixtures) {
            val id = fixture["id"].asString
            val source = Files.readString(root.resolve(fixture["path"].asString))
            val hash = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
            assertEquals("$id source hash", fixture["sha256"].asString, hash)
            val origin = fixture.getAsJsonObject("origin")
            assertEquals("Apache-2.0", origin["license"].asString)
            if (origin["kind"].asString == "copied") {
                assertTrue(origin["revision"].asString.matches(Regex("[0-9a-f]{40}")))
                for (field in listOf("repository", "path", "adaptation", "notice")) assertTrue(origin[field].asString.isNotBlank())
                assertTrue(Files.isRegularFile(root.resolve(origin["licenseFile"].asString)))
                assertTrue(source.startsWith("# SPDX-License-Identifier: Apache-2.0"))
                assertTrue(source.contains("SPDX-FileCopyrightText:"))
            }

            val file = myFixture.configureByText("$id.ex", source)
            assertEquals("$id retains all source text", source, file.node.text)
            assertSame(ElixirFileType, file.fileType)
            val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(ElixirLanguage, project, file.virtualFile)
            val lexer = highlighter.highlightingLexer
            val tokens = JsonArray()
            val states = mutableListOf<Int>()
            lexer.start(source)
            var covered = 0
            while (lexer.tokenType != null) {
                assertEquals("$id contiguous tokens", covered, lexer.tokenStart)
                assertTrue("$id lexer makes progress", lexer.tokenEnd > lexer.tokenStart)
                states += lexer.state
                val type = requireNotNull(lexer.tokenType)
                tokens.add(gson.toJsonTree(listOf(lexer.tokenStart, lexer.tokenEnd, type.toString(),
                    highlighter.getTokenHighlights(type).map { it.externalName },
                    source.substring(lexer.tokenStart, lexer.tokenEnd))))
                tokenTypes += type.toString()
                covered = lexer.tokenEnd
                lexer.advance()
            }
            assertEquals("$id complete lexer coverage", source.length, covered)

            // Full suffix equivalence on reduced cases; the real modules have separate snapshots.
            if (origin["kind"].asString == "original") {
                for (index in states.indices) {
                    lexer.start(source, tokens[index].asJsonArray[0].asInt, source.length, states[index])
                    var cursor = index
                    while (lexer.tokenType != null) {
                        val expected = tokens[cursor++].asJsonArray
                        assertEquals("$id restart boundary", expected[0].asInt, lexer.tokenStart)
                        assertEquals("$id restart end", expected[1].asInt, lexer.tokenEnd)
                        assertEquals("$id restart type", expected[2].asString, lexer.tokenType.toString())
                        lexer.advance()
                    }
                    assertEquals("$id restart suffix", tokens.size(), cursor)
                }
            }

            val psi = JsonArray()
            val errors = JsonArray()
            for (element in PsiTreeUtil.collectElements(file) { it.node?.elementType is ElixirElementType || it is PsiErrorElement }) {
                if (element is PsiErrorElement) {
                    errors.add(gson.toJsonTree(listOf(element.textRange.startOffset, element.textRange.endOffset, element.errorDescription)))
                } else {
                    val type = element.node.elementType.toString()
                    psiTypes += type
                    psi.add(gson.toJsonTree(listOf(element.textRange.startOffset, element.textRange.endOffset, type)))
                }
            }
            val snapshot = JsonObject().apply {
                addProperty("sourceSha256", hash)
                add("tokens", tokens)
                add("psi", psi)
                add("errors", errors)
            }
            Files.writeString(report.resolve("$id.json"), render(snapshot))

            for (group in listOf("assertions", "recovery", "differential")) {
                for (item in fixture.getAsJsonArray(group)) {
                    val anchor = item.asJsonObject
                    val text = anchor["text"].asString
                    val rows = if (anchor["kind"].asString == "psi") psi else tokens
                    if (rows.none { row ->
                        val values = row.asJsonArray
                        values[2].asString == anchor["type"].asString &&
                            source.substring(values[0].asInt, values[1].asInt) == text
                    }) failures += "$id $group: missing ${anchor["type"].asString} spanning '$text'"
                }
            }
            val expectedPath = root.resolve("expected/$id.json")
            if (!Files.exists(expectedPath)) failures += "$id: missing reviewed snapshot; candidate: ${report.resolve("$id.json")}"
            else if (JsonParser.parseString(Files.readString(expectedPath)) != snapshot) {
                failures += "$id: behavior differs from $expectedPath; candidate: ${report.resolve("$id.json")}"
            }
        }

        val vocabulary = ElixirLexicalVocabulary.categories.map { it.tokenType.toString() }.toSet()
        assertTrue("Missing token families: ${vocabulary - tokenTypes}", tokenTypes.containsAll(vocabulary))
        val concepts = setOf("MODULE_DECLARATION", "CALLABLE_DECLARATION", "ALIAS_DECLARATION", "CALL_EXPRESSION",
            "PARAMETER_LIST", "PATTERN", "DO_BLOCK", "LITERAL_EXPRESSION", "QUALIFIED_NAME", "LIST_EXPRESSION",
            "TUPLE_EXPRESSION", "MAP_EXPRESSION", "PARENTHESIZED_EXPRESSION", "CAPTURE_EXPRESSION")
        assertTrue("Missing PSI concepts: ${concepts - psiTypes}", psiTypes.containsAll(concepts))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun render(snapshot: JsonObject): String = buildString {
        append("{\n  \"sourceSha256\": ${snapshot["sourceSha256"]},\n")
        for ((index, key) in listOf("tokens", "psi", "errors").withIndex()) {
            append("  \"$key\": [\n")
            append(snapshot.getAsJsonArray(key).joinToString(",\n") { "    $it" })
            append("\n  ]${if (index < 2) "," else ""}\n")
        }
        append("}\n")
    }
}
