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

### Semantic capability checks

An advertised capability is not counted as passing until exercised through a
Host IDE.

| Capability | IntelliJ IDEA | GoLand | PyCharm |
| --- | --- | --- | --- |
| Completion | Pass: `String.up` offers `upcase` | Pending | Pending |
| Definition | Pass for project-local `normalize/1`; no navigation for `String.upcase/1` | Pending | Pending |
| Hover | Pass: `String.upcase/1` signature, spec, docs, and examples; undocumented local helper has no hover | Pending | Pending |
| Formatting | Fail: Reformat Code leaves malformed fixture unchanged | Not tested after decisive failure | Not tested after decisive failure |
| Document symbols | Pending | Pending | Pending |
| Workspace symbols | Pending | Pending | Pending |
| References | Pending | Pending | Pending |
| Code actions | Pending | Pending | Pending |

Completion, definition, hover, formatting, document/workspace symbols,
references, code actions, toolchain variants, restart behavior, and indexing
latency remain required by the ticket after this first proving slice.

## Verdict

Do not adopt Expert as the preferred Semantic Backend candidate under the
ticket's all-capabilities-must-pass gate. Direct initialization, diagnostics,
and missing-executable isolation passed across all Supported Hosts. IntelliJ
completion, project-local definition, and standard-library hover also passed,
with no standard-library source navigation. The decisive failure is document
formatting: Expert advertises the capability, but JetBrains Reformat Code left
the deliberately malformed fixture unchanged through the direct adapter.

The release path therefore remains Native Core-only. This prototype does not
rule Expert out forever; a later effort may diagnose the formatting bridge and
define a narrower capability contract before reconsidering adoption.
