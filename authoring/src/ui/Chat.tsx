// The chat pane — the brief→graph compile loop, live.
//
// The author types a brief; runTurn() asks the model for a decision and, on a
// commit, applies the validated diff to the graph (lifted to App via onCommit).
// Each assistant turn is either a question (gather) or a "here's what I changed"
// card with the validation problems (commit) — the review surface (spec §7).

import { useMemo, useRef, useState } from "react";
import type { WorkflowGraph } from "../schema/types";
import type { ToolRegistry } from "../descriptors/types";
import type { Problem } from "../schema/validate";
import { runTurn } from "../compile/engine";
import { AnthropicCompileClient } from "../compile/client-anthropic";
import type { ChatMessage, CompileClient } from "../compile/types";

interface Item {
  role: "user" | "assistant";
  text: string;
  tone?: "ask" | "commit" | "error";
  problems?: Problem[];
}

export function Chat({
  graph,
  registry,
  initialKeys,
  onCommit,
}: {
  graph: WorkflowGraph;
  registry: ToolRegistry;
  initialKeys: string[];
  onCommit: (graph: WorkflowGraph, summary: string) => void;
}) {
  const [items, setItems] = useState<Item[]>([]);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Build the client once. Absent key → assistant disabled, JSON editor still works.
  const client = useMemo<CompileClient | null>(() => {
    const apiKey = import.meta.env.VITE_ANTHROPIC_API_KEY;
    if (!apiKey) return null;
    return new AnthropicCompileClient({ apiKey, registry, initialKeys });
  }, [registry, initialKeys]);

  const send = async () => {
    const brief = draft.trim();
    if (!brief || busy || !client) return;
    const history: ChatMessage[] = items.map((i) => ({ role: i.role, text: i.text }));
    setItems((prev) => [...prev, { role: "user", text: brief }]);
    setDraft("");
    setBusy(true);
    queueMicrotask(() => scrollRef.current?.scrollTo(0, scrollRef.current.scrollHeight));

    const result = await runTurn(client, { graph, history, brief }, { registry, initialKeys });
    setItems((prev) => {
      if (result.kind === "ask")
        return [...prev, { role: "assistant", text: result.question, tone: "ask" }];
      if (result.kind === "commit") {
        onCommit(result.graph, result.summary);
        return [...prev, { role: "assistant", text: result.summary, tone: "commit", problems: result.problems }];
      }
      return [...prev, { role: "assistant", text: result.message, tone: "error" }];
    });
    setBusy(false);
    queueMicrotask(() => scrollRef.current?.scrollTo(0, scrollRef.current.scrollHeight));
  };

  return (
    <div className="chat">
      <div className="chat-log" ref={scrollRef}>
        {items.length === 0 && (
          <div className="chat-empty">
            Describe a change in plain language — e.g. <em>“if the refund was rejected, open a
            dispute with the PSP”</em>. I’ll ask about anything the schema needs, then commit a
            diff and re-render the chart.
          </div>
        )}
        {items.map((it, i) => (
          <div key={i} className={`bubble bubble-${it.role} tone-${it.tone ?? "plain"}`}>
            {it.tone === "commit" && <span className="bubble-tag">committed</span>}
            {it.tone === "error" && <span className="bubble-tag err">error</span>}
            <div className="bubble-text">{it.text}</div>
            {it.problems && it.problems.length > 0 && (
              <ul className="bubble-problems">
                {it.problems.map((p, j) => (
                  <li key={j} className={`problem-${p.severity}`}>
                    {p.severity === "error" ? "✗" : "!"} {p.nodeId ? `${p.nodeId}: ` : ""}{p.message}
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))}
        {busy && <div className="bubble bubble-assistant tone-plain"><div className="bubble-text">…thinking</div></div>}
      </div>

      {!client && (
        <div className="chat-disabled">
          Set <code>VITE_ANTHROPIC_API_KEY</code> to enable the assistant. The JSON editor below
          works regardless.
        </div>
      )}

      <div className="chat-input">
        <textarea
          placeholder={client ? "Describe a change…" : "Assistant disabled (no API key)"}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              void send();
            }
          }}
          disabled={!client || busy}
          rows={2}
        />
        <button onClick={() => void send()} disabled={!client || busy || !draft.trim()}>
          Send
        </button>
      </div>
    </div>
  );
}
