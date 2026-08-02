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
| Direct `expert --stdio` initialization | Pending | Pending | Pending |
| Diagnostics | Pending | Pending | Pending |
| Missing executable leaves local file support active | Pending | Pending | Pending |
| Negotiated capabilities recorded | Pending | Pending | Pending |

Completion, definition, hover, formatting, document/workspace symbols,
references, code actions, toolchain variants, restart behavior, and indexing
latency remain required by the ticket after this first proving slice.
