# Release gates

Spellixir releases use one immutable plugin ZIP throughout verification. The candidate manifest records the source commit and SHA-256 hashes for both the plugin ZIP and the separate smoke probe. CI verifies that provenance before each gate. Signing adds a Marketplace signing block to the ZIP; `verify-signed` confirms that its plugin payload remains byte-for-byte identical to the candidate. Marketplace publication consumes that signed ZIP.

## Host claims

IntelliJ IDEA, GoLand, and PyCharm are the Supported Hosts. The automated matrix checks the minimum supported IDEA build (`2026.1.4`, build `261.26222.65`) and the latest stable `262.*` release of each Supported Host. The plugin descriptor allows builds from `261.26222.65` through `262.*`. Other JetBrains IDEs may be Compatible Hosts when Marketplace dependency rules allow installation, but this project does not claim dedicated verification or support for them. WSL, containers, and remote IDE environments are outside the Supported Host claim.

The Native Core does not require Elixir, Erlang/OTP, Mix, or a language server. The smoke test puts trap executables for those commands first on `PATH` and fails if any is invoked.

## What each gate proves

Every pull request runs `check` (including the syntax corpus and responsiveness test), builds the plugin and smoke probe, checks plugin structure, and verifies repeatable Grammar-Kit output. It then freezes and validates the candidate ZIP.

Pull requests, pushes to `main`, version tags (`v*`), and manual verification runs also run Plugin Verifier against the minimum IDEA build and the latest stable 2026.2 builds of IDEA, GoLand, and PyCharm. Those jobs download the candidate artifact made by the build job and validate its recorded commit and hashes before use. They install the exact ZIP in each IDE and run the behavior smoke checks for `.ex` and `.exs`, syntax highlighting and parser recovery, ordinary and umbrella Mix contexts, standalone files, malformed metadata, and runtime independence. macOS and Windows also run the Native Core tests and responsiveness check.

Marketplace publication is a separate manual workflow dispatch on a version tag. Its checkbox defaults to false. When enabled, the job waits for the `marketplace-release` GitHub Environment, signs and verifies the candidate, runs a final IDEA smoke test against that signed ZIP, and publishes the same ZIP. Configure required reviewers on that environment and store these environment secrets there:

- `JB_CERTIFICATE_CHAIN`
- `JB_PRIVATE_KEY`
- `JB_PRIVATE_KEY_PASSWORD`
- `JB_MARKETPLACE_TOKEN`

Pull request and ordinary verification jobs do not receive these credentials. A tag push runs all verification gates but does not publish by itself.

## Running the gates locally

From a clean commit, build and freeze the candidate once:

```sh
./gradlew check buildPlugin smokeProbeZip verifyPluginStructure freezeReleaseCandidate --console=plain
python3 scripts/verify_generated_sources.py
python3 scripts/release_candidate.py verify --candidate build/release-candidate
```

The freeze command refuses modified tracked files. For a host smoke test, keep using the frozen probe and select a Supported Host:

```sh
./gradlew packagedSmoke -PsmokeHost=idea -PsmokeVersion=2026.1.4 \
  -PsmokeProbeArchive=build/release-candidate/smoke-probe.zip --console=plain
python3 scripts/release_candidate.py verify-smoke \
  --candidate build/release-candidate \
  --archive build/release-candidate/candidate.zip \
  --report build/packaged-smoke/idea/smoke.json \
  --runtime-attempted build/packaged-smoke/idea/runtime-attempted \
  --host idea --version 2026.1.4
```

Use `-PsmokeHost=goland` or `pycharm` and `-PsmokeVersion=latest` for the other latest stable hosts. Use `-PsmokeVersion=latest` for latest IDEA. A smoke run downloads and starts the selected IDE and can take several minutes. Use a fresh candidate output directory for each freeze; candidate output is intentionally not overwritten.
