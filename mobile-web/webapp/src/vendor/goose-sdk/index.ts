// Vendored subset of @aaif/goose-sdk (ui/sdk in the goose repo), reduced to the
// GooseClient surface the roam web client actually uses. Kept in sync with the
// goose repo's ui/sdk by hand; see mobile-web/README.md.
export * from "./generated/types.gen.js";
export * from "./generated/zod.gen.js";
export {
  type GooseClientCallbacks,
  type GooseExtNotifications,
} from "./generated/client.gen.js";
export { GooseClient } from "./goose-client.js";
export { createHttpStream } from "./http-stream.js";

export {
  ClientSideConnection,
  type Client,
  type Stream,
} from "@agentclientprotocol/sdk";
