// The compile engine — the deterministic control flow around the model leaf.
//
// One turn: ask the client to decide → if it's an `ask`, surface the question; if
// it's a `commit`, apply the diff with the PURE applyDiff and re-validate, then
// hand back the new graph + summary + problems. The model never mutates the graph;
// this function does. Mirrors the runtime interpreter's stance (spec §4.3 / §7).

import { applyDiff, DiffError } from "../diff/diff";
import { validateGraph } from "../schema/validate";
import type { ToolRegistry } from "../descriptors/types";
import type { CompileClient, CompileInput, TurnResult } from "./types";

export interface RunTurnOptions {
  registry: ToolRegistry;
  initialKeys?: string[];
}

export async function runTurn(
  client: CompileClient,
  input: CompileInput,
  opts: RunTurnOptions,
): Promise<TurnResult> {
  let decision;
  try {
    decision = await client.decide(input);
  } catch (err) {
    return { kind: "error", message: `model call failed: ${(err as Error).message}` };
  }

  if (decision.kind === "ask") {
    return { kind: "ask", question: decision.question, reason: decision.reason };
  }

  // commit: apply the diff in code, then validate the result.
  let graph;
  try {
    graph = applyDiff(input.graph, decision.ops);
  } catch (err) {
    const why = err instanceof DiffError ? err.message : (err as Error).message;
    return { kind: "error", message: `proposed diff did not apply: ${why}` };
  }

  const problems = validateGraph(graph, {
    registry: opts.registry,
    initialKeys: opts.initialKeys ?? [],
  });

  return { kind: "commit", graph, ops: decision.ops, summary: decision.summary, problems };
}
