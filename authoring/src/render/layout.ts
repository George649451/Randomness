// JSON -> React Flow graph, with dagre auto-layout.
//
// The chart is a *render* of the workflow JSON, never hand-drawn (spec §7). Because
// workflows re-converge and may loop, we need real graph layout — dagre (spec §7
// budgets for "graph auto-layout tuning").

import Dagre from "@dagrejs/dagre";
import { MarkerType, type Edge, type Node } from "@xyflow/react";
import type { WorkflowGraph, WorkflowNode } from "../schema/types";
import { nodeKind } from "../schema/types";
import type { ToolRegistry, ToolDescriptor } from "../descriptors/types";

export interface FlowNodeData extends Record<string, unknown> {
  node: WorkflowNode;
  descriptor?: ToolDescriptor;
  isStart: boolean;
}

const NODE_W = 240;

// The boxes are variable-height (a branch lists every condition; a to-do shows
// args + badges; a reply's prose wraps). dagre needs a real height per node or it
// packs ranks too tightly and the boxes overlap. We estimate from content — the
// width is fixed (CSS .wf-node) so only height varies. Generous, to avoid collisions.
const LINE = 16; // px per text line
const CHARS_PER_LINE = 30; // ~chars that fit at 240px before wrapping

function wrappedLines(text: string): number {
  return Math.max(1, Math.ceil(text.length / CHARS_PER_LINE));
}

function estimateHeight(node: WorkflowNode, hasDescriptor: boolean): number {
  let body = 0;
  const kind = nodeKind(node);
  if (kind === "todo") {
    body += LINE; // the `do` tool name
    body += Object.keys(node.args ?? {}).length * LINE; // one line per arg
    if (node.as) body += LINE;
    if (hasDescriptor) body += 26; // kind/tier badge row
  } else if (kind === "condition") {
    for (const b of node.branch ?? []) {
      const text = "else" in b ? `else ${b.else}` : (b.when ?? `LLM ${b.when_llm}`);
      body += wrappedLines(text) * LINE;
    }
  } else if (kind === "reply") {
    body += wrappedLines(node.say ?? "") * LINE;
    if (node.next === null) body += LINE;
  } else {
    body += LINE;
  }
  // padding + header + a little buffer for estimation error
  return 28 + 22 + body + 12;
}


/** A labelled outgoing edge with a semantic role used for colouring. */
type EdgeRole = "ok" | "error" | "timeout" | "empty" | "branch" | "llm" | "else";

interface RawEdge {
  target: string;
  label: string;
  role: EdgeRole;
}

function edgesFor(node: WorkflowNode): RawEdge[] {
  const out: RawEdge[] = [];
  if (node.branch) {
    for (const b of node.branch) {
      if ("else" in b && b.else) {
        out.push({ target: b.else, label: "else", role: "else" });
      } else if (b.when && b.goto) {
        out.push({ target: b.goto, label: b.when, role: "branch" });
      } else if (b.when_llm && b.goto) {
        out.push({ target: b.goto, label: `LLM: ${b.when_llm}`, role: "llm" });
      }
    }
    return out;
  }
  if (node.next) out.push({ target: node.next, label: "ok", role: "ok" });
  if (node.on_empty) out.push({ target: node.on_empty, label: "empty", role: "empty" });
  if (node.on_timeout) out.push({ target: node.on_timeout, label: "timeout", role: "timeout" });
  if (node.on_error) out.push({ target: node.on_error, label: "error", role: "error" });
  return out;
}

const EDGE_COLOR: Record<EdgeRole, string> = {
  ok: "#16a34a",
  error: "#dc2626",
  timeout: "#d97706",
  empty: "#64748b",
  branch: "#2563eb",
  llm: "#9333ea",
  else: "#64748b",
};

export type SizeOf = (n: Node<FlowNodeData>) => { width: number; height: number };

/** Estimate sizes from content — the fallback before React Flow has measured. */
export const estimateSizeOf: SizeOf = (n) => ({
  width: NODE_W,
  height: estimateHeight(n.data.node, Boolean(n.data.descriptor)),
});

/** Build the nodes + edges (no final positions yet). */
export function buildElements(
  graph: WorkflowGraph,
  registry: ToolRegistry,
): { nodes: Node<FlowNodeData>[]; edges: Edge[] } {
  const edges: Edge[] = [];
  for (const n of graph.nodes) {
    edgesFor(n).forEach((e, i) => {
      edges.push({
        id: `${n.id}->${e.target}#${i}`,
        source: n.id,
        target: e.target,
        label: e.label.length > 40 ? e.label.slice(0, 39) + "…" : e.label,
        labelStyle: { fontSize: 11, fill: EDGE_COLOR[e.role] },
        labelBgStyle: { fill: "#fff", fillOpacity: 0.85 },
        style: { stroke: EDGE_COLOR[e.role], strokeWidth: 1.5 },
        markerEnd: { type: MarkerType.ArrowClosed, color: EDGE_COLOR[e.role] },
      });
    });
  }

  const nodes: Node<FlowNodeData>[] = graph.nodes.map((n) => ({
    id: n.id,
    type: nodeKind(n),
    position: { x: 0, y: 0 },
    data: {
      node: n,
      descriptor: n.do ? registry[n.do] : undefined,
      isStart: n.id === graph.start,
    },
  }));

  return { nodes, edges };
}

/** Pure dagre pass: position nodes top-down using the given size function. */
export function dagreLayout(
  nodes: Node<FlowNodeData>[],
  edges: Edge[],
  sizeOf: SizeOf,
): Node<FlowNodeData>[] {
  const g = new Dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: "TB", nodesep: 60, ranksep: 90 });

  const size = new Map<string, { width: number; height: number }>();
  for (const n of nodes) {
    const s = sizeOf(n);
    size.set(n.id, s);
    g.setNode(n.id, { ...s });
  }
  for (const e of edges) {
    if (g.hasNode(e.source) && g.hasNode(e.target)) g.setEdge(e.source, e.target);
  }

  Dagre.layout(g);

  return nodes.map((n) => {
    const pos = g.node(n.id);
    const s = size.get(n.id)!;
    return { ...n, position: { x: (pos?.x ?? 0) - s.width / 2, y: (pos?.y ?? 0) - s.height / 2 } };
  });
}

/** Convenience: build + lay out with estimated sizes (the initial, pre-measure pass). */
export function toFlow(
  graph: WorkflowGraph,
  registry: ToolRegistry,
): { nodes: Node<FlowNodeData>[]; edges: Edge[] } {
  const { nodes, edges } = buildElements(graph, registry);
  return { nodes: dagreLayout(nodes, edges, estimateSizeOf), edges };
}
