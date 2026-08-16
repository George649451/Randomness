// Targeted graph diffs (spec §7).
//
// "Apply targeted diffs, never re-emit the whole graph. If the model returns the
//  entire JSON each turn, a turn about node 3 can silently reword node 7."
//
// So the chat's compile layer (built next) emits a list of these ops, and we apply
// them to the existing graph. This module is a PURE function — no React, no LLM —
// which is exactly why it's the part that gets unit-tested.

import type { WorkflowGraph, WorkflowNode } from "../schema/types";

export type DiffOp =
  | { op: "set_start"; node: string }
  | { op: "add_node"; node: WorkflowNode }
  | { op: "remove_node"; id: string }
  /** Shallow-merge `set` into the named node. `null` in `set` deletes a field. */
  | { op: "update_node"; id: string; set: Partial<Record<keyof WorkflowNode, unknown>> };

export class DiffError extends Error {}

function cloneGraph(graph: WorkflowGraph): WorkflowGraph {
  return { start: graph.start, nodes: graph.nodes.map((n) => ({ ...n })) };
}

/** Apply a list of ops in order, returning a new graph. Never mutates the input. */
export function applyDiff(graph: WorkflowGraph, ops: DiffOp[]): WorkflowGraph {
  const next = cloneGraph(graph);
  const indexOf = (id: string) => next.nodes.findIndex((n) => n.id === id);

  for (const op of ops) {
    switch (op.op) {
      case "set_start": {
        next.start = op.node;
        break;
      }
      case "add_node": {
        if (indexOf(op.node.id) !== -1) {
          throw new DiffError(`add_node: id "${op.node.id}" already exists`);
        }
        next.nodes.push({ ...op.node });
        break;
      }
      case "remove_node": {
        const i = indexOf(op.id);
        if (i === -1) throw new DiffError(`remove_node: unknown id "${op.id}"`);
        next.nodes.splice(i, 1);
        break;
      }
      case "update_node": {
        const i = indexOf(op.id);
        if (i === -1) throw new DiffError(`update_node: unknown id "${op.id}"`);
        const merged: Record<string, unknown> = { ...next.nodes[i] };
        for (const [k, v] of Object.entries(op.set)) {
          if (v === null) delete merged[k];
          else merged[k] = v;
        }
        if (merged.id !== op.id) {
          throw new DiffError(`update_node: cannot rename node id ("${op.id}" -> "${merged.id}")`);
        }
        next.nodes[i] = merged as unknown as WorkflowNode;
        break;
      }
      default: {
        const _exhaustive: never = op;
        throw new DiffError(`unknown op: ${JSON.stringify(_exhaustive)}`);
      }
    }
  }

  return next;
}

/** Human-readable "here's what I changed" — the review surface (spec §7). */
export function summarizeDiff(ops: DiffOp[]): string[] {
  return ops.map((op) => {
    switch (op.op) {
      case "set_start":
        return `Set start node to "${op.node}"`;
      case "add_node":
        return `Add node "${op.node.id}"`;
      case "remove_node":
        return `Remove node "${op.id}"`;
      case "update_node":
        return `Update "${op.id}": ${Object.keys(op.set).join(", ")}`;
    }
  });
}
