#!/usr/bin/env bash
set -euo pipefail

prototype_expert="${1:?usage: $0 <expert-executable> [working-directory]}"
prototype_working_directory="${2:-$(cd "$(dirname "$0")/../fixture" && pwd)}"
prototype_document="$prototype_working_directory/lib/sample.ex"
prototype_root_uri="file://$prototype_working_directory"
prototype_document_uri="file://$prototype_document"
prototype_text="$(jq -Rs . <"$prototype_document")"
prototype_initialize="{\"jsonrpc\":\"2.0\",\"id\":\"initialize-1\",\"method\":\"initialize\",\"params\":{\"processId\":null,\"rootUri\":\"$prototype_root_uri\",\"capabilities\":{}}}"
prototype_initialized='{"jsonrpc":"2.0","method":"initialized","params":{}}'
prototype_registered='{"jsonrpc":"2.0","id":1,"result":null}'
prototype_open="{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"$prototype_document_uri\",\"languageId\":\"elixir\",\"version\":1,\"text\":$prototype_text}}}"
prototype_format="{\"jsonrpc\":\"2.0\",\"id\":\"format-1\",\"method\":\"textDocument/formatting\",\"params\":{\"textDocument\":{\"uri\":\"$prototype_document_uri\"},\"options\":{\"tabSize\":2,\"insertSpaces\":true}}}"
prototype_shutdown='{"jsonrpc":"2.0","id":"shutdown-1","method":"shutdown","params":null}'
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
    send_message "$prototype_initialize"
    sleep 5
    send_message "$prototype_initialized"
    sleep 2
    send_message "$prototype_registered"
    send_message "$prototype_open"
    sleep 5
    send_message "$prototype_format"
    sleep 5
    send_message "$prototype_shutdown"
    sleep 1
    send_message "$prototype_exit"
  } | "$prototype_expert" --stdio >"$prototype_output" 2>"$prototype_error"
) || true

if grep -Fq '"id":"format-1"' "$prototype_output"; then
  echo "Expert formatting response:"
  tr '\r' '\n' <"$prototype_output" | grep -F '"id":"format-1"' | head -n 1
  exit 0
fi

echo "FAIL: Expert did not return a formatting response" >&2
cat "$prototype_error" >&2
cat "$prototype_output" >&2
exit 1
