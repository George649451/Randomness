# Flow Authoring Tool — chat + chart

The no-code surface where Ops composes workflows for the CS Assistant
(spec §7). This is the **foundation build**: schema, validator, diff engine,
and a faithful React Flow viewer. The chat→graph LLM compile layer is stubbed
and is the next piece to build.

## The one idea

**The workflow JSON is the single source of truth; the chart is a *render* of
it, never hand-drawn.** Reading the chart *is* reading what will run (spec §3, §7).
This deletes the expensive half of a node editor (drag/snap/connect/undo).

```
chat (edits, plain language)  →  targeted DIFF  →  workflow JSON  →  chart (renders)
                                                        ↑
                                              also editable directly here
```

## Layout

```
src/
  schema/        # the keystone JSON contract (§3) + load-time validator (§3.4)
    types.ts       node shapes: to-do / condition / reply
    validate.ts    footgun catcher → structured problems (not throws)
  diff/          # targeted graph diffs (§7) — pure, unit-tested
    diff.ts        applyDiff(graph, ops) + "here's what I changed"
  render/        # JSON → React Flow graph
    layout.ts      dagre auto-layout (loops / re-convergence, §7)
    nodes.tsx      each box shows actual content + kind/tier badges (§5.1)
  descriptors/   # mock tool registry (later GENERATED from adapter code, §5)
  samples/       # the canonical refund-status workflow
  ui/            # two-pane shell: <ChatStub/> | <Chart/>
```

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
npm run dev        # http://localhost:5173
npm test           # 19 tests over the diff engine + validator
npm run typecheck
npm run build
```

## What's deliberately NOT here yet

- **The brief→graph compile layer** (the bespoke LLM step that turns plain language
  into diffs and asks only the schema-missing clarifying questions). The `ChatStub`
  marks where it attaches; until then, drive the graph by editing JSON or pasting a
  `DiffOp[]`.
- **Click-to-edit node contents** — viewer mode supports it; the editing affordance
  is a later increment.
- **Sharing the schema with the runtime interpreter** (§4.3) — `src/schema` is written
  to be lifted into a shared package when the extension is built.
