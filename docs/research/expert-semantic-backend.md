# Expert as an optional Semantic Backend

Research for [Research Expert as an optional Semantic Backend](https://github.com/cimere/spellixir/issues/12). Sources were checked on 2 August 2026. This note uses Expert's official site/source/releases, JetBrains' LSP documentation, and the source of two existing open-source JetBrains Expert integrations.

## Recommendation

**Keep the Native Core as the always-available product and advance Expert to a direct-adapter proof of concept.** Expert is a credible optional, replaceable **Semantic Backend**: it is the official Elixir LSP, has OS/architecture-specific standalone release assets, and its public architecture intentionally runs project intelligence under the project's Elixir/OTP toolchain. It is a substantially better next candidate than ElixirLS because two independent JetBrains plugins start `expert --stdio` through JetBrains' native LSP descriptor without an ID-rewriting proxy.

This is not yet an adoption decision. Expert still describes itself as **alpha**, and source configuration cannot prove that the current JetBrains transport's string request IDs complete Expert's initialization on every Supported Host. That must be demonstrated before the backend is selected.

## Fit with the product boundary

Expert's manager node owns LSP transport and editor state; its engine node builds and runs with the project's Elixir/OTP, indexes the project, compiles code, and does project-aware intelligence. The engine build uses private Expert cache directories for Mix/Hex/Rebar state, while project compilation runs `mix compile` with its build artifacts under `.expert/build`. This fits an opt-in Semantic Backend, but also makes the trust boundary real: enabling Expert may fetch dependencies (by default) and execute Mix compilation in the opened project. [Expert architecture](https://github.com/expert-lsp/expert/blob/main/pages/architecture.md), [configuration](https://github.com/expert-lsp/expert/blob/main/pages/configuration.md)

Consequently, the plugin should never make Expert a precondition for Native Core features. Initial UX should be explicitly opt-in, explain that project code/tooling can run, and expose a clear disabled state and stop/restart control.

## LSP and Host IDE compatibility

JetBrains documents a direct stdio integration: an `LspIntegrationProvider` starts a `ProjectWideLspClientDescriptor`, whose command line can be `expert --stdio`. The LSP API is public in IntelliJ IDEA, GoLand, and PyCharm; the product's established 2026.1.4 baseline is also the point where the current client-named API and customization hooks apply. JetBrains lists diagnostics, completion, definition, hover, formatting, code actions, semantic tokens, range formatting, code lens, organize imports, rename, and on-type formatting among the features supported by various platform releases. [JetBrains LSP integration](https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html)

The two existing plugins provide concrete, but not conclusive, evidence for this exact server/adapter pairing:

- [Explore's descriptor](https://github.com/thiagopromano/explore/blob/main/src/main/kotlin/com/thiromano/exact/lsp/ElixirLspServerDescriptor.kt) starts `expert --stdio` with the project base directory; its [build](https://github.com/thiagopromano/explore/blob/main/build.gradle.kts) targets 2025.3 (build 253).
- [ElixirIJ's descriptor](https://github.com/mkaput/elixirij/blob/main/src/main/kotlin/dev/murek/elixirij/lsp/ExpertLspServerDescriptor.kt) does the same; its [configuration](https://github.com/mkaput/elixirij/blob/main/gradle.properties) also starts at build 253.

Neither repository provides a reason to force integer request IDs or a protocol proxy. That is promising evidence that Expert accepts JetBrains' native client behavior, but a local handshake is still required because these projects do not constitute a cross-host compatibility guarantee.

## Toolchain, executable, and lifecycle

Expert supports Elixir 1.15.3+ with OTP 25+ (OTP 26 requires 26.0.2+); its docs say it must be compiled under the oldest Elixir/OTP version intended for projects. The published Burrito-style release assets are standalone binaries for macOS arm64/amd64, Linux arm64/amd64, and Windows amd64, while a source/plain release relies on a locally compatible runtime. The current `v0.1.8` release (27 July 2026) supplies SHA-256 digests for those assets. [Installation](https://github.com/expert-lsp/expert/blob/main/pages/installation.md), [v0.1.8 assets](https://github.com/expert-lsp/expert/releases/tag/v0.1.8)

For the initial opt-in lifecycle, use this precedence:

1. User-configured executable path, validated by an initialize/shutdown probe.
2. Explicitly enabled plugin-managed, **pinned stable** release download for a supported OS/architecture, cached in the IDE system directory.
3. Otherwise remain disabled and link to setup instructions.

Do not silently download or auto-update. The release has checksums, but the official release page does not offer a detached signature in the current assets; download code must verify the pinned SHA-256 obtained from the trusted release metadata, use an atomic replacement, and show the version/source before first execution. Nightlies should be a separate, clearly labelled opt-in channel. This is stricter than Explore's downloader, which follows the `nightly` redirect and writes a fixed `~/.expert/expert` path, and closer to ElixirIJ's system-directory cache/channel model. [Explore downloader](https://github.com/thiagopromano/explore/blob/main/src/main/kotlin/com/thiromano/exact/settings/ExpertDownloader.kt), [ElixirIJ lifecycle](https://github.com/mkaput/elixirij/blob/main/src/main/kotlin/dev/murek/elixirij/lsp/Expert.kt)

When Expert resolves the project toolchain itself, show the discovered `elixir` and `erl` paths/versions before startup and permit explicit overrides. Its configuration protocol already has `elixirExecutablePath` and `erlangExecutablePath`; the plugin should set these only after the user has opted in. [Expert configuration](https://github.com/expert-lsp/expert/blob/main/pages/configuration.md)

## Available capability and gaps

Expert's provider source currently includes handlers for completion, definition, references, hover, formatting, code actions and resolve, document/workspace symbols, folding, and code lens. Its architecture documents diagnostics, persistent project search, compiled BEAM metadata, and macro-aware project compilation as inputs to intelligence. [Provider handlers](https://github.com/expert-lsp/expert/tree/main/apps/expert/lib/expert/provider/handlers), [architecture](https://github.com/expert-lsp/expert/blob/main/pages/architecture.md)

The current Explore adapter explicitly disables semantic tokens, type definition, folding, inlay hints, document highlights, signature help, and selection ranges because it considers them unsupported by Expert; it also notes missing rename, implementation, declaration, call/type hierarchy. Treat that as integration-maintainer evidence, not an upstream feature contract, and repeat it from Expert's initialize response in the proof of concept. [Explore customization](https://github.com/thiagopromano/explore/blob/main/src/main/kotlin/com/thiromano/exact/lsp/ElixirLspServerDescriptor.kt)

Native highlighting, PSI, and other Native Core functionality remain authoritative. LSP semantic tokens should stay disabled initially to avoid two highlighters competing; capabilities not advertised by Expert should be disabled through `LspCustomization` rather than allowed to produce unsupported requests.

## Smallest validation before adoption

Build one throwaway direct adapter—no downloader, proxy, or native-feature replacement—with `expert --stdio`, a project working directory, and explicit opt-in. Test the exact packaged plugin on IntelliJ IDEA, GoLand, and PyCharm at the 2026.1.4 baseline (and current supported build) against:

1. a plain Mix project using a supported local Elixir/OTP;
2. a project whose toolchain is found through each supported discovery route; and
3. a deliberately mismatched/missing toolchain, ensuring a clear disabled/error state and unaffected Native Core.

Pass only if Expert completes `initialize` with JetBrains' native client (therefore proving request-ID compatibility), starts/restarts cleanly, and supplies diagnostics plus completion, definition, hover, formatting, document/workspace symbols, references, and code actions. Record the negotiated server capabilities and first-index latency. Do not add automatic release management until this proof passes; it would obscure whether a failure belongs to JetBrains transport, Expert, toolchain discovery, or downloading.

## Decision inputs

Expert is suitable to evaluate as the ElixirLS replacement because it eliminates the already-proven ElixirLS request-ID incompatibility without introducing a protocol proxy, supports project-version toolchains by design, and has direct native-JetBrains examples. Adopt it only if the stated direct-adapter matrix passes and the team accepts the alpha/release-trust and project-execution trade-offs. Otherwise retain a Native Core-only release and leave Semantic Backend disabled.
