// Custom node renderers. "Each box shows the actual content (the lookup, the
// condition, the reply wording), derived from the node's fields — never authored
// on the side. Reading the chart IS reading what will run." (spec §7)

import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import type { FlowNodeData } from "./layout";
import type { ToolDescriptor } from "../descriptors/types";

type Props = NodeProps<Node<FlowNodeData>>;

function Shell({
  isStart,
  accent,
  children,
}: {
  isStart: boolean;
  accent: string;
  children: React.ReactNode;
}) {
  return (
    <div className="wf-node" style={{ borderTopColor: accent }}>
      <Handle type="target" position={Position.Top} />
      {isStart && <span className="wf-startpip">start</span>}
      {children}
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

function Badges({ descriptor }: { descriptor?: ToolDescriptor }) {
  if (!descriptor) return null;
  return (
    <div className="wf-badges">
      <span className={`wf-badge kind-${descriptor.kind}`}>{descriptor.kind}</span>
      <span className={`wf-badge tier-${descriptor.tier}`}>{descriptor.tier}</span>
    </div>
  );
}

export function TodoNode({ data }: Props) {
  const { node, descriptor, isStart } = data;
  const args = Object.entries(node.args ?? {});
  return (
    <Shell isStart={isStart} accent="#0f766e">
      <div className="wf-head">
        <span className="wf-type">to-do</span>
        <span className="wf-id">{node.id}</span>
      </div>
      <div className="wf-do">{node.do}</div>
      {args.length > 0 && (
        <div className="wf-args">
          {args.map(([k, v]) => (
            <div key={k}>
              <code>{k}</code>: <code>{String(v)}</code>
            </div>
          ))}
        </div>
      )}
      {node.as && <div className="wf-as">→ {node.as}</div>}
      <Badges descriptor={descriptor} />
    </Shell>
  );
}

export function ConditionNode({ data }: Props) {
  const { node, isStart } = data;
  return (
    <Shell isStart={isStart} accent="#1d4ed8">
      <div className="wf-head">
        <span className="wf-type">branch</span>
        <span className="wf-id">{node.id}</span>
      </div>
      <ul className="wf-branch">
        {(node.branch ?? []).map((b, i) => (
          <li key={i}>
            {"else" in b ? (
              <span className="wf-else">else → {b.else}</span>
            ) : b.when ? (
              <span>
                <code>{b.when}</code>
              </span>
            ) : (
              <span className="wf-llm">LLM: {b.when_llm}</span>
            )}
          </li>
        ))}
      </ul>
    </Shell>
  );
}

export function ReplyNode({ data }: Props) {
  const { node, isStart } = data;
  return (
    <Shell isStart={isStart} accent="#7c3aed">
      <div className="wf-head">
        <span className="wf-type">reply</span>
        <span className="wf-id">{node.id}</span>
      </div>
      <div className="wf-say">“{node.say}”</div>
      {node.next === null && <div className="wf-end">end</div>}
    </Shell>
  );
}

export function UnknownNode({ data }: Props) {
  return (
    <Shell isStart={data.isStart} accent="#dc2626">
      <div className="wf-head">
        <span className="wf-type">?</span>
        <span className="wf-id">{data.node.id}</span>
      </div>
      <div className="wf-say">incomplete node</div>
    </Shell>
  );
}

export const nodeTypes = {
  todo: TodoNode,
  condition: ConditionNode,
  reply: ReplyNode,
  unknown: UnknownNode,
};
