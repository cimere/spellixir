# Static Mix-project detection

The Native Core exposes `project.getService(MixProjectService::class.java).classify(fileOrDirectory)`.
Call it under normal platform read access. It returns an ordinary Mix project, umbrella root,
umbrella child (including both roots), standalone context, or unknown context.

The nearest enclosing file named exactly `mix.exs` establishes a metadata boundary. A malformed
child marker produces unknown context; it does not silently fall back to the parent's project.
An unrelated directory named `apps` does not establish an umbrella.

## Supported static metadata

The reader recognizes a single conventional `defmodule` containing `use Mix.Project` and one
public `project/0` definition that directly returns a keyword list. Both `def project do ... end`
and `def project(), do: ...` are supported. Ordinary projects require a literal `app: :name`;
umbrellas require a literal `apps_path: "apps"` (including custom paths such as `components/elixir`).
These conventions follow [Mix.Project](https://mix.hexdocs.pm/Mix.Project.html).

Child applications must have their own recognizable ordinary Mix definition in a direct
subdirectory of the configured apps directory. Deeper independent projects and projects outside
that directory remain ordinary. A subtree inside the apps directory without a child marker is
unknown. An umbrella can be recognized before its apps directory exists.

The bounded recognizer uses the Native Core lexer, independently of the tolerant editor PSI.
It accepts simple literals, lists, tuples, qualified names, calls with parentheses, and common
comparison/boolean expressions in other configuration fields and simple helper definitions.
Calls are opaque and never invoked. Comments, strings, nested keywords, and helper functions
cannot supply the umbrella configuration in place of `project/0`.

Computed project lists, dynamic `app` or `apps_path`, explicit `apps` selection, duplicate keys or
project definitions, macros, module attributes, complex helper bodies, interpolation, escaped
strings, heredocs, sigils, and syntax outside this subset return unknown. Literal apps paths must
be nonempty relative directory components: absolute paths, `.`/`..`, empty segments, backslashes,
colons, and control characters are unsupported. This is conservative layout recognition, not
compiler validation or a promise that arbitrary project code can execute successfully.

## Failure handling and changes

Reads use saved contents in the current VFS snapshot; unsaved editor documents are not inspected.
There is no classification cache. VFS-visible creation, deletion, movement, renaming, and saved
metadata edits affect the next lookup. External disk changes require the platform's normal VFS
refresh; classification does not trigger a refresh itself.

Metadata reads are capped at 256 KiB, 16,384 significant tokens, and 64 expression nesting levels.
Unreadable files, decoding errors, excessive input, and unsupported or malformed metadata return
unknown without notifications. Missing markers leave files standalone unless their location is
an unresolved umbrella-child subtree. Cancellation is preserved.

Detection never starts a process, looks for an Elixir runtime, evaluates `mix.exs`, or mutates the
project. Unknown project context does not disable file recognition, PSI, or highlighting.
