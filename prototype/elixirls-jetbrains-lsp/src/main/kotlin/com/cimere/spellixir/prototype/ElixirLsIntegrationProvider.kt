package com.cimere.spellixir.prototype

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor

/**
 * PROTOTYPE ONLY.
 *
 * Question: can the native JetBrains LSP API manage a user-installed ElixirLS
 * process for Elixir files while leaving all local-language responsibilities
 * outside the adapter?
 */
internal class ElixirLsIntegrationProvider : LspIntegrationProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        clientStarter: LspIntegrationProvider.LspClientStarter,
    ) {
        if (file.extension in ELIXIR_EXTENSIONS) {
            clientStarter.ensureClientStarted(ElixirLsClientDescriptor(project))
        }
    }
}

private class ElixirLsClientDescriptor(project: Project) :
    ProjectWideLspClientDescriptor(project, "ElixirLS") {
    override fun isSupportedFile(file: VirtualFile): Boolean = file.extension in ELIXIR_EXTENSIONS

    override fun createCommandLine(): GeneralCommandLine =
        GeneralCommandLine("elixir-ls")
}

private val ELIXIR_EXTENSIONS = setOf("ex", "exs")
