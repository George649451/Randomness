import { useMemo, useState } from "react";
import type { WorkflowGraph } from "./schema/types";
import { validateGraph, type Problem } from "./schema/validate";
import { mockRegistry } from "./descriptors/registry";
import { refundStatusWorkflow, sampleInitialKeys } from "./samples/refundStatus";
import { Chart } from "./ui/Chart";
import { Chat } from "./ui/Chat";

export default function App() {
  const [graph, setGraph] = useState<WorkflowGraph>(refundStatusWorkflow);
  const [jsonText, setJsonText] = useState(() => JSON.stringify(refundStatusWorkflow, null, 2));
  const [parseError, setParseError] = useState<string | null>(null);
  const [selected, setSelected] = useState<string | null>(null);

  const problems: Problem[] = useMemo(
    () => validateGraph(graph, { registry: mockRegistry, initialKeys: sampleInitialKeys }),
    [graph],
  );
  const errors = problems.filter((p) => p.severity === "error");
  const warnings = problems.filter((p) => p.severity === "warning");

  // Editing the JSON directly: parse, and only swap the rendered graph when valid JSON.
  const onJsonChange = (text: string) => {
    setJsonText(text);
    try {
      const parsed = JSON.parse(text) as WorkflowGraph;
      setGraph(parsed);
      setParseError(null);
    } catch (e) {
      setParseError((e as Error).message);
    }
  };

  // The compile loop commits a validated graph; mirror it into the JSON editor.
  const onCommit = (next: WorkflowGraph) => {
    setGraph(next);
    setJsonText(JSON.stringify(next, null, 2));
    setParseError(null);
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>CS Assistant — Flow Authoring</h1>
        <span className="app-sub">chat edits · chart displays · JSON is the source of truth</span>
      </header>

      <div className="app-body">
        <aside className="pane pane-left">
          <Chat
            graph={graph}
            registry={mockRegistry}
            initialKeys={sampleInitialKeys}
            onCommit={onCommit}
          />

          <div className="json-section">
            <label className="json-label">Workflow JSON (source of truth)</label>
            <textarea
              className="json-editor"
              value={jsonText}
              onChange={(e) => onJsonChange(e.target.value)}
              spellCheck={false}
            />
            {parseError && <div className="chat-error">JSON parse error: {parseError}</div>}

            <div className="problems">
              <div className="problems-summary">
                <span className={errors.length ? "pill pill-err" : "pill pill-ok"}>
                  {errors.length} error{errors.length === 1 ? "" : "s"}
                </span>
                <span className="pill pill-warn">{warnings.length} warning{warnings.length === 1 ? "" : "s"}</span>
              </div>
              {problems.length > 0 && (
                <ul className="problems-list">
                  {problems.map((p, i) => (
                    <li
                      key={i}
                      className={`problem problem-${p.severity}`}
                      onMouseEnter={() => p.nodeId && setSelected(p.nodeId)}
                    >
                      {p.nodeId && <code>{p.nodeId}</code>} {p.message}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </aside>

        <main className="pane pane-right">
          <Chart graph={graph} registry={mockRegistry} onSelectNode={setSelected} />
          {selected && (
            <div className="selection-tag">
              selected: <code>{selected}</code>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
