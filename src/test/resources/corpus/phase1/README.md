# Phase 1 syntax corpus, version 1

This directory is the reviewed behavioral baseline for Native Core lexing, highlighting, PSI,
and incomplete-code recovery. Fixtures are source text only: none are evaluated as Elixir.
The full corpus runs through the registered JetBrains file type, lexer/highlighter, and parser.

## Contents and provenance

`manifest.json` lists every source fixture, its SHA-256, validity status, provenance, and explicit
source-span assertions. Original reduced fixtures are Apache-2.0 Spellixir test material.
The two `upstream-*` fixtures are unmodified real Mix task modules copied from
[`elixir-lang/elixir` at `759443e724f55bf58e71c0603644e99058918d52`](https://github.com/elixir-lang/elixir/tree/759443e724f55bf58e71c0603644e99058918d52).
Their original SPDX copyright and license headers are retained. Their original paths and
adaptation status are recorded per fixture; the pinned upstream archive has no root NOTICE.

`licenses/elixir-LICENSE` preserves the source license. The official grammar's Apache license
and complete NOTICE (including its MIT notices) are in `licenses/tree-sitter-elixir-*`.
`licenses/tree-sitter-LICENSE` preserves the runtime's MIT license. These test dependencies and
fixtures are not part of the shipped plugin artifact.

`oracle-lock.json` pins both the official `elixir-lang/tree-sitter-elixir` grammar and the
`tree-sitter/tree-sitter` runtime to full commits and SHA-256-verified source archives. The
checker also compares their upstream license/notice files with the checked-in copies.

## Coverage

- Reduced valid fixtures exercise every Lexical Vocabulary category and the durable PSI concepts:
  module and callable declarations, aliases, calls, parameters, patterns, blocks, literals,
  qualified names, lists, tuples, maps, parentheses, and captures. The test checks coverage
  against the vocabulary itself and an explicit PSI concept list.
- Recovery fixtures cover unfinished calls, parameter lists, collections, parentheses, blocks,
  strings/charlists, interpolating/literal sigils, interpolation, and both heredoc quote forms.
  Invalid characters and malformed numbers have separate local-containment cases.
- Each recovery fixture asserts preserved declaration spans. Lexer progress, complete text
  coverage, and restart equivalence at every token boundary are checked on every reduced case.
- Unmodified Mix modules add realistic documentation, attributes, nested control flow, captures,
  operators, calls, and interpolation beyond the small regression fixtures.

## Snapshot format

`expected/<id>.json` fixes the complete Native Core output. Each token row is
`[start, end, category, highlightingKeys, text]`; each PSI row is `[start, end, elementType]`;
each error row is `[start, end, description]`. Native offsets count UTF-16 code units.
Internal lexer restart state and incidental generated implementation class names are excluded.

`oracle-expected/<id>.json` fixes Tree-sitter validity and named/missing node spans. Each row is
`[startByte, endByte, nodeType, missing]`. The checker converts Native Core UTF-16 offsets to
UTF-8 byte offsets; the Unicode fixture includes a supplementary-plane character to exercise this.

The differential check directly compares the manifest's selected declaration, call, map, list,
literal, and sigil spans across both parsers. It additionally compares every number, character,
and comment span in every valid fixture. It does not require identical tree shapes.

## Deliberate differences and current limits

Tree-sitter models declarations as calls; Native Core provides distinct declaration PSI.
Interpolated literals also have different internal structure. These differences are normalized
only at the explicit shared spans; both full representations remain independently snapshotted.

The Native Core intentionally recovers at following declaration lines after unfinished ordinary
strings and interpolating sigils. Tree-sitter may instead keep the remaining file inside an error
or literal. Unclosed heredocs and literal sigils can contain arbitrary declaration-looking text,
so their corpus cases assert retention of the preceding valid declaration and stable EOF lexing.
Recovery cases require Tree-sitter to report an error, but do not require matching recovery trees.

The editor parser is not a compiler-validity oracle. Both imported modules are valid according to
Tree-sitter, while the Native Core currently reports one parser error in each and exposes partial
structure around nested control flow. Those exact errors and PSI spans are recorded, not treated
as full syntax support or silently discarded. Future parser improvements must deliberately
update these baselines. The three reduced regressions fixed when establishing this corpus are
single-segment module names, keyword-valued maps, and declaration recovery after unfinished sigils.

## Running and reviewing changes

`./gradlew test` runs the native corpus and all other tests without an Elixir runtime, Python, or
C compiler. `./gradlew verifySyntaxCorpus` also runs the differential oracle; `./gradlew check`
includes it. The oracle requires Python 3 and a C11 compiler (`cc`, or the `CC` environment variable).
On Windows, provide a GCC/Clang-compatible compiler on PATH. The first oracle run downloads the
two checksum-verified archives; subsequent runs use `build/syntax-corpus-oracle/`. No Python
packages or Tree-sitter CLI installation are needed.

Tests never rewrite checked-in expectations. They emit candidates under
`build/reports/syntax-corpus/` and fail on missing or changed snapshots.

For an intentional change:

1. Edit the fixture and its manifest SHA-256; retain provenance and add a modification notice if
   adapting upstream code. Extend the explicit assertions instead of removing a failing contract.
2. Run `./gradlew test --tests '*ElixirSyntaxCorpusTest'`. Review the candidate native snapshot
   against `expected/<id>.json`, including any assertion failures. Copy only the reviewed candidate
   to that fixture's expected file.
3. Run `python3 scripts/verify_syntax_corpus.py` to generate the oracle candidate. Review its
   validity and shared-span checks, then copy the reviewed `<id>.oracle.json` candidate to
   `oracle-expected/<id>.json` when an oracle change is intended.
4. Run `./gradlew check buildPlugin`. Include the reason for any changed behavior in the PR.

Changing an upstream pin requires reviewing its archive hash, license/NOTICE, and all affected
oracle snapshots. Schema changes require a schema-version update; changes to the corpus contract
require a corpus-version update and an explanation here.
