# Elixir semantic-analysis and debugging backends

Research date: 2026-08-01

## Decision

Keep the plugin's file type, lexer/parser/PSI, syntax highlighting, and basic IDE integration native to the IntelliJ Platform. Treat semantic analysis and debugging as optional capabilities behind separately replaceable adapters.

For the first semantic integration, prototype **ElixirLS over LSP4IJ**, not JetBrains' native LSP API. For debugging, prototype **ElixirLS DAP over LSP4IJ** after ordinary Mix run configurations work. Do not bundle either integration into the initial syntax-highlighting release, and do not make LSP4IJ or an Elixir runtime prerequisites for installing the core plugin.

Re-evaluate **Expert** when it leaves alpha and its feature/compatibility surface is stable. Keep **Lexical** as a fallback candidate only; it adds another compatibility target without providing the debug backend needed by the roadmap.

## Why this boundary fits the product

The intended product must load in IntelliJ IDEA Community Edition as well as GoLand, PyCharm, RustRover, and other JetBrains host IDEs. JetBrains documents its LSP integration as an extension to commercial IntelliJ-based IDEs and explicitly says plugins using it are unavailable in IntelliJ IDEA open-source builds and Android Studio. JetBrains also says canonical custom-language support integrates more deeply than LSP, so LSP is additive rather than a replacement for native language support. Consequently, JetBrains' native LSP API cannot be the cross-host baseline even though it is attractive in supported commercial products. [JetBrains LSP documentation](https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html)

