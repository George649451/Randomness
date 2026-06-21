// The workflow graph — the keystone JSON contract (spec §3).
//
// A workflow is `{ start, nodes }`. This schema is the single interface between
// the authoring tool (this app) and the runtime interpreter. It is intentionally
// permissive in shape (a node may combine an action with routing, e.g. a `do`
// with a `next`) and made strict by the load-time validator (see validate.ts,
// spec §3.4) rather than by the type alone.

/** A branch edge inside a condition node (spec §3.1). */
export interface BranchEdge {
  /** Deterministic predicate over the run context, evaluated by code (spec §3.2). */
  when?: string;
  /** Semantic judgement, evaluated by the model returning true/false (spec §3.2). */
  when_llm?: string;
  /** Target node id for `when` / `when_llm` edges. */
  goto?: string;
  /** The fallback. Required on every branch (spec §3.1). */
  else?: string;
}

/**
 * A single workflow node. The three shapes (to-do / condition / reply) share one
 * interface; which fields are present determines the shape. The validator enforces
 * the invariants the type cannot (mandatory exits, required `else`, etc.).
 */
export interface WorkflowNode {
  id: string;

  // --- To-do (an action / lookup) -------------------------------------------
  /** Tool name; must exist in the registry (spec §3.1). */
  do?: string;
  /** Inputs, with `{{path.to.value}}` templating against the run context. */
  args?: Record<string, unknown>;
  /** Where the result is stored in context (defaults to the node id). */
  as?: string;
  /** Optional extra exit for "ran fine but returned nothing". */
  on_empty?: string;
  /** Mandatory failure exit on every to-do (spec §3.1 / §3.4). */
  on_error?: string;
  /** Mandatory timeout exit on every to-do (spec §3.1 / §3.4). */
  on_timeout?: string;

  // --- Condition (a branch) -------------------------------------------------
  /** Ordered branch edges; first match wins, `else` is the fallback. */
  branch?: BranchEdge[];

  // --- Reply (drafts text — does NOT send) ----------------------------------
  /** Prose instruction the model renders into reply text. Edited by ops in plain language. */
  say?: string;

  // --- Routing (shared) -----------------------------------------------------
  /** Success / unconditional next node. `null` terminates the workflow. */
  next?: string | null;
}

export interface WorkflowGraph {
  start: string;
  nodes: WorkflowNode[];
}

/** The shape of a node, derived from which fields are present. */
export type NodeKind = "todo" | "condition" | "reply" | "unknown";

export function nodeKind(node: WorkflowNode): NodeKind {
  if (node.branch) return "condition";
  if (node.do) return "todo";
  if (node.say != null) return "reply";
  return "unknown";
}
