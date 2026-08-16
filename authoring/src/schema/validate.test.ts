import { describe, it, expect } from "vitest";
import { validateGraph } from "./validate";
import type { WorkflowGraph } from "./types";
import { mockRegistry } from "../descriptors/registry";
import { refundStatusWorkflow, sampleInitialKeys } from "../samples/refundStatus";

const opts = { registry: mockRegistry, initialKeys: sampleInitialKeys };
const errorsOf = (g: WorkflowGraph) =>
  validateGraph(g, opts).filter((p) => p.severity === "error");

describe("validateGraph", () => {
  it("accepts the canonical sample workflow with no errors", () => {
    expect(errorsOf(refundStatusWorkflow)).toEqual([]);
  });

  it("flags a goto / next pointing at an unknown node (spec §3.4)", () => {
    const g: WorkflowGraph = {
      start: "a",
      nodes: [{ id: "a", say: "hi", next: "ghost" }],
    };
    expect(errorsOf(g).some((p) => p.message.includes("ghost"))).toBe(true);
  });

  it("flags a `do` naming a tool not in the registry (spec §3.4)", () => {
    const g: WorkflowGraph = {
      start: "a",
      nodes: [{ id: "a", do: "not_a_tool", on_error: "a", on_timeout: "a", next: null }],
    };
    expect(errorsOf(g).some((p) => p.message.includes("unknown tool"))).toBe(true);
  });

  it("flags a branch with no else (spec §3.1)", () => {
    const g: WorkflowGraph = {
      start: "a",
      nodes: [
        { id: "a", branch: [{ when: "customer_id == '1'", goto: "b" }] },
        { id: "b", say: "x", next: null },
      ],
    };
    expect(errorsOf(g).some((p) => p.message.includes("no `else`"))).toBe(true);
  });

  it("flags a missing start node (spec §3.4)", () => {
    const g: WorkflowGraph = { start: "ghost", nodes: [{ id: "a", say: "x", next: null }] };
    expect(errorsOf(g).some((p) => p.message.includes("start node"))).toBe(true);
  });

  it("flags a to-do missing its mandatory failure exits (spec §3.1)", () => {
    const g: WorkflowGraph = {
      start: "a",
      nodes: [{ id: "a", do: "lookup_order", args: { customer: "{{customer_id}}" }, next: null }],
    };
    const errs = errorsOf({ ...g, nodes: [{ ...g.nodes[0], on_error: undefined, on_timeout: undefined }] });
    expect(errs.some((p) => p.message.includes("on_error"))).toBe(true);
    expect(errs.some((p) => p.message.includes("on_timeout"))).toBe(true);
  });

  it("flags a required tool input that is not supplied (spec §5.1)", () => {
    const g: WorkflowGraph = {
      start: "a",
      nodes: [{ id: "a", do: "lookup_order", args: {}, on_error: "a", on_timeout: "a", next: null }],
    };
    expect(errorsOf(g).some((p) => p.message.includes('requires input "customer"'))).toBe(true);
  });

  it("flags a template referencing a value that can't exist (spec §3.4)", () => {
    const g: WorkflowGraph = {
      start: "a",
      nodes: [{ id: "a", say: "hello {{nonexistent.field}}", next: null }],
    };
    expect(errorsOf(g).some((p) => p.message.includes("nonexistent"))).toBe(true);
  });

  it("flags a `when` referencing an unknown root (spec §3.4)", () => {
    const g: WorkflowGraph = {
      start: "a",
      nodes: [
        { id: "a", branch: [{ when: "mystery.x == 1", goto: "b" }, { else: "b" }] },
        { id: "b", say: "x", next: null },
      ],
    };
    expect(errorsOf(g).some((p) => p.message.includes("mystery"))).toBe(true);
  });

  it("warns on an unreachable node (spec §9 thin-graph signal)", () => {
    const g: WorkflowGraph = {
      start: "a",
      nodes: [
        { id: "a", say: "x", next: null },
        { id: "orphan", say: "y", next: null },
      ],
    };
    const warns = validateGraph(g, opts).filter((p) => p.severity === "warning");
    expect(warns.some((p) => p.message.includes("unreachable"))).toBe(true);
  });
});
