# Native Core editing responsiveness

Run `./gradlew verifyNativeCoreResponsiveness` to exercise a large Elixir file through
JetBrains' document, PSI, and editor-highlighter integration. It requires no Elixir/OTP,
Semantic Backend, Python, or C compiler. `./gradlew test` excludes this slower suite;
`./gradlew check` includes it as a required Phase 1 quality gate. Packaging alone is
not a substitute for `check`.

The task uses a separate platform test sandbox, one test process, and the project's
IntelliJ platform version. It always executes, including when other tests are up to
date. During `check` it runs after the other suites to reduce CPU contention. A
five-minute Gradle task timeout bounds stalls, including correctness comparisons.
The test task uses the [JetBrains testing extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-testing-extension.html).

## Workload and correctness

`ElixirResponsivenessTest.source` is the versioned, deterministic fixture generator.
It produces an original synthetic account-service module with 300 distinct lookup
functions and six edit targets: 85,704 UTF-16 characters and 2,414 lines. This is a
large application-style module, not a claim to represent all Elixir programs.
Functions contain calls, patterns, default arguments, maps, lists, tuples, comments,
Unicode, and interpolated strings. The baseline must parse without errors and expose
all 306 callable declarations. It is generated to avoid checking in thousands of
nearly identical lines, and the exact large file is exported with every report.

After a smaller 12-function warmup, the suite measures initial file/editor setup,
forced PSI parsing, and a complete read of editor highlight tokens. The IDE/JVM
startup is outside that timing. The initial measurement is one sample; it is not a
cold-start benchmark.

Eight rounds each insert and delete text inside a string, heredoc, paired sigil,
interpolation, nested delimiters, and a block (96 measured edits). Some insertions
leave deliberately incomplete or invalid syntax. Each edit updates the existing
Document with `insertString` or `deleteString`, commits through `PsiDocumentManager`,
forces PSI access, and consumes all editor highlight tokens. The test does not replace
the entire document or manually reset the incremental highlighter on each edit.
The platform may choose a full reparse; this measures the actual current integration,
not an assumption that the parser implements subtree reparsing.

Outside the timed region, every edited and restored state is compared with a fresh
parse and fresh highlighter. Checks compare the complete AST shape (types, depth,
offsets, lengths), token boundaries, token types, and resolved text attributes.
Tokens must be nonempty, contiguous, and cover the entire document. Every unrelated
callable declaration must remain present with the same text, including declarations
after the edited region. Undoing the local insertion must restore all original
declarations and source text. These checks detect stale or truncated updates even
if a fast operation would otherwise pass the timing gate. They do not assert PSI
object identity or force unchanged parent relationships while block nesting is incomplete.

## Thresholds and investigation

| Measurement | Failure threshold |
| --- | --- |
| Initial processing after warmup | 3,000 ms |
| Edit p95, nearest rank across 96 insert/delete samples | 250 ms |
| Any individual edit | 2,000 ms |

These are coarse regression ceilings, not microbenchmark comparisons or promises
that typing always finishes within 250 ms on every computer. The p95 detects sustained
slow editing; the worst-sample ceiling catches severe pauses while allowing occasional
JIT/GC or scheduler noise. Initial processing has a larger allowance for editor setup.
The budgets provide substantial headroom over the first macOS ARM64 measurements
(roughly 90 ms initial, 30 ms edit p95, 48 ms worst edit, using IU-261.26222.65,
JBR 25.0.3, 10 available processors and a 2 GiB maximum heap).

On failure, inspect the per-operation samples and test failure, stop unrelated heavy
work, and rerun once on the same machine. Compare with the previous revision in the
same environment before attributing a timing failure to code. Investigate persistent
regressions in parsing, lexer restart behavior, allocations, or document listeners.
Do not increase thresholds merely to obtain a green build. Any calibration for a
new runner should document repeated baseline measurements and retain the correctness
gates. Cross-host validation belongs to the packaged Phase 1 artifact gate.

## Results

- `build/reports/responsiveness/results.json`: pass/fail status, platform/JVM/OS,
  CPU count, heap limit, fixture size, thresholds, p95/max, and raw timings.
- `build/reports/responsiveness/large.ex`: exact generated fixture, also available
  for manual opening in the test IDE.
- `build/reports/tests/verifyNativeCoreResponsiveness/index.html`: test failures.

The JSON is written on normal assertion failures as well as success. If the JVM is
killed or hangs until the task timeout, it may not be written; consult Gradle output
and report timestamps before treating an older report as evidence for that run.
Keep the report with release evidence. This suite measures synchronous Native Core
processing, including a full token read; it does not measure screen painting, input
latency end to end, indexing, completion, or Semantic Backend work. It adds no code
or fixtures to the distributed plugin.
