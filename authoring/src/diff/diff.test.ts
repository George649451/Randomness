import { describe, it, expect } from "vitest";
import { applyDiff, summarizeDiff, DiffError, type DiffOp } from "./diff";
import type { WorkflowGraph } from "../schema/types";

const base: WorkflowGraph = {
  start: "a",
  nodes: [
    { id: "a", do: "lookup_order", on_error: "b", on_timeout: "b", next: "b" },
    { id: "b", say: "hi", next: null },
  ],
};

describe("applyDiff", () => {
  it("does not mutate the input graph", () => {
    const snapshot = JSON.stringify(base);
    applyDiff(base, [{ op: "update_node", id: "b", set: { say: "changed" } }]);
    expect(JSON.stringify(base)).toBe(snapshot);
  });

  it("update_node shallow-merges fields", () => {
    const next = applyDiff(base, [{ op: "update_node", id: "b", set: { say: "new wording" } }]);
    expect(next.nodes.find((n) => n.id === "b")?.say).toBe("new wording");
    // untouched node is identical
    expect(next.nodes.find((n) => n.id === "a")?.do).toBe("lookup_order");
  });

  it("update_node with null deletes a field", () => {
    const next = applyDiff(base, [{ op: "update_node", id: "a", set: { on_empty: null } }]);
    expect("on_empty" in (next.nodes.find((n) => n.id === "a") ?? {})).toBe(false);
  });

  it("add_node appends and rejects duplicates", () => {
    const next = applyDiff(base, [{ op: "add_node", node: { id: "c", say: "x", next: null } }]);
    expect(next.nodes.map((n) => n.id)).toEqual(["a", "b", "c"]);
    expect(() => applyDiff(next, [{ op: "add_node", node: { id: "c", say: "y" } }])).toThrow(DiffError);
  });

  it("remove_node removes and rejects unknown", () => {
    const next = applyDiff(base, [{ op: "remove_node", id: "b" }]);
    expect(next.nodes.map((n) => n.id)).toEqual(["a"]);
    expect(() => applyDiff(base, [{ op: "remove_node", id: "zzz" }])).toThrow(DiffError);
  });

  it("set_start changes the entry node", () => {
    expect(applyDiff(base, [{ op: "set_start", node: "b" }]).start).toBe("b");
  });

  it("refuses to rename a node via update_node", () => {
    expect(() =>
      applyDiff(base, [{ op: "update_node", id: "a", set: { id: "z" } }]),
    ).toThrow(DiffError);
  });

  it("applies ops in order", () => {
    const ops: DiffOp[] = [
      { op: "add_node", node: { id: "c", say: "x", next: null } },
      { op: "update_node", id: "c", set: { say: "y" } },
    ];
    expect(applyDiff(base, ops).nodes.find((n) => n.id === "c")?.say).toBe("y");
  });
});

describe("summarizeDiff", () => {
  it("produces a human-readable line per op", () => {
    const lines = summarizeDiff([
      { op: "add_node", node: { id: "c" } },
      { op: "update_node", id: "b", set: { say: "x", next: null } },
    ]);
    expect(lines[0]).toContain('Add node "c"');
    expect(lines[1]).toContain('Update "b"');
    expect(lines[1]).toContain("say");
  });
});
