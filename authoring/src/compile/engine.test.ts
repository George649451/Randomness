import { describe, it, expect } from "vitest";
import { runTurn } from "./engine";
import { FakeCompileClient } from "./client-fake";
import { mockRegistry } from "../descriptors/registry";
import { refundStatusWorkflow, sampleInitialKeys } from "../samples/refundStatus";
import type { CompileInput } from "./types";

const opts = { registry: mockRegistry, initialKeys: sampleInitialKeys };
const input = (): CompileInput => ({ graph: refundStatusWorkflow, history: [], brief: "..." });

describe("runTurn", () => {
  it("surfaces a clarifying question without changing the graph", async () => {
    const client = new FakeCompileClient([{ kind: "ask", question: "Which country?" }]);
    const result = await runTurn(client, input(), opts);
    expect(result.kind).toBe("ask");
    if (result.kind === "ask") expect(result.question).toBe("Which country?");
  });

  it("applies a valid commit and reports no errors", async () => {
    const client = new FakeCompileClient([
      {
        kind: "commit",
        summary: "reword the pending reply",
        ops: [{ op: "update_node", id: "reply_pending", set: { say: "Reworded." } }],
      },
    ]);
    const result = await runTurn(client, input(), opts);
    expect(result.kind).toBe("commit");
    if (result.kind === "commit") {
      expect(result.graph.nodes.find((n) => n.id === "reply_pending")?.say).toBe("Reworded.");
      expect(result.problems.filter((p) => p.severity === "error")).toEqual([]);
      // input graph is untouched (applyDiff is pure)
      expect(refundStatusWorkflow.nodes.find((n) => n.id === "reply_pending")?.say).not.toBe("Reworded.");
    }
  });

  it("commits even when the result is invalid, surfacing problems (the review surface)", async () => {
    const client = new FakeCompileClient([
      {
        kind: "commit",
        summary: "point reply at a missing node",
        ops: [{ op: "update_node", id: "reply_done", set: { next: "ghost" } }],
      },
    ]);
    const result = await runTurn(client, input(), opts);
    expect(result.kind).toBe("commit");
    if (result.kind === "commit") {
      expect(result.problems.some((p) => p.severity === "error" && p.message.includes("ghost"))).toBe(true);
    }
  });

  it("returns an error when the diff cannot be applied", async () => {
    const client = new FakeCompileClient([
      // add_node onto an existing id → DiffError
      { kind: "commit", summary: "dup", ops: [{ op: "add_node", node: { id: "escalate", say: "x" } }] },
    ]);
    const result = await runTurn(client, input(), opts);
    expect(result.kind).toBe("error");
  });

  it("returns an error when the model call throws", async () => {
    const client = new FakeCompileClient([]); // empty queue → decide() throws
    const result = await runTurn(client, input(), opts);
    expect(result.kind).toBe("error");
  });
});
