#!/usr/bin/env bash
# Serve the roaming web client for a manual try.
#
# Assumes the wasm bindings are already built into src/wasm/
# (run ../build-web.sh if they're missing).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PORT="${PORT:-5178}"

if [[ ! -d "${HERE}/src/wasm" ]] || [[ ! -f "${HERE}/src/wasm/goose_roaming_web_bg.wasm" ]]; then
  echo "wasm bindings missing — building them (needs the wasm toolchain)…"
  "${HERE}/../build-web.sh"
fi

if [[ ! -d "${HERE}/node_modules" ]]; then
  echo "installing deps (pnpm install)…"
  ( cd "${HERE}" && pnpm install )
fi

echo "serving on http://localhost:${PORT}"
exec "${HERE}/node_modules/.bin/vite" --port "${PORT}" --strictPort
