// The chart pane: React Flow in VIEWER mode (spec §7's recommended sweet spot).
// Dragging/connecting are OFF — topology changes go through chat — but nodes stay
// selectable/clickable so node *contents* can later be edited with a click.

import { useMemo } from "react";
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  type NodeMouseHandler,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import type { WorkflowGraph } from "../schema/types";
import type { ToolRegistry } from "../descriptors/types";
import { toFlow } from "../render/layout";
import { nodeTypes } from "../render/nodes";

export function Chart({
  graph,
  registry,
  onSelectNode,
}: {
  graph: WorkflowGraph;
  registry: ToolRegistry;
  onSelectNode?: (id: string) => void;
}) {
  const { nodes, edges } = useMemo(() => toFlow(graph, registry), [graph, registry]);

  const handleClick: NodeMouseHandler = (_e, node) => onSelectNode?.(node.id);

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      onNodeClick={handleClick}
      // Viewer mode: render, don't author.
      nodesDraggable={false}
      nodesConnectable={false}
      elementsSelectable={true}
      edgesFocusable={false}
      fitView
      proOptions={{ hideAttribution: true }}
    >
      <Background />
      <Controls showInteractive={false} />
      <MiniMap pannable zoomable />
    </ReactFlow>
  );
}
