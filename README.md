# goose roam — web client (static build)

CDN-hosted build of the goose roaming web client
(source: aaif-goose/goose PR #10537, `crates/goose-roaming/web/`).

Pure-browser roam peer: iroh compiled to wasm, relay-only, drives a
`goose roam share` agent over ACP. No backend — all traffic goes
browser ⇄ iroh relay ⇄ your roam host, end-to-end encrypted.

Served via GitHub Pages from this `roam-web` branch. `main` is unrelated.
