#!/usr/bin/env bash
set -euo pipefail

prototype_working_directory="${1:?usage: $0 <working-directory>}"
prototype_id_kind="${2:-integer}"
prototype_root_uri="file://$prototype_working_directory"
if [[ "$prototype_id_kind" == "string" ]]; then
  prototype_id='"jetbrains-1"'
  prototype_expected_id='"id":"jetbrains-1"'
else
  prototype_id='1'
  prototype_expected_id='"id":1'
fi
prototype_request="{\"jsonrpc\":\"2.0\",\"id\":$prototype_id,\"method\":\"initialize\",\"params\":{\"processId\":null,\"rootUri\":\"$prototype_root_uri\",\"capabilities\":{}}}"
prototype_initialized="{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}"
prototype_output="$(mktemp)"
prototype_error="$(mktemp)"

cleanup() {
  rm -f "$prototype_output" "$prototype_error"
}
trap cleanup EXIT

(
  cd "$prototype_working_directory"
  {
    printf 'Content-Length: %s\r\n\r\n' "${#prototype_request}"
    printf '%s' "$prototype_request"
    sleep 2
    printf 'Content-Length: %s\r\n\r\n' "${#prototype_initialized}"
    printf '%s' "$prototype_initialized"
    sleep 2
  } | elixir-ls >"$prototype_output" 2>"$prototype_error"
) || true

if grep -Fq "$prototype_expected_id" "$prototype_output"; then
  echo "PASS: ElixirLS returned $prototype_id_kind-ID initialize response in $prototype_working_directory"
  exit 0
fi

echo "FAIL: ElixirLS did not return $prototype_id_kind-ID initialize response in $prototype_working_directory" >&2
cat "$prototype_error" >&2
cat "$prototype_output" >&2
exit 1
