# Elixir Examples Syntax-Highlighting Rules

Research date: 2026-08-13

## Question

What lexical highlighting rules are evidenced by all Elixir examples published at [Elixir Examples](https://elixir-examples.github.io/examples), and which of those rules should Spellixir adopt rather than merely copying the website's Rouge output?

## Sources and method

The example site is generated from the first-party [`elixir-examples/elixir-examples.github.io`](https://github.com/elixir-examples/elixir-examples.github.io) repository. I inspected every `<code class="language-elixir">` block in its generated [single-page corpus](https://elixir-examples.github.io/single-page): 115 Elixir blocks. Ruby, Bash, ERB, and inline plaintext blocks were excluded so their token classes would not pollute the Elixir results.

The rendered spans were compared with the first-party [Rouge Elixir lexer](https://github.com/rouge-ruby/rouge/blob/main/lib/rouge/lexers/elixir.rb), the official Elixir [syntax reference](https://elixir.hexdocs.pm/main/syntax-reference.html), and the official [operators reference](https://elixir.hexdocs.pm/main/operators.html). The corpus therefore tells us what the website colors; Rouge source explains why; Elixir documentation determines what is actually language syntax.

## Corpus inventory

The 115 Elixir blocks contain 3,240 styled spans across 17 Rouge classes:

| Rouge class | Meaning in this corpus | Spans | Representative forms |
|---|---|---:|---|
| `p` | punctuation | 916 | `(`, `)`, `[`, `]`, `{`, `}`, `%{`, `%`, `,`, `:`, `::`, `\\` |
| `o` | symbolic operator | 424 | `=`, `.`, `->`, `|`, `*`, `..`, `&`, `+`, `/`, `<-`, `==`, `=>`, `<>`, `|>`, `^` |
| `n` | generic name | 375 | `user`, `_`, `get_in`, `schema`, `field`, `full_name`, `get` after `Map.` |
| `mi` | integer | 318 | `0`, `1`, `42`, `122` |
| `no` | constant | 255 | `String`, `User`, each segment of `Ecto.Schema`, plus `true`, `false`, `nil` |
| `s2` | double-quoted string | 244 | `"hello"`, interpolated string text |
| `ss` | symbol/atom | 221 | `:ok`, `:first_name`, `email:`, `only:`, `otp_app:` |
| `k` | keyword | 213 | `do`, `end`, `def`, `defmodule`, `fn`, `if`, `case`, `defprotocol` |
| `c1` | single-line comment | 180 | `# comment` through the line ending |
| `si` | interpolation boundary | 20 | `#{`, `}` |
| `se` | string escape | 15 | `\n`, `\t`, `\"` |
| `kn` | namespace keyword | 11 | `use`, `import`, `require`, `quote` |
| `nv` | variable-like special form | 10 | `@behaviour`, `@callback`, `&1` |
| `ow` | word operator | 9 | `and`, `or`, `in`, `when` |
| `sr` | regex sigil | 7 | `~r/foo/`, `~r/foo([0-9])/`, interpolated regex |
| `sx` | other sigil/string form | 6 | `~w(...)`, `~s""`, `~S""`, `?a`, `?z` |
| `mf` | float | 3 | `2.5`, `3.14`, `8.0` |

Counts are evidence about this corpus, not a frequency specification for Elixir programs.

## Rules evidenced by the corpus

### Names, definitions, calls, and aliases

Rouge assigns `n` to ordinary variables, local calls, DSL macros, declaration names, and members after a dot. Thus `user`, `get_in`, `schema`, the `full_name` in `def full_name`, the `get` in `Map.get`, and the `email` in `user.email` are the same lexical class on the site. This is a Rouge choice, not a claim that those constructs are semantically identical.

Every uppercase alias segment is `no`: `Ecto.Schema` becomes constant `Ecto`, operator `.`, constant `Schema`. This agrees with Elixir: aliases begin with ASCII uppercase characters, multiple segments may be joined by `.`, and the dot can also compose calls or field access ([syntax reference: aliases and qualified calls](https://elixir.hexdocs.pm/main/syntax-reference.html#aliases)). The same official reference distinguishes `mod.fun()` as a call from `map.field` as field access, even though a lexer usually cannot resolve the distinction without parsing.

Recommendation: give alias segments a visible alias/constant style. Keep ordinary variables and unresolved local calls neutral, but give definition names after the `def*` family a function-declaration style and names after `.` a member/call style. Those two refinements are useful IDE distinctions even though Rouge does not make them.

### Keywords, directives, and word operators

The corpus's `k` class covers definition and control/block forms: `def`, `defp`, `defmacro`, `defmodule`, `defprotocol`, `defimpl`, `defstruct`, `fn`, `do`, `end`, `if`, `unless`, `case`, `cond`, and `else`. `kn` covers `use`, `import`, `require`, and `quote`. `ow` covers `and`, `or`, `in`, and `when`.

Do not treat those exact Rouge lists as the Elixir grammar. The website's lexer leaves `alias`, `with`, and `for` as generic names. The official syntax reference limits true reserved words to `true`, `false`, `nil`; the word operators `when`, `and`, `or`, `not`, `in`; `fn`; and the do/end block words `do`, `end`, `catch`, `rescue`, `after`, `else` ([reserved words](https://elixir.hexdocs.pm/main/syntax-reference.html#reserved-words)). Forms such as `defmodule`, `def`, `alias`, `import`, and `with` are macros or special forms rather than reserved words, even though syntax highlighters conventionally color them as keywords.

Recommendation: maintain a documented _highlighting vocabulary_, not a purported reserved-word set. It should include current definition forms, module directives (`alias`, `import`, `require`, `use`), quoting forms, and common control/special forms (`case`, `cond`, `if`, `unless`, `with`, `for`, `receive`, `try`, `rescue`, `catch`, `after`) while preserving the operator category for `when`, `and`, `or`, `not`, and `in`.

### Atoms, keyword keys, and literals

Rouge's `ss` class combines leading-colon atoms (`:ok`, `:email`) and trailing-colon keyword/map keys (`email:`, `only:`). That reflects Elixir syntax: keyword notation moves the colon to the end and is sugar for an atom-keyed pair ([maps and keyword lists](https://elixir.hexdocs.pm/main/syntax-reference.html#maps-and-keyword-lists)). The language also permits quoted atoms, operator atoms, Unicode atom names, and terminal `?`/`!` ([atoms](https://elixir.hexdocs.pm/main/syntax-reference.html#atoms)); the examples do not exercise all of these.

`true`, `false`, and `nil` are colored as constants (`no`) on the site. Elixir specifies them as reserved words represented by atoms, so a dedicated literal style is clearer than conflating them with module aliases.

Recommendation: style both atom spellings and keyword keys as atoms, including the colon. Style `true`, `false`, and `nil` as literals. Support quoted and operator atoms even though the corpus does not require them.

### Operators and punctuation

The corpus exercises `=`, `.`, `->`, `|`, `*`, `..`, `&`, `+`, `/`, `<-`, `==`, `=>`, `>`, `<>`, `&&`, `++`, `-`, `|>`, `||`, and `^`, plus word operators. The official operator reference is the authority for the complete parseable operator vocabulary and explains context-specific operators such as `=>`, `when`, `<-`, and `\\` ([operators reference](https://elixir.hexdocs.pm/main/operators.html)).

Rouge classifies `%`, `%{`, delimiters, commas, colons, `::`, and `\\` as punctuation in this corpus. It also coalesces adjacent punctuation into spans such as `([`, `])`, and `},`. That is formatter output, not a useful lexer contract. `%` starts map/struct syntax, while `::` and `\\` are operators according to the official operator reference.

Recommendation: emit stable atomic delimiter and punctuation tokens rather than copying Rouge's coalesced spans. Classify the full official operator set consistently; product styling may still make low-salience punctuation visually quiet.

### Numbers

The corpus separates decimal integers (`mi`) and floats (`mf`). The official syntax also supports digit separators, scientific notation, and base-prefixed integers ([numbers](https://elixir.hexdocs.pm/main/syntax-reference.html#numbers)). Rouge's current lexer has explicit hexadecimal, octal, and binary rules, although those forms do not appear in this corpus ([Rouge Elixir lexer](https://github.com/rouge-ruby/rouge/blob/main/lib/rouge/lexers/elixir.rb)).

Recommendation: cover integers and floats as separate token types, including underscores, exponent notation, and `0x`/`0o`/`0b` forms.

### Strings, heredocs, escapes, and interpolation

The examples exercise double-quoted strings (`s2`), heredocs (`sd`), escapes (`se`), and interpolation delimiters (`si`). Rouge returns to its root lexer inside `#{...}`, so the embedded expression receives normal tokens; its source explicitly implements these nested states ([Rouge string states](https://github.com/rouge-ruby/rouge/blob/main/lib/rouge/lexers/elixir.rb#L645-L707)). Elixir distinguishes strings from charlists and documents triple-quoted heredocs ([strings and charlists](https://elixir.hexdocs.pm/main/syntax-reference.html#strings)).

Recommendation: keep string/heredoc content, valid escapes, and interpolation boundaries distinct, and lex interpolation bodies as Elixir. Also cover charlists and charlist heredocs despite their limited or absent corpus coverage.

### Sigils and character literals

The corpus uses regex sigils (`sr`) and word/string sigils plus `?a` character codepoints (`sx`). Rouge's division between `sr` and `sx` is a highlighter-specific taxonomy. Elixir's rule is broader: `~` plus one lowercase letter or one or more uppercase letters, a supported delimiter pair, and optional ASCII-letter/digit modifiers; uppercase sigils do not interpolate ([sigils](https://elixir.hexdocs.pm/main/syntax-reference.html#sigils)). Rouge likewise supports paired delimiters, regex and word-list specializations, modifiers, and interpolation states ([Rouge sigil lexer](https://github.com/rouge-ruby/rouge/blob/main/lib/rouge/lexers/elixir.rb#L719-L805)).

Recommendation: recognize generic/custom sigils, not only `~r`, `~s`, and `~w`. Track paired delimiters, modifiers, uppercase no-interpolation semantics, escapes where applicable, and interpolation in lowercase sigils. A dedicated regex style is optional; it is not a language-level token distinction.

### Comments and embedded languages

All Elixir comments in the corpus are line comments beginning with `#`; each ends at the physical line boundary. This matches Rouge's `#.*$` rule ([Rouge root state](https://github.com/rouge-ruby/rouge/blob/main/lib/rouge/lexers/elixir.rb#L588-L592)).

One article includes EEx, but the site marks that block `language-erb`, not `language-elixir`. Its HTML tags and `<%=`/`%>` classes are therefore not evidence for the `.ex` lexer. EEx support should be a separate template-language concern.

## Coverage checklist derived from all examples

A corpus-level regression fixture should include:

- nested maps, structs, struct updates, and atom-key keyword notation;
- dotted aliases, remote calls, field access, local calls, DSL macros, and declaration names;
- bang/question names such as `read!` and `exists?`;
- anonymous functions, guards, comprehensions, pipelines, list tails, pinning, captures, and `&1`;
- protocols/implementations, module attributes, callbacks, and `::` types;
- strings, heredocs, escapes, nested interpolation, regex sigils, word/string sigils, and character codepoints;
- integers, floats, ranges, default arguments, comments, and malformed/incomplete input recovery.

## Recommended Spellixir policy

Use the website as a broad visual-coverage corpus, not as the specification. Spellixir should visibly distinguish:

1. language literals (`true`, `false`, `nil`), numbers, strings/charlists, and sigils;
2. atoms and keyword keys;
3. aliases, declaration names, and post-dot members;
4. documented highlighting keywords/directives and word operators;
5. symbolic operators, atomic punctuation, and delimiters;
6. module attributes, capture placeholders, comments, escapes, and interpolation boundaries.

Ordinary variables and unresolved local calls may remain neutral. Semantic distinctions such as local call versus variable, macro invocation, remote function versus field access, or actual module resolution should be added later through parsing/PSI or semantic analysis rather than guessed globally by the lexer.

This policy closes the visible gaps demonstrated by the examples while avoiding three Rouge artifacts: outdated special-form coverage (`alias`, `with`, `for`), treating `true`/`false`/`nil` as aliases/constants, and coalescing adjacent punctuation.
