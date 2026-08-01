# Native Elixir language-model options

Research for [Evaluate native Elixir language-model strategies](https://github.com/cimere/spellixir/issues/8), based only on official documentation and upstream source repositories. Sources were checked on 1 August 2026.

## Decision

Use a layered, JetBrains-native implementation:

1. A JFlex lexer implementing the IntelliJ `Lexer` contract, with explicit restart states for strings, heredocs, sigils, interpolation, and other multiline constructs.
2. A Grammar-Kit grammar generating a tolerant IntelliJ parser and PSI tree. Design named PSI elements around stable language concepts needed by IDE features, not around the exact shape of Elixir's compiler AST.
3. The official Elixir tokenizer/parser and `tree-sitter-elixir` as specifications and differential-test oracles, not as code embedded in the plugin's editor path.
4. An independently curated syntax corpus assembled from Apache-2.0 upstream fixtures with provenance recorded per imported case. Begin with `tree-sitter-elixir`'s corpus, then add focused cases from Elixir's tokenizer/parser tests and regressions found in real projects.

Do not use Tree-sitter as the primary runtime parser for the first native plugin, and do not translate Elixir's Yecc grammar mechanically into Grammar-Kit. Both remain useful references. Reassess Tree-sitter only if a prototype proves that a Tree-sitter-to-PSI bridge materially reduces total complexity while preserving ordinary IntelliJ editing and refactoring behavior.

## Why this fits the IntelliJ Platform

JetBrains defines the lexer as the foundation for highlighting, syntax-tree construction, word scanning, and later analysis. Highlighting may restart lexing in the middle of a file, so an incremental lexer must expose its restart context as a single integer state. JetBrains recommends JFlex as the easiest custom-language lexer implementation and provides `FlexLexer`/`FlexAdapter` specifically for this integration ([Implementing Lexer](https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html)).

The parser consumes lexer tokens through `PsiBuilder` and produces an AST from which PSI is created. JetBrains recommends Grammar-Kit for generating parsers and PSI classes; its BNF supports error-recovery controls such as `pin` and `recoverWhile`, which are crucial while users are typing incomplete Elixir ([Implementing Parser and PSI](https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html), [Grammar-Kit HOWTO](https://github.com/JetBrains/Grammar-Kit/blob/master/HOWTO.md)). Parser errors flow directly into IDE error highlighting through `PsiBuilder.error()`, while annotators can add semantic diagnostics over PSI ([Syntax and Error Highlighting](https://plugins.jetbrains.com/docs/intellij/syntax-highlighting-and-error-highlighting.html)).

This native tree is the shortest route to later product phases. JetBrains' custom-language APIs build references, resolve, completion, find usages, rename, navigation, and inspections around PSI or the newer Symbol APIs backed by language structure ([Custom Language Support](https://plugins.jetbrains.com/docs/intellij/custom-language-support.html), [References and Resolve](https://plugins.jetbrains.com/docs/intellij/references-and-resolve.html), [Rename Refactoring](https://plugins.jetbrains.com/docs/intellij/rename-refactoring.html)). A syntax tree alone does not provide those semantics, but a stable PSI gives each later feature the expected host-platform substrate.

## Correctness strategy

Elixir's canonical parser is not a directly reusable JVM parser. The language repository implements tokenization in Erlang and parsing in a Yecc grammar; the grammar contains Elixir-specific precedence, ambiguity, and no-parentheses-call rules ([`elixir_tokenizer.erl`](https://github.com/elixir-lang/elixir/blob/main/lib/elixir/src/elixir_tokenizer.erl), [`elixir_parser.yrl`](https://github.com/elixir-lang/elixir/blob/main/lib/elixir/src/elixir_parser.yrl)). Mechanically translating that compiler grammar would optimize for valid batch compilation rather than stable trees for incomplete editor text. It should instead be treated as the normative reference when behavior is disputed.

The official `tree-sitter-elixir` repository is the best reusable executable description of editor-oriented Elixir syntax. The Elixir organization describes it as production-ready and used by GitHub for highlighting and navigation. It includes a grammar, external C scanner, highlighting/tag queries, and a substantial test corpus ([repository](https://github.com/elixir-lang/tree-sitter-elixir), [`grammar.js`](https://github.com/elixir-lang/tree-sitter-elixir/blob/main/grammar.js), [`scanner.c`](https://github.com/elixir-lang/tree-sitter-elixir/blob/main/src/scanner.c), [test corpus](https://github.com/elixir-lang/tree-sitter-elixir/tree/main/test/corpus)). These are high-value references for sigils, interpolation, heredocs, operators, calls without parentheses, and malformed input.

Correctness should be measured rather than assumed:

- Lexer tests should assert contiguous token coverage, restart equivalence from every meaningful state, and graceful `BAD_CHARACTER` output instead of aborting. These are explicit IntelliJ lexer requirements.
- Parser fixtures should include valid, incomplete, and invalid programs. Assert stable PSI shapes around declarations, calls, aliases, blocks, captures, patterns, and literals—not merely that parsing finishes.
- Differential tests should compare whether the native parser and `tree-sitter-elixir` accept representative constructs, while allowing deliberately different tree shapes and recovery choices.
- A slower optional compatibility suite may invoke the installed Elixir parser over valid fixtures. The production editor path must remain runtime-independent.

## Options compared

| Strategy | Correctness and recovery | Incremental behavior | Maintainability and JetBrains feature fit | Legal/packaging |
| --- | --- | --- | --- | --- |
| JFlex + Grammar-Kit authored for this plugin | Correctness must be built and tested. `pin`/`recoverWhile` permit deliberate editor recovery. Direct control over PSI shape. | JFlex supports IntelliJ restart states; IntelliJ reparses through its own document/PSI machinery. | Best fit for references, completion, inspections, navigation, and refactoring. One native tree, ordinary Kotlin/JVM debugging, generated boilerplate. Main cost is learning and maintaining a difficult Elixir grammar. | Grammar-Kit is Apache-2.0; JFlex is BSD-3-Clause. Both are compatible with an Apache-2.0 plugin ([Grammar-Kit license](https://github.com/JetBrains/Grammar-Kit/blob/master/LICENSE.txt), [JFlex license](https://github.com/jflex-de/jflex/blob/master/LICENSE.md)). |
| Adapt the existing `intellij-elixir` JFlex/Grammar-Kit implementation | Thousands of upstream parser and lexer fixtures offer the fastest route to broad known coverage. Its source demonstrates that this toolchain can model Elixir. | Already designed for IntelliJ's lexer/parser APIs. | Technically aligned, but its large grammar and historical architecture would be inherited before this project's own PSI boundaries are understood. Selective copying also creates a continuing provenance and divergence burden. Use it as a comparative reference first. | The repository is Apache-2.0, legally compatible, but copied or modified files require preservation of notices and prominent modification notices; its license/NOTICE obligations must be tracked ([repository](https://github.com/KronicDeth/intellij-elixir), [license](https://github.com/KronicDeth/intellij-elixir/blob/main/LICENSE.md), [`Elixir.flex`](https://github.com/KronicDeth/intellij-elixir/blob/main/src/org/elixir_lang/Elixir.flex), [`Elixir.bnf`](https://github.com/KronicDeth/intellij-elixir/blob/main/src/org/elixir_lang/Elixir.bnf)). |
| Tree-sitter + official `tree-sitter-elixir` as the runtime parser | Strong existing grammar, explicit `ERROR` and `MISSING` nodes, and useful trees under invalid input. The official grammar is already production-used. | Tree-sitter is designed for incremental reparse and can reuse structure from an edited old tree ([Editing](https://tree-sitter.github.io/tree-sitter/using-parsers/3-advanced-parsing.html), [error nodes](https://tree-sitter.github.io/tree-sitter/using-parsers/queries/1-syntax.html)). | Poorer first fit: Tree-sitter yields its own CST, while IntelliJ features consume PSI/Symbol structures. The plugin would need native binaries or JVM bindings, lifecycle/threading management, and a bridge that keeps offsets, identities, errors, and edits synchronized. It risks maintaining two trees. | Tree-sitter is MIT and `tree-sitter-elixir` is Apache-2.0, both compatible. The Elixir grammar uses an external C scanner, so cross-platform native packaging is part of the product ([Tree-sitter license](https://github.com/tree-sitter/tree-sitter/blob/master/LICENSE), [`tree-sitter-elixir` license](https://github.com/elixir-lang/tree-sitter-elixir/blob/main/LICENSE)). |
| Port the canonical Elixir tokenizer and Yecc parser | Highest potential compatibility on complete valid code, but direct ports can drift from upstream and compiler error recovery is not an editor PSI design. | No ready IntelliJ incremental lexer state or PSI integration; likely full-file work unless substantial infrastructure is added. | Highest maintenance risk: a second implementation of Erlang source semantics, plus a separate PSI projection. It creates more work before any user-visible feature. | Elixir is Apache-2.0, so a properly attributed port is compatible; legal compatibility does not remove the engineering cost ([Elixir license](https://github.com/elixir-lang/elixir/blob/main/LICENSE)). |
| Hand-written Kotlin lexer/parser or another JVM parser generator | Maximum control, but no existing Elixir advantage was found that outweighs the platform-native generators. Recovery quality depends entirely on custom design. | Must implement the IntelliJ lexer contract and parser behavior manually. | Reasonable only for narrow wrappers or grammar cases generators cannot express. Otherwise it increases bespoke code and contributor learning cost without unlocking extra platform features. | Depends on the selected library; unnecessary license/dependency surface at present. |

## Error recovery and PSI policy

The grammar should prefer a useful, stable tree over compiler-identical rejection. Pin a construct only after its identifying prefix is unambiguous—for example, after enough of a declaration or block opener has been seen—and recover to local boundaries such as line ends, commas, closing delimiters, block clauses, or `end`. Recovery rules must be narrow enough not to consume the next valid declaration. Every recovery rule needs malformed-input fixtures.

PSI should expose durable concepts such as module aliases, callable declarations, calls, parameters, patterns, blocks, literals, and qualified names. It should not promise that macros have compile-time semantics without expansion. Elixir's quoted AST can remain a semantic interchange format for future runtime-backed analysis, but it is unsuitable as the editor's sole syntax tree because punctuation, trivia, malformed text, and source-preserving edits matter to IntelliJ.

## Corpus and provenance policy

Create an in-repository corpus manifest recording each imported fixture's upstream repository, exact commit, original path, license, and whether it was copied or adapted. Preserve required headers and notices. Keep generated lexer/parser outputs reproducible from checked-in `.flex` and `.bnf` sources; do not edit generated files.

Recommended corpus layers:

1. Small original tests for each lexer state and PSI rule.
2. Adapted `tree-sitter-elixir` corpus cases, especially malformed cases and lexical edge conditions.
3. Focused canonical Elixir tokenizer/parser regression cases.
4. Real-world snippets reduced to minimal regressions, with source/license recorded before committing them.
5. Compatibility comparisons against `intellij-elixir` tests without wholesale copying unless a fixture's provenance and value justify it.

Apache-2.0 permits copying and modification, but redistribution requires a license copy, retained relevant notices, prominent modification notices, and preservation of an upstream `NOTICE` when applicable ([Apache License 2.0 §4](https://www.apache.org/licenses/LICENSE-2.0)). This is engineering guidance, not legal advice.

## Consequences and next validation

The recommendation accepts more early grammar work in exchange for a single Community-compatible, runtime-independent model that naturally supports the planned native features. It avoids committing the product to native Tree-sitter packaging or an Elixir installation merely for editing.

Before implementing the full grammar, build one throwaway vertical prototype covering nested interpolation, heredocs/sigils, a no-parentheses call, a `do` block, and broken versions of each. The prototype should prove:

- lexer restart equivalence after edits inside multiline constructs;
- local parser recovery without swallowing the following declaration;
- generated typed PSI usable by a reference and rename test;
- acceptable parse/highlight latency on a large `.ex` file;
- a reproducible differential corpus runner against a pinned `tree-sitter-elixir` revision.

If that prototype fails specifically because Grammar-Kit cannot express robust Elixir recovery, compare a Tree-sitter-backed PSI adapter empirically before changing the architecture. Tree-sitter's theoretical strengths alone are not enough to justify the bridge.
