import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath } from "node:url";

// This app reuses goose's reference clients, vendored under src/vendor/:
//  - @aaif/goose-sdk (vendor/goose-sdk): GooseClient — protocol/transport layer
//  - @desktop (vendor/desktop): desktop components (MarkdownContent,
//    ToolCallStatusIndicator, …) imported as source; @vitejs/plugin-react
//    compiles their JSX, tailwind scans them for classes (via theme.css),
//    and a tiny window.electron shim covers the desktop-only APIs.
const gooseSdk = fileURLToPath(
  new URL("./src/vendor/goose-sdk/index.ts", import.meta.url),
);
const desktopSrc = fileURLToPath(
  new URL("./src/vendor/desktop", import.meta.url),
);

const buildStamp = `${new Date().toISOString().slice(0, 16).replace("T", " ")}Z`;

export default defineConfig({
  root: ".",
  // The desktop pairing QR points at a GitHub Pages *project* site
  // (https://aaif-goose.github.io/goose-mobile/), so assets must resolve
  // relative to index.html, not the domain root.
  base: "./",
  define: {
    __BUILD_STAMP__: JSON.stringify(buildStamp),
  },
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@aaif/goose-sdk": gooseSdk,
      "@desktop": desktopSrc,
    },
    // One shared copy across app + SDK + desktop sources.
    dedupe: ["react", "react-dom", "@agentclientprotocol/sdk", "zod", "react-intl"],
  },
  server: {
    port: 5178,
  },
  build: {
    target: "esnext",
    outDir: "dist",
  },
  assetsInclude: ["**/*.wasm"],
});
