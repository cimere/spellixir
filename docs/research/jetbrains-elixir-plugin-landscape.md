# JetBrains Elixir plugin landscape

Research date: 2026-08-01

## Question

What do current JetBrains Marketplace offerings and their source repositories reveal about the strengths, gaps, maintenance risks, architecture, licensing, and user expectations for Elixir language support? What evidence-based opportunities exist for a distinct greenfield plugin?

## Executive answer

The Marketplace has two relevant active offerings, and neither makes the proposed plugin redundant.

- [Elixir](https://plugins.jetbrains.com/plugin/7522-elixir) is a mature, free, source-available native IntelliJ language implementation. Its repository shows broad functionality, generated lexer/parser/PSI code, extensive tests, SDK and Mix integration, and years of compatibility work. It is the strongest evidence that deep native Elixir support is possible, but also that supporting the whole language and many JetBrains hosts becomes a large maintenance commitment.
- [Flexible Elixir](https://plugins.jetbrains.com/plugin/30043-flexible-elixir) is a newer, fast-moving cross-IDE product. Its Marketplace history advertises native parsing/editor features, templates, SDK discovery, and optional LSP navigation. However, its linked [GitHub repository](https://github.com/ilscipio/Flexible-Elixir) explicitly says it is only a community hub and issue tracker; as of the research date its tree contains only `README.md`. Its implementation, tests, architecture, and effective source license therefore cannot be audited there.

A greenfield plugin should differentiate through trust and maintainability: Apache-2.0 source, a small native Community-compatible core, documented compatibility across host IDEs, grammar conformance tests, graceful operation without an Elixir runtime for basic editing, and incremental releases. It should not promise a feature-complete replacement at launch.

## Offerings and evidence

### Elixir (KronicDeth/intellij-elixir)

The Marketplace describes support for Elixir, ExUnit, Ecto, Phoenix, EEx/LEEx, decompilation, disassembly, and debugging. It lists compatibility across IntelliJ IDEA Community and many smaller IDEs. The current Marketplace version history continues through 2026, while the source repository's latest release at the time of research is [`v23.8.2`](https://github.com/KronicDeth/intellij-elixir/releases/tag/v23.8.2), published 2026-05-16. These are direct signs of an active rather than abandoned competitor.

The [source tree](https://github.com/KronicDeth/intellij-elixir) reveals a native IntelliJ architecture rather than an LSP wrapper:

- generated JFlex lexer, Grammar-Kit parser, and PSI classes under `gen/org/elixir_lang`;
- language implementation and platform integrations under `src/org/elixir_lang`;
- separate JPS builder modules;
- SDK, Mix import/synchronization, formatting, completion, navigation, reference resolution, decompilation, debugging, and refactoring code;
- large parser fixtures and focused tests for PSI, completion, references, navigation, Mix, SDK discovery, and refactoring.

This breadth is a strength for users and a warning for a new maintainer. The [version history](https://plugins.jetbrains.com/plugin/7522-elixir/versions) records recurring work caused by IntelliJ API changes, host-specific project models, threading requirements, operating-system command handling, Erlang/OTP changes, and Elixir grammar changes. It also documents optional Java dependencies specifically to preserve compatibility with smaller IDEs. Cross-host support is therefore an architectural constraint, not merely a Marketplace checkbox.

The repository has thousands of commits and hundreds of open issues. Those counts should not be read as quality scores, but together with the source tree they show the surface area accumulated by a feature-rich language plugin. The repository's [license file](https://github.com/KronicDeth/intellij-elixir/blob/main/LICENSE.md) must be reviewed before reusing any implementation; GitHub does not classify it as a standard SPDX license. Treat it as a source for product and architecture lessons, not a code donor by default.

### Flexible Elixir

The [Marketplace version history](https://plugins.jetbrains.com/plugin/30043-flexible-elixir/versions) shows frequent 2026 releases and declares a broad minimum compatibility range: IntelliJ IDEA Community, GoLand, PyCharm, WebStorm, RubyMine, RustRover, and other hosts. The plugin depends only on `com.intellij.modules.platform` according to the Marketplace metadata, which is consistent with prioritizing broad host portability.

Marketplace release notes describe syntax highlighting, completion, navigation, folding, validation, formatting, Mix recognition, SDK auto-configuration, EEx/HEEx/LEEx support, and an optional [Dexter](https://github.com/remoteoss/dexter) LSP server for navigation. This is evidence of the feature set being marketed and released, but not sufficient evidence of implementation quality.

The linked [source repository](https://github.com/ilscipio/Flexible-Elixir) contains only a README and states that it exists as a community hub and issue tracker. Although that README invites contributions and refers to a `LICENSE` file, no license file or plugin source is present in the repository tree as of the research date. Consequently:

- the implementation cannot be independently audited or learned from;
- its parser, PSI, indexing, testing, privacy, and LSP process-management choices cannot be verified;
- external contributors cannot submit implementation changes through the published repository;
- Marketplace claims should be validated hands-on before using them as a benchmark.

This creates a concrete transparency opportunity for an Apache-2.0 greenfield project.

## User expectations implied by the market

The overlapping feature sets imply a baseline users will eventually expect:

1. `.ex` and `.exs` recognition with accurate highlighting, commenting, brace matching, and folding.
2. Mix project recognition and understandable SDK/toolchain setup.
3. Navigation and completion for modules and functions.
4. Formatting and useful diagnostics.
5. ExUnit run/debug integration.
6. Phoenix template support.
7. Compatibility with the JetBrains IDE the user already owns, not only IntelliJ IDEA.

This is an eventual expectation, not an MVP checklist. The mature plugin demonstrates that attempting all of it immediately creates a very large correctness and compatibility surface.

## Maintenance risks to plan for

- **Elixir syntax is macro-friendly and context-sensitive.** Coloring tokens is small; trustworthy reference resolution, completion, inspections, and refactoring are not.
- **JetBrains APIs and host project models evolve.** Compatibility needs automated verification against a declared IDE matrix and deliberate upgrade windows.
- **Host IDE behavior differs.** Project/module attachment, context menus, and bundled language plugins can conflict. GoLand, PyCharm, and RustRover require real smoke tests.
- **Runtime integration multiplies environments.** Elixir, Erlang/OTP, Mix, version managers, Windows/WSL, and command quoting all add compatibility dimensions.
- **LSP adds another lifecycle.** Server installation, version compatibility, startup, cancellation, crashes, logs, and capability negotiation become plugin responsibilities. It can accelerate semantics, but does not remove native editor and host-integration work.
- **Feature breadth can hide reliability problems.** A public support matrix and conformance corpus are better early differentiators than a long unverified feature list.

## Recommended greenfield position

Build a free Apache-2.0 plugin whose first promise is deliberately modest: reliable `.ex`/`.exs` recognition and native syntax highlighting in IntelliJ IDEA Community, with Mix detection and no runtime requirement for basic editing. Design against `com.intellij.modules.lang` or the smallest viable platform dependency and verify that decision before implementation.

The distinctive product principles should be:

- **Transparent:** implementation, tests, architecture decisions, limitations, and compatibility matrix are public.
- **Portable:** generic IntelliJ Platform APIs first; each claimed host IDE is tested before Marketplace declaration.
- **Native at the core:** lexer/parser/PSI work is introduced only to the depth required by the next feature, with grammar and malformed-code tests.
- **Runtime-optional for editing:** runtime-dependent capabilities clearly detect and explain missing Elixir/OTP configuration.
- **Incremental:** syntax highlighting first, then structural navigation, completion, inspections, templates, run/debug, and refactoring based on shared foundations and user evidence.
- **Honest about LSP:** keep ElixirLS/Dexter or another semantic provider as a separate researched decision. Do not make an LSP mandatory until Community Edition compatibility, redistribution, process management, and failure behavior are proven.

## Decisions supported by this research

1. Proceed with a greenfield plugin only if the goal is a maintainable, transparent alternative and learning project—not novelty or an empty-market claim.
2. Keep Phase 1 narrow and native; broad language intelligence and runtime integration are later decisions.
3. Treat cross-IDE support as a tested compatibility program from the start.
4. Use competitors to establish expectations and risks, but do not copy claims, source, grammar, or architecture without separate license and technical review.
5. Create follow-up research for the lexer/parser strategy, Community-compatible semantic/LSP options, and the host-IDE compatibility matrix.

## Primary sources

- [Elixir — JetBrains Marketplace](https://plugins.jetbrains.com/plugin/7522-elixir)
- [Elixir version history — JetBrains Marketplace](https://plugins.jetbrains.com/plugin/7522-elixir/versions)
- [KronicDeth/intellij-elixir source repository](https://github.com/KronicDeth/intellij-elixir)
- [KronicDeth/intellij-elixir releases](https://github.com/KronicDeth/intellij-elixir/releases)
- [KronicDeth/intellij-elixir license](https://github.com/KronicDeth/intellij-elixir/blob/main/LICENSE.md)
- [Flexible Elixir — JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30043-flexible-elixir)
- [Flexible Elixir version history — JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30043-flexible-elixir/versions)
- [ilscipio/Flexible-Elixir community repository](https://github.com/ilscipio/Flexible-Elixir)
- [remoteoss/dexter source repository](https://github.com/remoteoss/dexter)
