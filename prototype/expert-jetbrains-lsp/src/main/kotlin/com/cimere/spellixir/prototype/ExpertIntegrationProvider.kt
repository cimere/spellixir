package com.cimere.spellixir.prototype

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor

/**
 * PROTOTYPE ONLY.
 *
 * Question: can an explicitly enabled, user-installed Expert process provide
 * diagnostics through JetBrains' native LSP API without owning any Native Core
 * capability?
 */
internal class ExpertIntegrationProvider : LspIntegrationProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        clientStarter: LspIntegrationProvider.LspClientStarter,
    ) {
        if (file.extension !in ELIXIR_EXTENSIONS) return

        if (System.getenv(EXPERT_ENABLED_ENV) != "1") {
            LOG.warn("Expert prototype is disabled; set $EXPERT_ENABLED_ENV=1 to opt in")
            return
        }

        clientStarter.ensureClientStarted(ExpertClientDescriptor(project))
    }
}

private class ExpertClientDescriptor(project: Project) :
    ProjectWideLspClientDescriptor(project, "Expert") {
    override fun isSupportedFile(file: VirtualFile): Boolean = file.extension in ELIXIR_EXTENSIONS

    override fun createCommandLine(): GeneralCommandLine =
        GeneralCommandLine(System.getenv(EXPERT_PATH_ENV) ?: "expert", "--stdio")
}

private val LOG = Logger.getInstance(ExpertIntegrationProvider::class.java)
private const val EXPERT_ENABLED_ENV = "SPELLIXIR_EXPERT_ENABLED"
private const val EXPERT_PATH_ENV = "EXPERT_PATH"
private val ELIXIR_EXTENSIONS = setOf("ex", "exs")
