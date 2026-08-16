// The chart pane: React Flow in VIEWER mode (spec §7's recommended sweet spot).
// Dragging/connecting are OFF — topology changes go through chat — but nodes stay
// selectable/clickable.
//
// Layout is two-pass: render the boxes once (so React Flow can MEASURE their real
// heights), then re-run dagre with those measured sizes. Variable-height boxes
// (multi-condition branches, to-dos with badges, wrapped reply prose) then never
// overlap, without us guessing pixel heights.

import { useEffect, useMemo, useRef } from "react";
import {
  ReactFlow,
  ReactFlowProvider,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useNodesInitialized,
  useReactFlow,
  type NodeMouseHandler,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import type { WorkflowGraph } from "../schema/types";
import type { ToolRegistry } from "../descriptors/types";
import { buildElements, dagreLayout } from "../render/layout";
import { nodeTypes } from "../render/nodes";

interface ChartProps {
  graph: WorkflowGraph;
  registry: ToolRegistry;
  onSelectNode?: (id: string) => void;
}

function ChartInner({ graph, registry, onSelectNode }: ChartProps) {
  const { nodes: built, edges } = useMemo(() => buildElements(graph, registry), [graph, registry]);
  const [nodes, setNodes, onNodesChange] = useNodesState(built);
  const initialized = useNodesInitialized();
  const { fitView } = useReactFlow();
  const laidOutFor = useRef<unknown>(null);

  // New graph → reset to the freshly-built (unpositioned) nodes so they re-measure.
  useEffect(() => {
    setNodes(built);
    laidOutFor.current = null;
  }, [built, setNodes]);

  // Once measured, lay out with the real DOM sizes (once per built graph).
  useEffect(() => {
    if (!initialized || laidOutFor.current === built) return;
    setNodes((cur) =>
      dagreLayout(cur, edges, (n) => ({
        width: n.measured?.width ?? 240,
        height: n.measured?.height ?? 120,
      })),
    );
    laidOutFor.current = built;
    requestAnimationFrame(() => fitView({ padding: 0.15 }));
  }, [initialized, built, edges, setNodes, fitView]);

  const handleClick: NodeMouseHandler = (_e, node) => onSelectNode?.(node.id);

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      onNodesChange={onNodesChange}
      onNodeClick={handleClick}
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

export function Chart(props: ChartProps) {
  // Provider so ChartInner can use useNodesInitialized / useReactFlow.
  return (
    <ReactFlowProvider>
      <ChartInner {...props} />
    </ReactFlowProvider>
  );
}
