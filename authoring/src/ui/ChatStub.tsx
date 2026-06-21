// The chat pane — STUBBED for the foundation build.
//
// In the full tool this is where an Ops author describes intent in plain language
// and the system asks only the schema-missing questions, then COMMITs targeted
// diffs to the JSON (spec §7). That brief→diff compile layer is the next piece to
// build; for now this pane explains the loop and lets you exercise the real diff
// pipeline by pasting a DiffOp[] — proving JSON→chart works before an LLM drives it.

import { useState } from "react";
import type { DiffOp } from "../diff/diff";

export function ChatStub({ onApplyDiff }: { onApplyDiff: (ops: DiffOp[]) => void }) {
  const [draft, setDraft] = useState("");
  const [error, setError] = useState<string | null>(null);

  const apply = () => {
    setError(null);
    try {
      const ops = JSON.parse(draft) as DiffOp[];
      if (!Array.isArray(ops)) throw new Error("expected an array of DiffOps");
      onApplyDiff(ops);
      setDraft("");
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <div className="chat-stub">
      <div className="chat-note">
        <strong>Chat → graph compile (next layer).</strong>
        <p>
          Here an Ops author will describe a workflow in plain language. The system asks
          only the questions the schema says are missing, then <em>commits</em> targeted
          diffs to the JSON — never re-emitting the whole graph. The chart re-renders when
          the JSON changes.
        </p>
        <p>
          Until that LLM layer is wired, drive the graph two ways: edit the{" "}
          <strong>Workflow JSON</strong> below directly, or paste a{" "}
          <code>DiffOp[]</code> here to exercise the real diff pipeline.
        </p>
      </div>

      <label className="chat-difflabel">Paste a diff (DiffOp[])</label>
      <textarea
        className="chat-diff"
        placeholder={`[{ "op": "update_node", "id": "reply_pending",\n   "set": { "say": "New wording…" } }]`}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        spellCheck={false}
      />
      {error && <div className="chat-error">{error}</div>}
      <button className="chat-apply" onClick={apply} disabled={!draft.trim()}>
        Apply diff
      </button>
    </div>
  );
}
