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
const NODE_H = 96;

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

export function toFlow(
  graph: WorkflowGraph,
  registry: ToolRegistry,
): { nodes: Node<FlowNodeData>[]; edges: Edge[] } {
  const g = new Dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: "TB", nodesep: 40, ranksep: 60 });

  for (const n of graph.nodes) g.setNode(n.id, { width: NODE_W, height: NODE_H });

  const edges: Edge[] = [];
  for (const n of graph.nodes) {
    edgesFor(n).forEach((e, i) => {
      // Only lay out edges whose target exists; the validator surfaces dangling ones.
      if (g.hasNode(e.target)) g.setEdge(n.id, e.target);
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

  Dagre.layout(g);

  const nodes: Node<FlowNodeData>[] = graph.nodes.map((n) => {
    const pos = g.node(n.id);
    return {
      id: n.id,
      type: nodeKind(n),
      position: { x: (pos?.x ?? 0) - NODE_W / 2, y: (pos?.y ?? 0) - NODE_H / 2 },
      data: {
        node: n,
        descriptor: n.do ? registry[n.do] : undefined,
        isStart: n.id === graph.start,
      },
    };
  });

  return { nodes, edges };
}
