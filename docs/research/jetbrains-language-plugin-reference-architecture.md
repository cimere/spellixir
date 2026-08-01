# JetBrains language-plugin reference architecture

Research for GitHub issue 2. Sources were checked on 2026-08-01. This note uses only JetBrains documentation, JetBrains-maintained repositories, and source/configuration from open-source language plugins. Statements labeled **JetBrains guidance** are requirements or recommendations in first-party material. Statements labeled **Repository pattern** are inferences from maintained examples, not platform requirements.

## Executive recommendation

Start with one Gradle project and one deployable plugin artifact, organized internally into narrow packages for language model, lexer/parser/PSI, editor features, project integration, and infrastructure. Compile against IntelliJ IDEA Community Edition, but make the required runtime dependency only `com.intellij.modules.platform`; do not use Java/IDEA APIs unless the feature genuinely needs them. Keep host-specific integrations optional and isolated behind separate plugin descriptors, and add Gradle subprojects only when those integrations acquire real compile-time dependencies or independent test matrices. This preserves the broadest Host IDE compatibility without paying the complexity cost of an experimental modular-plugin architecture on day one. JetBrains says module dependencies determine which products can load a plugin, warns that a plugin with no module dependency is treated as legacy and IDEA-only, and recommends verifying every claimed host with Plugin Verifier ([product compatibility](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html)).

