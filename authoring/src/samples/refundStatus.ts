import type { WorkflowGraph } from "../schema/types";

// The canonical "refund status" workflow from the spec, fleshed out to cover every
// node shape: a lookup, a deterministic branch, a `when_llm` edge, replies, and a
// PSP write (so the chart shows the write/tier badges). Assumes `customer_id` and
// `evidence` are in the initial context (spec §12: grab/paste them for the MVP).
export const refundStatusWorkflow: WorkflowGraph = {
  start: "find_order",
  nodes: [
    {
      id: "find_order",
      do: "lookup_order",
      args: { customer: "{{customer_id}}" },
      as: "order",
      on_empty: "no_order",
      on_error: "escalate",
      on_timeout: "escalate",
      next: "check_status",
    },
    {
      id: "check_status",
      branch: [
        { when: "order.refund_status == 'processed'", goto: "reply_done" },
        { when: "order.refund_status == 'pending'", goto: "reply_pending" },
        { when: "order.refund_status == 'rejected'", goto: "open_dispute" },
        { when_llm: "the customer sounds upset or frustrated", goto: "escalate" },
        { else: "reply_generic" },
      ],
    },
    {
      id: "open_dispute",
      do: "psp_open_dispute",
      args: {
        order: "{{order.id}}",
        amount: "{{order.refund_amount}}",
        evidence: "{{evidence}}",
      },
      as: "dispute",
      on_error: "escalate",
      on_timeout: "escalate",
      next: "check_dispute",
    },
    {
      id: "check_dispute",
      branch: [
        { when: "dispute.status == 'open'", goto: "reply_dispute_opened" },
        { else: "escalate" },
      ],
    },
    {
      id: "reply_done",
      say: "Tell them their refund of {{order.refund_amount}} landed, 3–5 business days.",
      next: null,
    },
    {
      id: "reply_pending",
      say: "Tell them the refund is still pending and will arrive within the usual window.",
      next: null,
    },
    {
      id: "reply_dispute_opened",
      say: "Let them know we've formally opened a dispute with the payment provider on their behalf.",
      next: null,
    },
    {
      id: "reply_generic",
      say: "Give a friendly, generic acknowledgement and say we're looking into the order.",
      next: null,
    },
    {
      id: "no_order",
      say: "Apologize that we couldn't find an order on file and ask for an order number.",
      next: null,
    },
    {
      id: "escalate",
      say: "Hand off to a human agent with a short summary of what was found so far.",
      next: null,
    },
  ],
};

export const sampleInitialKeys = ["customer_id", "evidence"];
