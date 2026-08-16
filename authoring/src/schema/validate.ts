// Load-time validation (spec §3.4) — "the footgun catcher".
//
// Validate when the workflow loads, not at execution. The runtime's validateGraph
// (spec §4.3) THROWS; an authoring UI instead wants structured problems it can show
// inline, so this returns a list. `assertValid` is the throwing wrapper for callers
// that want the runtime's behaviour.

import type { WorkflowGraph, WorkflowNode } from "./types";
import { nodeKind } from "./types";
import type { ToolRegistry } from "../descriptors/types";

export type Severity = "error" | "warning";

export interface Problem {
  severity: Severity;
  message: string;
  /** The node the problem is attached to, when applicable. */
  nodeId?: string;
}

export interface ValidateOptions {
  registry: ToolRegistry;
  /** Context keys assumed present before the workflow runs (e.g. `customer_id`). */
  initialKeys?: string[];
}

const EXPR_KEYWORDS = new Set([
  "and", "or", "not", "in", "true", "false", "null",
]);

/** Roots of `{{a.b.c}}` references in a string. */
function templateRoots(str: string): string[] {
  const roots: string[] = [];
  for (const m of str.matchAll(/\{\{\s*([\w.]+)\s*\}\}/g)) {
    roots.push(m[1].split(".")[0]);
  }
  return roots;
}

/** Roots of identifiers in a `when` expression (string literals stripped). */
function exprRoots(expr: string): string[] {
  const noStrings = expr.replace(/'[^']*'/g, "").replace(/"[^"]*"/g, "");
  const roots: string[] = [];
  for (const m of noStrings.matchAll(/[A-Za-z_]\w*(?:\.\w+)*/g)) {
    const root = m[0].split(".")[0];
    if (!EXPR_KEYWORDS.has(root)) roots.push(root);
  }
  return roots;
}

export function validateGraph(graph: WorkflowGraph, opts: ValidateOptions): Problem[] {
  const { registry, initialKeys = [] } = opts;
  const problems: Problem[] = [];
  const err = (message: string, nodeId?: string) =>
    problems.push({ severity: "error", message, nodeId });
  const warn = (message: string, nodeId?: string) =>
    problems.push({ severity: "warning", message, nodeId });

  if (!Array.isArray(graph.nodes) || graph.nodes.length === 0) {
    err("workflow has no nodes");
    return problems;
  }

  const ids = new Set<string>();
  for (const node of graph.nodes) {
    if (ids.has(node.id)) err(`duplicate node id "${node.id}"`, node.id);
    ids.add(node.id);
  }

  // The set of context roots that can ever exist: initial keys + every `do` node's output.
  const producedKeys = new Set<string>(initialKeys);
  for (const node of graph.nodes) {
    if (node.do) producedKeys.add(node.as ?? node.id);
  }

  // start must exist (spec §3.4).
  if (!graph.start) err("missing `start` node");
  else if (!ids.has(graph.start)) err(`start node "${graph.start}" does not exist`);

  const checkTarget = (target: string | null | undefined, label: string, nodeId: string) => {
    if (target == null) return;
    if (!ids.has(target)) err(`${label} points at unknown node "${target}"`, nodeId);
  };

  for (const node of graph.nodes) {
    const kind = nodeKind(node);
    if (kind === "unknown") {
      err(`node "${node.id}" is none of to-do / condition / reply (no \`do\`, \`branch\`, or \`say\`)`, node.id);
    }

    // --- routing targets exist (spec §3.4) ---
    checkTarget(node.next, "`next`", node.id);
    checkTarget(node.on_error, "`on_error`", node.id);
    checkTarget(node.on_timeout, "`on_timeout`", node.id);
    checkTarget(node.on_empty, "`on_empty`", node.id);

    // --- to-do checks ---
    if (node.do) {
      const tool = registry[node.do];
      if (!tool) {
        err(`uses unknown tool "${node.do}" (not in the registry)`, node.id);
      } else {
        // required-but-unmentioned inputs (drives clarifying questions, spec §5.1).
        for (const [name, spec] of Object.entries(tool.inputs)) {
          if (spec.required && !(node.args && name in node.args)) {
            err(`tool "${node.do}" requires input "${name}", not supplied in \`args\``, node.id);
          }
        }
      }
      // Three mandatory outcomes (spec §3.1): success(next), timeout, error.
      if (!node.on_error) err(`to-do "${node.id}" has no \`on_error\` exit`, node.id);
      if (!node.on_timeout) err(`to-do "${node.id}" has no \`on_timeout\` exit`, node.id);
      if (node.next === undefined && !node.branch) {
        warn(`to-do "${node.id}" has no \`next\` — is it a terminal action?`, node.id);
      }
    }

    // --- condition checks (spec §3.1) ---
    if (node.branch) {
      if (!node.branch.some((b) => "else" in b)) {
        err(`branch node "${node.id}" has no \`else\` — a no-match would hard-stop`, node.id);
      }
      for (const edge of node.branch) {
        if ("else" in edge) {
          checkTarget(edge.else, "branch `else`", node.id);
        } else {
          if (!edge.when && !edge.when_llm) {
            err(`branch edge in "${node.id}" has neither \`when\` nor \`when_llm\``, node.id);
          }
          checkTarget(edge.goto, "branch `goto`", node.id);
          if (edge.when) {
            for (const root of exprRoots(edge.when)) {
              if (!producedKeys.has(root)) {
                err(`\`when\` in "${node.id}" references unknown root "${root}"`, node.id);
              }
            }
          }
        }
      }
    }

    // --- template references (spec §3.4: unknown keys are loud, never silently false) ---
    const templates: string[] = [];
    if (node.say) templates.push(node.say);
    for (const v of Object.values(node.args ?? {})) {
      if (typeof v === "string") templates.push(v);
    }
    for (const t of templates) {
      for (const root of templateRoots(t)) {
        if (!producedKeys.has(root)) {
          err(`template in "${node.id}" references unknown value "{{${root}...}}"`, node.id);
        }
      }
    }
  }

  // Reachability from start (a thin graph is a quality signal, spec §9) — warning only.
  if (graph.start && ids.has(graph.start)) {
    const reachable = reachableFrom(graph);
    for (const node of graph.nodes) {
      if (!reachable.has(node.id)) {
        warn(`node "${node.id}" is unreachable from start`, node.id);
      }
    }
  }

  return problems;
}

function reachableFrom(graph: WorkflowGraph): Set<string> {
  const byId = new Map(graph.nodes.map((n) => [n.id, n]));
  const seen = new Set<string>();
  const stack = [graph.start];
  while (stack.length) {
    const id = stack.pop()!;
    if (seen.has(id) || !byId.has(id)) continue;
    seen.add(id);
    for (const t of outgoing(byId.get(id)!)) stack.push(t);
  }
  return seen;
}

/** All node ids a node can route to. Exported for the renderer (edge drawing). */
export function outgoing(node: WorkflowNode): string[] {
  const out: string[] = [];
  if (node.next) out.push(node.next);
  if (node.on_error) out.push(node.on_error);
  if (node.on_timeout) out.push(node.on_timeout);
  if (node.on_empty) out.push(node.on_empty);
  for (const b of node.branch ?? []) {
    if (b.goto) out.push(b.goto);
    if (b.else) out.push(b.else);
  }
  return out;
}

export function assertValid(graph: WorkflowGraph, opts: ValidateOptions): void {
  const errors = validateGraph(graph, opts).filter((p) => p.severity === "error");
  if (errors.length) {
    throw new Error("invalid graph:\n  - " + errors.map((p) => p.message).join("\n  - "));
  }
}
