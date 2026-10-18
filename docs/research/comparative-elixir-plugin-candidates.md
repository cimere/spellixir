# Comparative Elixir plugin candidates

Research snapshot: 2026-10-18. This establishes acquisition and declared compatibility, not measured performance or final benchmark inclusion. No third-party plugin was executed or installed. ZIP descriptors were inspected as data.

## Feasible common host

IntelliJ IDEA **2026.1.4, build 261.26222.65**, is within Spellixir's existing supported window and both candidate descriptors below. JetBrains' [release API](https://data.services.jetbrains.com/products/releases?code=IIU&latest=false&type=release) still advertises this exact release, including the [Apple Silicon DMG](https://download.jetbrains.com/idea/idea-2026.1.4-aarch64.dmg) and its [SHA-256 file](https://download.jetbrains.com/idea/idea-2026.1.4-aarch64.dmg.sha256). This is a feasible acquisition target, not a decision to change the project's host policy. Preserve the same host artifact and bundled JBR for every participant; record its checksum before execution. Use the host's lawful free functionality or an appropriate license; GoLand headless activation is unnecessary for this option.

## Pinned candidates

| Candidate | Exact acquisition | Declared compatibility | Constraints |
| --- | --- | --- | --- |
| Elixir, `org.elixir_lang`, Marketplace 7522 | **24.0.1**, [update 1131817](https://plugins.jetbrains.com/plugin/7522-elixir/versions/stable/1131817), [Marketplace ZIP](https://plugins.jetbrains.com/files/7522/1131817/intellij-elixir-24.0.1.zip) | Since **253.28294.1**, no upper bound; Marketplace lists IDEA/IDEA Community, PyCharm and GoLand among supported products | Open source [Apache 2.0](https://github.com/intellij-elixir/intellij-elixir/blob/v24.0.1/LICENSE.md); native parser, highlighter and annotators. Required platform/lang/XML/Markdown dependencies; Java support optional. |
| Flexible Elixir, `com.flexible.elixir`, Marketplace 30043 | **2026.2.72**, [update 1192996](https://plugins.jetbrains.com/plugin/30043-flexible-elixir/versions/stable/1192996), [Marketplace ZIP](https://plugins.jetbrains.com/files/30043/1192996/flexible-elixir-2026.2.72.zip) | Since **242**, no upper bound; IDEA/IDEA Community, PyCharm and GoLand listed | Paid, non-optional purchase model; API reports **7-day trial**. Vendor EULA applies; do not redistribute binary or bypass licensing. **Do not select the separate `2026.2.72-243` artifact**, whose upper bound is 243.*. |

Compatibility evidence: [Elixir updates API](https://plugins.jetbrains.com/api/plugins/7522/updates?size=1), [Flexible updates API](https://plugins.jetbrains.com/api/plugins/30043/updates?size=2), [Flexible metadata and license link](https://plugins.jetbrains.com/api/plugins/30043). Descriptors in the downloaded ZIPs agree with these build bounds. Declared compatibility is not successful execution; a later setup smoke must verify loading, file recognition, highlighting and absence of plugin exceptions.

Locally calculated SHA-256 for the exact Marketplace downloads:

```text
4d5800bb863e30aa6b73e42ea5f8ec634e705229aad7231f4ce918e063e357f0  intellij-elixir-24.0.1.zip
ca67876ee519bf5530a037e19d851158085bfc6a154ae0ff2a3a4ce632b0795e  flexible-elixir-2026.2.72.zip
```

The [Elixir GitHub release](https://github.com/intellij-elixir/intellij-elixir/releases/tag/v24.0.1) also offers a ZIP, but its API digest is `d601d567d75e095bdfe9fa723fb79fbed1d08503b7acdc0a542dad3633115fb0`. That is a different artifact: do not substitute it silently or reuse its hash for the Marketplace package.

## Runtime and feature separation

**Elixir 24.0.1** registers native parsing, syntax highlighting and semantic annotators directly in its [versioned descriptor](https://github.com/intellij-elixir/intellij-elixir/blob/v24.0.1/resources/META-INF/plugin.xml). No language-server dependency or registration appears there. Its [versioned README](https://github.com/intellij-elixir/intellij-elixir/blob/v24.0.1/README.md) documents Elixir plus internal Erlang SDK setup for runtime/project functionality. The release notes document an **Enable automatic Mix deps checking** toggle and opt-in tool-manager integration. A useful first setup candidate is a standalone source fixture, no configured SDK, automatic deps checks off and tool managers off. Native parsing/highlighting without an SDK is an inference from the architecture, to verify during setup; disabling external services does **not** remove native semantic annotators or indexing. Do not describe its default editor as equivalent to Spellixir's narrower feature set.

**Flexible Elixir** has native highlighting plus an LSP service and external diagnostics annotator in the inspected descriptor. Its [Marketplace documentation](https://plugins.jetbrains.com/api/plugins/30043) explicitly calls the language server optional but lists Elixir as a prerequisite, while its [vendor README](https://github.com/ilscipio/Flexible-Elixir) says the runtime is unnecessary for the plugin itself and only needed to run code. That documentation discrepancy remains relevant: runtime-free editing and the exact persisted LSP-off setting need a setup smoke or vendor clarification. No reproducible documented switch was established in this investigation. Treat it as a **conditional** comparator, pending a lawful license/trial, verified server disablement, Credo/AI services off and process evidence. Do not invent a switch, modify the binary, or interpret old descriptor development comments as permission to disable activation.

A full semantic configuration needs separate version pins for runtime, OTP, server, project dependencies and background tools. It is a separate workload rather than a core-editing row with hidden additional work.

## Reproducible isolated setup and baseline

Use one dedicated IDE installation and separate fresh configuration, system/cache, plugin and log directories **per participant and repeat policy**. JetBrains documents `idea.config.path`, `idea.system.path`, `idea.plugins.path` and `idea.log.path` in [IDE directories](https://www.jetbrains.com/help/idea/directories-used-by-the-ide-to-store-settings-caches-plugins-and-logs.html), and external custom properties/JVM options in [advanced configuration](https://www.jetbrains.com/help/idea/tuning-the-ide.html). Do not import personal settings or enable settings sync. Install the complete pinned ZIP in the isolated plugins directory, retain bundled dependency versions, and ensure only one Elixir plugin is enabled. Snapshot effective file associations, plugins, VM options and project settings. Disable automatic plugin updates during a run; retain downloaded artifacts and hashes privately according to their licenses.

For the **plain-text baseline**, use the same fixture bytes and edit sequence with all Elixir plugins absent and an explicit Plain Text association/override. JetBrains documents the per-file **Override File Type** action in [file type associations](https://www.jetbrains.com/help/idea/creating-and-registering-file-types.html). Verify the actual file type before measurement; keeping `.ex` names alone does not prove plain text. This measures editor overhead, not equivalent language correctness.

## Decisions still required

The protocol owner must select the host/edition, whether to start with the open-source candidate alone, which native inspections remain enabled, runtime policy, and what setup evidence admits a candidate. Binary loading, optional-service shutdown and output correctness remain execution acceptance checks. No support-window downgrade or paid purchase was made. The two candidates are sufficient feasible options for planning, not an exhaustive Marketplace inventory.
