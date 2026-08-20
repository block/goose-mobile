// React entry for the roam web client.
//
// Reuses goose's reference clients, vendored under src/vendor/:
//  - @aaif/goose-sdk (vendor/goose-sdk): GooseClient over the roam byte-duplex
//  - @desktop (vendor/desktop): desktop components (MarkdownContent,
//    ToolCallStatusIndicator, Button) + the desktop Tailwind theme
//
// Still fully stateless + CDN-hostable: no backend, all state in the tab,
// all traffic browser ⇄ relay ⇄ roam host.
import "./shim";
import "./theme.css";
// CRITICAL: the desktop's main.css only *registers* token names for Tailwind;
// the actual color values are applied at runtime by applyThemeTokens() (the
// desktop calls this in renderer.tsx before first paint). Without it every
// semantic token (--color-text-primary, …) is undefined and the UI renders
// washed out. It's browser-safe: localStorage + matchMedia only.
import { applyThemeTokens, getResolvedTheme } from "@desktop/theme/theme-tokens";
import React from "react";
import { createRoot } from "react-dom/client";
import { IntlProvider } from "react-intl";
import initWasm, { RoamClient } from "./wasm/goose_roaming_web.js";
import { App } from "./App";

const SECRET_STORAGE_KEY = "goose-roam-secret-hex";

async function boot() {
  // Apply the desktop theme (token values + .dark class) before first paint,
  // exactly like the desktop's renderer.tsx + ThemeContext do.
  const resolved = getResolvedTheme();
  applyThemeTokens(resolved);
  document.documentElement.classList.toggle("dark", resolved === "dark");
  window
    .matchMedia("(prefers-color-scheme: dark)")
    .addEventListener("change", (e) => {
      const t = e.matches ? "dark" : "light";
      applyThemeTokens(t);
      document.documentElement.classList.toggle("dark", t === "dark");
    });

  await initWasm();
  // Stable per-browser roam identity so the host only accepts this tab once.
  const saved = localStorage.getItem(SECRET_STORAGE_KEY) ?? undefined;
  const roam = new RoamClient(saved);
  if (!saved) localStorage.setItem(SECRET_STORAGE_KEY, roam.secretHex());

  createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
      <IntlProvider locale="en" defaultLocale="en" messages={{}}>
        <App roam={roam} />
      </IntlProvider>
    </React.StrictMode>,
  );
}

void boot();