Use Kotlin/JVM, Gradle Kotlin DSL, the Gradle Wrapper, IntelliJ Platform Gradle Plugin 2.x, Grammar-Kit, and JFlex. Keep `.bnf` and `.flex` files as source-of-truth inputs and generated parser/PSI/lexer code in a generated source directory. JetBrains recommends Grammar-Kit-generated parsers and PSI, calls JFlex the easiest lexer route, and the current JetBrains template uses Kotlin DSL and the 2.x platform plugin ([parser and PSI](https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html), [lexer](https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html), [template](https://github.com/JetBrains/intellij-platform-plugin-template)).

## What is version-sensitive

- **JetBrains guidance:** IntelliJ Platform Gradle Plugin 2.x is the current build, test, verification, run, and publishing integration; the older `org.jetbrains.intellij` 1.x plugin is no longer actively developed. Apply `org.jetbrains.intellij.platform` to the main plugin project ([2.x overview](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html), [dependency migration warning](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)).
- **JetBrains guidance:** Kotlin 2.x is recommended when targeting 2024.3+ and required for 2025.1+. A plugin should use the Kotlin standard library bundled by the target IDE rather than package its own; set `kotlin.stdlib.default.dependency=false`, and compile against APIs available in the lowest supported bundled stdlib ([Kotlin support](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html)). Recheck this page whenever the minimum platform branch changes.
- **JetBrains guidance:** the platform API and extension points can change between release lines. `since-build`/`until-build` describe a compatibility claim; they do not prove it. Check the incompatible-changes pages and run Plugin Verifier against the claimed range and products ([compatibility verification](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html), [platform compatibility](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html)).
- **JetBrains guidance:** modular plugins are explicitly experimental. Their descriptor format, per-module classloaders, and frontend/backend loading controls are useful for advanced deployment boundaries, but are not the prudent baseline for this plugin ([modular plugins](https://plugins.jetbrains.com/docs/intellij/modular-plugins.html)).
- **Repository pattern:** do not copy old versions from a mature repository blindly. For example, the archived/currently mirrored IntelliJ Rust build demonstrates valuable multi-project and generated-source boundaries but still shows the obsolete Gradle IntelliJ Plugin 1.x, so its version pins are historical rather than recommendations ([IntelliJ Rust build](https://android.googlesource.com/platform/external/jetbrains/intellij-rust/+/refs/heads/upstream-master/build.gradle.kts)).

## Architecture and module boundaries

### Initial boundary: one artifact, deep internal seams

**JetBrains guidance:** a conventional plugin is an artifact described by `src/main/resources/META-INF/plugin.xml`; extensions are implementations registered with extension points. Plugin dependencies must exist both on the Gradle compile classpath and in the plugin descriptor, and optional dependencies must place their registrations in a separate `config-file` descriptor ([extensions](https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html), [plugin dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)).

The recommended internal dependency direction is:

```text
language + file type
        ↓
tokens / lexer → parser → syntax PSI
                            ↓
                    semantic services
                 (names, references, indexes)
                            ↓
          editor features and project integration
                            ↓
              plugin.xml extension adapters
```

The arrows mean “may depend on.” Keep the grammar-facing layers unaware of UI, project discovery, process execution, or a particular Host IDE. Editor extensions should call cohesive semantic services rather than each re-walking raw AST nodes. Platform registrations remain thin adapters because the platform owns their lifecycle.

Suggested packages inside the initial plugin module:

- `lang`: `Language`, file type, icons, token/element types;
- `lexer` and `parser`: adapters plus generated implementations;
- `psi`: generated PSI interfaces/implementations, handwritten mixins, visitors, factories, and utilities;
- `resolve` (later): names, references, scope, indexes, symbols;
- `highlighting`, `formatting`, `navigation` (as features arrive): extension implementations;
- `mix`: project detection and Mix-specific services, kept out of syntax/PSI;
- `platform`: narrowly scoped services, settings, notifications, and extension adapters.

This package layout is a recommendation, not a JetBrains-mandated taxonomy. It follows the platform's actual separation: lexers produce tokens, parsers build AST structure, PSI adds semantic/manipulation APIs, and language features are registered independently through extension points ([lexer](https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html), [parser and PSI](https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html), [custom-language feature map](https://plugins.jetbrains.com/docs/intellij/custom-language-support.html)).

### When to split Gradle modules

Keep the initial build single-module. Split only at a dependency boundary that the compiler and test matrix should enforce, for example:

```text
:plugin-core              platform-only language support
:plugin-idea-java         optional Java/IDEA integration
:plugin-goland            optional Go-specific integration
:plugin-distribution      assembles descriptors and plugin ZIP
```

**JetBrains guidance:** if a feature uses another bundled or Marketplace plugin's classes, declare that dependency in Gradle and in XML. Optional plugin functionality belongs in its own optional descriptor so the base plugin can still load without it ([plugin dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)). **Repository pattern:** IntelliJ Rust applies common configuration to subprojects, has a root distribution concern, and marks generated source directories explicitly; this shows that multi-project builds can enforce large-plugin seams, but also demonstrates the maintenance overhead that a greenfield Phase 1 plugin should defer ([IntelliJ Rust root build](https://android.googlesource.com/platform/external/jetbrains/intellij-rust/+/refs/heads/upstream-master/build.gradle.kts), [IntelliJ Rust settings](https://github.com/intellij-rust/intellij-rust/blob/master/settings.gradle.kts)).

Do not confuse Gradle subprojects with JetBrains' experimental modular-plugin format. A conventional plugin ZIP may contain code assembled from multiple Gradle projects without adopting per-module descriptors/classloaders. Adopt modular plugins only if later requirements genuinely need independently loadable parts or remote-development frontend/backend placement, and reassess the then-current experimental status ([modular plugins](https://plugins.jetbrains.com/docs/intellij/modular-plugins.html)).

## Gradle and build setup

**JetBrains guidance:** use the current template as the baseline: Gradle Wrapper, Kotlin DSL, repositories declared through the IntelliJ Platform repositories extension, plugin and dependency versions in Gradle files, target/platform settings in `build.gradle.kts`, and plugin identity plus extensions in `plugin.xml`. The template enables Gradle build/configuration caches and disables Kotlin's automatic stdlib dependency ([template structure and Gradle configuration](https://github.com/JetBrains/intellij-platform-plugin-template)).

The build should contain these concepts (exact plugin and IDE versions must be selected at implementation time):

1. `org.jetbrains.kotlin.jvm` and `org.jetbrains.intellij.platform` 2.x; add the maintained `org.jetbrains.grammarkit` plugin for generation.
2. `repositories { intellijPlatform { defaultRepositories() } }` plus Maven Central only for genuine third-party libraries.
3. Exactly one development platform dependency, initially the chosen IntelliJ IDEA Community release. The 2.x dependency DSL permits only one target platform per project; installers are the advised default and include JetBrains Runtime ([dependency extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html)).
4. Explicit `testFramework(TestFrameworkType.Platform)` and the chosen JUnit engine. Test-only host dependencies should use `testBundledPlugin`, `testBundledModule`, or related helpers rather than polluting production scope ([test dependencies](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#testing)).
5. Grammar generation tasks wired before Kotlin/Java compilation, with `.bnf` and `.flex` inputs and generated outputs marked as source directories. JetBrains recommends the Gradle Grammar-Kit integration, while IntelliJ Rust provides a concrete open-source example of a generation task and `src/gen` source root ([parser generation guidance](https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html), [IntelliJ Rust generation setup](https://android.googlesource.com/platform/external/jetbrains/intellij-rust/+/refs/heads/upstream-master/build.gradle.kts)).
6. No direct production dependency on jars copied from an IDE installation. Use `bundledPlugin()`/`bundledModule()` and matching XML declarations; JetBrains warns that manually adding plugin jars can create duplicate class copies at runtime ([plugin dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html), [dependency helpers](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html)).

Commit the wrapper and grammar sources. Whether generated Java is committed should be a deliberate repository policy: generating in CI avoids stale diffs; committing can simplify source browsing and downstream packaging. JetBrains requires neither policy in the cited guidance. Whichever is chosen, CI must regenerate deterministically and fail on compilation or unexpected drift.

## Compatibility from IDEA Community to multiple Host IDEs

**JetBrains requirement:** declare at least one IntelliJ Platform module dependency. A new broadly compatible language plugin should normally require `com.intellij.modules.platform`, not `com.intellij.modules.java` or `com.intellij.java`, unless it actually uses those capabilities. Marketplace derives compatible products from declared dependencies; a plugin that declares no module dependency is treated as legacy and loads only in IntelliJ IDEA ([product compatibility](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html)).

Compiling against IDEA Community is a useful development baseline, not proof of cross-product compatibility. Maintain an explicit matrix containing the oldest and newest supported platform branches and each claimed Host IDE (initially IDEA Community, later GoLand, PyCharm, and RustRover). Run the packaged ZIP through Plugin Verifier for that matrix. The 2.x Gradle extension can select explicit product releases, the current target, latest releases, or JetBrains' recommended set ([verification DSL](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html), [Plugin Verifier](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html)). Add at least smoke-level functional tests on representative non-IDEA hosts when they become supported, because binary verification cannot prove runtime behavior.

If a later feature integrates with another language or product plugin, place its registrations in an optional XML descriptor and its implementation behind a source/module boundary. Required dependencies narrow the Marketplace compatibility set; optional descriptors let the core load without that plugin ([optional dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)).

## Lexer, parser, AST, and PSI boundaries

**JetBrains guidance:** the lexer must cover the entire input, emit a token even for invalid characters, and never stop early. The parser must consume the complete token stream even for syntactically invalid input. Reuse platform token types where applicable and return stable singleton `IElementType` instances ([lexer contract](https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html), [parser contract](https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html)). These recovery properties deserve regression tests because incomplete code is the editor's normal operating state.

Use JFlex for tokenization and Grammar-Kit BNF for parsing and generated PSI. Keep handwritten behavior in PSI interfaces, mixins, reference implementations, and service classes rather than editing generated files. `ParserDefinition` is the assembly boundary: it supplies the lexer/parser, creates PSI files/elements, and declares whitespace/comment token sets. JetBrains specifically recommends a dedicated token-set constants class to avoid unnecessary classloading during `ParserDefinition` initialization ([parser and PSI](https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html)).

Treat the AST as concrete syntax and PSI as the platform-facing semantic model, not as a compiler-independent domain AST. PSI nodes map to document ranges and edits affect the document; renameable/referenceable constructs need `PsiNamedElement` to participate in Rename and Find Usages ([parser and PSI](https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html), [PSI files](https://plugins.jetbrains.com/docs/intellij/psi-files.html)). If later phases add a standalone Elixir parser or LSP, put that model behind a separate service/module and write explicit mappings; do not make editor PSI depend on an external process for basic parsing, highlighting, or navigation.

## Testing layers

The recommended pyramid is:

1. **Pure Kotlin tests:** fast tests for platform-independent algorithms, text transformations, and Mix metadata parsing. No IDE fixture when no platform object is needed.
2. **Lexer/parser/PSI fixture tests:** golden token streams, parse trees, malformed/incomplete input recovery, PSI element types/names, and round-trip edits. Keep fixtures in `src/test/testData`, matching the JetBrains template ([template testing and layout](https://github.com/JetBrains/intellij-platform-plugin-template)).
3. **Light platform feature tests:** use `BasePlatformTestCase` or `LightPlatformTestCase` for non-Java functionality; exercise highlighting, completion, navigation, rename, inspections, formatting, and project detection through real platform services. JetBrains says plugin tests run against real rather than mocked platform implementations, recommends light tests where possible, and reserves heavy tests for cases needing a new project each time ([light and heavy tests](https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html)).
4. **Heavy/integration tests:** use only for true project-model, filesystem, process, SDK, or lifecycle isolation requirements. Separate these from the fast PR suite if runtime warrants it.
5. **Host smoke tests:** once additional IDEs are claimed, launch or functionally test the packaged plugin on representative host distributions in addition to Plugin Verifier.
6. **UI tests:** add only for consequential UI flows. The current template intentionally does not wire UI testing by default and directs projects to add their own source set/tasks/OS matrix when needed ([template UI-testing position](https://github.com/JetBrains/intellij-platform-plugin-template)).

Every language feature should have malformed-code cases, not only valid examples. Lexer/parser tests protect the foundation; feature-level fixture tests protect registration, threading/lifecycle assumptions, and actual PSI behavior that pure unit tests cannot represent.

## CI and verification gates

For every pull request, run independent jobs for:

- `check` (pure and platform tests);
- grammar generation plus compilation/build of the distributable ZIP;
- `verifyPlugin` against the supported product/version matrix;
- static analysis and formatting chosen by the project;
- artifact upload of the plugin ZIP and verifier/test reports.

**JetBrains guidance:** the maintained template's Build workflow runs `buildPlugin`, `check`, and `verifyPlugin` in separate jobs and uploads the plugin artifact. Plugin Verifier checks binary compatibility, while Gradle's plugin-configuration verification also catches configuration concerns such as an omitted Kotlin stdlib policy ([template CI](https://github.com/JetBrains/intellij-platform-plugin-template), [verifier](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html), [Kotlin Gradle check](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html)). Configure verifier failure levels deliberately; compatibility problems, invalid structure/plugin, missing dependencies, internal APIs, scheduled removals, and deprecated APIs should be visible, with the strictness ratcheted rather than silently ignored ([verification configuration](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html)).

Add a scheduled job against EAP/upcoming IDE releases using multi-OS archives if early warning is worth the download cost; JetBrains advises installers for normal releases but notes that EAP archives help validate upcoming IDE support ([target platform artifacts](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#target-versions)). Keep release publishing out of PR jobs and require a protected environment/manual approval for Marketplace credentials.

## Signing, publishing, and release tooling

Use the 2.x tasks `buildPlugin`, `signPlugin`, `verifyPluginSignature`, and `publishPlugin`. Keep `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `CERTIFICATE_CHAIN`, and `PUBLISH_TOKEN` in CI secrets, never repository files. When signing material is configured, `signPlugin` runs before publishing; signature verification can be an additional release gate ([plugin signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html), [template secret model](https://github.com/JetBrains/intellij-platform-plugin-template)).

Follow the template's release shape: a CI build produces a reviewable ZIP; a GitHub Release event drives the release workflow; the workflow signs, publishes with the Marketplace token, attaches the distribution, and updates changelog/release notes. This is a JetBrains-maintained template pattern, not a Marketplace requirement ([template release workflow overview](https://github.com/JetBrains/intellij-platform-plugin-template)). Marketplace does require review of new plugins and updates under its current process, so release automation must tolerate approval delay ([approval guidelines](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html)).

Use a custom channel or hidden release for alpha/beta validation before broad availability. Compatibility ranges may be edited after submission, but JetBrains says the declared range must still be verified with Plugin Verifier ([upload channels](https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html), [plugin updates](https://plugins.jetbrains.com/docs/marketplace/plugin-updates.html)). Pin CI actions and Gradle plugin/tool versions, use dependency update automation, and review upgrades alongside the platform's incompatible-change notes.

## Pragmatic starting tree

```text
.
├── .github/
│   └── workflows/             build, scheduled compatibility, release
├── gradle/wrapper/
├── src/
│   ├── main/
│   │   ├── grammars/          Elixir.bnf, Elixir.flex (source of truth)
│   │   ├── kotlin/.../
│   │   │   ├── lang/
│   │   │   ├── lexer/
│   │   │   ├── parser/
│   │   │   ├── psi/
│   │   │   ├── highlighting/
│   │   │   ├── mix/
│   │   │   └── platform/
│   │   └── resources/
│   │       └── META-INF/plugin.xml
│   ├── test/
│   │   ├── kotlin/.../        pure + light fixture tests
│   │   └── testData/          lexer, parser, highlighting, Mix fixtures
│   └── gen/                   generated sources, if kept under src
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── CHANGELOG.md
└── gradlew, gradlew.bat
```

This extends the official template's single-module `src/main`, `src/test`, resources, test-data, wrapper, CI, and changelog layout with language-plugin-specific grammar/generated/package boundaries ([official template tree](https://github.com/JetBrains/intellij-platform-plugin-template)). Begin here; do not pre-create Host IDE adapter modules. The trigger to add one is concrete: a feature requires APIs unavailable in the platform-only core, an optional descriptor, or a separate host test matrix.

## Decisions to carry into implementation

1. Select and document the minimum platform branch, matching JDK/JVM target, Kotlin compiler/API level, and bundled stdlib ceiling together; these are a compatibility unit, not independent “latest” choices ([Kotlin support matrix](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html)).
2. Make `com.intellij.modules.platform` the only required product module for Phase 1 unless implementation proves another dependency necessary ([product module rules](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html)).
3. Pin current 2.x platform/Grammar-Kit versions and configure deterministic BNF/JFlex generation; never hand-edit outputs ([2.x plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html), [Grammar-Kit recommendation](https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html)).
4. Define the supported Host IDE/version matrix in one Gradle/CI configuration and use it for verifier gates; add runtime smoke coverage as products are claimed ([verification DSL](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html)).
5. Keep release secrets solely in the protected CI environment, publish prereleases to a channel/hidden update first, and verify the exact ZIP that is signed and published ([signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html), [hidden releases](https://plugins.jetbrains.com/docs/marketplace/hidden-plugin.html)).

