# Flow Authoring Tool — chat + chart

The no-code surface where Ops composes workflows for the CS Assistant
(spec §7): schema, validator, diff engine, a faithful React Flow viewer, **and
the brief→graph LLM compile layer** that turns plain-language briefs into
validated diffs.

## The one idea

**The workflow JSON is the single source of truth; the chart is a *render* of
it, never hand-drawn.** Reading the chart *is* reading what will run (spec §3, §7).
This deletes the expensive half of a node editor (drag/snap/connect/undo).

```
chat (plain language) → model decides → DIFF → applyDiff → validate → workflow JSON → chart
                              │                                              ↑
                          ask | commit                            also editable directly here
```

## Layout

```
src/
  schema/        # the keystone JSON contract (§3) + load-time validator (§3.4)
    types.ts       node shapes: to-do / condition / reply
    validate.ts    footgun catcher → structured problems (not throws)
  diff/          # targeted graph diffs (§7) — pure, unit-tested
    diff.ts        applyDiff(graph, ops) + "here's what I changed"
  compile/       # brief → graph compile layer — the bespoke LLM step
    prompt.ts      pure: registry + schema rules → system prompt + tool schemas
    parse.ts       pure: model tool-call → typed ModelDecision (ask | commit)
    engine.ts      runTurn(): decision → applyDiff → validate (control flow in code)
    client-anthropic.ts  the only module that imports the SDK / hits the API
    client-fake.ts       scripted client for tests + offline dev
  render/        # JSON → React Flow graph
    layout.ts      dagre auto-layout (loops / re-convergence, §7)
    nodes.tsx      each box shows actual content + kind/tier badges (§5.1)
  descriptors/   # mock tool registry (later GENERATED from adapter code, §5)
  samples/       # the canonical refund-status workflow
  ui/            # two-pane shell: <Chat/> | <Chart/>
```

## The compile layer (the model is a leaf, not the wheel)

The model never edits the graph. Each turn it returns one of two **decisions** via
tool use — `ask_question` (a schema-missing clarification) or `commit_changes`
(targeted `DiffOp[]` + a one-line summary). Plain code (`engine.runTurn`) then
applies the diff with the pure `applyDiff` and re-validates — so control flow stays
deterministic and auditable (Directive 1). Key design points:

- **Targeted diffs, never whole-graph re-emit** (§7) — the model emits ops, not a
  new JSON blob, so a turn about node 3 can't reword node 7.
- **Commit-then-validate** — even an invalid commit is surfaced with its validation
  problems rather than silently dropped: the chart + problem list are the review
  surface.
- **Swappable client** (`CompileClient`) — `AnthropicCompileClient` in production,
  `FakeCompileClient` in tests, so the engine and UI are exercised without a network.
- **Model:** `claude-opus-4-8`, called client-side (`dangerouslyAllowBrowser`) — the
  spec's accepted tradeoff for an internal tool with trusted users (§4.2).

## Why these choices (traceable to the spec)

- **React Flow in viewer mode** (`nodesDraggable={false}`, `nodesConnectable={false}`,
  nodes still clickable) — the spec's recommended sweet spot (§7): topology from
  chat, contents from clicks. Not a full workflow-builder product (n8n/Flowise) —
  those bind JSON to their own runtime and can't run in a content script.
- **Diffs, never whole-graph re-emit** (§7) — a turn about node 3 must not silently
  reword node 7. `applyDiff` is a pure function precisely so it can be tested without
  an LLM, and so the LLM layer drops in on top of a proven core.
- **Validate at load, not execution** (§3.4) — unknown node targets, unknown tools,
  branches with no `else`, missing `start`, and `{{template}}`/`when` references to
  values that can't exist all become loud errors. Unknown keys are loud, never
  silently false.
- **`kind` (read/write) and `tier` (api/dom/a11y/vision)** are surfaced as badges on
  every tool box — the two properties that leak from an adapter into how a workflow
  behaves (§5.1, Appendix). The `write` badge marks where the human-approval gate (§8)
  will fire.

## Run

```bash
npm install
echo "VITE_ANTHROPIC_API_KEY=sk-ant-..." > .env.local   # enables the assistant
npm run dev        # http://localhost:5173
npm test           # 33 tests: diff engine, validator, compile parse + engine
npm run typecheck
npm run build
```

Without a key the assistant pane is disabled but the chart + JSON editor still work.

## What's deliberately NOT here yet

- **Click-to-edit node contents** — viewer mode supports it; the editing affordance
  is a later increment.
- **Auto-repair loop** — a failed-validation commit currently just shows its problems;
  feeding them back to the model for a fix is a natural follow-up.
- **Sharing the schema with the runtime interpreter** (§4.3) — `src/schema` is written
  to be lifted into a shared package when the extension is built.
