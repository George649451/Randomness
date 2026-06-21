// Pure parsing/validation of the model's raw output into a ModelDecision.
// Defensive: the model's tool input is untrusted shape until checked here.

import type { DiffOp } from "../diff/diff";
import type { WorkflowNode } from "../schema/types";
import type { ModelDecision } from "./types";

export class CompileParseError extends Error {}

const KNOWN_OPS = new Set(["set_start", "add_node", "remove_node", "update_node"]);

/** Validate the raw `ops` array from commit_changes into typed DiffOps. */
export function parseOps(raw: unknown): DiffOp[] {
  if (!Array.isArray(raw)) throw new CompileParseError("`ops` must be an array");
  return raw.map((o, i) => {
    if (typeof o !== "object" || o === null) {
      throw new CompileParseError(`op[${i}] is not an object`);
    }
    const op = o as Record<string, unknown>;
    if (typeof op.op !== "string" || !KNOWN_OPS.has(op.op)) {
      throw new CompileParseError(`op[${i}] has unknown op "${String(op.op)}"`);
    }
    switch (op.op) {
      case "set_start":
        if (typeof op.node !== "string") throw new CompileParseError(`op[${i}] set_start needs string \`node\``);
        return { op: "set_start", node: op.node };
      case "add_node":
        if (typeof op.node !== "object" || op.node === null || typeof (op.node as { id?: unknown }).id !== "string") {
          throw new CompileParseError(`op[${i}] add_node needs a \`node\` with a string id`);
        }
        return { op: "add_node", node: op.node as WorkflowNode };
      case "remove_node":
        if (typeof op.id !== "string") throw new CompileParseError(`op[${i}] remove_node needs string \`id\``);
        return { op: "remove_node", id: op.id };
      case "update_node":
        if (typeof op.id !== "string") throw new CompileParseError(`op[${i}] update_node needs string \`id\``);
        if (typeof op.set !== "object" || op.set === null) {
          throw new CompileParseError(`op[${i}] update_node needs an object \`set\``);
        }
        return { op: "update_node", id: op.id, set: op.set as Record<string, unknown> };
      default:
        throw new CompileParseError(`op[${i}] unreachable`);
    }
  });
}

/** Turn a (toolName, input) pair from the model into a typed decision. */
export function parseDecision(toolName: string, input: unknown): ModelDecision {
  const obj = (typeof input === "object" && input !== null ? input : {}) as Record<string, unknown>;
  if (toolName === "ask_question") {
    if (typeof obj.question !== "string" || !obj.question.trim()) {
      throw new CompileParseError("ask_question needs a non-empty `question`");
    }
    return { kind: "ask", question: obj.question, reason: typeof obj.reason === "string" ? obj.reason : undefined };
  }
  if (toolName === "commit_changes") {
    if (typeof obj.summary !== "string" || !obj.summary.trim()) {
      throw new CompileParseError("commit_changes needs a non-empty `summary`");
    }
    return { kind: "commit", summary: obj.summary, ops: parseOps(obj.ops) };
  }
  throw new CompileParseError(`unexpected tool "${toolName}"`);
}
