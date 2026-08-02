#!/usr/bin/env bash
set -euo pipefail

prototype_root="$(cd "$(dirname "$0")" && pwd)"
prototype_idea_home="${IDEA_HOME:-/Applications/IntelliJ IDEA.app/Contents}"
prototype_compiler="$prototype_idea_home/plugins/Kotlin/kotlinc/bin/kotlinc"
prototype_jar="$(command -v jar || true)"
prototype_classes="$prototype_root/build/prototype-classes"
prototype_plugin_root="$prototype_root/build/plugin/spellixir-elixirls-prototype"
prototype_distribution="$prototype_root/build/distributions/spellixir-elixirls-prototype.zip"

if [[ ! -x "$prototype_compiler" || -z "$prototype_jar" ]]; then
  echo "Set IDEA_HOME to an IntelliJ IDEA installation containing Kotlin, and ensure a JDK jar tool is on PATH."
  exit 1
fi

prototype_classpath="$(find "$prototype_idea_home/lib" -maxdepth 1 -type f -name '*.jar' -print | paste -sd: -)"
mkdir -p "$prototype_classes" "$prototype_plugin_root/lib" "$(dirname "$prototype_distribution")"

"$prototype_compiler" \
  "$prototype_root/src/main/kotlin/com/cimere/spellixir/prototype/ElixirLsIntegrationProvider.kt" \
  "$prototype_root/src/main/kotlin/com/cimere/spellixir/prototype/ElixirPrototypeFileType.kt" \
  -classpath "$prototype_classpath" \
  -d "$prototype_classes"

"$prototype_jar" --create \
  --file "$prototype_plugin_root/lib/spellixir-elixirls-prototype.jar" \
  -C "$prototype_classes" . \
  -C "$prototype_root/src/main/resources" META-INF/plugin.xml

(
  cd "$(dirname "$prototype_plugin_root")"
  zip -qr "$prototype_distribution.new" "$(basename "$prototype_plugin_root")"
)
mv "$prototype_distribution.new" "$prototype_distribution"

echo "Created $prototype_distribution"
