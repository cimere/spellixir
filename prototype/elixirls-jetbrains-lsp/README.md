# ElixirLS / JetBrains LSP prototype

**Throwaway integration prototype — not production plugin code.**

## Question

Can the JetBrains LSP API start a user-installed ElixirLS process for `.ex` and
`.exs` files, independently of all Native Core capabilities?

## Run

From this directory, run:

```sh
./build-prototype.sh
```

This builds `build/distributions/spellixir-elixirls-prototype.zip` using the
locally installed IntelliJ IDEA SDK. Install that archive in a Host IDE, create
or open a Mix project, then open an Elixir file. Inspect the Language Services
widget and the IDE log for ElixirLS lifecycle and protocol messages.

The server command is intentionally hard-coded to `elixir-ls`: this answers
whether the native transport can own a discovered executable. The tiny file
type registration is a fixture bridge only; the future Native Core owns real
file recognition and highlighting. Path settings, version-manager support,
diagnostics UX, restart controls, and Native Core integration are deliberately
outside this prototype.

## Observed verdict

The native JetBrains LSP adapter loads and launches Homebrew ElixirLS 0.31.1
in IntelliJ IDEA, GoLand, and PyCharm 2026.2. ElixirLS then terminates during
initialization because JetBrains uses a string JSON-RPC request ID while the
generated ElixirLS request schema accepts integers only.

The protocol harness demonstrates the boundary directly:

```sh
scripts/assert-initialize.sh "$PWD/fixture" integer
scripts/assert-initialize.sh "$PWD/fixture" string
```

The integer case passes. The string case reproduces the IDE failure with
`{"id":"expected an integer"}`. JSON-RPC and LSP permit both integer and
string request IDs, and the integer-only schema remains on ElixirLS main as of
2026-08-02.

Direct adoption is therefore blocked pending upstream string-ID support. A
protocol-rewriting proxy would be a new compatibility layer to own and was not
validated by this prototype.
