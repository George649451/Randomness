import type { ToolRegistry } from "./types";

// A small mock registry. In production these are generated, one per operation,
// kept granular (spec §6: `lookup_order`, `open_dispute`, not a monolithic `psp`).
export const mockRegistry: ToolRegistry = {
  lookup_order: {
    name: "lookup_order",
    kind: "read",
    tier: "api",
    inputs: {
      customer: { type: "string", required: true },
    },
    returns: {
      id: "string",
      refund_status: "string", // "processed" | "pending" | "rejected"
      refund_amount: "number",
      age_days: "number",
    },
    description:
      "Looks up the most recent order for a customer, including its refund status and amount.",
  },

  lookup_refund_policy: {
    name: "lookup_refund_policy",
    kind: "read",
    tier: "api",
    inputs: {
      country: { type: "string", required: true },
    },
    returns: {
      window_days: "number",
      restocking_fee: "number",
    },
    description: "Returns the refund policy (window, fees) for a given country.",
  },

  psp_open_dispute: {
    name: "psp_open_dispute",
    kind: "write",
    tier: "a11y",
    inputs: {
      order: { type: "string", required: true },
      amount: { type: "number", required: true },
      evidence: { type: "file", required: true },
    },
    returns: {
      id: "string",
      status: "string", // "open" | "rejected"
    },
    description:
      "Opens a payment dispute with the PSP for an order, attaching the customer's evidence. " +
      "Use when a refund the PSP hasn't actioned needs to be formally contested.",
  },
};
