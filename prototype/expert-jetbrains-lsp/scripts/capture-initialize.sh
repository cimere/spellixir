#!/usr/bin/env bash
set -euo pipefail

prototype_expert="${1:?usage: $0 <expert-executable> [working-directory]}"
prototype_working_directory="${2:-$(cd "$(dirname "$0")/../fixture" && pwd)}"
prototype_root_uri="file://$prototype_working_directory"
prototype_request="{\"jsonrpc\":\"2.0\",\"id\":\"jetbrains-1\",\"method\":\"initialize\",\"params\":{\"processId\":null,\"rootUri\":\"$prototype_root_uri\",\"capabilities\":{}}}"
prototype_initialized='{"jsonrpc":"2.0","method":"initialized","params":{}}'
prototype_shutdown='{"jsonrpc":"2.0","id":"jetbrains-2","method":"shutdown","params":null}'
prototype_exit='{"jsonrpc":"2.0","method":"exit","params":null}'
prototype_output="$(mktemp)"
prototype_error="$(mktemp)"

cleanup() {
  rm -f "$prototype_output" "$prototype_error"
}
trap cleanup EXIT

send_message() {
  local prototype_message="$1"
  printf 'Content-Length: %s\r\n\r\n' "${#prototype_message}"
  printf '%s' "$prototype_message"
}

(
  cd "$prototype_working_directory"
  {
    send_message "$prototype_request"
    sleep 15
    send_message "$prototype_initialized"
    send_message "$prototype_shutdown"
    sleep 2
    send_message "$prototype_exit"
  } | "$prototype_expert" --stdio >"$prototype_output" 2>"$prototype_error"
) || true

if grep -Fq '"id":"jetbrains-1"' "$prototype_output"; then
  echo "PASS: Expert returned JetBrains-style string-ID initialize response"
  tr '\r' '\n' <"$prototype_output" | grep -F '"id":"jetbrains-1"' | head -n 1
  exit 0
fi

echo "FAIL: Expert did not return the initialize response" >&2
cat "$prototype_error" >&2
cat "$prototype_output" >&2
exit 1