[LSP4IJ](https://github.com/redhat-developer/lsp4ij) is an open-source LSP and DAP client that declares compatibility with all IntelliJ variants. It supports `stdio` language servers, exposes extension points for a plugin to register a server and map it to an IntelliJ language/file type, and provides a DAP Run/Debug configuration. Its current requirements are IntelliJ IDEA 2024.2+ and Java 17+, and its maintainers target the latest four major IDEA releases. This makes it the only evaluated ready-made transport that covers both the Community baseline and the planned debugging path, at the cost of a third-party plugin dependency and its release cadence.

The core plugin should declare only IntelliJ Platform module dependencies shared by the desired hosts. JetBrains states that module dependencies determine cross-product compatibility; `com.intellij.modules.xdebugger` is available across IntelliJ Platform products for debug sessions, stack frames, breakpoints, and source positions. [JetBrains plugin compatibility](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html) If LSP4IJ integration is shipped in the same plugin artifact, it should be an optional plugin dependency with registrations isolated in an additional descriptor. JetBrains' optional-dependency mechanism loads the main plugin without the dependency and loads the extra descriptor only when it is present. [JetBrains plugin dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)

## Backend assessment

### JetBrains native LSP

**Viability:** good only for supported commercial IDE distributions; unsuitable as the shared baseline.

Advantages are first-party APIs, no extra client plugin, and direct platform ownership. The blocking limitation is product availability: the implementation is not part of IntelliJ IDEA Community/open-source builds. Supporting both native LSP and a Community client would also create two client integrations to test, while providing little product value initially. Defer it unless later measurements show a concrete feature, performance, or maintenance advantage over LSP4IJ.

### LSP4IJ

**Viability:** recommended Community-compatible LSP/DAP bridge.

Its developer guide requires an external plugin to depend on `com.redhat.devtools.lsp4ij`, register a server factory, launch the server process, and map the server to the plugin's language or file type. LSP4IJ supplies Eclipse LSP4J itself and warns integrations not to embed another copy because classloader conflicts can cause `ClassCastException`. [LSP4IJ developer guide](https://github.com/redhat-developer/lsp4ij/blob/main/docs/DeveloperGuide.md)

This dependency should remain optional. Two packaging designs are viable:

1. **One Marketplace plugin with an optional LSP4IJ descriptor.** This gives users one Elixir plugin; semantic/debug registrations activate only when LSP4IJ is installed. It is the recommended starting design because JetBrains explicitly supports optional plugin descriptors.
2. **A separate companion plugin.** The native core remains dependency-free and an `Elixir Semantic Support` companion depends on both the core and LSP4IJ. This creates clearer failure and compatibility boundaries, but adds Marketplace, versioning, discovery, and support overhead. Use it only if LSP/DAP dependencies materially destabilize the core artifact or if JetBrains native LSP is later offered as a distinct commercial-host companion.

### ElixirLS

**Viability:** recommended first backend to prototype; do not promise it as permanent.

ElixirLS provides an LSP language server and a DAP debug adapter. Its documented language features include completion, hover documentation, definitions, references, symbols, formatting, compiler diagnostics, Dialyzer analysis, and spec suggestions. Its debugger interprets Mix-project modules before launching tasks and supports breakpoints, conditions, hit counts, log points, and remote attachment, with documented BEAM interpreter and Phoenix limitations. [ElixirLS README](https://github.com/elixir-lsp/elixir-ls#readme)

Operational cost is substantial. ElixirLS supports a moving Elixir/OTP matrix, recommends project-aware version managers, and attempts to activate `asdf`, `mise`, or `vfox`; Windows version-manager support is limited. The current README supports the last five Elixir releases and the last three supported OTP releases plus selected combinations. Initial Dialyzer cache creation can take significant CPU time. The plugin therefore needs explicit toolchain discovery, per-project server lifecycle, logs, cancellation/restart, version diagnostics, and a way to disable expensive features. It must launch against the project's selected Elixir/OTP rather than an arbitrary system executable.

For debugging, ElixirLS is the strongest candidate because it already implements Elixir-specific DAP behavior. Its own documentation records constraints that must be surfaced rather than hidden: `.exs` files need `requireFiles`; interpreted Cowboy/Ecto code can perform badly; Phoenix live reload can invalidate sessions; NIF modules cannot be interpreted; and expression evaluation is limited by the Erlang interpreter. Debug support should therefore follow basic Mix run configurations and begin with local Mix tasks/tests, leaving Phoenix and remote-node debugging for later.

### Expert

**Viability:** watch closely; not the first production backend today.

[Expert](https://github.com/expert-lsp/expert) identifies itself as the official Elixir language-server implementation and publishes per-OS/architecture executables, which could simplify runtime isolation compared with building a server locally. Its project README currently labels it **alpha** and tracks work toward its first full release. That status makes its protocol behavior and feature surface too volatile to anchor this plugin now. It also does not advertise a DAP implementation, so adopting it would not replace the ElixirLS debugging decision.

### Lexical

**Viability:** technically viable LSP fallback, but not preferred for this roadmap.

[Lexical](https://github.com/lexical-lsp/lexical) provides context-aware completion, compilation-driven diagnostics, code actions, formatting, definitions, references, and indexing. Its documented architecture runs project code in a separate VM, while its packaged server targets Erlang 24+ and Elixir 1.13+. That still leaves an Elixir/OTP compatibility and process-management burden, and Lexical does not advertise a DAP adapter. Maintaining integrations for both Lexical and ElixirLS before there is demonstrated user demand would multiply server-specific testing without completing the run/debug roadmap.

## Runtime and installation policy

The initial native editing plugin must work without Elixir, Erlang/OTP, a language server, or LSP4IJ. Runtime-dependent features should activate only after the user selects or confirms a project toolchain.

For semantic/debug features:

- Detect the project toolchain and show the exact `elixir`, `mix`, OTP, server, and server-version choices.
- Prefer a user-managed server initially; later consider verified downloads with checksums and explicit update policy.
- Run one server lifecycle per appropriate project root, not blindly per editor file.
- Keep server logs and restart controls visible and make failures degrade to native editing.
- Test supported host IDEs separately from the Elixir/OTP/server matrix; do not imply that one matrix substitutes for the other.
- Keep ordinary Mix execution on JetBrains' Execution API. JetBrains defines run configurations as persistent profiles that prepare command lines, working directories, and environments for external processes. [JetBrains Execution API](https://plugins.jetbrains.com/docs/intellij/execution.html)

## Suggested validation gates

Before committing to the integration, build a throwaway prototype that answers:

1. Can one optional LSP4IJ descriptor register ElixirLS for the native Elixir language without replacing native highlighting?
2. Do completion, diagnostics, hover, definitions, references, formatting, and cancellation behave acceptably in IntelliJ IDEA Community and at least one non-IDEA host?
3. Can the plugin reliably select `asdf`/`mise` project toolchains on macOS and Linux and provide an explicit path fallback on Windows?
4. Does LSP4IJ's DAP UI map ElixirLS stack frames, source positions, variables, and breakpoints well enough for local `mix run` and `mix test`?
5. Can all backend failures leave native highlighting/navigation usable after disable, crash, or incompatible runtime detection?

Passing these gates would justify adopting optional ElixirLS/LSP4IJ semantic support. Debugging should receive its own acceptance decision because DAP usability and BEAM limitations are independent of LSP success.

## Primary sources

- [JetBrains: Language Server Protocol](https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html)
- [JetBrains: Plugin Compatibility with IntelliJ Platform Products](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html)
- [JetBrains: Plugin Dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)
- [JetBrains: Execution](https://plugins.jetbrains.com/docs/intellij/execution.html)
- [LSP4IJ repository and requirements](https://github.com/redhat-developer/lsp4ij)
- [LSP4IJ developer guide](https://github.com/redhat-developer/lsp4ij/blob/main/docs/DeveloperGuide.md)
- [ElixirLS repository and documentation](https://github.com/elixir-lsp/elixir-ls)
- [Expert repository and status](https://github.com/expert-lsp/expert)
- [Lexical repository and documentation](https://github.com/lexical-lsp/lexical)
