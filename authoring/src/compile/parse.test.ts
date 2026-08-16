import { describe, it, expect } from "vitest";
import { parseDecision, parseOps, CompileParseError } from "./parse";

describe("parseDecision", () => {
  it("parses ask_question", () => {
    const d = parseDecision("ask_question", { question: "Which country?", reason: "policy lookup" });
    expect(d).toEqual({ kind: "ask", question: "Which country?", reason: "policy lookup" });
  });

  it("rejects ask_question with no question", () => {
    expect(() => parseDecision("ask_question", { question: "  " })).toThrow(CompileParseError);
  });

  it("parses commit_changes", () => {
    const d = parseDecision("commit_changes", {
      summary: "reword pending reply",
      ops: [{ op: "update_node", id: "reply_pending", set: { say: "New text" } }],
    });
    expect(d.kind).toBe("commit");
    if (d.kind === "commit") {
      expect(d.summary).toBe("reword pending reply");
      expect(d.ops).toHaveLength(1);
    }
  });

  it("rejects an unknown tool name", () => {
    expect(() => parseDecision("do_something", {})).toThrow(CompileParseError);
  });
});

describe("parseOps", () => {
  it("accepts all four op shapes", () => {
    const ops = parseOps([
      { op: "set_start", node: "a" },
      { op: "add_node", node: { id: "a", say: "hi", next: null } },
      { op: "remove_node", id: "b" },
      { op: "update_node", id: "c", set: { say: "x" } },
    ]);
    expect(ops.map((o) => o.op)).toEqual(["set_start", "add_node", "remove_node", "update_node"]);
  });

  it("rejects a non-array", () => {
    expect(() => parseOps({})).toThrow(CompileParseError);
  });

  it("rejects an unknown op", () => {
    expect(() => parseOps([{ op: "rename_node", id: "a" }])).toThrow(CompileParseError);
  });

  it("rejects add_node without a string id", () => {
    expect(() => parseOps([{ op: "add_node", node: { say: "x" } }])).toThrow(CompileParseError);
  });

  it("rejects update_node without a set object", () => {
    expect(() => parseOps([{ op: "update_node", id: "a" }])).toThrow(CompileParseError);
  });
});
