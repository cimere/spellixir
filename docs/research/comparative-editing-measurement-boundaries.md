# Comparable editing and highlighting measurement boundaries

Research for [Research comparable editing and highlighting measurement boundaries](https://github.com/cimere/spellixir/issues/39), 2026-10-18. This records technical feasibility, not the final benchmark protocol.

## Findings

There is a feasible common **processing proxy**, but no single completion signal in the inspected public APIs establishes “all plugin work is finished and its pixels are on screen.” Separate document mutation, lexer highlighting, committed PSI, background analysis, and visible painting. Combining them without an explicit boundary can reward implementations that defer work.

The platform explicitly separates lexer syntax coloring, parser errors, and background annotators. Therefore an editor-highlighter scan does not capture all colored/error ranges produced by every plugin. [SDK highlighting](https://plugins.jetbrains.com/docs/intellij/syntax-highlighting-and-error-highlighting.html)

## Candidate boundaries

| Boundary | Common observation | Meaning and limitation |
| --- | --- | --- |
| Document edit return | Time the same insert/delete command in the existing editor document | Synchronous command/listener cost only. Useful supplemental metric; deferred work is excluded. |
| Edit plus complete editor-highlighter token scan | Apply edit, obtain the editor highlighter, iterate from offset zero to end, consume token boundaries/types/attributes | Recommended feasibility candidate for lexical processing. Includes observer traversal/allocation proportional to token count. Does not include semantic overlays or prove visual presentation. |
| Edit plus committed PSI | Commit the edited document, verify its committed state, then force the same generic PSI access for each language plugin | Separate parsing proxy. PSI structures and lazy work differ; forcing a root child is not proof every embedded/lazy subtree has been expanded. Plain text is a baseline without equivalent language parsing work. |
| Edit to background-analysis completion | Observe the daemon lifecycle for the edited file and matching document revision | Potential additional metric after validating against the selected host. Includes scheduling delay and enabled analyses; not equivalent functionality across plugins. Does not establish completion of arbitrary plugin/LSP jobs or painting. |
| Input to displayed frame | Requires a real visible IDE and a validated presentation observer | Defer until separately designed. Document methods bypass OS input dispatch; a paint callback or idle event alone does not prove compositor/display presentation. |

`EditorHighlighter` is a public interface extending `DocumentListener`, with `createIterator`; it supplies the lexical observer without calling Spellixir internals. Its interface does not expose a general asynchronous completion future. [Pinned API source](https://github.com/JetBrains/intellij-community/blob/3036f01320f55ad49169d1f5f1a65b7a387582c5/platform/editor-ui-api/src/com/intellij/openapi/editor/highlighter/EditorHighlighter.java)

`PsiDocumentManager.commitDocument` updates PSI for the document; before commit PSI may show the old state. `isCommitted`, `performForCommittedDocument`, and last-committed stamps support checking the specific revision. Their completion means PSI commitment, not daemon or rendering completion. Match the API contract of the pinned benchmark host, rather than assuming current master signatures are available there. [Pinned API source](https://github.com/JetBrains/intellij-community/blob/3036f01320f55ad49169d1f5f1a65b7a387582c5/platform/core-api/src/com/intellij/psi/PsiDocumentManager.java)

The public daemon topic offers starting, finished, and canceled notifications; finished identifies the analyzed file editors, while canceled must remain a distinct outcome. `isRunning()` is marked internal: do not build a portable benchmark around it. Subscribe before mutation, correlate the target editor and unchanged document revision, and bound waits. These checks are necessary but must be validated on the chosen host: a lifecycle event is not a universal per-edit completion token. [Pinned daemon API source](https://github.com/JetBrains/intellij-community/blob/3036f01320f55ad49169d1f5f1a65b7a387582c5/platform/analysis-api/src/com/intellij/codeInsight/daemon/DaemonCodeAnalyzer.java)

## Audit of the existing Spellixir gate

At [baseline 0770ca2](https://github.com/cimere/spellixir/blob/0770ca2/src/test/kotlin/com/cimere/spellixir/lang/ElixirResponsivenessTest.kt):

- One 12-function warmup precedes one 300-function fixture (2,414 lines); eight rounds of six insert/delete scenarios yield 96 edit samples.
- Initial timing covers fixture/editor configuration, `file.node.firstChildNode`, and the complete editor token scan. IDE/JVM startup is excluded; this is not cold startup.
- Edit timing covers `WriteCommandAction`, document mutation, explicit PSI commit, root-child access, and full token scanning. Token objects/string attributes and token-contiguity assertions are inside the timer.
- Fresh parsing, complete AST comparison, fresh-highlighter comparison, declaration checks, and restoration assertions happen outside the timer. These checks still affect caches/JIT/GC before subsequent samples; the current gate is not an isolated microbenchmark.
- Timings use monotonic `measureNanoTime`; output includes raw samples, p95/max, host build, Java, OS, CPU count, and heap. Correctness failures are reported, but a killed/hung process may leave an old report.
- There is no wait for background daemon analysis, language-server completion, screen painting, indexing, or actual keyboard input.

The existing [gate documentation](https://github.com/cimere/spellixir/blob/0770ca2/docs/native-core-responsiveness.md) accurately describes this synchronous integration scope. Its Spellixir declaration/AST checks cannot be copied unchanged into another plugin's PSI model.

## Harness prerequisites and safeguards

Use one exact IDE build/JBR, OS/machine, heap, installed plugin versions, color scheme, fixture bytes, viewport, and enabled feature set per comparison. Use isolated IDE config/system directories and one Elixir provider per process; verify the actual file type/highlighter/plugin loaded, including the plain-text association. Record indexing/runtime/backend state instead of silently allowing different work.

Document mutations require commands plus write-action rules; on current supported platform practice they run on EDT. Schedule in a write-safe context, and do not block EDT awaiting deferred completion: background analysis and callbacks may need it. PSI reads need appropriate read access. Keep mutation duration and scheduling/queue delay distinguishable. [Documents](https://plugins.jetbrains.com/docs/intellij/documents.html), [threading model](https://plugins.jetbrains.com/docs/intellij/threading-model.html)

Use a disposable per trial for message-bus subscriptions/listeners and cancel timeouts/background work on failure. Close editors, release manually created editors, dispose test projects/fixtures, and terminate isolated processes between configurations so listeners and caches cannot contaminate another plugin's trial. Parent-child disposal is the supported lifecycle pattern. [Disposer guidance](https://plugins.jetbrains.com/docs/intellij/disposers.html)

Warm each configuration with the same declared workload; retain warmup samples separately and use repeated fresh processes to expose run-to-run variation. Save every raw sample, scenario, fixture hash, command, versions, timeout/cancellation/correctness outcome and run identifier; summarize per scenario and run. These are research recommendations, with counts, ordering, statistics and acceptance budgets left to the protocol decision.

For correctness, compare each plugin's incremental results with its own fresh state, supplemented by small shared observable expectations; token names and AST shapes need not match across implementations. Lexical coverage alone cannot detect a highlighter that colors everything as plain text. Run validation separately or disclose its cache effects consistently. The SDK distinguishes lexer-highlighting tests from fixture diagnostic-highlighting tests. [Testing highlighting](https://plugins.jetbrains.com/docs/intellij/testing-highlighting.html)

## Alternatives to reject for the first comparison

- Timing only `insertString` as “highlighting latency”: leaves deferred work unobserved.
- Calling each plugin's parser/lexer directly: bypasses editor integration and introduces unequal adapters.
- Treating `checkHighlighting()` as lexical latency: it exercises a different diagnostic layer; not a substitute for the editor-highlighter observer.
- Waiting a fixed sleep or one EDT idle callback: does not establish background completion.
- Reporting forced synchronous parsing as perceived typing latency: changes scheduling and adds work a particular edit may not immediately require.
- Ranking full-feature plugins against Spellixir with all semantic features enabled under a “Phase 1 highlighting” label: reports different functionality. Such a full-experience comparison needs a separate disclosed configuration.

Next decision: choose the first metric bundle and permitted configurations. Then validate the common observer with the selected comparator before interpreting comparative numbers. No benchmark code or human protocol policy was decided here.
