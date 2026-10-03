package com.cimere.spellixir.smoke

import com.cimere.spellixir.lang.ElixirFileType
import com.cimere.spellixir.lang.psi.ElixirCallableDeclaration
import com.cimere.spellixir.mix.MixProjectContext
import com.cimere.spellixir.mix.MixProjectService
import com.google.gson.GsonBuilder
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/** Runs only from the separate smoke plugin, with production classes supplied by the installed ZIP. */
class PackagedSmokeStarter : ApplicationStarter {
    override val isHeadless: Boolean get() = true

    override fun main(args: List<String>) {
        val root = Path.of(args[1])
        val report = Path.of(args[2])
        val checks = mutableListOf<String>()
        var failure: Throwable? = null
        try {
            val plugin = checkNotNull(PluginManagerCore.getPlugin(PluginId.getId("com.cimere.spellixir")))
            check(plugin.isEnabled) { "Packaged plugin is disabled" }
            check(ElixirFileType.javaClass.classLoader == plugin.pluginClassLoader) { "Production class escaped plugin classloader" }
            val pluginPath = plugin.pluginPath.toAbsolutePath().normalize()
            check(pluginPath.parent.fileName.toString().startsWith("plugins_packagedSmoke")) {
                "Production class was not loaded from the installed candidate"
            }
            checks.add("installed-plugin-classloader")
            Files.createDirectories(root)
            val project = checkNotNull(ProjectManager.getInstance().createProject("Spellixir smoke", root.toString()))
            try {
                ApplicationManager.getApplication().invokeAndWait {
                    checkEditing(project, root, checks)
                    checkMix(project, root, checks)
                }
            } finally {
                ApplicationManager.getApplication().invokeAndWait { Disposer.dispose(project) }
            }
        } catch (error: Throwable) {
            failure = error
            error.printStackTrace()
        }
        Files.writeString(report, GsonBuilder().setPrettyPrinting().create().toJson(mapOf(
            "passed" to (failure == null),
            "build" to ApplicationInfo.getInstance().build.asString(),
            "version" to ApplicationInfo.getInstance().fullVersion,
            "host" to System.getProperty("spellixir.smoke.host"),
            "checks" to checks,
            "failure" to failure?.stackTraceToString(),
        )))
        exitProcess(if (failure == null) 0 else 1)
    }

    private fun file(root: Path, relative: String, text: String): com.intellij.openapi.vfs.VirtualFile {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
    }

    private fun checkEditing(project: Project, root: Path, checks: MutableList<String>) {
        val valid = "def run(value), do: [value]\ndef complete, do: :ok\n"
        for (extension in listOf("ex", "exs")) {
            val virtualFile = file(root, "standalone/demo.$extension", valid)
            check(FileTypeManager.getInstance().getFileTypeByFile(virtualFile) === ElixirFileType)
            val document = checkNotNull(FileDocumentManager.getInstance().getDocument(virtualFile))
            val psi = checkNotNull(PsiManager.getInstance(project).findFile(virtualFile))
            val editor = EditorFactory.getInstance().createEditor(document, project, virtualFile, false) as EditorEx
            try {
                fun assertState() {
                    check(psi.text == document.text)
                    val declarations = PsiTreeUtil.findChildrenOfType(psi, ElixirCallableDeclaration::class.java)
                    check(declarations.size == 2)
                    check(declarations.last().text == "def complete, do: :ok")
                    val iterator = editor.highlighter.createIterator(0)
                    val types = mutableSetOf<String>()
                    var end = 0
                    while (!iterator.atEnd()) {
                        check(iterator.start == end && iterator.end > iterator.start)
                        types.add(iterator.tokenType.toString())
                        end = iterator.end
                        iterator.advance()
                    }
                    check(end == document.textLength)
                    check(types.containsAll(listOf("FUNCTION_DECLARATION", "ATOM", "BRACKETS"))) { "Missing highlight tokens: $types" }
                }
                assertState()
                repeat(3) {
                    val offset = document.text.indexOf(']')
                    WriteCommandAction.runWriteCommandAction(project) {
                        document.deleteString(offset, offset + 1)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                    }
                    assertState()
                    WriteCommandAction.runWriteCommandAction(project) {
                        document.insertString(offset, "]")
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                    }
                    assertState()
                }
                checks.add("$extension-recognition-highlighting-recovery")
            } finally {
                EditorFactory.getInstance().releaseEditor(editor)
            }
        }
    }

    private fun checkMix(project: Project, root: Path, checks: MutableList<String>) {
        fun metadata(config: String) = "defmodule Demo.MixProject do\n use Mix.Project\n def project, do: [$config]\nend\n"
        val service = project.getService(MixProjectService::class.java)
        val ordinary = file(root, "ordinary/mix.exs", metadata("app: :demo"))
        val source = file(root, "ordinary/lib/demo.ex", "defmodule Demo do\nend")
        check(service.classify(source) == MixProjectContext.MixProject(ordinary.parent))
        val umbrella = file(root, "umbrella/mix.exs", metadata("apps_path: \"apps\""))
        val child = file(root, "umbrella/apps/child/mix.exs", metadata("app: :child"))
        val childSource = file(root, "umbrella/apps/child/lib/demo.ex", "")
        check(service.classify(umbrella) == MixProjectContext.UmbrellaRoot(umbrella.parent))
        check(service.classify(childSource) == MixProjectContext.UmbrellaChild(child.parent, umbrella.parent))
        val standalone = file(root, "standalone/task.exs", "IO.puts(:ok)")
        check(service.classify(standalone) == MixProjectContext.Standalone)
        file(root, "malformed/mix.exs", "defmodule Broken do\n def project, do: [app:")
        check(service.classify(file(root, "malformed/demo.ex", "")) == MixProjectContext.Unknown)
        checks.addAll(listOf("ordinary-mix", "umbrella-root", "umbrella-child", "standalone", "malformed-metadata"))
    }
}
