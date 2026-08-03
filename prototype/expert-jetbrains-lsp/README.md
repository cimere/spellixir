# Expert / JetBrains LSP prototype

**Throwaway integration prototype — not production plugin code.**

## Question

Can an explicitly enabled, user-installed Expert process provide diagnostics
through JetBrains' native LSP API while leaving every Native Core capability
independent of the process?

This is milestone one of the broader adoption gate in [Prototype Expert as the
optional Semantic Backend](https://github.com/cimere/spellixir/issues/13). It
does not by itself settle cross-host adoption.

## Run

Build the plugin with one command:

```sh
./build-prototype.sh
```

Install `build/distributions/spellixir-expert-prototype.zip` in the Host IDE.
Start the IDE from a shell with explicit opt-in and an explicit executable:

```sh
SPELLIXIR_EXPERT_ENABLED=1 EXPERT_PATH=/absolute/path/to/expert \
  "/Applications/IntelliJ IDEA.app/Contents/MacOS/idea"
```

Open `fixture` as a project, then open `fixture/lib/sample.ex`. Inspect the
Language Services widget, editor diagnostics, and IDE log. With opt-in absent,
the provider does not start Expert. With an invalid `EXPERT_PATH`, JetBrains'
LSP lifecycle reports startup failure while the prototype file type remains
available; the future Native Core is intentionally not represented here.

Before installing the plugin, the transport handshake can be isolated with:

```sh
./scripts/capture-initialize.sh /absolute/path/to/expert
```

The harness deliberately uses a string request ID, matching the JetBrains
client behavior that exposed ElixirLS's incompatibility.

## Evidence matrix

Verified locally on 2026-08-02 with Expert v0.1.8
`expert_darwin_arm64` (SHA-256
`65b574eb64cbf3ccb0f5c1e0d8147e5f67f71f087a7a93c05ebec757659d4d72`):

- The isolated transport harness passed with request ID `"jetbrains-1"`.
- Expert identified itself as version 0.1.8.
- Expert advertised incremental document sync, completion, definition, hover,
  formatting, document/workspace symbols, references, and code actions.
- The harness result proves protocol compatibility, not IDE integration or
  diagnostics presentation; those remain pending below.

| Check | IntelliJ IDEA | GoLand | PyCharm |
| --- | --- | --- | --- |
| Direct `expert --stdio` initialization | Pass: Expert 0.1.8 shown running | Pass: diagnostic flow proves initialization | Pass: diagnostic flow proves initialization |
| Diagnostics | Pass: undefined local function reported | Pass: undefined local function reported | Pass: undefined local function reported |
| Missing executable leaves local file support active | Pass; raw startup stack trace needs production UX | Pass; raw startup stack trace needs production UX | Pass; raw startup stack trace needs production UX |
| Negotiated capabilities recorded | Pass: captured by transport harness | Pending | Pending |

### Toolchain and lifecycle boundary

The missing Expert executable produces an isolated LSP startup failure while
local file editing remains available in every Supported Host. With Expert
present and Homebrew removed from `PATH`, Expert still discovered a working
project toolchain and provided diagnostics. Forcing an invalid
`elixirExecutablePath` requires LSP settings plumbing, which belongs to the
later production lifecycle/UX decision and is excluded from this throwaway
adapter.

### Semantic capability checks

An advertised capability is not counted as passing until exercised through a
Host IDE.

The compatibility gate is layered: IntelliJ IDEA exercises each semantic
capability; GoLand and PyCharm prove installation, native-LSP initialization,
diagnostics, and failure isolation. The three hosts share JetBrains' LSP
implementation, so the prototype does not repeat every semantic gesture in
each host.

| Capability | IntelliJ IDEA | GoLand | PyCharm |
| --- | --- | --- | --- |
| Completion | Pass: `String.up` offers `upcase` | Covered by layered gate | Covered by layered gate |
| Definition | Pass for project-local `normalize/1`; no navigation for `String.upcase/1` | Covered by layered gate | Covered by layered gate |
| Hover | Pass: `String.upcase/1` signature, spec, docs, and examples; undocumented local helper has no hover | Covered by layered gate | Covered by layered gate |
| Formatting | Outside Semantic Backend gate: Expert returns edits, but Reformat Code does not apply them | Outside Semantic Backend gate | Outside Semantic Backend gate |
| Document symbols | Outside Semantic Backend gate: Native Core PSI owns local structure | Outside Semantic Backend gate | Outside Semantic Backend gate |
| Workspace symbols | Pass: module found through Navigate → Symbol | Covered by layered gate | Covered by layered gate |
| References | Pass: Find Usages locates the `normalize/1` call | Covered by layered gate | Covered by layered gate |
| Code actions | Pass: unused argument offers `_unused` fix | Covered by layered gate | Covered by layered gate |

Completion, definition, hover, formatting, document/workspace symbols,
references, code actions, toolchain variants, restart behavior, and indexing
latency remain required by the ticket after this first proving slice.

### Formatting ownership

Formatting is not part of the Semantic Backend adoption gate. For a fixture
with irregular spaces, Expert returned correct LSP text edits while JetBrains
Reformat Code reported that no lines changed. ElixirIJ's working formatting is
not routed through Expert; it registers a separate formatting service that
runs `mix format` on a temporary file. The Elixir Plugin should use the same
ownership boundary: a dedicated optional Formatter Service, independent of
Expert and the Native Core.

### Document-structure ownership

Document structure is not part of the Semantic Backend adoption gate. Expert
returns a complete `textDocument/documentSymbol` tree for the fixture, but the
minimal placeholder file type has no Structure view while ElixirIJ's full
parser/PSI registration exposes one. The Native Core's PSI should remain the
authoritative source of local document structure; Expert may augment it with
project-wide workspace-symbol search.

## Verdict

Adopt Expert as the preferred optional Semantic Backend candidate, initially
through explicit opt-in and a user-configured executable. The direct adapter
passes cross-host initialization, diagnostics, and failure isolation, and the
IntelliJ deep pass covers completion, project-local definition, hover,
workspace symbols, references, and code actions.

The Native Core remains independently useful and authoritative for parsing,
PSI, highlighting, and document structure. A separate optional Formatter
Service owns `mix format`. Production lifecycle work must add clear error UX,
toolchain/executable configuration, trust controls, restart behavior, and
updates without making Expert mandatory. Standard-library source navigation
requires a configured `elixirSourcePath`.
