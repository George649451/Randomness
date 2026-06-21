// The brief→graph compile layer (spec §7) — types.
//
// The model is a LEAF here, exactly as Directive 1 demands: it never edits the
// graph or decides control flow. It returns one of two *decisions* — ask a
// clarifying question, or propose a targeted diff — and plain code applies the
// diff, validates the result, and re-renders. The model is in the joint, not at
// the wheel.

import type { DiffOp } from "../diff/diff";
import type { Problem } from "../schema/validate";
import type { WorkflowGraph } from "../schema/types";

/** A single turn of the authoring conversation, as the UI stores it. */
export interface ChatMessage {
  role: "user" | "assistant";
  text: string;
}

/** What the model decided this turn (before any diff is applied). */
export type ModelDecision =
  | { kind: "ask"; question: string; reason?: string }
  | { kind: "commit"; ops: DiffOp[]; summary: string };

/** Everything a turn needs. The graph is always sent fresh — it is the truth. */
export interface CompileInput {
  graph: WorkflowGraph;
  history: ChatMessage[];
  brief: string;
}

/**
 * The swappable model seam. The interpreter-style discipline: the rest of the
 * system never knows whether this is a real API call or a fake — tests inject a
 * scripted client, production injects the Anthropic one.
 */
export interface CompileClient {
  decide(input: CompileInput): Promise<ModelDecision>;
}

/** The outcome of a turn, after code has applied + validated any diff. */
export type TurnResult =
  | { kind: "ask"; question: string; reason?: string }
  | {
      kind: "commit";
      graph: WorkflowGraph;
      ops: DiffOp[];
      summary: string;
      /** Validation problems on the NEW graph — the review surface (spec §7). */
      problems: Problem[];
    }
  | { kind: "error"; message: string };
